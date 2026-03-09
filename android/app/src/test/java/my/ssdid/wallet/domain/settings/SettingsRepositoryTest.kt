package my.ssdid.wallet.domain.settings

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test

class FakeSettingsRepository : SettingsRepository {
    private val biometric = MutableStateFlow(true)
    private val autoLock = MutableStateFlow(5)
    private val algorithm = MutableStateFlow("KAZ_SIGN_192")
    private val lang = MutableStateFlow("en")

    override fun biometricEnabled(): Flow<Boolean> = biometric
    override suspend fun setBiometricEnabled(enabled: Boolean) { biometric.value = enabled }
    override fun autoLockMinutes(): Flow<Int> = autoLock
    override suspend fun setAutoLockMinutes(minutes: Int) { autoLock.value = minutes }
    override fun defaultAlgorithm(): Flow<String> = algorithm
    override suspend fun setDefaultAlgorithm(algorithm: String) { this.algorithm.value = algorithm }
    override fun language(): Flow<String> = lang
    override suspend fun setLanguage(language: String) { lang.value = language }
}

class SettingsRepositoryTest {
    private val repo = FakeSettingsRepository()

    @Test
    fun `biometric defaults to true`() = runBlocking {
        assertThat(repo.biometricEnabled().first()).isTrue()
    }

    @Test
    fun `biometric toggle persists`() = runBlocking {
        repo.setBiometricEnabled(false)
        assertThat(repo.biometricEnabled().first()).isFalse()
    }

    @Test
    fun `auto lock defaults to 5`() = runBlocking {
        assertThat(repo.autoLockMinutes().first()).isEqualTo(5)
    }

    @Test
    fun `language change persists`() = runBlocking {
        repo.setLanguage("ms")
        assertThat(repo.language().first()).isEqualTo("ms")
    }

    @Test
    fun `algorithm change persists`() = runBlocking {
        repo.setDefaultAlgorithm("ED25519")
        assertThat(repo.defaultAlgorithm().first()).isEqualTo("ED25519")
    }
}
