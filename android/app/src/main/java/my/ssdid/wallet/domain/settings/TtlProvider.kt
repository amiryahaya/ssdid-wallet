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
        val fetched = try { Instant.parse(fetchedAt) } catch (e: Exception) { return true }
        return Instant.now().isAfter(fetched.plus(getTtl()))
    }

    suspend fun freshnessRatio(fetchedAt: String): Double {
        val fetched = try { Instant.parse(fetchedAt) } catch (e: Exception) { return 1.0 }
        val age = Duration.between(fetched, Instant.now())
        val ttl = getTtl()
        if (ttl.isZero) return 1.0
        return (age.toMillis().toDouble() / ttl.toMillis().toDouble()).coerceAtLeast(0.0)
    }
}
