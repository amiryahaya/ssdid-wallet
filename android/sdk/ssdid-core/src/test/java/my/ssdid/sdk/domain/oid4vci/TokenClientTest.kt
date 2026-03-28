package my.ssdid.sdk.domain.oid4vci

import com.google.common.truth.Truth.assertThat
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class TokenClientTest {

    private lateinit var server: MockWebServer
    private lateinit var tokenClient: TokenClient

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        tokenClient = TokenClient(OkHttpClient())
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun exchangePreAuthorizedCode() {
        server.enqueue(
            MockResponse().setBody(
                """{"access_token":"at-123","token_type":"Bearer","c_nonce":"nonce-1","c_nonce_expires_in":300}"""
            )
        )
        val result = tokenClient.exchangePreAuthorizedCode(
            server.url("/token").toString(),
            "pre-code-1"
        )
        assertThat(result.isSuccess).isTrue()
        val token = result.getOrThrow()
        assertThat(token.accessToken).isEqualTo("at-123")
        assertThat(token.tokenType).isEqualTo("Bearer")
        assertThat(token.cNonce).isEqualTo("nonce-1")
        assertThat(token.cNonceExpiresIn).isEqualTo(300)

        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertThat(body).contains("grant_type=urn")
        assertThat(body).contains("pre-authorized_code=pre-code-1")
    }

    @Test
    fun exchangePreAuthorizedCodeWithTxCode() {
        server.enqueue(
            MockResponse().setBody(
                """{"access_token":"at-456","token_type":"Bearer"}"""
            )
        )
        val result = tokenClient.exchangePreAuthorizedCode(
            server.url("/token").toString(),
            "pre-code-2",
            "123456"
        )
        assertThat(result.isSuccess).isTrue()
        val token = result.getOrThrow()
        assertThat(token.accessToken).isEqualTo("at-456")
        assertThat(token.cNonce).isNull()
        assertThat(token.cNonceExpiresIn).isNull()

        val request = server.takeRequest()
        assertThat(request.body.readUtf8()).contains("tx_code=123456")
    }

    @Test
    fun exchangeAuthorizationCode() {
        server.enqueue(
            MockResponse().setBody(
                """{"access_token":"at-789","token_type":"Bearer","c_nonce":"n-2"}"""
            )
        )
        val result = tokenClient.exchangeAuthorizationCode(
            server.url("/token").toString(),
            "auth-code-1",
            "verifier-1",
            "https://wallet.example.com/cb"
        )
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow().accessToken).isEqualTo("at-789")
        assertThat(result.getOrThrow().cNonce).isEqualTo("n-2")

        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertThat(body).contains("grant_type=authorization_code")
        assertThat(body).contains("code=auth-code-1")
        assertThat(body).contains("code_verifier=verifier-1")
        assertThat(body).contains("redirect_uri=https")
    }

    @Test
    fun failsOnHttpError() {
        server.enqueue(
            MockResponse().setResponseCode(400).setBody("""{"error":"invalid_grant"}""")
        )
        val result = tokenClient.exchangePreAuthorizedCode(
            server.url("/token").toString(),
            "bad-code"
        )
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("HTTP 400")
    }
}
