package my.ssdid.wallet.domain.transport.dto

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AuthDtosTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonNoNulls = Json { explicitNulls = false }

    @Test
    fun `ClaimRequest serialization round trip`() {
        val request = ClaimRequest(key = "email", required = true)
        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<ClaimRequest>(encoded)
        assertThat(decoded).isEqualTo(request)
    }

    @Test
    fun `ClaimRequest default required is false`() {
        val request = ClaimRequest(key = "name")
        assertThat(request.required).isFalse()
    }

    @Test
    fun `AuthChallengeResponse deserialization from JSON string`() {
        val jsonStr = """
            {
                "challenge": "abc123",
                "server_name": "Demo Service",
                "server_did": "did:ssdid:server1",
                "server_key_id": "key-1"
            }
        """.trimIndent()
        val response = json.decodeFromString<AuthChallengeResponse>(jsonStr)
        assertThat(response.challenge).isEqualTo("abc123")
        assertThat(response.serverName).isEqualTo("Demo Service")
        assertThat(response.serverDid).isEqualTo("did:ssdid:server1")
        assertThat(response.serverKeyId).isEqualTo("key-1")
    }

    @Test
    fun `AuthChallengeResponse serializes with snake_case`() {
        val resp = AuthChallengeResponse(
            challenge = "abc",
            serverName = "Test",
            serverDid = "did:ssdid:s1",
            serverKeyId = "k1"
        )
        val encoded = json.encodeToString(resp)
        assertThat(encoded).contains("\"server_name\"")
        assertThat(encoded).contains("\"server_did\"")
        assertThat(encoded).contains("\"server_key_id\"")
    }

    @Test
    fun `AuthVerifyRequest serialization includes all fields including amr list`() {
        val request = AuthVerifyRequest(
            did = "did:ssdid:user1",
            keyId = "key-1",
            signedChallenge = "sig123",
            sharedClaims = mapOf("email" to "user@example.com"),
            amr = listOf("pqc", "biometric"),
            sessionId = "sess-1"
        )
        val encoded = json.encodeToString(request)
        assertThat(encoded).contains("\"did\":\"did:ssdid:user1\"")
        assertThat(encoded).contains("\"key_id\":\"key-1\"")
        assertThat(encoded).contains("\"signed_challenge\":\"sig123\"")
        assertThat(encoded).contains("\"shared_claims\"")
        assertThat(encoded).contains("\"email\"")
        assertThat(encoded).contains("\"amr\"")
        assertThat(encoded).contains("\"pqc\"")
        assertThat(encoded).contains("\"biometric\"")
        assertThat(encoded).contains("\"session_id\":\"sess-1\"")
    }

    @Test
    fun `AuthVerifyRequest with null session_id omits it from JSON`() {
        val request = AuthVerifyRequest(
            did = "did:ssdid:user1",
            keyId = "key-1",
            signedChallenge = "sig123",
            sharedClaims = emptyMap(),
            amr = listOf("pqc")
        )
        val encoded = jsonNoNulls.encodeToString(request)
        assertThat(encoded).doesNotContain("session_id")
    }

    @Test
    fun `AuthVerifyResponse deserialization`() {
        val jsonStr = """
            {
                "session_token": "token-xyz",
                "server_did": "did:ssdid:server1",
                "server_key_id": "key-2",
                "server_signature": "sig-abc"
            }
        """.trimIndent()
        val response = json.decodeFromString<AuthVerifyResponse>(jsonStr)
        assertThat(response.sessionToken).isEqualTo("token-xyz")
        assertThat(response.serverDid).isEqualTo("did:ssdid:server1")
        assertThat(response.serverKeyId).isEqualTo("key-2")
        assertThat(response.serverSignature).isEqualTo("sig-abc")
    }
}
