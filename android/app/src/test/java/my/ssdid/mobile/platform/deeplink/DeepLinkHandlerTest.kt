package my.ssdid.mobile.platform.deeplink

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

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
}
