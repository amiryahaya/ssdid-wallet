package my.ssdid.wallet.domain.transport

import com.google.common.truth.Truth.assertThat
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class RetryInterceptorTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder()
            .addInterceptor(RetryInterceptor(maxRetries = 2))
            .connectTimeout(1, TimeUnit.SECONDS)
            .readTimeout(1, TimeUnit.SECONDS)
            .build()
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun `successful request on first try does not retry`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val request = Request.Builder().url(server.url("/")).build()
        val response = client.newCall(request).execute()

        assertThat(response.code).isEqualTo(200)
        assertThat(response.body?.string()).isEqualTo("ok")
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun `retries on 500 and succeeds on second attempt`() {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(200).setBody("recovered"))

        val request = Request.Builder().url(server.url("/")).build()
        val response = client.newCall(request).execute()

        assertThat(response.code).isEqualTo(200)
        assertThat(response.body?.string()).isEqualTo("recovered")
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun `returns final 500 after retries exhausted`() {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(500))

        val request = Request.Builder().url(server.url("/")).build()
        val response = client.newCall(request).execute()

        assertThat(response.code).isEqualTo(500)
        assertThat(server.requestCount).isEqualTo(3) // 1 initial + 2 retries
    }

    @Test
    fun `does not retry on 4xx errors`() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("not found"))

        val request = Request.Builder().url(server.url("/")).build()
        val response = client.newCall(request).execute()

        assertThat(response.code).isEqualTo(404)
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test(expected = IOException::class)
    fun `throws IOException after all retries exhausted on connection failure`() {
        // Shut down server to force IOException
        server.shutdown()

        val request = Request.Builder().url(server.url("/")).build()
        client.newCall(request).execute()
    }
}
