package my.ssdid.wallet.feature.profile

import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import my.ssdid.wallet.domain.model.CredentialSubject
import my.ssdid.wallet.domain.model.Proof
import my.ssdid.wallet.domain.model.VerifiableCredential
import my.ssdid.wallet.domain.profile.ProfileManager
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProfileSetupViewModelTest {

    private lateinit var profileManager: ProfileManager
    private lateinit var vm: ProfileSetupViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        profileManager = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ProfileSetupViewModel {
        return ProfileSetupViewModel(profileManager)
    }

    @Test
    fun `initial state has empty fields and no errors`() = runTest {
        coEvery { profileManager.getProfile() } returns null
        vm = createViewModel()
        advanceUntilIdle()
        assertThat(vm.name.value).isEmpty()
        assertThat(vm.email.value).isEmpty()
        assertThat(vm.nameError.value).isNull()
        assertThat(vm.emailError.value).isNull()
    }

    @Test
    fun `loads existing profile in edit mode`() = runTest {
        val profile = VerifiableCredential(
            id = "urn:ssdid:profile",
            type = listOf("VerifiableCredential", "ProfileCredential"),
            issuer = "did:ssdid:self",
            issuanceDate = "2026-03-11T00:00:00Z",
            credentialSubject = CredentialSubject(
                id = "did:ssdid:self",
                claims = mapOf("name" to "Alice", "email" to "alice@example.com")
            ),
            proof = Proof(type = "SelfIssued2024", created = "2026-03-11T00:00:00Z",
                verificationMethod = "did:ssdid:self", proofPurpose = "selfAssertion", proofValue = "")
        )
        coEvery { profileManager.getProfile() } returns profile
        vm = createViewModel()
        advanceUntilIdle()
        assertThat(vm.name.value).isEqualTo("Alice")
        assertThat(vm.email.value).isEqualTo("alice@example.com")
    }

    @Test
    fun `isValid is false when name empty`() = runTest {
        coEvery { profileManager.getProfile() } returns null
        vm = createViewModel()
        advanceUntilIdle()
        vm.updateEmail("alice@example.com")
        assertThat(vm.isValid.value).isFalse()
    }

    @Test
    fun `isValid is false when email invalid`() = runTest {
        coEvery { profileManager.getProfile() } returns null
        vm = createViewModel()
        advanceUntilIdle()
        vm.updateName("Alice")
        vm.updateEmail("not-an-email")
        assertThat(vm.isValid.value).isFalse()
    }

    @Test
    fun `isValid is true when name and email valid`() = runTest {
        coEvery { profileManager.getProfile() } returns null
        vm = createViewModel()
        advanceUntilIdle()
        vm.updateName("Alice")
        vm.updateEmail("alice@example.com")
        assertThat(vm.isValid.value).isTrue()
    }

    @Test
    fun `save calls profileManager and sets saved state`() = runTest {
        coEvery { profileManager.getProfile() } returns null
        coEvery { profileManager.saveProfile(any(), any()) } returns Result.success(Unit)
        vm = createViewModel()
        advanceUntilIdle()
        vm.updateName("Alice")
        vm.updateEmail("alice@example.com")
        vm.save()
        advanceUntilIdle()
        assertThat(vm.saved.value).isTrue()
        coVerify { profileManager.saveProfile("Alice", "alice@example.com") }
    }

    @Test
    fun `save sets error on failure`() = runTest {
        coEvery { profileManager.getProfile() } returns null
        coEvery { profileManager.saveProfile(any(), any()) } returns Result.failure(RuntimeException("Storage error"))
        vm = createViewModel()
        advanceUntilIdle()
        vm.updateName("Alice")
        vm.updateEmail("alice@example.com")
        vm.save()
        advanceUntilIdle()
        assertThat(vm.saved.value).isFalse()
        assertThat(vm.error.value).isNotNull()
    }
}
