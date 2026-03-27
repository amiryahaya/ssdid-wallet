package my.ssdid.wallet.domain.logging

import org.junit.Test

class SsdidLoggerTest {

    @Test
    fun `NoOpLogger info does not throw`() {
        val logger: SsdidLogger = NoOpLogger()
        logger.info("test", "message")
        logger.info("test", "message", mapOf("key" to "value"))
    }

    @Test
    fun `NoOpLogger warning does not throw`() {
        val logger: SsdidLogger = NoOpLogger()
        logger.warning("test", "message")
        logger.warning("test", "message", mapOf("key" to "value"))
    }

    @Test
    fun `NoOpLogger error does not throw`() {
        val logger: SsdidLogger = NoOpLogger()
        logger.error("test", "message")
        logger.error("test", "message", RuntimeException("test"))
        logger.error("test", "message", RuntimeException("test"), mapOf("key" to "value"))
    }
}
