package my.ssdid.wallet.domain.transport

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class RetryInterceptor(private val maxRetries: Int = 2) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var lastException: IOException? = null
        repeat(maxRetries + 1) { attempt ->
            try {
                val response = chain.proceed(chain.request())
                if (response.code < 500 || attempt == maxRetries) return response
                response.close()
            } catch (e: IOException) {
                lastException = e
                if (attempt == maxRetries) throw e
            }
            // Exponential backoff
            Thread.sleep((1000L shl attempt).coerceAtMost(5000))
        }
        throw lastException ?: IOException("Retry exhausted")
    }
}
