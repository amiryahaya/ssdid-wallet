package my.ssdid.wallet.platform.deeplink

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeepLinkHandlerTest {

    private fun mockUri(
        scheme: String? = "ssdid",
        host: String? = null,
        queryParams: Map<String, String?> = emptyMap()
    ): Uri = mockk {
        every { getScheme() } returns scheme
        every { getHost() } returns host
        every { getQueryParameter(any()) } answers {
            queryParams[firstArg()]
        }
    }

    @Test
    fun `parse register deep link returns correct action`() {
        val uri = mockUri(
            scheme = "ssdid",
            host = "register",
            queryParams = mapOf(
                "server_url" to "https://demo.ssdid.my",
                "server_did" to "did:ssdid:server:demo"
            )
        )
        val result = DeepLinkHandler.parse(uri)

        assertThat(result).isNotNull()
        assertThat(result!!.action).isEqualTo("register")
        assertThat(result.serverUrl).isEqualTo("https://demo.ssdid.my")
        assertThat(result.serverDid).isEqualTo("did:ssdid:server:demo")
        assertThat(result.sessionToken).isEmpty()
    }

    @Test
    fun `parse authenticate deep link returns correct action`() {
        val uri = mockUri(
            scheme = "ssdid",
            host = "authenticate",
            queryParams = mapOf("server_url" to "https://demo.ssdid.my")
        )
        val result = DeepLinkHandler.parse(uri)

        assertThat(result).isNotNull()
        assertThat(result!!.action).isEqualTo("authenticate")
        assertThat(result.serverUrl).isEqualTo("https://demo.ssdid.my")
        assertThat(result.serverDid).isEmpty()
        assertThat(result.sessionToken).isEmpty()
    }

    @Test
    fun `parse sign deep link returns correct action`() {
        val uri = mockUri(
            scheme = "ssdid",
            host = "sign",
            queryParams = mapOf(
                "server_url" to "https://demo.ssdid.my",
                "session_token" to "abc123"
            )
        )
        val result = DeepLinkHandler.parse(uri)

        assertThat(result).isNotNull()
        assertThat(result!!.action).isEqualTo("sign")
        assertThat(result.serverUrl).isEqualTo("https://demo.ssdid.my")
        assertThat(result.sessionToken).isEqualTo("abc123")
        assertThat(result.serverDid).isEmpty()
    }

    @Test
    fun `parse returns null for non-ssdid scheme`() {
        val uri = mockUri(
            scheme = "https",
            host = "register",
            queryParams = mapOf("server_url" to "https://demo.ssdid.my")
        )
        val result = DeepLinkHandler.parse(uri)

        assertThat(result).isNull()
    }

    @Test
    fun `parse returns null when server_url is missing`() {
        val uri = mockUri(
            scheme = "ssdid",
            host = "register",
            queryParams = mapOf("server_did" to "did:ssdid:server:demo")
        )
        val result = DeepLinkHandler.parse(uri)

        assertThat(result).isNull()
    }

    @Test
    fun `parse returns null for null host`() {
        val uri = mockUri(
            scheme = "ssdid",
            host = null,
            queryParams = mapOf("server_url" to "https://demo.ssdid.my")
        )
        val result = DeepLinkHandler.parse(uri)

        assertThat(result).isNull()
    }

    @Test
    fun `toNavRoute returns registration route for register action`() {
        val deepLink = DeepLinkAction(
            action = "register",
            serverUrl = "https://demo.ssdid.my",
            serverDid = "did:ssdid:server:demo"
        )
        val route = deepLink.toNavRoute()

        assertThat(route).isNotNull()
        assertThat(route).contains("registration")
    }

    @Test
    fun `toNavRoute returns auth route for authenticate action`() {
        val deepLink = DeepLinkAction(
            action = "authenticate",
            serverUrl = "https://demo.ssdid.my"
        )
        val route = deepLink.toNavRoute()

        assertThat(route).isNotNull()
        assertThat(route).contains("auth_flow")
    }

    @Test
    fun `toNavRoute returns tx signing route for sign action`() {
        val deepLink = DeepLinkAction(
            action = "sign",
            serverUrl = "https://demo.ssdid.my",
            sessionToken = "abc123"
        )
        val route = deepLink.toNavRoute()

        assertThat(route).isNotNull()
        assertThat(route).contains("tx_signing")
    }

    @Test
    fun `toNavRoute returns null for unknown action`() {
        val deepLink = DeepLinkAction(
            action = "unknown",
            serverUrl = "https://demo.ssdid.my"
        )
        val route = deepLink.toNavRoute()

        assertThat(route).isNull()
    }

    @Test
    fun `parse returns null for unrecognized action`() {
        val uri = mockUri(
            scheme = "ssdid",
            host = "delete",
            queryParams = mapOf("server_url" to "https://demo.ssdid.my")
        )
        val result = DeepLinkHandler.parse(uri)

        assertThat(result).isNull()
    }

    @Test
    fun `parse returns null for HTTP server_url`() {
        val uri = mockUri(
            scheme = "ssdid",
            host = "register",
            queryParams = mapOf(
                "server_url" to "http://evil.example.com",
                "server_did" to "did:ssdid:server:demo"
            )
        )
        val result = DeepLinkHandler.parse(uri)

        assertThat(result).isNull()
    }

    @Test
    fun `parse returns null for private IP server_url`() {
        val uri = mockUri(
            scheme = "ssdid",
            host = "register",
            queryParams = mapOf(
                "server_url" to "https://192.168.1.1",
                "server_did" to "did:ssdid:server:demo"
            )
        )
        val result = DeepLinkHandler.parse(uri)

        assertThat(result).isNull()
    }

    @Test
    fun `parse authenticate with callback_url returns callbackUrl`() {
        val uri = mockUri(
            scheme = "ssdid",
            host = "authenticate",
            queryParams = mapOf(
                "server_url" to "https://demo.ssdid.my",
                "callback_url" to "ssdiddrive://auth/callback"
            )
        )
        val result = DeepLinkHandler.parse(uri)

        assertThat(result).isNotNull()
        assertThat(result!!.action).isEqualTo("authenticate")
        assertThat(result.callbackUrl).isEqualTo("ssdiddrive://auth/callback")
    }

    @Test
    fun `parse authenticate without callback_url has empty callbackUrl`() {
        val uri = mockUri(
            scheme = "ssdid",
            host = "authenticate",
            queryParams = mapOf("server_url" to "https://demo.ssdid.my")
        )
        val result = DeepLinkHandler.parse(uri)

        assertThat(result).isNotNull()
        assertThat(result!!.callbackUrl).isEmpty()
    }

    @Test
    fun `parse authenticate rejects non-ssdiddrive callback_url scheme`() {
        val uri = mockUri(
            scheme = "ssdid",
            host = "authenticate",
            queryParams = mapOf(
                "server_url" to "https://demo.ssdid.my",
                "callback_url" to "https://evil.com/steal"
            )
        )
        val result = DeepLinkHandler.parse(uri)

        assertThat(result).isNotNull()
        assertThat(result!!.callbackUrl).isEmpty()
    }

    @Test
    fun `parse authenticate rejects javascript callback_url scheme`() {
        val uri = mockUri(
            scheme = "ssdid",
            host = "authenticate",
            queryParams = mapOf(
                "server_url" to "https://demo.ssdid.my",
                "callback_url" to "javascript:alert(1)"
            )
        )
        val result = DeepLinkHandler.parse(uri)

        assertThat(result).isNotNull()
        assertThat(result!!.callbackUrl).isEmpty()
    }

    @Test
    fun `parse register action does not parse callback_url`() {
        val uri = mockUri(
            scheme = "ssdid",
            host = "register",
            queryParams = mapOf(
                "server_url" to "https://demo.ssdid.my",
                "server_did" to "did:ssdid:server:demo",
                "callback_url" to "ssdiddrive://auth/callback"
            )
        )
        val result = DeepLinkHandler.parse(uri)
        assertThat(result).isNotNull()
        assertThat(result!!.callbackUrl).isEmpty()
    }

    @Test
    fun `parse sign action does not parse callback_url`() {
        val uri = mockUri(
            scheme = "ssdid",
            host = "sign",
            queryParams = mapOf(
                "server_url" to "https://demo.ssdid.my",
                "session_token" to "abc123",
                "callback_url" to "ssdiddrive://auth/callback"
            )
        )
        val result = DeepLinkHandler.parse(uri)
        assertThat(result).isNotNull()
        assertThat(result!!.callbackUrl).isEmpty()
    }

    @Test
    fun `parse authenticate with explicitly empty callback_url has empty callbackUrl`() {
        val uri = mockUri(
            scheme = "ssdid",
            host = "authenticate",
            queryParams = mapOf(
                "server_url" to "https://demo.ssdid.my",
                "callback_url" to ""
            )
        )
        val result = DeepLinkHandler.parse(uri)
        assertThat(result).isNotNull()
        assertThat(result!!.callbackUrl).isEmpty()
    }

    @Test
    fun `parse credential-offer deep link returns correct action`() {
        val uri = mockUri(
            scheme = "ssdid",
            host = "credential-offer",
            queryParams = mapOf(
                "issuer_url" to "https://issuer.ssdid.my",
                "offer_id" to "offer-xyz-123"
            )
        )
        val result = DeepLinkHandler.parse(uri)
        assertThat(result).isNotNull()
        assertThat(result!!.action).isEqualTo("credential-offer")
        assertThat(result.issuerUrl).isEqualTo("https://issuer.ssdid.my")
        assertThat(result.offerId).isEqualTo("offer-xyz-123")
    }

    @Test
    fun `parse credential-offer returns null when issuer_url missing`() {
        val uri = mockUri(
            scheme = "ssdid",
            host = "credential-offer",
            queryParams = mapOf("offer_id" to "offer-xyz-123")
        )
        assertThat(DeepLinkHandler.parse(uri)).isNull()
    }

    @Test
    fun `parse credential-offer rejects HTTP issuer_url`() {
        val uri = mockUri(
            scheme = "ssdid",
            host = "credential-offer",
            queryParams = mapOf(
                "issuer_url" to "http://issuer.evil.com",
                "offer_id" to "offer-xyz"
            )
        )
        assertThat(DeepLinkHandler.parse(uri)).isNull()
    }

    @Test
    fun `toNavRoute returns credential offer route`() {
        val deepLink = DeepLinkAction(
            action = "credential-offer",
            serverUrl = "",
            issuerUrl = "https://issuer.ssdid.my",
            offerId = "offer-xyz"
        )
        val route = deepLink.toNavRoute()
        assertThat(route).isNotNull()
        assertThat(route).contains("credential_offer")
    }

    @Test
    fun `toNavRoute for authenticate with callbackUrl includes callbackUrl param`() {
        val deepLink = DeepLinkAction(
            action = "authenticate",
            serverUrl = "https://demo.ssdid.my",
            callbackUrl = "ssdiddrive://auth/callback"
        )
        val route = deepLink.toNavRoute()

        assertThat(route).isNotNull()
        assertThat(route).contains("auth_flow")
        assertThat(route).contains("callbackUrl")
    }
}
