package my.ssdid.sdk.domain.logging

interface SsdidLogger {
    fun info(category: String, message: String, data: Map<String, String> = emptyMap())
    fun warning(category: String, message: String, data: Map<String, String> = emptyMap())
    fun error(category: String, message: String, throwable: Throwable? = null, data: Map<String, String> = emptyMap())
}

class NoOpLogger : SsdidLogger {
    override fun info(category: String, message: String, data: Map<String, String>) {}
    override fun warning(category: String, message: String, data: Map<String, String>) {}
    override fun error(category: String, message: String, throwable: Throwable?, data: Map<String, String>) {}
}
