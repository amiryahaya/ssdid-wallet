package my.ssdid.wallet.domain.oid4vp

import com.google.common.truth.Truth.assertThat
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class OpenId4VpTransportTest {

    private lateinit var server: MockWebServer
    private lateinit var transport: OpenId4VpTransport

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        transport = OpenId4VpTransport(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `fetchRequestObject returns parsed authorization request`() {
        server.enqueue(MockResponse().setBody("""
            {
                "client_id": "https://verifier.example.com",
                "response_type": "vp_token",
                "nonce": "test-nonce",
                "response_mode": "direct_post",
                "response_uri": "https://verifier.example.com/response",
                "presentation_definition": {"id": "req-1", "input_descriptors": []}
            }
        """.trimIndent()))

        val result = transport.fetchRequestObject(server.url("/request/abc").toString())
        assertThat(result.isSuccess).isTrue()
        val req = result.getOrThrow()
        assertThat(req.clientId).isEqualTo("https://verifier.example.com")
        assertThat(req.nonce).isEqualTo("test-nonce")
        assertThat(req.responseType).isEqualTo("vp_token")
        assertThat(req.responseMode).isEqualTo("direct_post")
        assertThat(req.presentationDefinition).contains("req-1")
    }

    @Test
    fun `fetchRequestObject fails on HTTP error`() {
        server.enqueue(MockResponse().setResponseCode(404))
        val result = transport.fetchRequestObject(server.url("/request/bad").toString())
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("HTTP 404")
    }

    @Test
    fun `fetchRequestObject fails on empty body`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        val result = transport.fetchRequestObject(server.url("/request/empty").toString())
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `postVpResponse sends form-encoded body`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val submission = PresentationSubmission(
            id = "sub-1",
            definitionId = "req-1",
            descriptorMap = listOf(DescriptorMapEntry("emp-cred", "vc+sd-jwt", "$"))
        )

        val result = transport.postVpResponse(
            server.url("/response").toString(),
            "vp-token-value",
            submission,
            "state-123"
        )
        assertThat(result.isSuccess).isTrue()

        val recorded = server.takeRequest()
        assertThat(recorded.method).isEqualTo("POST")
        val body = recorded.body.readUtf8()
        assertThat(body).contains("vp_token=vp-token-value")
        assertThat(body).contains("state=state-123")
        assertThat(body).contains("presentation_submission=")
    }

    @Test
    fun `postVpResponse works without optional fields`() {
        server.enqueue(MockResponse().setResponseCode(200))

        val result = transport.postVpResponse(
            server.url("/response").toString(),
            "vp-token-only",
            null,
            null
        )
        assertThat(result.isSuccess).isTrue()

        val recorded = server.takeRequest()
        val body = recorded.body.readUtf8()
        assertThat(body).contains("vp_token=vp-token-only")
        assertThat(body).doesNotContain("state=")
        assertThat(body).doesNotContain("presentation_submission=")
    }

    @Test
    fun `postVpResponse fails on HTTP error`() {
        server.enqueue(MockResponse().setResponseCode(500))
        val result = transport.postVpResponse(
            server.url("/response").toString(),
            "token",
            null,
            null
        )
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("HTTP 500")
    }

    @Test
    fun `postError sends error form body`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val result = transport.postError(
            server.url("/response").toString(),
            "access_denied",
            "state-456"
        )
        assertThat(result.isSuccess).isTrue()

        val recorded = server.takeRequest()
        val body = recorded.body.readUtf8()
        assertThat(body).contains("error=access_denied")
        assertThat(body).contains("state=state-456")
    }

    @Test
    fun `postError works without state`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val result = transport.postError(
            server.url("/response").toString(),
            "invalid_request",
            null
        )
        assertThat(result.isSuccess).isTrue()

        val recorded = server.takeRequest()
        val body = recorded.body.readUtf8()
        assertThat(body).contains("error=invalid_request")
        assertThat(body).doesNotContain("state=")
    }

    @Test
    fun `postError fails on HTTP error`() {
        server.enqueue(MockResponse().setResponseCode(502))
        val result = transport.postError(
            server.url("/response").toString(),
            "server_error",
            null
        )
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("HTTP 502")
    }
}
