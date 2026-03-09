package my.ssdid.wallet.feature.transaction

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import my.ssdid.wallet.domain.SsdidClient
import my.ssdid.wallet.domain.model.Algorithm
import my.ssdid.wallet.domain.model.Identity
import my.ssdid.wallet.domain.transport.dto.TxSubmitResponse
import my.ssdid.wallet.domain.vault.Vault
import my.ssdid.wallet.platform.biometric.BiometricAuthenticator
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class TxSigningViewModelTest {

    @get:Rule
    val mainDispatcherRule = TxMainDispatcherRule()

    private lateinit var viewModel: TxSigningViewModel
    private lateinit var client: SsdidClient
    private lateinit var vault: Vault
    private lateinit var biometricAuth: BiometricAuthenticator

    private val testIdentity = Identity(
        name = "Test",
        did = "did:ssdid:abc123",
        keyId = "did:ssdid:abc123#key-1",
        algorithm = Algorithm.ED25519,
        publicKeyMultibase = "z6Mkf5rGMoatrSj1f4CyvuHBeXJELe9RPdzo2PKGNCKVtZxP",
        createdAt = "2024-01-01T00:00:00Z"
    )

    private val txDetails = mapOf("amount" to "100", "recipient" to "did:ssdid:xyz")

    @Before
    fun setup() {
        client = mockk(relaxed = true)
        vault = mockk(relaxed = true)
        biometricAuth = mockk(relaxed = true)

        // Stub fetchTransactionDetails (called in init) to succeed
        coEvery { client.fetchTransactionDetails(any(), any()) } returns Result.success(txDetails)

        viewModel = TxSigningViewModel(
            client = client,
            vault = vault,
            biometricAuth = biometricAuth,
            savedStateHandle = SavedStateHandle(
                mapOf("serverUrl" to "https://example.com", "sessionToken" to "tok123")
            )
        )
    }

    @Test
    fun `signTransaction transitions to Confirmed on success`() = runTest {
        coEvery { vault.listIdentities() } returns listOf(testIdentity)
        val txResponse = mockk<TxSubmitResponse>(relaxed = true)
        coEvery { client.signTransaction("tok123", testIdentity, txDetails, "https://example.com") } returns Result.success(txResponse)

        viewModel.signTransaction()

        assertThat(viewModel.state.value).isEqualTo(TxState.Confirmed)
    }

    @Test
    fun `signTransaction transitions to Failed on failure`() = runTest {
        coEvery { vault.listIdentities() } returns listOf(testIdentity)
        coEvery { client.signTransaction(any(), any(), any(), any()) } returns Result.failure(
            RuntimeException("Signing error")
        )

        viewModel.signTransaction()

        val state = viewModel.state.value
        assertThat(state).isInstanceOf(TxState.Failed::class.java)
        assertThat((state as TxState.Failed).message).isEqualTo("Signing error")
    }

    @Test
    fun `no identity available sets error state`() = runTest {
        coEvery { vault.listIdentities() } returns emptyList()

        viewModel.signTransaction()

        val state = viewModel.state.value
        assertThat(state).isInstanceOf(TxState.Failed::class.java)
        assertThat((state as TxState.Failed).message).isEqualTo("No identity available for signing")
    }

    @Test
    fun `serverUrl and sessionToken are extracted from SavedStateHandle`() {
        assertThat(viewModel.serverUrl).isEqualTo("https://example.com")
        assertThat(viewModel.sessionToken).isEqualTo("tok123")
    }

    @Test
    fun `empty transaction details sets Failed state`() = runTest {
        coEvery { vault.listIdentities() } returns listOf(testIdentity)
        // Override the transactionDetails to empty via a fresh ViewModel with failed fetch
        coEvery { client.fetchTransactionDetails(any(), any()) } returns Result.success(emptyMap())

        val vm = TxSigningViewModel(
            client = client,
            vault = vault,
            biometricAuth = biometricAuth,
            savedStateHandle = SavedStateHandle(
                mapOf("serverUrl" to "https://example.com", "sessionToken" to "tok123")
            )
        )
        vm.signTransaction()

        val state = vm.state.value
        assertThat(state).isInstanceOf(TxState.Failed::class.java)
        assertThat((state as TxState.Failed).message).isEqualTo("No transaction details available")
    }
}

class TxMainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }
    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
