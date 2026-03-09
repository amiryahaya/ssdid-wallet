package my.ssdid.wallet.domain.transport.dto

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import my.ssdid.wallet.domain.model.*
import org.junit.Test

class DtoSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // --- Registry DTOs ---

    @Test
    fun `RegisterDidRequest serializes with did_document and proof`() {
        val doc = DidDocument(
            id = "did:ssdid:test",
            controller = "did:ssdid:test",
            verificationMethod = listOf(
                VerificationMethod(
                    id = "did:ssdid:test#key-1",
                    type = "Ed25519VerificationKey2020",
                    controller = "did:ssdid:test",
                    publicKeyMultibase = "uPubKey"
                )
            ),
            authentication = listOf("did:ssdid:test#key-1"),
            assertionMethod = listOf("did:ssdid:test#key-1")
        )
        val proof = Proof(
            type = "Ed25519Signature2020",
            created = "2026-03-06T00:00:00Z",
            verificationMethod = "did:ssdid:test#key-1",
            proofPurpose = "assertionMethod",
            proofValue = "uSig"
        )
        val request = RegisterDidRequest(doc, proof)
        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<RegisterDidRequest>(encoded)

        assertThat(decoded.did_document.id).isEqualTo("did:ssdid:test")
        assertThat(decoded.proof.proofValue).isEqualTo("uSig")
    }

    @Test
    fun `RegisterDidResponse round trips`() {
        val resp = RegisterDidResponse(did = "did:ssdid:test", status = "registered")
        val encoded = json.encodeToString(resp)
        val decoded = json.decodeFromString<RegisterDidResponse>(encoded)

        assertThat(decoded.did).isEqualTo("did:ssdid:test")
        assertThat(decoded.status).isEqualTo("registered")
    }

    @Test
    fun `ChallengeResponse round trips with optional expires_at`() {
        val resp = ChallengeResponse(challenge = "abc123", expires_at = "2026-03-06T01:00:00Z")
        val decoded = json.decodeFromString<ChallengeResponse>(json.encodeToString(resp))
        assertThat(decoded.challenge).isEqualTo("abc123")
        assertThat(decoded.expires_at).isEqualTo("2026-03-06T01:00:00Z")

        val respNoExpiry = ChallengeResponse(challenge = "def456")
        val decodedNoExpiry = json.decodeFromString<ChallengeResponse>(json.encodeToString(respNoExpiry))
        assertThat(decodedNoExpiry.expires_at).isNull()
    }

    // --- Server DTOs ---

    @Test
    fun `RegisterStartRequest round trips`() {
        val req = RegisterStartRequest(did = "did:ssdid:test", key_id = "did:ssdid:test#key-1")
        val decoded = json.decodeFromString<RegisterStartRequest>(json.encodeToString(req))
        assertThat(decoded.did).isEqualTo("did:ssdid:test")
        assertThat(decoded.key_id).isEqualTo("did:ssdid:test#key-1")
    }

    @Test
    fun `RegisterStartResponse round trips`() {
        val resp = RegisterStartResponse(
            challenge = "ch",
            server_did = "did:ssdid:server",
            server_key_id = "did:ssdid:server#key-1",
            server_signature = "uSig"
        )
        val decoded = json.decodeFromString<RegisterStartResponse>(json.encodeToString(resp))
        assertThat(decoded.challenge).isEqualTo("ch")
        assertThat(decoded.server_did).isEqualTo("did:ssdid:server")
    }

    @Test
    fun `RegisterVerifyRequest round trips`() {
        val req = RegisterVerifyRequest(did = "did:ssdid:test", key_id = "did:ssdid:test#key-1", signed_challenge = "uSigned")
        val decoded = json.decodeFromString<RegisterVerifyRequest>(json.encodeToString(req))
        assertThat(decoded.signed_challenge).isEqualTo("uSigned")
    }

    @Test
    fun `RegisterVerifyResponse round trips with VerifiableCredential`() {
        val vc = VerifiableCredential(
            id = "urn:uuid:vc-1",
            type = listOf("VerifiableCredential"),
            issuer = "did:ssdid:issuer",
            issuanceDate = "2026-03-06T00:00:00Z",
            credentialSubject = CredentialSubject(id = "did:ssdid:holder"),
            proof = Proof(
                type = "Ed25519Signature2020",
                created = "2026-03-06T00:00:00Z",
                verificationMethod = "did:ssdid:issuer#key-1",
                proofPurpose = "assertionMethod",
                proofValue = "uABC"
            )
        )
        val resp = RegisterVerifyResponse(credential = vc)
        val decoded = json.decodeFromString<RegisterVerifyResponse>(json.encodeToString(resp))
        assertThat(decoded.credential.id).isEqualTo("urn:uuid:vc-1")
        assertThat(decoded.credential.credentialSubject.id).isEqualTo("did:ssdid:holder")
    }

    @Test
    fun `AuthenticateRequest round trips`() {
        val vc = VerifiableCredential(
            id = "urn:uuid:vc-1",
            type = listOf("VerifiableCredential"),
            issuer = "did:ssdid:issuer",
            issuanceDate = "2026-03-06T00:00:00Z",
            credentialSubject = CredentialSubject(id = "did:ssdid:holder"),
            proof = Proof(
                type = "Ed25519Signature2020",
                created = "2026-03-06T00:00:00Z",
                verificationMethod = "did:ssdid:issuer#key-1",
                proofPurpose = "assertionMethod",
                proofValue = "uABC"
            )
        )
        val req = AuthenticateRequest(credential = vc)
        val decoded = json.decodeFromString<AuthenticateRequest>(json.encodeToString(req))
        assertThat(decoded.credential.issuer).isEqualTo("did:ssdid:issuer")
    }

    @Test
    fun `AuthenticateResponse round trips with optional server_signature`() {
        val withSig = AuthenticateResponse(
            session_token = "tok",
            server_did = "did:ssdid:server",
            server_key_id = "did:ssdid:server#key-1",
            server_signature = "uSig"
        )
        val decoded1 = json.decodeFromString<AuthenticateResponse>(json.encodeToString(withSig))
        assertThat(decoded1.server_signature).isEqualTo("uSig")

        val withoutSig = AuthenticateResponse(
            session_token = "tok",
            server_did = "did:ssdid:server",
            server_key_id = "did:ssdid:server#key-1",
            server_signature = null
        )
        val decoded2 = json.decodeFromString<AuthenticateResponse>(json.encodeToString(withoutSig))
        assertThat(decoded2.server_signature).isNull()
    }

    @Test
    fun `TxChallengeRequest round trips`() {
        val req = TxChallengeRequest(session_token = "session-abc")
        val decoded = json.decodeFromString<TxChallengeRequest>(json.encodeToString(req))
        assertThat(decoded.session_token).isEqualTo("session-abc")
    }

    @Test
    fun `TxChallengeResponse round trips with transaction map`() {
        val resp = TxChallengeResponse(
            challenge = "ch-1",
            transaction = mapOf("amount" to "100", "to" to "Bob")
        )
        val decoded = json.decodeFromString<TxChallengeResponse>(json.encodeToString(resp))
        assertThat(decoded.challenge).isEqualTo("ch-1")
        assertThat(decoded.transaction).containsEntry("amount", "100")
        assertThat(decoded.transaction).containsEntry("to", "Bob")
    }

    @Test
    fun `TxChallengeResponse defaults to empty transaction map`() {
        val jsonStr = """{"challenge":"ch"}"""
        val decoded = json.decodeFromString<TxChallengeResponse>(jsonStr)
        assertThat(decoded.transaction).isEmpty()
    }

    @Test
    fun `TxSubmitRequest round trips`() {
        val req = TxSubmitRequest(
            session_token = "tok",
            did = "did:ssdid:test",
            key_id = "did:ssdid:test#key-1",
            signed_challenge = "uSigned",
            transaction = mapOf("amount" to "50")
        )
        val decoded = json.decodeFromString<TxSubmitRequest>(json.encodeToString(req))
        assertThat(decoded.session_token).isEqualTo("tok")
        assertThat(decoded.transaction).containsEntry("amount", "50")
    }

    @Test
    fun `TxSubmitResponse round trips`() {
        val resp = TxSubmitResponse(transaction_id = "tx-001", status = "confirmed")
        val decoded = json.decodeFromString<TxSubmitResponse>(json.encodeToString(resp))
        assertThat(decoded.transaction_id).isEqualTo("tx-001")
        assertThat(decoded.status).isEqualTo("confirmed")
    }

    // --- Spec compliance additions ---

    @Test
    fun `RegistryInfoResponse deserializes from server JSON`() {
        val serverJson = """
            {
                "name": "SSDID Registry",
                "version": "1.0.0",
                "did_method": "ssdid",
                "supported_algorithms": ["Ed25519VerificationKey2020", "MlDsa44VerificationKey2024"],
                "supported_proof_types": ["Ed25519Signature2020"],
                "policies": {"proof_max_age_seconds": 300}
            }
        """.trimIndent()
        val decoded = json.decodeFromString<RegistryInfoResponse>(serverJson)
        assertThat(decoded.name).isEqualTo("SSDID Registry")
        assertThat(decoded.version).isEqualTo("1.0.0")
        assertThat(decoded.did_method).isEqualTo("ssdid")
        assertThat(decoded.supported_algorithms).hasSize(2)
        assertThat(decoded.policies?.proof_max_age_seconds).isEqualTo(300)
    }

    @Test
    fun `AuthenticateResponse includes optional status and did fields`() {
        val serverJson = """
            {
                "session_token": "tok",
                "server_did": "did:ssdid:server",
                "server_key_id": "did:ssdid:server#key-1",
                "server_signature": "uSig",
                "status": "authenticated",
                "did": "did:ssdid:client"
            }
        """.trimIndent()
        val decoded = json.decodeFromString<AuthenticateResponse>(serverJson)
        assertThat(decoded.status).isEqualTo("authenticated")
        assertThat(decoded.did).isEqualTo("did:ssdid:client")
    }

    @Test
    fun `AuthenticateResponse works without status and did fields`() {
        val serverJson = """
            {
                "session_token": "tok",
                "server_did": "did:ssdid:server",
                "server_key_id": "did:ssdid:server#key-1"
            }
        """.trimIndent()
        val decoded = json.decodeFromString<AuthenticateResponse>(serverJson)
        assertThat(decoded.status).isNull()
        assertThat(decoded.did).isNull()
    }

    @Test
    fun `TxChallengeResponse includes optional did field`() {
        val serverJson = """{"challenge":"ch","did":"did:ssdid:client"}"""
        val decoded = json.decodeFromString<TxChallengeResponse>(serverJson)
        assertThat(decoded.did).isEqualTo("did:ssdid:client")
    }

    @Test
    fun `ChallengeResponse includes optional domain field`() {
        val serverJson = """{"challenge":"abc","expires_at":"2026-01-01T00:05:00Z","domain":"registry.ssdid.my"}"""
        val decoded = json.decodeFromString<ChallengeResponse>(serverJson)
        assertThat(decoded.domain).isEqualTo("registry.ssdid.my")
    }
}
