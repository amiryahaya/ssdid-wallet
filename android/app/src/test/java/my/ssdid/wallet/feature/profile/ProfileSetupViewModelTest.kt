package my.ssdid.wallet.feature.profile

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import my.ssdid.wallet.domain.model.Algorithm
import my.ssdid.wallet.domain.model.Identity
import my.ssdid.wallet.domain.vault.Vault
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProfileSetupViewModelTest {

    private lateinit var vault: Vault
    private lateinit var vm: ProfileSetupViewModel

    private val testKeyId = "did:ssdid:abc#key-1"

    @Before
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        vault = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ProfileSetupViewModel {
        return ProfileSetupViewModel(vault, SavedStateHandle(mapOf("keyId" to testKeyId)))
    }

    @Test
    fun `initial state has empty fields and no errors`() = runTest {
        coEvery { vault.getIdentity(testKeyId) } returns null
        vm = createViewModel()
        advanceUntilIdle()
        assertThat(vm.name.value).isEmpty()
        assertThat(vm.email.value).isEmpty()
        assertThat(vm.nameError.value).isNull()
        assertThat(vm.emailError.value).isNull()
    }

    @Test
    fun `loads existing identity profile in edit mode`() = runTest {
        val identity = Identity(
            name = "Alice",
            keyId = testKeyId,
            did = "did:ssdid:abc",
            algorithm = Algorithm.ED25519,
            publicKeyMultibase = "z6Mk...",
            createdAt = "2026-03-11T00:00:00Z",
            profileName = "Alice",
            email = "alice@example.com"
        )
        coEvery { vault.getIdentity(testKeyId) } returns identity
        vm = createViewModel()
        advanceUntilIdle()
        assertThat(vm.name.value).isEqualTo("Alice")
        assertThat(vm.email.value).isEqualTo("alice@example.com")
    }

    @Test
    fun `isValid is false when name empty`() = runTest {
        coEvery { vault.getIdentity(testKeyId) } returns null
        vm = createViewModel()
        advanceUntilIdle()
        vm.updateEmail("alice@example.com")
        assertThat(vm.isValid.value).isFalse()
    }

    @Test
    fun `isValid is false when email invalid`() = runTest {
        coEvery { vault.getIdentity(testKeyId) } returns null
        vm = createViewModel()
        advanceUntilIdle()
        vm.updateName("Alice")
        vm.updateEmail("not-an-email")
        assertThat(vm.isValid.value).isFalse()
    }

    @Test
    fun `isValid is true when name and email valid`() = runTest {
        coEvery { vault.getIdentity(testKeyId) } returns null
        vm = createViewModel()
        advanceUntilIdle()
        vm.updateName("Alice")
        vm.updateEmail("alice@example.com")
        assertThat(vm.isValid.value).isTrue()
    }

    @Test
    fun `save calls vault updateIdentityProfile and sets saved state`() = runTest {
        coEvery { vault.getIdentity(testKeyId) } returns null
        coEvery { vault.updateIdentityProfile(any(), any(), any(), any()) } returns Result.success(Unit)
        vm = createViewModel()
        advanceUntilIdle()
        vm.updateName("Alice")
        vm.updateEmail("alice@example.com")
        vm.save()
        advanceUntilIdle()
        assertThat(vm.saved.value).isTrue()
        coVerify { vault.updateIdentityProfile(testKeyId, profileName = "Alice", email = "alice@example.com") }
    }

    @Test
    fun `save sets error on failure`() = runTest {
        coEvery { vault.getIdentity(testKeyId) } returns null
        coEvery { vault.updateIdentityProfile(any(), any(), any(), any()) } returns Result.failure(RuntimeException("Storage error"))
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
