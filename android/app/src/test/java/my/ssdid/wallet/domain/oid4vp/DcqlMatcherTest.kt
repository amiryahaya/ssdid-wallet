package my.ssdid.wallet.domain.oid4vp

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import my.ssdid.wallet.domain.sdjwt.StoredSdJwtVc
import org.junit.Test

class DcqlMatcherTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val matcher = DcqlMatcher()

    private val credential = StoredSdJwtVc(
        id = "vc-1", compact = "eyJ...",
        issuer = "did:ssdid:issuer1", subject = "did:ssdid:holder1",
        type = "IdentityCredential",
        claims = mapOf("name" to "Ahmad", "email" to "a@example.com"),
        disclosableClaims = listOf("name", "email"),
        issuedAt = 1700000000L
    )

    @Test
    fun matchByVctValues() {
        val dcql = json.parseToJsonElement("""{"credentials":[{"id":"cred-1","format":"vc+sd-jwt","meta":{"vct_values":["IdentityCredential"]}}]}""") as JsonObject
        val results = matcher.match(dcql, listOf(credential))
        assertThat(results).hasSize(1)
        assertThat(results[0].descriptorId).isEqualTo("cred-1")
    }

    @Test
    fun noMatchWhenVctDiffers() {
        val dcql = json.parseToJsonElement("""{"credentials":[{"id":"cred-1","format":"vc+sd-jwt","meta":{"vct_values":["DriverLicense"]}}]}""") as JsonObject
        val results = matcher.match(dcql, listOf(credential))
        assertThat(results).isEmpty()
    }

    @Test
    fun matchWithClaimsPaths() {
        val dcql = json.parseToJsonElement("""{"credentials":[{"id":"cred-1","format":"vc+sd-jwt","meta":{"vct_values":["IdentityCredential"]},"claims":[{"path":["name"]},{"path":["email"],"optional":true}]}]}""") as JsonObject
        val results = matcher.match(dcql, listOf(credential))
        assertThat(results).hasSize(1)
        assertThat(results[0].requiredClaims).contains("name")
        assertThat(results[0].optionalClaims).contains("email")
    }

    @Test
    fun missingCredentialId() {
        val dcql = json.parseToJsonElement("""{"credentials":[{"format":"vc+sd-jwt"}]}""") as JsonObject
        val results = matcher.match(dcql, listOf(credential))
        assertThat(results).isEmpty()
    }

    @Test
    fun skipNonSdJwtFormat() {
        val dcql = json.parseToJsonElement("""{"credentials":[{"id":"cred-1","format":"mso_mdoc","meta":{"vct_values":["IdentityCredential"]}}]}""") as JsonObject
        val results = matcher.match(dcql, listOf(credential))
        assertThat(results).isEmpty()
    }

    // --- G14: null format branch in matchAll ---

    @Test
    fun matchAllWithNullFormatMatchesSdJwt() {
        // When format is null in matchAll, it should match SD-JWT credentials
        val dcql = json.parseToJsonElement("""{"credentials":[{"id":"cred-1","meta":{"vct_values":["IdentityCredential"]}}]}""") as JsonObject
        val results = matcher.matchAll(dcql, listOf(credential), emptyList())
        assertThat(results).hasSize(1)
        assertThat(results[0].descriptorId).isEqualTo("cred-1")
    }

    @Test
    fun matchAllWithMsoMdocFormatMatchesMDocs() {
        val mdoc = my.ssdid.wallet.domain.mdoc.StoredMDoc(
            id = "mdoc-1",
            docType = "org.iso.18013.5.1.mDL",
            issuerSignedCbor = byteArrayOf(),
            deviceKeyId = "key-1",
            issuedAt = 1700000000L,
            nameSpaces = mapOf("org.iso.18013.5.1" to listOf("family_name"))
        )
        val dcql = json.parseToJsonElement("""{"credentials":[{"id":"cred-1","format":"mso_mdoc","meta":{"doctype_value":"org.iso.18013.5.1.mDL"}}]}""") as JsonObject
        val results = matcher.matchAll(dcql, emptyList(), listOf(mdoc))
        assertThat(results).hasSize(1)
        assertThat(results[0].descriptorId).isEqualTo("cred-1")
    }

    @Test
    fun matchWithoutClaimsSpec() {
        val dcql = json.parseToJsonElement("""{"credentials":[{"id":"cred-1","format":"vc+sd-jwt","meta":{"vct_values":["IdentityCredential"]}}]}""") as JsonObject
        val results = matcher.match(dcql, listOf(credential))
        assertThat(results).hasSize(1)
        // When no claims specified, all disclosable claims are available
        assertThat(results[0].requiredClaims).containsAtLeast("name", "email")
    }
}
