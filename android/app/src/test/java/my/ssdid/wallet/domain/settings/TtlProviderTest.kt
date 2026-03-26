package my.ssdid.wallet.domain.settings

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.Instant

class FakeSettingsRepositoryWithTtl : SettingsRepository {
    private val biometric = MutableStateFlow(true)
    private val autoLock = MutableStateFlow(5)
    private val algorithm = MutableStateFlow("KAZ_SIGN_192")
    private val lang = MutableStateFlow("en")
    private val bundleTtl = MutableStateFlow(7)

    override fun biometricEnabled(): Flow<Boolean> = biometric
    override suspend fun setBiometricEnabled(enabled: Boolean) { biometric.value = enabled }
    override fun autoLockMinutes(): Flow<Int> = autoLock
    override suspend fun setAutoLockMinutes(minutes: Int) { autoLock.value = minutes }
    override fun defaultAlgorithm(): Flow<String> = algorithm
    override suspend fun setDefaultAlgorithm(algorithm: String) { this.algorithm.value = algorithm }
    override fun language(): Flow<String> = lang
    override suspend fun setLanguage(language: String) { lang.value = language }
    override fun bundleTtlDays(): Flow<Int> = bundleTtl
    override suspend fun setBundleTtlDays(days: Int) { bundleTtl.value = days }
}

class TtlProviderTest {

    private val fakeSettings = FakeSettingsRepositoryWithTtl()
    private val provider = TtlProvider(fakeSettings)

    @Test
    fun `getTtl returns default 7 days when no setting changed`() = runBlocking {
        val ttl = provider.getTtl()
        assertThat(ttl.toDays()).isEqualTo(7)
    }

    @Test
    fun `getTtl returns user-configured value`() = runBlocking {
        fakeSettings.setBundleTtlDays(14)
        val ttl = provider.getTtl()
        assertThat(ttl.toDays()).isEqualTo(14)
    }

    @Test
    fun `isExpired returns true when bundle age exceeds TTL`() = runBlocking {
        // fetched 8 days ago, TTL is 7 days → expired
        val fetchedAt = Instant.now().minusSeconds(8 * 24 * 60 * 60L).toString()
        assertThat(provider.isExpired(fetchedAt)).isTrue()
    }

    @Test
    fun `isExpired returns false when bundle age within TTL`() = runBlocking {
        // fetched 3 days ago, TTL is 7 days → not expired
        val fetchedAt = Instant.now().minusSeconds(3 * 24 * 60 * 60L).toString()
        assertThat(provider.isExpired(fetchedAt)).isFalse()
    }

    @Test
    fun `freshnessRatio returns correct ratio`() = runBlocking {
        // fetched 3.5 days ago, TTL is 7 days → ratio = 0.5
        val halfTtlSeconds = 7 * 24 * 60 * 60L / 2
        val fetchedAt = Instant.now().minusSeconds(halfTtlSeconds).toString()
        val ratio = provider.freshnessRatio(fetchedAt)
        assertThat(ratio).isWithin(0.01).of(0.5)
    }

    @Test
    fun `freshnessRatio returns near zero for just-fetched bundle`() = runBlocking {
        // fetched just now — age is ~0 seconds, ratio should be near 0
        val fetchedAt = Instant.now().toString()
        val ratio = provider.freshnessRatio(fetchedAt)
        assertThat(ratio).isLessThan(0.01)
    }

    @Test
    fun `freshnessRatio returns approximately 0_5 at half TTL`() = runBlocking {
        // fetched exactly half the TTL ago (3.5 days with default 7-day TTL)
        val halfTtlSeconds = 7 * 24 * 60 * 60L / 2
        val fetchedAt = Instant.now().minusSeconds(halfTtlSeconds).toString()
        val ratio = provider.freshnessRatio(fetchedAt)
        assertThat(ratio).isWithin(0.02).of(0.5)
    }

    @Test
    fun `freshnessRatio returns 1_0 or greater for expired bundle`() = runBlocking {
        // fetched 8 days ago with 7-day TTL → bundle is expired, ratio ≥ 1.0
        val expiredSeconds = 8 * 24 * 60 * 60L
        val fetchedAt = Instant.now().minusSeconds(expiredSeconds).toString()
        val ratio = provider.freshnessRatio(fetchedAt)
        assertThat(ratio).isAtLeast(1.0)
    }

    @Test
    fun `freshnessRatio returns 1_0 for unparseable fetchedAt`() = runBlocking {
        val ratio = provider.freshnessRatio("not-a-valid-timestamp")
        assertThat(ratio).isEqualTo(1.0)
    }
}
