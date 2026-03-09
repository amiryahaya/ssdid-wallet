package my.ssdid.wallet.domain.revocation

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test

class HttpStatusListFetcherTest {

    private val fetcher = HttpStatusListFetcher(Json { ignoreUnknownKeys = true })

    @Test
    fun `fetch rejects non-https URL`() = runTest {
        val result = fetcher.fetch("http://insecure.example/status/1")
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("HTTPS")
    }

    @Test
    fun `fetch rejects empty URL`() = runTest {
        val result = fetcher.fetch("")
        assertThat(result.isFailure).isTrue()
    }
}
