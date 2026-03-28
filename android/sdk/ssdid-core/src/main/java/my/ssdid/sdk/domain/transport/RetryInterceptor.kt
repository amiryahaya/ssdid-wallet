package my.ssdid.sdk.domain.transport

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
            // Exponential backoff with jitter
            val jitter = (Math.random() * 200).toLong()
            Thread.sleep((500L * (1 shl attempt)).coerceAtMost(5000) + jitter)
        }
        throw lastException ?: IOException("Retry exhausted")
    }
}
