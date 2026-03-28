package my.ssdid.wallet.feature.profile

import android.provider.Settings
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import my.ssdid.sdk.domain.transport.ConfirmCodeResponse
import my.ssdid.sdk.domain.transport.EmailVerifyApi
import my.ssdid.sdk.domain.transport.SendCodeResponse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.HttpException
import retrofit2.Response

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

    private fun httpException(code: Int): HttpException {
        val body = "{}".toResponseBody("application/json".toMediaType())
        return HttpException(Response.error<Any>(code, body))
    }

    // --- init ---

    @Test
    fun `init sends code automatically`() = runTest {
        coEvery { emailVerifyApi.sendCode(any()) } returns SendCodeResponse(600)
        vm = createViewModel()
        advanceUntilIdle()
        coVerify { emailVerifyApi.sendCode(match { it.email == "alice@example.com" && it.deviceId == "test-device-id" }) }
    }

    @Test
    fun `init with blank email shows error and does not send`() = runTest {
        vm = createViewModel("")
        advanceUntilIdle()
        assertThat(vm.error.value).isEqualTo("Invalid email address.")
        coVerify(exactly = 0) { emailVerifyApi.sendCode(any()) }
    }

    @Test
    fun `email comes from SavedStateHandle`() = runTest {
        coEvery { emailVerifyApi.sendCode(any()) } returns SendCodeResponse(600)
        vm = createViewModel("bob@test.com")
        advanceUntilIdle()
        assertThat(vm.email).isEqualTo("bob@test.com")
    }

    // --- updateCode ---

    @Test
    fun `updateCode accepts valid digits up to 6`() = runTest {
        coEvery { emailVerifyApi.sendCode(any()) } returns SendCodeResponse(600)
        vm = createViewModel()
        advanceUntilIdle()
        vm.updateCode("123")
        assertThat(vm.code.value).isEqualTo("123")
    }

    @Test
    fun `updateCode accepts exactly 6 digits`() = runTest {
        coEvery { emailVerifyApi.sendCode(any()) } returns SendCodeResponse(600)
        vm = createViewModel()
        advanceUntilIdle()
        vm.updateCode("123456")
        assertThat(vm.code.value).isEqualTo("123456")
    }

    @Test
    fun `updateCode rejects non-digits`() = runTest {
        coEvery { emailVerifyApi.sendCode(any()) } returns SendCodeResponse(600)
        vm = createViewModel()
        advanceUntilIdle()
        vm.updateCode("123")
        vm.updateCode("abc")
        assertThat(vm.code.value).isEqualTo("123")
    }

    @Test
    fun `updateCode rejects more than 6 digits and keeps previous value`() = runTest {
        coEvery { emailVerifyApi.sendCode(any()) } returns SendCodeResponse(600)
        vm = createViewModel()
        advanceUntilIdle()
        vm.updateCode("123456")
        vm.updateCode("1234567")
        assertThat(vm.code.value).isEqualTo("123456")
    }

    @Test
    fun `updateCode clears error`() = runTest {
        coEvery { emailVerifyApi.sendCode(any()) } returns SendCodeResponse(600)
        coEvery { emailVerifyApi.confirmCode(any()) } returns ConfirmCodeResponse(false)
        vm = createViewModel()
        advanceUntilIdle()
        vm.updateCode("123456")
        vm.verify()
        advanceUntilIdle()
        assertThat(vm.error.value).isNotNull()
        vm.updateCode("654321")
        assertThat(vm.error.value).isNull()
    }

    // --- sendCode ---

    @Test
    fun `sendCode sets error on network failure`() = runTest {
        coEvery { emailVerifyApi.sendCode(any()) } throws RuntimeException("No network")
        vm = createViewModel()
        advanceUntilIdle()
        assertThat(vm.error.value).isEqualTo("Network error. Check your connection.")
        assertThat(vm.sending.value).isFalse()
    }

    @Test
    fun `sendCode sets error on HTTP 429`() = runTest {
        coEvery { emailVerifyApi.sendCode(any()) } throws httpException(429)
        vm = createViewModel()
        advanceUntilIdle()
        assertThat(vm.error.value).isEqualTo("Too many requests. Please wait before trying again.")
        assertThat(vm.sending.value).isFalse()
    }

    @Test
    fun `sendCode sets error on other HTTP errors`() = runTest {
        coEvery { emailVerifyApi.sendCode(any()) } throws httpException(500)
        vm = createViewModel()
        advanceUntilIdle()
        assertThat(vm.error.value).isEqualTo("Failed to send code. Please try again.")
    }

    @Test
    fun `sendCode during cooldown does nothing`() = runTest {
        coEvery { emailVerifyApi.sendCode(any()) } returns SendCodeResponse(600)
        vm = createViewModel()
        runCurrent()
        // Cooldown is active after init send
        assertThat(vm.cooldown.value).isGreaterThan(0)
        vm.sendCode()
        runCurrent()
        // Only the init call should have been made
        coVerify(exactly = 1) { emailVerifyApi.sendCode(any()) }
    }

    // --- verify ---

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
        assertThat(vm.verifying.value).isFalse()
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
        assertThat(vm.verifying.value).isFalse()
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
    fun `verify sets error on HTTP 429`() = runTest {
        coEvery { emailVerifyApi.sendCode(any()) } returns SendCodeResponse(600)
        coEvery { emailVerifyApi.confirmCode(any()) } throws httpException(429)
        vm = createViewModel()
        advanceUntilIdle()
        vm.updateCode("123456")
        vm.verify()
        advanceUntilIdle()
        assertThat(vm.error.value).isEqualTo("Too many failed attempts. Try again in 15 minutes.")
        assertThat(vm.verifying.value).isFalse()
    }

    @Test
    fun `verify sets error on HTTP 400`() = runTest {
        coEvery { emailVerifyApi.sendCode(any()) } returns SendCodeResponse(600)
        coEvery { emailVerifyApi.confirmCode(any()) } throws httpException(400)
        vm = createViewModel()
        advanceUntilIdle()
        vm.updateCode("123456")
        vm.verify()
        advanceUntilIdle()
        assertThat(vm.error.value).isEqualTo("Invalid code. Please check and try again.")
    }

    @Test
    fun `verify sets error on network failure`() = runTest {
        coEvery { emailVerifyApi.sendCode(any()) } returns SendCodeResponse(600)
        coEvery { emailVerifyApi.confirmCode(any()) } throws RuntimeException("No network")
        vm = createViewModel()
        advanceUntilIdle()
        vm.updateCode("123456")
        vm.verify()
        advanceUntilIdle()
        assertThat(vm.error.value).isEqualTo("Network error. Check your connection.")
    }

    // --- cooldown ---

    @Test
    fun `first cooldown is exactly 60 seconds`() = runTest {
        coEvery { emailVerifyApi.sendCode(any()) } returns SendCodeResponse(600)
        vm = createViewModel()
        runCurrent()
        assertThat(vm.cooldown.value).isEqualTo(60)
    }

    @Test
    fun `second cooldown is exactly 120 seconds`() = runTest {
        coEvery { emailVerifyApi.sendCode(any()) } returns SendCodeResponse(600)
        vm = createViewModel()
        runCurrent()
        // Exhaust first cooldown
        advanceTimeBy(61_000)
        runCurrent()
        assertThat(vm.cooldown.value).isEqualTo(0)
        vm.sendCode()
        runCurrent()
        assertThat(vm.cooldown.value).isEqualTo(120)
    }

    @Test
    fun `third cooldown is exactly 300 seconds`() = runTest {
        coEvery { emailVerifyApi.sendCode(any()) } returns SendCodeResponse(600)
        vm = createViewModel()
        runCurrent()
        // Exhaust first cooldown (60s)
        advanceTimeBy(61_000)
        runCurrent()
        vm.sendCode()
        runCurrent()
        // Exhaust second cooldown (120s)
        advanceTimeBy(121_000)
        runCurrent()
        vm.sendCode()
        runCurrent()
        assertThat(vm.cooldown.value).isEqualTo(300)
    }

    @Test
    fun `cooldown decrements to zero`() = runTest {
        coEvery { emailVerifyApi.sendCode(any()) } returns SendCodeResponse(600)
        vm = createViewModel()
        runCurrent()
        assertThat(vm.cooldown.value).isEqualTo(60)
        advanceTimeBy(61_000)
        runCurrent()
        assertThat(vm.cooldown.value).isEqualTo(0)
    }
}
