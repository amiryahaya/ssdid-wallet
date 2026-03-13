package my.ssdid.wallet.platform.scan

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QrScannerAuthTest {

    @Test
    fun `parsePayload with requested_claims and accepted_algorithms`() {
        val raw = """{"action":"authenticate","server_url":"https://demo.ssdid.my","session_id":"abc-123","requested_claims":[{"key":"name","required":true},{"key":"phone","required":false}],"accepted_algorithms":["ED25519","KAZ_SIGN_192"]}"""
        val payload = QrScanner.parsePayload(raw)
        assertThat(payload).isNotNull()
        assertThat(payload!!.action).isEqualTo("authenticate")
        assertThat(payload.sessionId).isEqualTo("abc-123")
        assertThat(payload.resolvedClaims).hasSize(2)
        assertThat(payload.resolvedClaims[0].key).isEqualTo("name")
        assertThat(payload.resolvedClaims[0].required).isTrue()
        assertThat(payload.resolvedClaims[1].required).isFalse()
        assertThat(payload.acceptedAlgorithms).containsExactly("ED25519", "KAZ_SIGN_192")
    }

    @Test
    fun `parsePayload without optional auth fields still works`() {
        val raw = """{"action":"authenticate","server_url":"https://demo.ssdid.my"}"""
        val payload = QrScanner.parsePayload(raw)
        assertThat(payload).isNotNull()
        assertThat(payload!!.resolvedClaims).isEmpty()
        assertThat(payload.acceptedAlgorithms).isEmpty()
        assertThat(payload.sessionId).isEmpty()
        assertThat(payload.callbackUrl).isEmpty()
    }

    @Test
    fun `parsePayload with callback_url`() {
        val raw = """{"action":"authenticate","server_url":"https://demo.ssdid.my","callback_url":"myapp://cb"}"""
        val payload = QrScanner.parsePayload(raw)
        assertThat(payload).isNotNull()
        assertThat(payload!!.callbackUrl).isEqualTo("myapp://cb")
    }

    // --- Security validation tests ---

    @Test
    fun `parsePayload rejects http server_url`() {
        val raw = """{"action":"authenticate","server_url":"http://evil.example.com"}"""
        assertThat(QrScanner.parsePayload(raw)).isNull()
    }

    @Test
    fun `parsePayload rejects private IP server_url`() {
        val raw = """{"action":"authenticate","server_url":"https://192.168.1.1"}"""
        assertThat(QrScanner.parsePayload(raw)).isNull()
    }

    @Test
    fun `parsePayload rejects localhost server_url`() {
        val raw = """{"action":"authenticate","server_url":"https://localhost"}"""
        assertThat(QrScanner.parsePayload(raw)).isNull()
    }

    @Test
    fun `parsePayload rejects unknown action`() {
        val raw = """{"action":"evil","server_url":"https://demo.ssdid.my"}"""
        assertThat(QrScanner.parsePayload(raw)).isNull()
    }

    @Test
    fun `parsePayload rejects oversized payload`() {
        val padding = "x".repeat(4097)
        val raw = """{"action":"authenticate","server_url":"https://demo.ssdid.my","padding":"$padding"}"""
        assertThat(QrScanner.parsePayload(raw)).isNull()
    }

    @Test
    fun `parsePayload rejects malformed JSON`() {
        assertThat(QrScanner.parsePayload("{not valid json")).isNull()
    }

    @Test
    fun `parsePayload rejects credential-offer with blank offer_id`() {
        val raw = """{"action":"credential-offer","issuer_url":"https://issuer.ssdid.my","offer_id":""}"""
        assertThat(QrScanner.parsePayload(raw)).isNull()
    }

    @Test
    fun `parsePayload rejects credential-offer with http issuer_url`() {
        val raw = """{"action":"credential-offer","issuer_url":"http://evil.com","offer_id":"offer-1"}"""
        assertThat(QrScanner.parsePayload(raw)).isNull()
    }

    // --- Login action tests ---

    @Test
    fun `parsePayload with login action and service_url`() {
        val raw = """{"action":"login","service_url":"https://drive.ssdid.my","service_name":"SSDID Drive","challenge_id":"ch-001"}"""
        val payload = QrScanner.parsePayload(raw)
        assertThat(payload).isNotNull()
        assertThat(payload!!.action).isEqualTo("login")
        assertThat(payload.serviceUrl).isEqualTo("https://drive.ssdid.my")
        assertThat(payload.serviceName).isEqualTo("SSDID Drive")
        assertThat(payload.challengeId).isEqualTo("ch-001")
    }

    @Test
    fun `parsePayload with login action falls back to server_url`() {
        val raw = """{"action":"login","server_url":"https://drive.ssdid.my"}"""
        val payload = QrScanner.parsePayload(raw)
        assertThat(payload).isNotNull()
        assertThat(payload!!.action).isEqualTo("login")
    }

    @Test
    fun `parsePayload rejects login when both service_url and server_url are blank`() {
        val raw = """{"action":"login","service_name":"X"}"""
        assertThat(QrScanner.parsePayload(raw)).isNull()
    }

    @Test
    fun `parsePayload rejects login with http service_url`() {
        val raw = """{"action":"login","service_url":"http://evil.com"}"""
        assertThat(QrScanner.parsePayload(raw)).isNull()
    }

    @Test
    fun `parsePayload with login action and object format requested_claims`() {
        val raw = """{"action":"login","service_url":"https://drive.ssdid.my","requested_claims":{"required":["name"],"optional":["email"]}}"""
        val payload = QrScanner.parsePayload(raw)
        assertThat(payload).isNotNull()
        assertThat(payload!!.resolvedClaims).hasSize(2)
        assertThat(payload.resolvedClaims.first { it.key == "name" }.required).isTrue()
        assertThat(payload.resolvedClaims.first { it.key == "email" }.required).isFalse()
    }

    @Test
    fun `parsePayload with login action and list format requested_claims`() {
        val raw = """{"action":"login","service_url":"https://drive.ssdid.my","requested_claims":[{"key":"name","required":true}]}"""
        val payload = QrScanner.parsePayload(raw)
        assertThat(payload).isNotNull()
        assertThat(payload!!.resolvedClaims).hasSize(1)
        assertThat(payload.resolvedClaims[0].key).isEqualTo("name")
        assertThat(payload.resolvedClaims[0].required).isTrue()
    }

    @Test
    fun `parsePayload handles non-string elements in object claims gracefully`() {
        val raw = """{"action":"login","service_url":"https://drive.ssdid.my","requested_claims":{"required":[{"nested":"obj"}],"optional":["email"]}}"""
        val payload = QrScanner.parsePayload(raw)
        assertThat(payload).isNotNull()
        // Non-string "required" element should be skipped, "email" should parse
        assertThat(payload!!.resolvedClaims).hasSize(1)
        assertThat(payload.resolvedClaims[0].key).isEqualTo("email")
    }

    @Test
    fun `parsePayload copy preserves resolvedClaims`() {
        val raw = """{"action":"authenticate","server_url":"https://demo.ssdid.my","requested_claims":[{"key":"name","required":true}]}"""
        val payload = QrScanner.parsePayload(raw)
        assertThat(payload).isNotNull()
        val copied = payload!!.copy(sessionId = "new-id")
        assertThat(copied.resolvedClaims).hasSize(1)
        assertThat(copied.resolvedClaims[0].key).isEqualTo("name")
    }
}
