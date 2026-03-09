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
import my.ssdid.wallet.domain.recovery.social.SocialRecoveryManager
import my.ssdid.wallet.domain.vault.VaultStorage
import my.ssdid.wallet.feature.identity.MainDispatcherRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SocialRecoveryRestoreViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: SocialRecoveryRestoreViewModel
    private lateinit var socialRecoveryManager: SocialRecoveryManager
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
        socialRecoveryManager = mockk(relaxed = true)
        ssdidClient = mockk(relaxed = true)
        storage = mockk(relaxed = true)
        viewModel = SocialRecoveryRestoreViewModel(
            socialRecoveryManager = socialRecoveryManager,
            ssdidClient = ssdidClient,
            storage = storage
        )
    }

    @Test
    fun `initial state is Idle with 2 empty shares`() {
        assertThat(viewModel.state.value).isEqualTo(SocialRestoreState.Idle)
        assertThat(viewModel.shares.value).hasSize(2)
        assertThat(viewModel.shares.value[0]).isEqualTo(ShareEntry())
        assertThat(viewModel.shares.value[1]).isEqualTo(ShareEntry())
    }

    @Test
    fun `addShare appends entry`() {
        viewModel.addShare()

        assertThat(viewModel.shares.value).hasSize(3)
    }

    @Test
    fun `removeShare removes entry when more than 2`() {
        viewModel.addShare()
        assertThat(viewModel.shares.value).hasSize(3)

        viewModel.removeShare(2)

        assertThat(viewModel.shares.value).hasSize(2)
    }

    @Test
    fun `removeShare blocked when only 2 shares`() {
        viewModel.removeShare(0)

        assertThat(viewModel.shares.value).hasSize(2)
    }

    @Test
    fun `restore with blank did shows error`() = runTest {
        viewModel.updateShare(0, ShareEntry(index = "1", data = "abc"))
        viewModel.updateShare(1, ShareEntry(index = "2", data = "def"))

        viewModel.restore("", "Test", Algorithm.ED25519)

        assertThat(viewModel.state.value).isInstanceOf(SocialRestoreState.Error::class.java)
        assertThat((viewModel.state.value as SocialRestoreState.Error).message)
            .isEqualTo("DID and identity name are required")
    }

    @Test
    fun `restore with non-numeric share index shows error`() = runTest {
        viewModel.updateShare(0, ShareEntry(index = "abc", data = "sharedata1"))
        viewModel.updateShare(1, ShareEntry(index = "2", data = "sharedata2"))

        viewModel.restore("did:ssdid:test", "Test", Algorithm.ED25519)

        assertThat(viewModel.state.value).isInstanceOf(SocialRestoreState.Error::class.java)
        assertThat((viewModel.state.value as SocialRestoreState.Error).message)
            .isEqualTo("Share 1: index must be a number")
    }

    @Test
    fun `restore with duplicate share indices shows error`() = runTest {
        viewModel.updateShare(0, ShareEntry(index = "1", data = "sharedata1"))
        viewModel.updateShare(1, ShareEntry(index = "1", data = "sharedata2"))

        viewModel.restore("did:ssdid:test", "Test", Algorithm.ED25519)

        assertThat(viewModel.state.value).isInstanceOf(SocialRestoreState.Error::class.java)
        assertThat((viewModel.state.value as SocialRestoreState.Error).message)
            .isEqualTo("Duplicate share indices detected")
    }

    @Test
    fun `restore with invalid DID format shows error`() = runTest {
        viewModel.updateShare(0, ShareEntry(index = "1", data = "dGVzdA"))
        viewModel.updateShare(1, ShareEntry(index = "2", data = "dGVzdA"))

        viewModel.restore("not-a-did", "Test", Algorithm.ED25519)

        assertThat(viewModel.state.value).isInstanceOf(SocialRestoreState.Error::class.java)
        assertThat((viewModel.state.value as SocialRestoreState.Error).message)
            .isEqualTo("Invalid DID format")
    }

    @Test
    fun `restore with blank name shows error`() = runTest {
        viewModel.updateShare(0, ShareEntry(index = "1", data = "dGVzdA"))
        viewModel.updateShare(1, ShareEntry(index = "2", data = "dGVzdA"))

        viewModel.restore("did:ssdid:test", "", Algorithm.ED25519)

        assertThat(viewModel.state.value).isInstanceOf(SocialRestoreState.Error::class.java)
        assertThat((viewModel.state.value as SocialRestoreState.Error).message)
            .isEqualTo("DID and identity name are required")
    }

    @Test
    fun `restore with blank share data shows error`() = runTest {
        viewModel.updateShare(0, ShareEntry(index = "1", data = "dGVzdA"))
        viewModel.updateShare(1, ShareEntry(index = "2", data = ""))

        viewModel.restore("did:ssdid:test", "Test", Algorithm.ED25519)

        assertThat(viewModel.state.value).isInstanceOf(SocialRestoreState.Error::class.java)
        assertThat((viewModel.state.value as SocialRestoreState.Error).message)
            .isEqualTo("Share 2: data is required")
    }

    @Test
    fun `restore with invalid Base64 share data shows error`() = runTest {
        viewModel.updateShare(0, ShareEntry(index = "1", data = "!!!invalid!!!"))
        viewModel.updateShare(1, ShareEntry(index = "2", data = "dGVzdA"))

        viewModel.restore("did:ssdid:test", "Test", Algorithm.ED25519)

        assertThat(viewModel.state.value).isInstanceOf(SocialRestoreState.Error::class.java)
        assertThat((viewModel.state.value as SocialRestoreState.Error).message)
            .isEqualTo("Share 1: invalid Base64 data")
    }

    @Test
    fun `restore success transitions to Success and calls updateDidDocument and setOnboardingCompleted`() = runTest {
        viewModel.updateShare(0, ShareEntry(index = "1", data = "dGVzdA"))
        viewModel.updateShare(1, ShareEntry(index = "2", data = "dGVzdA"))

        coEvery {
            socialRecoveryManager.recoverWithShares(any(), any(), any(), any())
        } returns Result.success(testIdentity)
        coEvery { storage.setOnboardingCompleted() } returns Unit

        viewModel.restore("did:ssdid:test", "Test", Algorithm.ED25519)
        advanceUntilIdle()

        assertThat(viewModel.state.value).isEqualTo(SocialRestoreState.Success)
        coVerify { ssdidClient.updateDidDocument(testIdentity.keyId) }
        coVerify { storage.setOnboardingCompleted() }
    }

    @Test
    fun `restore success still succeeds when updateDidDocument fails`() = runTest {
        viewModel.updateShare(0, ShareEntry(index = "1", data = "dGVzdA"))
        viewModel.updateShare(1, ShareEntry(index = "2", data = "dGVzdA"))

        coEvery {
            socialRecoveryManager.recoverWithShares(any(), any(), any(), any())
        } returns Result.success(testIdentity)
        coEvery { ssdidClient.updateDidDocument(any()) } throws RuntimeException("Network error")

        viewModel.restore("did:ssdid:test", "Test", Algorithm.ED25519)
        advanceUntilIdle()

        assertThat(viewModel.state.value).isEqualTo(SocialRestoreState.Success)
        coVerify { storage.setOnboardingCompleted() }
    }

    @Test
    fun `restore failure transitions to Error`() = runTest {
        viewModel.updateShare(0, ShareEntry(index = "1", data = "dGVzdA"))
        viewModel.updateShare(1, ShareEntry(index = "2", data = "dGVzdA"))

        coEvery {
            socialRecoveryManager.recoverWithShares(any(), any(), any(), any())
        } returns Result.failure(RuntimeException("Recovery failed"))

        viewModel.restore("did:ssdid:test", "Test", Algorithm.ED25519)
        advanceUntilIdle()

        assertThat(viewModel.state.value).isInstanceOf(SocialRestoreState.Error::class.java)
        assertThat((viewModel.state.value as SocialRestoreState.Error).message)
            .isEqualTo("Recovery failed")
    }

    @Test
    fun `resetState returns to Idle`() = runTest {
        viewModel.updateShare(0, ShareEntry(index = "1", data = "dGVzdA"))
        viewModel.updateShare(1, ShareEntry(index = "2", data = "dGVzdA"))
        viewModel.restore("did:ssdid:test", "", Algorithm.ED25519)
        assertThat(viewModel.state.value).isInstanceOf(SocialRestoreState.Error::class.java)

        viewModel.resetState()

        assertThat(viewModel.state.value).isEqualTo(SocialRestoreState.Idle)
    }
}
