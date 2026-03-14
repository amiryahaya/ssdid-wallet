package my.ssdid.wallet.domain.model

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

class VerifiableCredentialTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun minimalVcJson(context: String) = """
        {
            "@context": ["$context"],
            "id": "urn:uuid:test-1",
            "type": ["VerifiableCredential"],
            "issuer": "did:ssdid:issuer1",
            "issuanceDate": "2024-06-01T00:00:00Z",
            "credentialSubject": {"id": "did:ssdid:holder1"},
            "proof": {
                "type": "Ed25519Signature2020",
                "created": "2024-06-01T00:00:00Z",
                "verificationMethod": "did:ssdid:issuer1#key-1",
                "proofPurpose": "assertionMethod",
                "proofValue": "uSignatureValue"
            }
        }
    """.trimIndent()

    @Test
    fun `new VC defaults to v2 context`() {
        val vc = VerifiableCredential(
            id = "urn:uuid:test-1",
            type = listOf("VerifiableCredential"),
            issuer = "did:ssdid:issuer1",
            issuanceDate = "2024-06-01T00:00:00Z",
            credentialSubject = CredentialSubject(id = "did:ssdid:holder1"),
            proof = Proof(
                type = "Ed25519Signature2020",
                created = "2024-06-01T00:00:00Z",
                verificationMethod = "did:ssdid:issuer1#key-1",
                proofPurpose = "assertionMethod",
                proofValue = "uSignatureValue"
            )
        )
        assertThat(vc.context).containsExactly("https://www.w3.org/ns/credentials/v2")
    }

    @Test
    fun `VC with old v1 context deserializes correctly`() {
        val vc = json.decodeFromString<VerifiableCredential>(
            minimalVcJson("https://www.w3.org/2018/credentials/v1")
        )
        assertThat(vc.context).contains("https://www.w3.org/2018/credentials/v1")
        assertThat(vc.id).isEqualTo("urn:uuid:test-1")
    }

    @Test
    fun `VC with new v2 context deserializes correctly`() {
        val vc = json.decodeFromString<VerifiableCredential>(
            minimalVcJson("https://www.w3.org/ns/credentials/v2")
        )
        assertThat(vc.context).contains("https://www.w3.org/ns/credentials/v2")
    }

    @Test
    fun `VC round-trips through serialization`() {
        val vc = VerifiableCredential(
            id = "urn:uuid:round-trip",
            type = listOf("VerifiableCredential"),
            issuer = "did:ssdid:issuer1",
            issuanceDate = "2024-06-01T00:00:00Z",
            credentialSubject = CredentialSubject(id = "did:ssdid:holder1"),
            proof = Proof(
                type = "Ed25519Signature2020",
                created = "2024-06-01T00:00:00Z",
                verificationMethod = "did:ssdid:issuer1#key-1",
                proofPurpose = "assertionMethod",
                proofValue = "uSignatureValue"
            )
        )
        val encoded = json.encodeToString(vc)
        val decoded = json.decodeFromString<VerifiableCredential>(encoded)
        assertThat(decoded).isEqualTo(vc)
        assertThat(encoded).contains("https://www.w3.org/ns/credentials/v2")
    }
}
