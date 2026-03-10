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
        assertThat(payload.requestedClaims).hasSize(2)
        assertThat(payload.requestedClaims[0].key).isEqualTo("name")
        assertThat(payload.requestedClaims[0].required).isTrue()
        assertThat(payload.requestedClaims[1].required).isFalse()
        assertThat(payload.acceptedAlgorithms).containsExactly("ED25519", "KAZ_SIGN_192")
    }

    @Test
    fun `parsePayload without optional auth fields still works`() {
        val raw = """{"action":"authenticate","server_url":"https://demo.ssdid.my"}"""
        val payload = QrScanner.parsePayload(raw)
        assertThat(payload).isNotNull()
        assertThat(payload!!.requestedClaims).isEmpty()
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
}
