package my.ssdid.wallet.feature.auth

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
import my.ssdid.sdk.domain.model.CredentialSubject
import my.ssdid.sdk.domain.model.Proof
import my.ssdid.sdk.domain.model.VerifiableCredential
import my.ssdid.wallet.domain.transport.dto.AuthenticateResponse
import my.ssdid.wallet.domain.vault.Vault
import my.ssdid.wallet.platform.biometric.BiometricAuthenticator
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AuthFlowViewModelTest {

    @get:Rule
    val mainDispatcherRule = AuthMainDispatcherRule()

    private lateinit var viewModel: AuthFlowViewModel
    private lateinit var client: SsdidClient
    private lateinit var vault: Vault
    private lateinit var biometricAuth: BiometricAuthenticator

    private val testCredential = VerifiableCredential(
        id = "urn:uuid:test-cred-1",
        type = listOf("VerifiableCredential", "TestCredential"),
        issuer = "did:ssdid:issuer",
        issuanceDate = "2024-01-01T00:00:00Z",
        credentialSubject = CredentialSubject(id = "did:ssdid:abc123"),
        proof = Proof(
            type = "Ed25519Signature2020",
            created = "2024-01-01T00:00:00Z",
            verificationMethod = "did:ssdid:issuer#key-1",
            proofPurpose = "assertionMethod",
            proofValue = "z3abc123"
        )
    )

    private fun createViewModel(
        serverUrl: String = "https://example.com",
        callbackUrl: String = "",
        state: String = ""
    ): AuthFlowViewModel {
        val map = mutableMapOf<String, Any>("serverUrl" to serverUrl)
        if (callbackUrl.isNotEmpty()) map["callbackUrl"] = callbackUrl
        if (state.isNotEmpty()) map["state"] = state
        return AuthFlowViewModel(
            client = client,
            vault = vault,
            biometricAuth = biometricAuth,
            savedStateHandle = SavedStateHandle(map)
        )
    }

    @Before
    fun setup() {
        client = mockk(relaxed = true)
        vault = mockk(relaxed = true)
        biometricAuth = mockk(relaxed = true)
        coEvery { vault.listCredentials() } returns listOf(testCredential)

        viewModel = AuthFlowViewModel(
            client = client,
            vault = vault,
            biometricAuth = biometricAuth,
            savedStateHandle = SavedStateHandle(mapOf("serverUrl" to "https://example.com"))
        )
    }

    @Test
    fun `initial state is Idle`() {
        assertThat(viewModel.state.value).isEqualTo(AuthState.Idle)
    }

    @Test
    fun `authenticate with valid credential transitions to Success`() = runTest {
        val authResponse = AuthenticateResponse(
            session_token = "tok_test",
            server_did = "did:ssdid:server",
            server_key_id = "did:ssdid:server#key-1"
        )
        coEvery { client.authenticate(testCredential, "https://example.com") } returns Result.success(authResponse)

        viewModel.selectCredential(testCredential)
        viewModel.authenticate()

        assertThat(viewModel.state.value).isInstanceOf(AuthState.Success::class.java)
        val success = viewModel.state.value as AuthState.Success
        assertThat(success.sessionToken).isEqualTo("tok_test")
    }

    @Test
    fun `authenticate with failure transitions to Error`() = runTest {
        coEvery { client.authenticate(testCredential, "https://example.com") } returns Result.failure(
            RuntimeException("Auth failed")
        )

        viewModel.selectCredential(testCredential)
        viewModel.authenticate()

        val state = viewModel.state.value
        assertThat(state).isInstanceOf(AuthState.Error::class.java)
        assertThat((state as AuthState.Error).message).isEqualTo("Auth failed")
    }

    @Test
    fun `selectCredential updates selectedCredential state`() {
        assertThat(viewModel.selectedCredential.value).isNull()

        viewModel.selectCredential(testCredential)

        assertThat(viewModel.selectedCredential.value).isEqualTo(testCredential)
    }

    @Test
    fun `authenticate without selected credential does nothing`() = runTest {
        viewModel.authenticate()

        assertThat(viewModel.state.value).isEqualTo(AuthState.Idle)
    }

    @Test
    fun `serverUrl is extracted from SavedStateHandle`() {
        assertThat(viewModel.serverUrl).isEqualTo("https://example.com")
    }

    @Test
    fun `credentials are loaded from vault on init`() {
        assertThat(viewModel.credentials.value).containsExactly(testCredential)
    }

    @Test
    fun `callbackUrl is extracted from SavedStateHandle`() {
        val vm = AuthFlowViewModel(
            client = client,
            vault = vault,
            biometricAuth = biometricAuth,
            savedStateHandle = SavedStateHandle(mapOf(
                "serverUrl" to "https://example.com",
                "callbackUrl" to "ssdiddrive://auth/callback"
            ))
        )
        assertThat(vm.callbackUrl).isEqualTo("ssdiddrive://auth/callback")
    }

    @Test
    fun `callbackUrl defaults to empty when not provided`() {
        assertThat(viewModel.callbackUrl).isEmpty()
    }

    @Test
    fun `hasCallback is true when callbackUrl is non-empty`() {
        val vm = AuthFlowViewModel(
            client = client,
            vault = vault,
            biometricAuth = biometricAuth,
            savedStateHandle = SavedStateHandle(mapOf(
                "serverUrl" to "https://example.com",
                "callbackUrl" to "ssdiddrive://auth/callback"
            ))
        )
        assertThat(vm.hasCallback).isTrue()
    }

    @Test
    fun `hasCallback is false when callbackUrl is empty`() {
        assertThat(viewModel.hasCallback).isFalse()
    }

    @Test
    fun `buildCallbackUri appends session_token to callbackUrl`() {
        val vm = AuthFlowViewModel(
            client = client,
            vault = vault,
            biometricAuth = biometricAuth,
            savedStateHandle = SavedStateHandle(mapOf(
                "serverUrl" to "https://example.com",
                "callbackUrl" to "ssdiddrive://auth/callback"
            ))
        )
        val uri = vm.buildCallbackUri("mytoken123")
        assertThat(uri).isNotNull()
        assertThat(uri.toString()).isEqualTo("ssdiddrive://auth/callback?session_token=mytoken123")
    }

    @Test
    fun `buildCallbackUri returns null when no callbackUrl`() {
        val uri = viewModel.buildCallbackUri("mytoken123")
        assertThat(uri).isNull()
    }

    @Test
    fun `buildCallbackUri URL-encodes special characters in session token`() {
        val vm = createViewModel(callbackUrl = "ssdiddrive://auth/callback")
        val uri = vm.buildCallbackUri("tok+abc=def&ghi")

        assertThat(uri).isNotNull()
        // The query parameter value must be properly encoded
        assertThat(uri!!.getQueryParameter("session_token")).isEqualTo("tok+abc=def&ghi")
    }

    @Test
    fun `buildCallbackUri handles callbackUrl with existing query parameters`() {
        val vm = createViewModel(callbackUrl = "ssdiddrive://auth/callback?source=wallet")
        val uri = vm.buildCallbackUri("tok123")

        assertThat(uri).isNotNull()
        assertThat(uri!!.getQueryParameter("source")).isEqualTo("wallet")
        assertThat(uri.getQueryParameter("session_token")).isEqualTo("tok123")
    }

    @Test
    fun `authenticate success with callback produces valid callback URI`() = runTest {
        val response = AuthenticateResponse(
            session_token = "tok_abc",
            server_did = "did:ssdid:server",
            server_key_id = "did:ssdid:server#key-1"
        )
        val cred = mockk<VerifiableCredential>(relaxed = true)
        coEvery { vault.listCredentials() } returns listOf(cred)
        coEvery { client.authenticate(any(), any()) } returns Result.success(response)

        val vm = createViewModel(callbackUrl = "ssdiddrive://auth/callback")
        vm.selectCredential(cred)
        vm.authenticate()

        val success = vm.state.value as AuthState.Success
        val callbackUri = vm.buildCallbackUri(success.sessionToken)
        assertThat(callbackUri).isNotNull()
        assertThat(callbackUri!!.getQueryParameter("session_token")).isEqualTo("tok_abc")
    }

    // --- CSRF state parameter tests ---

    @Test
    fun `buildCallbackUri includes state parameter when provided`() {
        val vm = createViewModel(callbackUrl = "ssdiddrive://auth/callback", state = "csrf-state-123")
        val uri = vm.buildCallbackUri("tok-abc")
        assertThat(uri).isNotNull()
        assertThat(uri!!.getQueryParameter("session_token")).isEqualTo("tok-abc")
        assertThat(uri.getQueryParameter("state")).isEqualTo("csrf-state-123")
    }

    @Test
    fun `buildCallbackUri omits state parameter when empty`() {
        val vm = createViewModel(callbackUrl = "ssdiddrive://auth/callback", state = "")
        val uri = vm.buildCallbackUri("tok-abc")
        assertThat(uri).isNotNull()
        assertThat(uri!!.getQueryParameter("state")).isNull()
    }

    @Test
    fun `authenticate success captures session token in state`() = runTest {
        val response = AuthenticateResponse(
            session_token = "tok_abc",
            server_did = "did:ssdid:server",
            server_key_id = "did:ssdid:server#key-1"
        )
        coEvery { client.authenticate(testCredential, "https://example.com") } returns Result.success(response)

        val vm = AuthFlowViewModel(
            client = client,
            vault = vault,
            biometricAuth = biometricAuth,
            savedStateHandle = SavedStateHandle(mapOf(
                "serverUrl" to "https://example.com",
                "callbackUrl" to "ssdiddrive://auth/callback"
            ))
        )
        vm.selectCredential(testCredential)
        vm.authenticate()

        assertThat(vm.state.value).isInstanceOf(AuthState.Success::class.java)
        val success = vm.state.value as AuthState.Success
        assertThat(success.sessionToken).isEqualTo("tok_abc")
    }
}

class AuthMainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }
    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
