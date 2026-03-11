package my.ssdid.wallet.feature.profile

import android.provider.Settings
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import my.ssdid.wallet.domain.transport.ConfirmCodeResponse
import my.ssdid.wallet.domain.transport.EmailVerifyApi
import my.ssdid.wallet.domain.transport.SendCodeResponse
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class EmailVerificationViewModelTest {

    private lateinit var emailVerifyApi: EmailVerifyApi
    private lateinit var vm: EmailVerificationViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        emailVerifyApi = mockk()
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ANDROID_ID, "test-device-id")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(email: String = "alice@example.com"): EmailVerificationViewModel {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val savedState = SavedStateHandle(mapOf("email" to email))
        return EmailVerificationViewModel(emailVerifyApi, context, savedState)
    }

    @Test
    fun `init sends code automatically`() = runTest {
        coEvery { emailVerifyApi.sendCode(any()) } returns SendCodeResponse(600)
        vm = createViewModel()
        advanceUntilIdle()
        coVerify { emailVerifyApi.sendCode(match { it.email == "alice@example.com" && it.deviceId == "test-device-id" }) }
    }

    @Test
    fun `updateCode accepts valid digits up to 6`() = runTest {
        coEvery { emailVerifyApi.sendCode(any()) } returns SendCodeResponse(600)
        vm = createViewModel()
        advanceUntilIdle()
        vm.updateCode("123")
        assertThat(vm.code.value).isEqualTo("123")
    }

    @Test
    fun `updateCode rejects non-digits`() = runTest {
        coEvery { emailVerifyApi.sendCode(any()) } returns SendCodeResponse(600)
        vm = createViewModel()
        advanceUntilIdle()
        vm.updateCode("abc")
        assertThat(vm.code.value).isEmpty()
    }

    @Test
    fun `updateCode rejects more than 6 digits`() = runTest {
        coEvery { emailVerifyApi.sendCode(any()) } returns SendCodeResponse(600)
        vm = createViewModel()
        advanceUntilIdle()
        vm.updateCode("1234567")
        assertThat(vm.code.value).isEmpty()
    }

    @Test
    fun `verify sets verified on success`() = runTest {
        coEvery { emailVerifyApi.sendCode(any()) } returns SendCodeResponse(600)
        coEvery { emailVerifyApi.confirmCode(any()) } returns ConfirmCodeResponse(true)
        vm = createViewModel()
        advanceUntilIdle()
        vm.updateCode("123456")
        vm.verify()
        advanceUntilIdle()
        assertThat(vm.verified.value).isTrue()
    }

    @Test
    fun `verify sets error when response is not verified`() = runTest {
        coEvery { emailVerifyApi.sendCode(any()) } returns SendCodeResponse(600)
        coEvery { emailVerifyApi.confirmCode(any()) } returns ConfirmCodeResponse(false)
        vm = createViewModel()
        advanceUntilIdle()
        vm.updateCode("123456")
        vm.verify()
        advanceUntilIdle()
        assertThat(vm.verified.value).isFalse()
        assertThat(vm.error.value).isEqualTo("Invalid code.")
    }

    @Test
    fun `verify does nothing with less than 6 digits`() = runTest {
        coEvery { emailVerifyApi.sendCode(any()) } returns SendCodeResponse(600)
        vm = createViewModel()
        advanceUntilIdle()
        vm.updateCode("123")
        vm.verify()
        advanceUntilIdle()
        coVerify(exactly = 0) { emailVerifyApi.confirmCode(any()) }
    }

    @Test
    fun `sendCode sets error on network failure`() = runTest {
        coEvery { emailVerifyApi.sendCode(any()) } throws RuntimeException("No network")
        vm = createViewModel()
        advanceUntilIdle()
        assertThat(vm.error.value).isEqualTo("Network error. Check your connection.")
    }

    @Test
    fun `cooldown starts after successful send`() = runTest {
        coEvery { emailVerifyApi.sendCode(any()) } returns SendCodeResponse(600)
        vm = createViewModel()
        // Only advance the sendCode coroutine, not the cooldown timer
        advanceTimeBy(100)
        runCurrent()
        // Cooldown should be set to 60 (first send)
        assertThat(vm.cooldown.value).isGreaterThan(0)
    }

    @Test
    fun `email comes from SavedStateHandle`() = runTest {
        coEvery { emailVerifyApi.sendCode(any()) } returns SendCodeResponse(600)
        vm = createViewModel("bob@test.com")
        advanceUntilIdle()
        assertThat(vm.email).isEqualTo("bob@test.com")
    }
}
