package my.ssdid.wallet.feature.recovery

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import my.ssdid.sdk.domain.model.Algorithm
import my.ssdid.sdk.domain.model.Identity
import my.ssdid.sdk.domain.recovery.RecoveryManager
import my.ssdid.sdk.domain.recovery.institutional.InstitutionalRecoveryManager
import my.ssdid.sdk.domain.recovery.social.SocialRecoveryManager
import my.ssdid.sdk.domain.vault.Vault
import my.ssdid.wallet.feature.identity.MainDispatcherRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecoverySetupViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: RecoverySetupViewModel
    private lateinit var recoveryManager: RecoveryManager
    private lateinit var socialRecoveryManager: SocialRecoveryManager
    private lateinit var institutionalManager: InstitutionalRecoveryManager
    private lateinit var vault: Vault

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
        socialRecoveryManager = mockk(relaxed = true)
        institutionalManager = mockk(relaxed = true)
        vault = mockk(relaxed = true)
        coEvery { vault.getIdentity("did:ssdid:test#key-1") } returns testIdentity
    }

    private fun createViewModel(keyId: String = "did:ssdid:test#key-1"): RecoverySetupViewModel {
        return RecoverySetupViewModel(
            recoveryManager = recoveryManager,
            socialRecoveryManager = socialRecoveryManager,
            institutionalManager = institutionalManager,
            vault = vault,
            savedStateHandle = SavedStateHandle(mapOf("keyId" to keyId))
        )
    }

    @Test
    fun `initial state is Idle`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value).isEqualTo(RecoverySetupState.Idle)
    }

    @Test
    fun `init loads identity`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.identity.value).isEqualTo(testIdentity)
    }

    @Test
    fun `init loads social recovery status`() = runTest {
        coEvery { socialRecoveryManager.hasSocialRecovery("did:ssdid:test") } returns true
        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.hasSocialRecovery.value).isTrue()
    }

    @Test
    fun `init loads institutional recovery status`() = runTest {
        coEvery { institutionalManager.hasOrgRecovery("did:ssdid:test") } returns true
        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.hasInstitutionalRecovery.value).isTrue()
    }

    @Test
    fun `init handles vault error gracefully`() = runTest {
        coEvery { vault.getIdentity(any()) } throws RuntimeException("DB error")
        viewModel = createViewModel("bad-key")
        advanceUntilIdle()

        assertThat(viewModel.identity.value).isNull()
        assertThat(viewModel.state.value).isEqualTo(RecoverySetupState.Idle)
    }

    @Test
    fun `generateRecoveryKey with null identity returns early`() = runTest {
        coEvery { vault.getIdentity(any()) } returns null
        viewModel = createViewModel("nonexistent")
        advanceUntilIdle()

        viewModel.generateRecoveryKey()
        advanceUntilIdle()

        assertThat(viewModel.state.value).isEqualTo(RecoverySetupState.Idle)
    }

    @Test
    fun `generateRecoveryKey success transitions to Success`() = runTest {
        val keyBytes = byteArrayOf(1, 2, 3, 4)
        coEvery { recoveryManager.generateRecoveryKey(testIdentity) } returns Result.success(keyBytes)
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.generateRecoveryKey()
        advanceUntilIdle()

        assertThat(viewModel.state.value).isInstanceOf(RecoverySetupState.Success::class.java)
        assertThat((viewModel.state.value as RecoverySetupState.Success).recoveryKeyBytes)
            .isEqualTo(keyBytes)
    }

    @Test
    fun `generateRecoveryKey failure transitions to Error`() = runTest {
        coEvery {
            recoveryManager.generateRecoveryKey(testIdentity)
        } returns Result.failure(RuntimeException("Generation failed"))
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.generateRecoveryKey()
        advanceUntilIdle()

        assertThat(viewModel.state.value).isInstanceOf(RecoverySetupState.Error::class.java)
        assertThat((viewModel.state.value as RecoverySetupState.Error).message)
            .isEqualTo("Generation failed")
    }

    @Test
    fun `generateRecoveryKey passes correct identity to manager`() = runTest {
        coEvery { recoveryManager.generateRecoveryKey(any()) } returns Result.success(byteArrayOf())
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.generateRecoveryKey()
        advanceUntilIdle()

        coVerify { recoveryManager.generateRecoveryKey(testIdentity) }
    }
}
