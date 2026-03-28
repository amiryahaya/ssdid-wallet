package my.ssdid.wallet.platform.storage

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import my.ssdid.sdk.domain.storage.OnboardingStorage
import org.junit.Test

class OnboardingStateTest {
    private val storage = FakeOnboardingStorage()

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

private class FakeOnboardingStorage : OnboardingStorage {
    private var completed = false
    override suspend fun isOnboardingCompleted(): Boolean = completed
    override suspend fun setOnboardingCompleted() { completed = true }
}
