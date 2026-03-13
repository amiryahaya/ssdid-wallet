package my.ssdid.wallet.domain.oid4vp

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import my.ssdid.wallet.domain.sdjwt.Disclosure
import my.ssdid.wallet.domain.sdjwt.SdJwtIssuer
import org.junit.Test
import java.util.Base64

class VpTokenBuilderTest {

    private val dummySigner: (ByteArray) -> ByteArray = { it.copyOf(64) }

    private fun issueCredential(
        disclosableClaims: Map<String, String>,
        visibleClaims: Map<String, String> = emptyMap()
    ): my.ssdid.wallet.domain.sdjwt.SdJwtVc {
        val issuer = SdJwtIssuer(signer = dummySigner, algorithm = "EdDSA")
        val allClaims = (visibleClaims + disclosableClaims).mapValues { JsonPrimitive(it.value) }
        return issuer.issue(
            issuer = "did:ssdid:issuer123",
            subject = "did:ssdid:holder456",
            type = listOf("VerifiableCredential", "VerifiedEmployee"),
            claims = allClaims,
            disclosable = disclosableClaims.keys,
        )
    }

    @Test
    fun `builds VP token with selected disclosures and KB-JWT`() {
        val vc = issueCredential(
            disclosableClaims = mapOf("name" to "Alice", "email" to "alice@example.com")
        )

        val vpToken = VpTokenBuilder.build(
            credential = vc,
            selectedClaimNames = setOf("name", "email"),
            audience = "https://verifier.example.com",
            nonce = "test-nonce-123",
            algorithm = "EdDSA",
            signer = dummySigner
        )

        // Token structure: issuerJwt~disclosure1~disclosure2~...~kbJwt
        val parts = vpToken.split("~")
        // First part is the issuer JWT (has 3 dot-separated segments)
        assertThat(parts[0].split(".")).hasSize(3)
        // Last part is the KB-JWT (also 3 dot-separated segments)
        val kbJwt = parts.last()
        assertThat(kbJwt.split(".")).hasSize(3)
        // Middle parts are disclosures (2 in this case)
        val disclosureParts = parts.subList(1, parts.size - 1)
        assertThat(disclosureParts).hasSize(2)

        // Verify KB-JWT payload has correct aud and nonce
        val kbPayloadB64 = kbJwt.split(".")[1]
        val kbPayloadJson = String(Base64.getUrlDecoder().decode(kbPayloadB64), Charsets.UTF_8)
        val kbPayload = Json.parseToJsonElement(kbPayloadJson).jsonObject
        assertThat(kbPayload["aud"]?.jsonPrimitive?.content).isEqualTo("https://verifier.example.com")
        assertThat(kbPayload["nonce"]?.jsonPrimitive?.content).isEqualTo("test-nonce-123")
        assertThat(kbPayload["sd_hash"]).isNotNull()
    }

    @Test
    fun `builds presentation submission for PE response`() {
        val submission = PresentationSubmission(
            id = "submission-1",
            definitionId = "pd-1",
            descriptorMap = listOf(
                DescriptorMapEntry(
                    id = "input-1",
                    format = "vc+sd-jwt",
                    path = "$"
                )
            )
        )

        val json = submission.toJson()
        val parsed = Json.parseToJsonElement(json).jsonObject
        assertThat(parsed["definition_id"]?.jsonPrimitive?.content).isEqualTo("pd-1")

        // Round-trip through serialization
        val reparsed = Json.decodeFromString<PresentationSubmission>(json)
        assertThat(reparsed.definitionId).isEqualTo("pd-1")
        assertThat(reparsed.descriptorMap).hasSize(1)
        assertThat(reparsed.descriptorMap[0].id).isEqualTo("input-1")
        assertThat(reparsed.descriptorMap[0].format).isEqualTo("vc+sd-jwt")
        assertThat(reparsed.descriptorMap[0].path).isEqualTo("$")
    }

    @Test
    fun `selects only matching disclosures by claim name`() {
        val vc = issueCredential(
            disclosableClaims = mapOf(
                "name" to "Alice",
                "email" to "alice@example.com",
                "age" to "30"
            )
        )
        // All 3 disclosures exist in the VC
        assertThat(vc.disclosures).hasSize(3)

        // Select only 2 of the 3
        val vpToken = VpTokenBuilder.build(
            credential = vc,
            selectedClaimNames = setOf("name", "age"),
            audience = "https://verifier.example.com",
            nonce = "nonce-456",
            algorithm = "EdDSA",
            signer = dummySigner
        )

        val parts = vpToken.split("~")
        // issuerJwt + 2 disclosures + kbJwt = 4 parts
        assertThat(parts).hasSize(4)

        // Decode the two disclosure parts and verify claim names
        val disclosureClaimNames = parts.subList(1, parts.size - 1).map { encoded ->
            Disclosure.decode(encoded).claimName
        }.toSet()
        assertThat(disclosureClaimNames).containsExactly("name", "age")
    }
}
