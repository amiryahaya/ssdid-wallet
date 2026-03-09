package my.ssdid.wallet.platform.storage

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import my.ssdid.wallet.domain.vault.FakeVaultStorage
import org.junit.Test

class OnboardingStateTest {
    private val storage = FakeVaultStorage()

    @Test
    fun `onboarding not completed by default`() = runBlocking {
        assertThat(storage.isOnboardingCompleted()).isFalse()
    }

    @Test
    fun `onboarding completed after setting`() = runBlocking {
        storage.setOnboardingCompleted()
        assertThat(storage.isOnboardingCompleted()).isTrue()
    }
}
