package my.ssdid.wallet.platform.logging

import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import my.ssdid.wallet.domain.logging.SsdidLogger

class SentryLogger : SsdidLogger {
    override fun info(category: String, message: String, data: Map<String, String>) {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            this.category = category; this.message = message; this.level = SentryLevel.INFO
            data.forEach { (k, v) -> this.data[k] = v }
        })
    }
    override fun warning(category: String, message: String, data: Map<String, String>) {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            this.category = category; this.message = message; this.level = SentryLevel.WARNING
            data.forEach { (k, v) -> this.data[k] = v }
        })
    }
    override fun error(category: String, message: String, throwable: Throwable?, data: Map<String, String>) {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            this.category = category; this.message = message; this.level = SentryLevel.ERROR
            data.forEach { (k, v) -> this.data[k] = v }
        })
        throwable?.let { Sentry.captureException(it) }
    }
}
