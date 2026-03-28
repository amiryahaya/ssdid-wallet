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
import my.ssdid.wallet.domain.recovery.institutional.InstitutionalRecoveryManager
import my.ssdid.wallet.domain.recovery.institutional.OrgRecoveryConfig
import my.ssdid.sdk.domain.vault.Vault
import my.ssdid.wallet.feature.identity.MainDispatcherRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InstitutionalSetupViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: InstitutionalSetupViewModel
    private lateinit var institutionalRecoveryManager: InstitutionalRecoveryManager
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
        institutionalRecoveryManager = mockk(relaxed = true)
        vault = mockk(relaxed = true)
        coEvery { vault.getIdentity("did:ssdid:test#key-1") } returns testIdentity
        viewModel = InstitutionalSetupViewModel(
            institutionalRecoveryManager = institutionalRecoveryManager,
            vault = vault,
            savedStateHandle = SavedStateHandle(mapOf("keyId" to "did:ssdid:test#key-1"))
        )
    }

    @Test
    fun `initial state is Idle`() {
        assertThat(viewModel.state.value).isEqualTo(InstitutionalSetupState.Idle)
    }

    @Test
    fun `enroll with blank orgName shows error`() = runTest {
        viewModel.enroll("", "did:ssdid:org", "dGVzdA==")

        assertThat(viewModel.state.value).isInstanceOf(InstitutionalSetupState.Error::class.java)
        assertThat((viewModel.state.value as InstitutionalSetupState.Error).message)
            .isEqualTo("All fields are required")
    }

    @Test
    fun `enroll with blank orgDid shows error`() = runTest {
        viewModel.enroll("TestOrg", "", "dGVzdA==")

        assertThat(viewModel.state.value).isInstanceOf(InstitutionalSetupState.Error::class.java)
        assertThat((viewModel.state.value as InstitutionalSetupState.Error).message)
            .isEqualTo("All fields are required")
    }

    @Test
    fun `enroll with invalid DID format shows error`() = runTest {
        viewModel.enroll("TestOrg", "not-a-did", "dGVzdA==")

        assertThat(viewModel.state.value).isInstanceOf(InstitutionalSetupState.Error::class.java)
        assertThat((viewModel.state.value as InstitutionalSetupState.Error).message)
            .isEqualTo("Invalid DID format")
    }

    @Test
    fun `enroll with null identity returns silently`() = runTest {
        coEvery { vault.getIdentity(any()) } returns null
        val vm = InstitutionalSetupViewModel(
            institutionalRecoveryManager = institutionalRecoveryManager,
            vault = vault,
            savedStateHandle = SavedStateHandle(mapOf("keyId" to "nonexistent"))
        )
        advanceUntilIdle()

        vm.enroll("OrgName", "did:ssdid:org", "dGVzdA==")

        assertThat(vm.state.value).isEqualTo(InstitutionalSetupState.Idle)
    }

    @Test
    fun `enroll success transitions to Success with orgName`() = runTest {
        val orgConfig = OrgRecoveryConfig(
            userDid = "did:ssdid:test",
            orgDid = "did:ssdid:org",
            orgName = "TestOrg",
            encryptedRecoveryKey = "dGVzdA",
            enrolledAt = "2024-01-01T00:00:00Z"
        )
        coEvery {
            institutionalRecoveryManager.enrollOrganization(any(), any(), any(), any())
        } returns Result.success(orgConfig)

        viewModel.enroll("TestOrg", "did:ssdid:org", "dGVzdA==")
        advanceUntilIdle()

        assertThat(viewModel.state.value).isInstanceOf(InstitutionalSetupState.Success::class.java)
        assertThat((viewModel.state.value as InstitutionalSetupState.Success).orgName)
            .isEqualTo("TestOrg")
    }

    @Test
    fun `enroll with invalid Base64 transitions to Error`() = runTest {
        viewModel.enroll("TestOrg", "did:ssdid:org", "!!!invalid-base64!!!")
        advanceUntilIdle()

        assertThat(viewModel.state.value).isInstanceOf(InstitutionalSetupState.Error::class.java)
        assertThat((viewModel.state.value as InstitutionalSetupState.Error).message)
            .contains("Invalid Base64 input")
    }

    @Test
    fun `enroll failure from manager transitions to Error`() = runTest {
        coEvery {
            institutionalRecoveryManager.enrollOrganization(any(), any(), any(), any())
        } returns Result.failure(RuntimeException("Enrollment rejected"))

        viewModel.enroll("TestOrg", "did:ssdid:org", "dGVzdA==")
        advanceUntilIdle()

        assertThat(viewModel.state.value).isInstanceOf(InstitutionalSetupState.Error::class.java)
        assertThat((viewModel.state.value as InstitutionalSetupState.Error).message)
            .isEqualTo("Enrollment rejected")
    }

    @Test
    fun `resetState returns to Idle`() = runTest {
        viewModel.enroll("TestOrg", "not-a-did", "dGVzdA==")
        assertThat(viewModel.state.value).isInstanceOf(InstitutionalSetupState.Error::class.java)

        viewModel.resetState()

        assertThat(viewModel.state.value).isEqualTo(InstitutionalSetupState.Idle)
    }

    @Test
    fun `hasExistingConfig reflects institutional recovery status`() = runTest {
        coEvery { institutionalRecoveryManager.hasOrgRecovery("did:ssdid:test") } returns true
        val vm = InstitutionalSetupViewModel(
            institutionalRecoveryManager = institutionalRecoveryManager,
            vault = vault,
            savedStateHandle = SavedStateHandle(mapOf("keyId" to "did:ssdid:test#key-1"))
        )
        advanceUntilIdle()

        assertThat(vm.hasExistingConfig.value).isTrue()
    }

    @Test
    fun `init handles vault error gracefully`() = runTest {
        coEvery { vault.getIdentity(any()) } throws RuntimeException("DB error")
        val vm = InstitutionalSetupViewModel(
            institutionalRecoveryManager = institutionalRecoveryManager,
            vault = vault,
            savedStateHandle = SavedStateHandle(mapOf("keyId" to "bad-key"))
        )
        advanceUntilIdle()

        assertThat(vm.identity.value).isNull()
        assertThat(vm.state.value).isEqualTo(InstitutionalSetupState.Idle)
    }

    @Test
    fun `enroll with blank encryptedKeyBase64 shows error`() = runTest {
        viewModel.enroll("TestOrg", "did:ssdid:org", "")

        assertThat(viewModel.state.value).isInstanceOf(InstitutionalSetupState.Error::class.java)
        assertThat((viewModel.state.value as InstitutionalSetupState.Error).message)
            .isEqualTo("All fields are required")
    }

    @Test
    fun `enroll passes correct arguments to enrollOrganization`() = runTest {
        val orgConfig = OrgRecoveryConfig(
            userDid = "did:ssdid:test",
            orgDid = "did:ssdid:org",
            orgName = "TestOrg",
            encryptedRecoveryKey = "dGVzdA",
            enrolledAt = "2024-01-01T00:00:00Z"
        )
        coEvery {
            institutionalRecoveryManager.enrollOrganization(any(), any(), any(), any())
        } returns Result.success(orgConfig)

        viewModel.enroll("TestOrg", "did:ssdid:org", "dGVzdA")
        advanceUntilIdle()

        coVerify {
            institutionalRecoveryManager.enrollOrganization(
                testIdentity, "did:ssdid:org", "TestOrg", any()
            )
        }
    }
}
