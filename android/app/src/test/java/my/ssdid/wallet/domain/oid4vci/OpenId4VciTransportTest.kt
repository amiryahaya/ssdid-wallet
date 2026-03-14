package my.ssdid.wallet.domain.oid4vci

import com.google.common.truth.Truth.assertThat
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class OpenId4VciTransportTest {

    private lateinit var server: MockWebServer
    private lateinit var transport: OpenId4VciTransport

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        transport = OpenId4VciTransport(OkHttpClient())
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun postCredentialRequest() {
        server.enqueue(MockResponse().setBody("""{"credential":"eyJ..."}"""))
        val result = transport.postCredentialRequest(
            server.url("/credential").toString(),
            "access-token-1",
            """{"format":"vc+sd-jwt","credential_definition":{"vct":"IdCard"}}"""
        )
        assertThat(result).contains("credential")
        val request = server.takeRequest()
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer access-token-1")
        assertThat(request.getHeader("Content-Type")).contains("application/json")
    }

    @Test
    fun postTokenRequest() {
        server.enqueue(MockResponse().setBody("""{"access_token":"at-1"}"""))
        val form = FormBody.Builder().add("grant_type", "pre-authorized_code").build()
        val result = transport.postTokenRequest(server.url("/token").toString(), form)
        assertThat(result).contains("access_token")
    }

    @Test
    fun postDeferredRequest() {
        server.enqueue(MockResponse().setBody("""{"credential":"eyJ...deferred"}"""))
        val result = transport.postDeferredRequest(
            server.url("/deferred").toString(), "at-2", "tx-id-1"
        )
        assertThat(result).contains("deferred")
        val request = server.takeRequest()
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer at-2")
        assertThat(request.body.readUtf8()).contains("transaction_id")
    }

    @Test
    fun fetchCredentialOffer() {
        server.enqueue(MockResponse().setBody("""{"credential_issuer":"https://iss.example.com"}"""))
        val result = transport.fetchCredentialOffer(server.url("/offer").toString())
        assertThat(result).contains("credential_issuer")
    }

    @Test
    fun fetchIssuerMetadata() {
        server.enqueue(MockResponse().setBody("""{"credential_issuer":"https://iss.example.com"}"""))
        val result = transport.fetchIssuerMetadata(server.url("/").toString())
        assertThat(result).contains("credential_issuer")
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/.well-known/openid-credential-issuer")
    }

    @Test
    fun fetchAuthServerMetadata() {
        server.enqueue(MockResponse().setBody("""{"issuer":"https://auth.example.com"}"""))
        val result = transport.fetchAuthServerMetadata(server.url("/").toString())
        assertThat(result).contains("issuer")
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/.well-known/oauth-authorization-server")
    }

    @Test(expected = RuntimeException::class)
    fun failsOnHttpError() {
        server.enqueue(MockResponse().setResponseCode(500))
        transport.fetchCredentialOffer(server.url("/fail").toString())
    }
}
