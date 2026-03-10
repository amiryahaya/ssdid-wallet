package my.ssdid.wallet.feature.identity

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import androidx.lifecycle.SavedStateHandle
import my.ssdid.wallet.domain.SsdidClient
import my.ssdid.wallet.domain.model.Algorithm
import my.ssdid.wallet.domain.model.Identity
import my.ssdid.wallet.domain.vault.VaultStorage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class CreateIdentityViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: CreateIdentityViewModel
    private lateinit var client: SsdidClient
    private lateinit var storage: VaultStorage

    private val testIdentity = Identity(
        name = "Test",
        did = "did:ssdid:abc123",
        keyId = "did:ssdid:abc123#key-1",
        algorithm = Algorithm.ED25519,
        publicKeyMultibase = "z6Mkf5rGMoatrSj1f4CyvuHBeXJELe9RPdzo2PKGNCKVtZxP",
        createdAt = "2024-01-01T00:00:00Z"
    )

    @Before
    fun setup() {
        client = mockk(relaxed = true)
        storage = mockk(relaxed = true)
        viewModel = CreateIdentityViewModel(client, storage, SavedStateHandle())
    }

    @Test
    fun `initial state has isCreating false and no error`() {
        assertThat(viewModel.isCreating.value).isFalse()
        assertThat(viewModel.error.value).isNull()
    }

    @Test
    fun `createIdentity success calls onSuccess and marks onboarding completed`() = runTest {
        coEvery { client.initIdentity("Test", Algorithm.ED25519) } returns Result.success(testIdentity)
        coEvery { storage.setOnboardingCompleted() } returns Unit

        var successCalled = false
        viewModel.createIdentity("Test", Algorithm.ED25519) { successCalled = true }

        assertThat(successCalled).isTrue()
        assertThat(viewModel.isCreating.value).isFalse()
        assertThat(viewModel.error.value).isNull()
        coVerify { storage.setOnboardingCompleted() }
    }

    @Test
    fun `createIdentity failure sets error message`() = runTest {
        coEvery { client.initIdentity("Test", Algorithm.ED25519) } returns Result.failure(
            RuntimeException("Network error")
        )

        var successCalled = false
        viewModel.createIdentity("Test", Algorithm.ED25519) { successCalled = true }

        assertThat(successCalled).isFalse()
        assertThat(viewModel.error.value).isEqualTo("Network error")
        assertThat(viewModel.isCreating.value).isFalse()
    }

    @Test
    fun `createIdentity failure with null message uses default error`() = runTest {
        coEvery { client.initIdentity("Test", Algorithm.KAZ_SIGN_128) } returns Result.failure(
            RuntimeException()
        )

        viewModel.createIdentity("Test", Algorithm.KAZ_SIGN_128) { }

        assertThat(viewModel.error.value).isEqualTo("Failed to create identity")
    }

    @Test
    fun `createIdentity sets isCreating to false after completion`() = runTest {
        coEvery { client.initIdentity(any(), any()) } returns Result.success(testIdentity)

        viewModel.createIdentity("Test", Algorithm.ED25519) { }

        assertThat(viewModel.isCreating.value).isFalse()
    }
}

class MainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }
    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
