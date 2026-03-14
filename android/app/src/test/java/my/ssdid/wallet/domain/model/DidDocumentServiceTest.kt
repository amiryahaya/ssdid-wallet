package my.ssdid.wallet.domain.model

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class DidDocumentServiceTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `DidDocument with keyAgreement and service fields`() {
        val doc = DidDocument(
            id = "did:ssdid:test123",
            verificationMethod = listOf(
                VerificationMethod(
                    id = "did:ssdid:test123#key-1",
                    type = "Ed25519VerificationKey2020",
                    controller = "did:ssdid:test123",
                    publicKeyMultibase = "z6Mk..."
                )
            ),
            authentication = listOf("did:ssdid:test123#key-1"),
            keyAgreement = listOf("did:ssdid:test123#key-agree-1"),
            service = listOf(
                Service(
                    id = "did:ssdid:test123#didcomm",
                    type = "DIDCommMessaging",
                    serviceEndpoint = "https://example.com/didcomm"
                )
            )
        )

        assertThat(doc.keyAgreement).containsExactly("did:ssdid:test123#key-agree-1")
        assertThat(doc.service).hasSize(1)
        assertThat(doc.service[0].type).isEqualTo("DIDCommMessaging")
        assertThat(doc.service[0].serviceEndpoint).isEqualTo("https://example.com/didcomm")
    }

    @Test
    fun `serialization round-trip includes keyAgreement and service`() {
        val doc = DidDocument(
            id = "did:ssdid:test456",
            verificationMethod = listOf(
                VerificationMethod(
                    id = "did:ssdid:test456#key-1",
                    type = "Ed25519VerificationKey2020",
                    controller = "did:ssdid:test456"
                )
            ),
            keyAgreement = listOf("did:ssdid:test456#key-agree-1"),
            service = listOf(
                Service(
                    id = "did:ssdid:test456#messaging",
                    type = "DIDCommMessaging",
                    serviceEndpoint = "https://msg.example.com"
                )
            )
        )

        val encoded = json.encodeToString(DidDocument.serializer(), doc)
        assertThat(encoded).contains("keyAgreement")
        assertThat(encoded).contains("service")
        assertThat(encoded).contains("DIDCommMessaging")
        assertThat(encoded).contains("https://msg.example.com")

        val decoded = json.decodeFromString(DidDocument.serializer(), encoded)
        assertThat(decoded.keyAgreement).containsExactly("did:ssdid:test456#key-agree-1")
        assertThat(decoded.service).hasSize(1)
        assertThat(decoded.service[0].id).isEqualTo("did:ssdid:test456#messaging")
        assertThat(decoded.service[0].type).isEqualTo("DIDCommMessaging")
        assertThat(decoded.service[0].serviceEndpoint).isEqualTo("https://msg.example.com")
    }

    @Test
    fun `default empty lists for backward compatibility`() {
        val doc = DidDocument(
            id = "did:ssdid:minimal",
            verificationMethod = emptyList()
        )

        assertThat(doc.keyAgreement).isEmpty()
        assertThat(doc.service).isEmpty()
    }

    @Test
    fun `deserialization without keyAgreement or service fields uses defaults`() {
        val jsonString = """
            {
                "@context": ["https://www.w3.org/ns/did/v1"],
                "id": "did:ssdid:legacy",
                "controller": "",
                "verificationMethod": [],
                "authentication": [],
                "assertionMethod": [],
                "capabilityInvocation": []
            }
        """.trimIndent()

        val doc = json.decodeFromString(DidDocument.serializer(), jsonString)
        assertThat(doc.keyAgreement).isEmpty()
        assertThat(doc.service).isEmpty()
    }

    @Test
    fun `Service data class holds correct values`() {
        val service = Service(
            id = "did:ssdid:abc#svc-1",
            type = "DIDCommMessaging",
            serviceEndpoint = "https://mediator.example.com"
        )

        assertThat(service.id).isEqualTo("did:ssdid:abc#svc-1")
        assertThat(service.type).isEqualTo("DIDCommMessaging")
        assertThat(service.serviceEndpoint).isEqualTo("https://mediator.example.com")
    }
}
