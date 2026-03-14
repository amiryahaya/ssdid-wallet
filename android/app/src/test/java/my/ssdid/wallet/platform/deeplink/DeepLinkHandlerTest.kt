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
    fun `parse authenticate accepts https callback_url`() {
        val uri = mockUri(
            scheme = "ssdid",
            host = "authenticate",
            queryParams = mapOf(
                "server_url" to "https://demo.ssdid.my",
                "callback_url" to "https://app.example.com/callback"
            )
        )
        val result = DeepLinkHandler.parse(uri)

        assertThat(result).isNotNull()
        assertThat(result!!.callbackUrl).isEqualTo("https://app.example.com/callback")
    }

    @Test
    fun `parse authenticate accepts custom scheme callback_url`() {
        val uri = mockUri(
            scheme = "ssdid",
            host = "authenticate",
            queryParams = mapOf(
                "server_url" to "https://demo.ssdid.my",
                "callback_url" to "myapp://auth-callback"
            )
        )
        val result = DeepLinkHandler.parse(uri)

        assertThat(result).isNotNull()
        assertThat(result!!.callbackUrl).isEqualTo("myapp://auth-callback")
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
    fun `parse authenticate rejects data callback_url scheme`() {
        val uri = mockUri(
            scheme = "ssdid",
            host = "authenticate",
            queryParams = mapOf(
                "server_url" to "https://demo.ssdid.my",
                "callback_url" to "data:text/html,<script>alert(1)</script>"
            )
        )
        val result = DeepLinkHandler.parse(uri)

        assertThat(result).isNotNull()
        assertThat(result!!.callbackUrl).isEmpty()
    }

    @Test
    fun `parse authenticate rejects file callback_url scheme`() {
        val uri = mockUri(
            scheme = "ssdid",
            host = "authenticate",
            queryParams = mapOf(
                "server_url" to "https://demo.ssdid.my",
                "callback_url" to "file:///etc/passwd"
            )
        )
        val result = DeepLinkHandler.parse(uri)

        assertThat(result).isNotNull()
        assertThat(result!!.callbackUrl).isEmpty()
    }

    @Test
    fun `parse authenticate rejects non-localhost http callback_url`() {
        val uri = mockUri(
            scheme = "ssdid",
            host = "authenticate",
            queryParams = mapOf(
                "server_url" to "https://demo.ssdid.my",
                "callback_url" to "http://evil.com/steal"
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

    // --- Android-specific dangerous scheme tests ---

    @Test
    fun `parse authenticate rejects intent callback_url scheme`() {
        val uri = mockUri(
            scheme = "ssdid",
            host = "authenticate",
            queryParams = mapOf(
                "server_url" to "https://demo.ssdid.my",
                "callback_url" to "intent://example.com#Intent;scheme=https;end"
            )
        )
        val result = DeepLinkHandler.parse(uri)
        assertThat(result).isNotNull()
        assertThat(result!!.callbackUrl).isEmpty()
    }

    @Test
    fun `parse authenticate rejects content callback_url scheme`() {
        val uri = mockUri(
            scheme = "ssdid",
            host = "authenticate",
            queryParams = mapOf(
                "server_url" to "https://demo.ssdid.my",
                "callback_url" to "content://com.example.provider/data"
            )
        )
        val result = DeepLinkHandler.parse(uri)
        assertThat(result).isNotNull()
        assertThat(result!!.callbackUrl).isEmpty()
    }

    @Test
    fun `parse authenticate rejects android-app callback_url scheme`() {
        val uri = mockUri(
            scheme = "ssdid",
            host = "authenticate",
            queryParams = mapOf(
                "server_url" to "https://demo.ssdid.my",
                "callback_url" to "android-app://com.attacker.app/callback"
            )
        )
        val result = DeepLinkHandler.parse(uri)
        assertThat(result).isNotNull()
        assertThat(result!!.callbackUrl).isEmpty()
    }

    @Test
    fun `parse authenticate rejects blob callback_url scheme`() {
        val uri = mockUri(
            scheme = "ssdid",
            host = "authenticate",
            queryParams = mapOf(
                "server_url" to "https://demo.ssdid.my",
                "callback_url" to "blob:https://evil.com/uuid"
            )
        )
        val result = DeepLinkHandler.parse(uri)
        assertThat(result).isNotNull()
        assertThat(result!!.callbackUrl).isEmpty()
    }

    @Test
    fun `isValidCallbackUrl rejects https with blank host`() {
        assertThat(DeepLinkHandler.isValidCallbackUrl("https://")).isFalse()
    }

    @Test
    fun `isValidCallbackUrl accepts ssdiddrive scheme`() {
        assertThat(DeepLinkHandler.isValidCallbackUrl("ssdiddrive://auth/callback")).isTrue()
    }

    @Test
    fun `isValidCallbackUrl accepts https with valid host`() {
        assertThat(DeepLinkHandler.isValidCallbackUrl("https://app.example.com/cb")).isTrue()
    }

    // --- OpenID4VP scheme tests ---

    @Test
    fun `parse openid4vp URI returns openid4vp action`() {
        val uri = Uri.parse("openid4vp://?client_id=https://verifier.example.com&request_uri=https://verifier.example.com/request/123")
        val action = DeepLinkHandler.parse(uri)
        assertThat(action).isNotNull()
        assertThat(action!!.action).isEqualTo("openid4vp")
        assertThat(action.callbackUrl).contains("openid4vp://")
    }

    @Test
    fun `toNavRoute routes openid4vp action to PresentationRequest`() {
        val deepLink = DeepLinkAction(
            action = "openid4vp",
            serverUrl = "",
            callbackUrl = "openid4vp://?client_id=https://verifier.example.com"
        )
        val route = deepLink.toNavRoute()
        assertThat(route).isNotNull()
        assertThat(route).contains("presentation_request")
    }

    // --- openid-credential-offer scheme tests ---

    @Test
    fun `parse credential offer by value returns correct action`() {
        val offerJson = """{"credential_issuer":"https://issuer.example.com","credential_configuration_ids":["x"],"grants":{"authorization_code":{}}}"""
        val uri = Uri.parse("openid-credential-offer://?credential_offer=${Uri.encode(offerJson)}")
        val action = DeepLinkHandler.parse(uri)
        assertThat(action).isNotNull()
        assertThat(action!!.action).isEqualTo("openid-credential-offer")
    }

    @Test
    fun `parse credential offer by reference returns correct action`() {
        val uri = Uri.parse("openid-credential-offer://?credential_offer_uri=https://issuer.example.com/offers/123")
        val action = DeepLinkHandler.parse(uri)
        assertThat(action).isNotNull()
        assertThat(action!!.action).isEqualTo("openid-credential-offer")
    }

    @Test
    fun `parse credential offer rejects HTTP credential_offer_uri`() {
        val uri = Uri.parse("openid-credential-offer://?credential_offer_uri=http://bad.com/offer")
        val action = DeepLinkHandler.parse(uri)
        assertThat(action).isNull()
    }

    @Test
    fun `parse credential offer rejects missing params`() {
        val uri = Uri.parse("openid-credential-offer://")
        val action = DeepLinkHandler.parse(uri)
        assertThat(action).isNull()
    }

    // --- ssdid://authorize (SDK integration) tests ---

    @Test
    fun `parse authorize deep link returns correct action`() {
        val uri = mockUri(
            scheme = "ssdid",
            host = "authorize",
            queryParams = mapOf(
                "dcql_query" to """{"credentials":[{"id":"cred-1","format":"vc+sd-jwt","meta":{"vct_values":["IdentityCredential"]}}]}""",
                "response_url" to "https://myapp.com/api/ssdid/response",
                "callback_scheme" to "myapp://ssdid/callback",
                "nonce" to "n-123"
            )
        )
        val result = DeepLinkHandler.parse(uri)
        assertThat(result).isNotNull()
        assertThat(result!!.action).isEqualTo("authorize")
        assertThat(result.callbackUrl).contains("openid4vp")
        assertThat(result.callbackUrl).contains("dcql_query")
        assertThat(result.callbackScheme).isEqualTo("myapp://ssdid/callback")
    }

    @Test
    fun `parse authorize returns null without dcql_query`() {
        val uri = mockUri(
            scheme = "ssdid",
            host = "authorize",
            queryParams = mapOf(
                "response_url" to "https://myapp.com/api/ssdid/response",
                "callback_scheme" to "myapp://ssdid/callback",
                "nonce" to "n-123"
            )
        )
        assertThat(DeepLinkHandler.parse(uri)).isNull()
    }

    @Test
    fun `parse authorize returns null without response_url`() {
        val uri = mockUri(
            scheme = "ssdid",
            host = "authorize",
            queryParams = mapOf(
                "dcql_query" to """{"credentials":[]}""",
                "callback_scheme" to "myapp://ssdid/callback",
                "nonce" to "n-123"
            )
        )
        assertThat(DeepLinkHandler.parse(uri)).isNull()
    }

    @Test
    fun `parse authorize rejects HTTP response_url`() {
        val uri = mockUri(
            scheme = "ssdid",
            host = "authorize",
            queryParams = mapOf(
                "dcql_query" to """{"credentials":[]}""",
                "response_url" to "http://evil.com/response",
                "callback_scheme" to "myapp://ssdid/callback",
                "nonce" to "n-123"
            )
        )
        assertThat(DeepLinkHandler.parse(uri)).isNull()
    }

    @Test
    fun `toNavRoute routes authorize to PresentationRequest`() {
        val deepLink = DeepLinkAction(
            action = "authorize",
            serverUrl = "",
            callbackUrl = "openid4vp://?dcql_query=...",
            callbackScheme = "myapp://ssdid/callback"
        )
        val route = deepLink.toNavRoute()
        assertThat(route).isNotNull()
        assertThat(route).contains("presentation_request")
    }

    @Test
    fun `toNavRoute returns credential offer route for openid-credential-offer action`() {
        val deepLink = DeepLinkAction(
            action = "openid-credential-offer",
            serverUrl = "",
            callbackUrl = "https://issuer.example.com/offers/123"
        )
        val route = deepLink.toNavRoute()
        assertThat(route).isNotNull()
        assertThat(route).contains("credential_offer")
    }

    @Test
    fun `parse authorize rejects javascript callback_scheme`() {
        val uri = mockUri(
            scheme = "ssdid",
            host = "authorize",
            queryParams = mapOf(
                "dcql_query" to """{"credentials":[]}""",
                "response_url" to "https://myapp.com/api/response",
                "callback_scheme" to "javascript:alert(1)",
                "nonce" to "n-1"
            )
        )
        assertThat(DeepLinkHandler.parse(uri)).isNull()
    }

    @Test
    fun `parse authenticate handles CSV accepted_algorithms`() {
        val uri = mockUri(
            scheme = "ssdid",
            host = "authenticate",
            queryParams = mapOf(
                "server_url" to "https://demo.ssdid.my",
                "accepted_algorithms" to "Ed25519,EcdsaP256"
            )
        )
        val result = DeepLinkHandler.parse(uri)
        assertThat(result).isNotNull()
        assertThat(result!!.acceptedAlgorithms).containsExactly("Ed25519", "EcdsaP256")
    }
}
