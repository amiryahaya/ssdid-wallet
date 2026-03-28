package my.ssdid.sdk.domain.oid4vp

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
    fun setup() {
        server = MockWebServer()
        server.start()
        transport = OpenId4VpTransport(OkHttpClient())
    }

    @After
    fun teardown() { server.shutdown() }

    @Test
    fun fetchRequestObject() {
        val json = """{"client_id":"https://v.example.com","response_uri":"https://v.example.com/cb","nonce":"n","response_mode":"direct_post","presentation_definition":{"id":"pd-1","input_descriptors":[]}}"""
        server.enqueue(MockResponse().setBody(json).setResponseCode(200))
        val body = transport.fetchRequestObject(server.url("/request/123").toString())
        assertThat(body).isEqualTo(json)
    }

    @Test
    fun postVpResponse() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        transport.postVpResponse(
            responseUri = server.url("/response").toString(),
            vpToken = "eyJ...",
            presentationSubmission = """{"id":"sub-1"}""",
            state = "state-123"
        )
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        val body = request.body.readUtf8()
        assertThat(body).contains("vp_token=")
        assertThat(body).contains("presentation_submission=")
        assertThat(body).contains("state=state-123")
        assertThat(request.getHeader("Content-Type")).contains("application/x-www-form-urlencoded")
    }

    @Test
    fun postError() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        transport.postError(server.url("/response").toString(), "access_denied", "s-1")
        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertThat(body).contains("error=access_denied")
        assertThat(body).contains("state=s-1")
    }

    @Test(expected = RuntimeException::class)
    fun fetchRequestObjectFailsOnHttpError() {
        server.enqueue(MockResponse().setResponseCode(500))
        transport.fetchRequestObject(server.url("/fail").toString())
    }

    @Test
    fun postVpResponseWithoutState() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        transport.postVpResponse(server.url("/response").toString(), "tok", """{"id":"s"}""", null)
        val body = server.takeRequest().body.readUtf8()
        assertThat(body).doesNotContain("state=")
    }
}
