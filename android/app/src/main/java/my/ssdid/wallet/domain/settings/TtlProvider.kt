package my.ssdid.wallet.domain.settings

import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant

class TtlProvider(private val settings: SettingsRepository) {

    suspend fun getTtl(): Duration {
        val days = settings.bundleTtlDays().first()
        return Duration.ofDays(days.toLong())
    }

    suspend fun isExpired(fetchedAt: String): Boolean {
        val fetched = Instant.parse(fetchedAt)
        return Instant.now().isAfter(fetched.plus(getTtl()))
    }

    suspend fun freshnessRatio(fetchedAt: String): Double {
        val fetched = Instant.parse(fetchedAt)
        val age = Duration.between(fetched, Instant.now())
        return age.toMillis().toDouble() / getTtl().toMillis().toDouble()
    }
}
