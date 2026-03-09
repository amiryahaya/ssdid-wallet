package my.ssdid.wallet.feature.recovery

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import my.ssdid.wallet.domain.SsdidClient
import my.ssdid.wallet.domain.model.Algorithm
import my.ssdid.wallet.domain.model.Identity
import my.ssdid.wallet.domain.recovery.RecoveryManager
import my.ssdid.wallet.domain.vault.VaultStorage
import my.ssdid.wallet.feature.identity.MainDispatcherRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecoveryRestoreViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: RecoveryRestoreViewModel
    private lateinit var recoveryManager: RecoveryManager
    private lateinit var ssdidClient: SsdidClient
    private lateinit var storage: VaultStorage

    private val testIdentity = Identity(
        name = "Test",
        did = "did:ssdid:test",
        keyId = "did:ssdid:test#key-1",
        algorithm = Algorithm.ED25519,
        publicKeyMultibase = "z6Mk...",
        createdAt = "2024-01-01T00:00:00Z"
    )

    @Before
    fun setup() {
        recoveryManager = mockk(relaxed = true)
        ssdidClient = mockk(relaxed = true)
        storage = mockk(relaxed = true)
        viewModel = RecoveryRestoreViewModel(
            recoveryManager = recoveryManager,
            ssdidClient = ssdidClient,
            storage = storage
        )
    }

    @Test
    fun `initial state is Idle`() {
        assertThat(viewModel.state.value).isEqualTo(RestoreState.Idle)
    }

    @Test
    fun `restore with blank did shows error`() = runTest {
        viewModel.restore("", "key", "Name", Algorithm.ED25519)

        assertThat(viewModel.state.value).isInstanceOf(RestoreState.Error::class.java)
        assertThat((viewModel.state.value as RestoreState.Error).message)
            .isEqualTo("All fields are required")
    }

    @Test
    fun `restore with blank recoveryKey shows error`() = runTest {
        viewModel.restore("did:ssdid:test", "", "Name", Algorithm.ED25519)

        assertThat(viewModel.state.value).isInstanceOf(RestoreState.Error::class.java)
        assertThat((viewModel.state.value as RestoreState.Error).message)
            .isEqualTo("All fields are required")
    }

    @Test
    fun `restore with blank name shows error`() = runTest {
        viewModel.restore("did:ssdid:test", "key", "", Algorithm.ED25519)

        assertThat(viewModel.state.value).isInstanceOf(RestoreState.Error::class.java)
        assertThat((viewModel.state.value as RestoreState.Error).message)
            .isEqualTo("All fields are required")
    }

    @Test
    fun `restore with invalid DID format shows error`() = runTest {
        viewModel.restore("not-a-did", "key", "Name", Algorithm.ED25519)

        assertThat(viewModel.state.value).isInstanceOf(RestoreState.Error::class.java)
        assertThat((viewModel.state.value as RestoreState.Error).message)
            .isEqualTo("Invalid DID format")
    }

    @Test
    fun `restore success transitions to Success and calls updateDidDocument`() = runTest {
        coEvery {
            recoveryManager.restoreWithRecoveryKey(any(), any(), any(), any())
        } returns Result.success(testIdentity)

        viewModel.restore("did:ssdid:test", "recoveryKey", "Test", Algorithm.ED25519)
        advanceUntilIdle()

        assertThat(viewModel.state.value).isEqualTo(RestoreState.Success)
        coVerify { ssdidClient.updateDidDocument(testIdentity.keyId) }
        coVerify { storage.setOnboardingCompleted() }
    }

    @Test
    fun `restore success still succeeds when updateDidDocument fails`() = runTest {
        coEvery {
            recoveryManager.restoreWithRecoveryKey(any(), any(), any(), any())
        } returns Result.success(testIdentity)
        coEvery { ssdidClient.updateDidDocument(any()) } throws RuntimeException("Network error")

        viewModel.restore("did:ssdid:test", "recoveryKey", "Test", Algorithm.ED25519)
        advanceUntilIdle()

        assertThat(viewModel.state.value).isEqualTo(RestoreState.Success)
        coVerify { storage.setOnboardingCompleted() }
    }

    @Test
    fun `restore failure transitions to Error`() = runTest {
        coEvery {
            recoveryManager.restoreWithRecoveryKey(any(), any(), any(), any())
        } returns Result.failure(RuntimeException("Restoration failed"))

        viewModel.restore("did:ssdid:test", "recoveryKey", "Test", Algorithm.ED25519)
        advanceUntilIdle()

        assertThat(viewModel.state.value).isInstanceOf(RestoreState.Error::class.java)
        assertThat((viewModel.state.value as RestoreState.Error).message)
            .isEqualTo("Restoration failed")
    }

    @Test
    fun `restore trims recovery key before passing to manager`() = runTest {
        coEvery {
            recoveryManager.restoreWithRecoveryKey(any(), any(), any(), any())
        } returns Result.success(testIdentity)

        viewModel.restore("did:ssdid:test", "  myKey  ", "MyName", Algorithm.KAZ_SIGN_192)
        advanceUntilIdle()

        coVerify {
            recoveryManager.restoreWithRecoveryKey(
                "did:ssdid:test", "myKey", "MyName", Algorithm.KAZ_SIGN_192
            )
        }
    }

    @Test
    fun `restore passes correct arguments to recoveryManager`() = runTest {
        coEvery {
            recoveryManager.restoreWithRecoveryKey(any(), any(), any(), any())
        } returns Result.success(testIdentity)

        viewModel.restore("did:ssdid:test", "myKey", "MyName", Algorithm.KAZ_SIGN_192)
        advanceUntilIdle()

        coVerify {
            recoveryManager.restoreWithRecoveryKey(
                "did:ssdid:test", "myKey", "MyName", Algorithm.KAZ_SIGN_192
            )
        }
    }
}
