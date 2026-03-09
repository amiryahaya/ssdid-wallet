package my.ssdid.wallet.feature.recovery

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import my.ssdid.wallet.domain.model.Algorithm
import my.ssdid.wallet.domain.model.Identity
import my.ssdid.wallet.domain.recovery.social.SocialRecoveryManager
import my.ssdid.wallet.domain.vault.Vault
import my.ssdid.wallet.feature.identity.MainDispatcherRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SocialRecoverySetupViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: SocialRecoverySetupViewModel
    private lateinit var socialRecoveryManager: SocialRecoveryManager
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
        socialRecoveryManager = mockk(relaxed = true)
        vault = mockk(relaxed = true)
        coEvery { vault.getIdentity("did:ssdid:test#key-1") } returns testIdentity
        viewModel = SocialRecoverySetupViewModel(
            socialRecoveryManager = socialRecoveryManager,
            vault = vault,
            savedStateHandle = SavedStateHandle(mapOf("keyId" to "did:ssdid:test#key-1"))
        )
    }

    @Test
    fun `initial state is Idle with 2 guardians and threshold 2`() {
        assertThat(viewModel.state.value).isEqualTo(SocialSetupState.Idle)
        assertThat(viewModel.guardians.value).hasSize(2)
        assertThat(viewModel.threshold.value).isEqualTo(2)
    }

    @Test
    fun `addGuardian appends entry and preserves threshold`() {
        viewModel.addGuardian()

        assertThat(viewModel.guardians.value).hasSize(3)
        assertThat(viewModel.threshold.value).isEqualTo(2)
    }

    @Test
    fun `removeGuardian removes entry and clamps threshold`() {
        // Start with 3 guardians, threshold 3
        viewModel.addGuardian()
        viewModel.setThreshold(3)
        assertThat(viewModel.guardians.value).hasSize(3)
        assertThat(viewModel.threshold.value).isEqualTo(3)

        // Remove one: threshold should clamp to 2
        viewModel.removeGuardian(2)

        assertThat(viewModel.guardians.value).hasSize(2)
        assertThat(viewModel.threshold.value).isEqualTo(2)
    }

    @Test
    fun `removeGuardian blocked when only 2 guardians`() {
        viewModel.removeGuardian(0)

        assertThat(viewModel.guardians.value).hasSize(2)
    }

    @Test
    fun `setThreshold clamps to valid range`() {
        // Cannot go below 2
        viewModel.setThreshold(1)
        assertThat(viewModel.threshold.value).isEqualTo(2)

        // Cannot exceed guardian count (2)
        viewModel.setThreshold(5)
        assertThat(viewModel.threshold.value).isEqualTo(2)

        // Add guardians and set threshold within range
        viewModel.addGuardian()
        viewModel.addGuardian()
        viewModel.setThreshold(3)
        assertThat(viewModel.threshold.value).isEqualTo(3)
    }

    @Test
    fun `createShares with blank guardian name shows error`() = runTest {
        // Default guardians have blank names and DIDs
        viewModel.createShares()

        assertThat(viewModel.state.value).isInstanceOf(SocialSetupState.Error::class.java)
        assertThat((viewModel.state.value as SocialSetupState.Error).message)
            .isEqualTo("All guardians must have a name and DID")
    }

    @Test
    fun `createShares with null identity returns silently`() = runTest {
        // Create VM with no identity
        coEvery { vault.getIdentity(any()) } returns null
        val vm = SocialRecoverySetupViewModel(
            socialRecoveryManager = socialRecoveryManager,
            vault = vault,
            savedStateHandle = SavedStateHandle(mapOf("keyId" to "nonexistent"))
        )

        vm.createShares()

        assertThat(vm.state.value).isEqualTo(SocialSetupState.Idle)
    }

    @Test
    fun `createShares success transitions to Success state`() = runTest {
        // Fill in guardian details
        viewModel.updateGuardian(0, GuardianEntry(name = "Alice", did = "did:ssdid:alice"))
        viewModel.updateGuardian(1, GuardianEntry(name = "Bob", did = "did:ssdid:bob"))

        val sharesMap = mapOf("uuid1" to "share1data", "uuid2" to "share2data")
        coEvery {
            socialRecoveryManager.setupSocialRecovery(any(), any(), any())
        } returns Result.success(sharesMap)

        viewModel.createShares()

        assertThat(viewModel.state.value).isInstanceOf(SocialSetupState.Success::class.java)
        val success = viewModel.state.value as SocialSetupState.Success
        assertThat(success.guardianShares).hasSize(2)
        assertThat(success.guardianShares[0].first).isEqualTo("Alice")
        assertThat(success.guardianShares[1].first).isEqualTo("Bob")
    }

    @Test
    fun `createShares failure transitions to Error state`() = runTest {
        viewModel.updateGuardian(0, GuardianEntry(name = "Alice", did = "did:ssdid:alice"))
        viewModel.updateGuardian(1, GuardianEntry(name = "Bob", did = "did:ssdid:bob"))

        coEvery {
            socialRecoveryManager.setupSocialRecovery(any(), any(), any())
        } returns Result.failure(RuntimeException("Shamir split failed"))

        viewModel.createShares()

        assertThat(viewModel.state.value).isInstanceOf(SocialSetupState.Error::class.java)
        assertThat((viewModel.state.value as SocialSetupState.Error).message)
            .isEqualTo("Shamir split failed")
    }
}
