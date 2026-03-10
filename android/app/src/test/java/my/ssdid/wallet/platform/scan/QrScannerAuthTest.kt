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
}
