package my.ssdid.mobile.domain.model

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

class ModelPrerequisitesTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `DidDocument deserializes with nextKeyHash`() {
        val raw = """{"@context":["https://www.w3.org/ns/did/v1"],"id":"did:ssdid:abc","controller":"did:ssdid:abc","verificationMethod":[],"authentication":[],"assertionMethod":[],"nextKeyHash":"uSHA3hash"}"""
        val doc = json.decodeFromString<DidDocument>(raw)
        assertThat(doc.nextKeyHash).isEqualTo("uSHA3hash")
    }

    @Test
    fun `DidDocument deserializes without nextKeyHash`() {
        val raw = """{"@context":["https://www.w3.org/ns/did/v1"],"id":"did:ssdid:abc","controller":"did:ssdid:abc","verificationMethod":[],"authentication":[],"assertionMethod":[]}"""
        val doc = json.decodeFromString<DidDocument>(raw)
        assertThat(doc.nextKeyHash).isNull()
    }

    @Test
    fun `CredentialStatus serialization round-trip`() {
        val status = CredentialStatus(
            id = "https://reg.example/api/status/1#42",
            type = "BitstringStatusListEntry",
            statusPurpose = "revocation",
            statusListIndex = "42",
            statusListCredential = "https://reg.example/api/status/1"
        )
        val encoded = Json.encodeToString(status)
        val decoded = Json.decodeFromString<CredentialStatus>(encoded)
        assertThat(decoded).isEqualTo(status)
    }

    @Test
    fun `VerifiableCredential deserializes with credentialStatus`() {
        val raw = """{"@context":["https://www.w3.org/2018/credentials/v1"],"id":"urn:vc:1","type":["VerifiableCredential"],"issuer":"did:ssdid:issuer","issuanceDate":"2026-01-01T00:00:00Z","credentialSubject":{"id":"did:ssdid:subject"},"credentialStatus":{"id":"https://reg.example/api/status/1#42","type":"BitstringStatusListEntry","statusPurpose":"revocation","statusListIndex":"42","statusListCredential":"https://reg.example/api/status/1"},"proof":{"type":"Ed25519Signature2020","created":"2026-01-01T00:00:00Z","verificationMethod":"did:ssdid:issuer#key-1","proofPurpose":"assertionMethod","proofValue":"uABC"}}"""
        val vc = json.decodeFromString<VerifiableCredential>(raw)
        assertThat(vc.credentialStatus).isNotNull()
        assertThat(vc.credentialStatus!!.statusListIndex).isEqualTo("42")
    }

    @Test
    fun `VerifiableCredential deserializes without credentialStatus`() {
        val raw = """{"@context":["https://www.w3.org/2018/credentials/v1"],"id":"urn:vc:1","type":["VerifiableCredential"],"issuer":"did:ssdid:issuer","issuanceDate":"2026-01-01T00:00:00Z","credentialSubject":{"id":"did:ssdid:subject"},"proof":{"type":"Ed25519Signature2020","created":"2026-01-01T00:00:00Z","verificationMethod":"did:ssdid:issuer#key-1","proofPurpose":"assertionMethod","proofValue":"uABC"}}"""
        val vc = json.decodeFromString<VerifiableCredential>(raw)
        assertThat(vc.credentialStatus).isNull()
    }

    @Test
    fun `Identity deserializes with recovery fields`() {
        val identity = Identity(
            name = "Test",
            did = "did:ssdid:abc",
            keyId = "did:ssdid:abc#key-1",
            algorithm = Algorithm.ED25519,
            publicKeyMultibase = "uABC",
            createdAt = "2026-01-01T00:00:00Z",
            recoveryKeyId = "did:ssdid:abc#key-1-recovery",
            hasRecoveryKey = true,
            preRotatedKeyId = "did:ssdid:abc#key-2"
        )
        val encoded = Json.encodeToString(identity)
        val decoded = Json.decodeFromString<Identity>(encoded)
        assertThat(decoded.recoveryKeyId).isEqualTo("did:ssdid:abc#key-1-recovery")
        assertThat(decoded.hasRecoveryKey).isTrue()
        assertThat(decoded.preRotatedKeyId).isEqualTo("did:ssdid:abc#key-2")
    }
}
