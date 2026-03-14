package my.ssdid.wallet.domain.didcomm

import com.google.common.truth.Truth.assertThat
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class DIDCommTransportTest {
    private lateinit var server: MockWebServer
    private lateinit var transport: DIDCommTransport

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        transport = DIDCommTransport(OkHttpClient())
    }

    @After
    fun teardown() { server.shutdown() }

    @Test
    fun sendPostsCorrectContentType() {
        server.enqueue(MockResponse().setResponseCode(200))
        val packed = """{"ciphertext":"abc"}""".toByteArray()
        val result = transport.send(packed, server.url("/didcomm").toString())
        assertThat(result.isSuccess).isTrue()
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.getHeader("Content-Type")).contains("application/didcomm-encrypted+json")
    }

    @Test
    fun sendPostsCorrectBody() {
        server.enqueue(MockResponse().setResponseCode(200))
        val packed = """{"ciphertext":"test-data"}""".toByteArray()
        transport.send(packed, server.url("/didcomm").toString())
        val request = server.takeRequest()
        assertThat(request.body.readUtf8()).isEqualTo("""{"ciphertext":"test-data"}""")
    }

    @Test
    fun sendReturnsFailureOnHttpError() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))
        val packed = "data".toByteArray()
        val result = transport.send(packed, server.url("/didcomm").toString())
        assertThat(result.isFailure).isTrue()
        val exception = result.exceptionOrNull()
        assertThat(exception).isInstanceOf(DIDCommTransportException::class.java)
        assertThat(exception!!.message).contains("500")
    }
}
