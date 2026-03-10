package my.ssdid.wallet.feature.auth

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import my.ssdid.wallet.domain.model.Algorithm
import my.ssdid.wallet.domain.model.Identity
import my.ssdid.wallet.domain.transport.ServerApi
import my.ssdid.wallet.domain.transport.SsdidHttpClient
import my.ssdid.wallet.domain.vault.Vault
import my.ssdid.wallet.domain.verifier.Verifier
import my.ssdid.wallet.platform.biometric.BiometricAuthenticator
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ConsentViewModelTest {

    private lateinit var vault: Vault
    private lateinit var httpClient: SsdidHttpClient
    private lateinit var serverApi: ServerApi
    private lateinit var verifier: Verifier
    private lateinit var biometricAuth: BiometricAuthenticator

    private val testIdentity = Identity(
        name = "Personal",
        did = "did:ssdid:user1",
        keyId = "did:ssdid:user1#key-1",
        algorithm = Algorithm.ED25519,
        publicKeyMultibase = "uPubKey",
        createdAt = "2026-03-10T00:00:00Z"
    )

    private val pqcIdentity = Identity(
        name = "PQC",
        did = "did:ssdid:user2",
        keyId = "did:ssdid:user2#key-1",
        algorithm = Algorithm.KAZ_SIGN_192,
        publicKeyMultibase = "uPubKey2",
        createdAt = "2026-03-10T00:00:00Z"
    )

    @Before
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        vault = mockk()
        httpClient = mockk()
        serverApi = mockk()
        verifier = mockk()
        biometricAuth = mockk()
        every { httpClient.serverApi(any()) } returns serverApi
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        serverUrl: String = "https://app.example.com",
        requestedClaims: String = """[{"key":"name","required":true},{"key":"phone","required":false}]""",
        acceptedAlgorithms: String = "",
        callbackUrl: String = "",
        sessionId: String = ""
    ): ConsentViewModel {
        val handle = SavedStateHandle(mapOf(
            "serverUrl" to serverUrl,
            "requestedClaims" to requestedClaims,
            "acceptedAlgorithms" to acceptedAlgorithms,
            "callbackUrl" to callbackUrl,
            "sessionId" to sessionId
        ))
        return ConsentViewModel(vault, httpClient, verifier, biometricAuth, handle)
    }

    @Test
    fun `parses requested claims from saved state`() = runTest {
        coEvery { vault.listIdentities() } returns listOf(testIdentity)
        val vm = createViewModel()
        advanceUntilIdle()
        assertThat(vm.requestedClaims).hasSize(2)
        assertThat(vm.requestedClaims[0].key).isEqualTo("name")
        assertThat(vm.requestedClaims[0].required).isTrue()
        assertThat(vm.requestedClaims[1].key).isEqualTo("phone")
        assertThat(vm.requestedClaims[1].required).isFalse()
    }

    @Test
    fun `filters identities by accepted algorithms`() = runTest {
        coEvery { vault.listIdentities() } returns listOf(testIdentity, pqcIdentity)
        val vm = createViewModel(acceptedAlgorithms = """["KAZ_SIGN_192"]""")
        advanceUntilIdle()
        assertThat(vm.identities.value).hasSize(1)
        assertThat(vm.identities.value[0].algorithm).isEqualTo(Algorithm.KAZ_SIGN_192)
    }

    @Test
    fun `no algorithm filter shows all identities`() = runTest {
        coEvery { vault.listIdentities() } returns listOf(testIdentity, pqcIdentity)
        val vm = createViewModel(acceptedAlgorithms = "")
        advanceUntilIdle()
        assertThat(vm.identities.value).hasSize(2)
    }

    @Test
    fun `selects first identity by default`() = runTest {
        coEvery { vault.listIdentities() } returns listOf(testIdentity, pqcIdentity)
        val vm = createViewModel()
        advanceUntilIdle()
        assertThat(vm.selectedIdentity.value).isEqualTo(testIdentity)
    }

    @Test
    fun `selectIdentity changes selection`() = runTest {
        coEvery { vault.listIdentities() } returns listOf(testIdentity, pqcIdentity)
        val vm = createViewModel()
        advanceUntilIdle()
        vm.selectIdentity(pqcIdentity)
        assertThat(vm.selectedIdentity.value).isEqualTo(pqcIdentity)
    }

    @Test
    fun `all claims start selected`() = runTest {
        coEvery { vault.listIdentities() } returns listOf(testIdentity)
        val vm = createViewModel()
        advanceUntilIdle()
        assertThat(vm.selectedClaims.value).containsEntry("name", true)
        assertThat(vm.selectedClaims.value).containsEntry("phone", true)
    }

    @Test
    fun `toggleClaim toggles optional claim`() = runTest {
        coEvery { vault.listIdentities() } returns listOf(testIdentity)
        val vm = createViewModel()
        advanceUntilIdle()
        vm.toggleClaim("phone")
        assertThat(vm.selectedClaims.value).doesNotContainKey("phone")
        vm.toggleClaim("phone") // toggle back
        assertThat(vm.selectedClaims.value).containsEntry("phone", true)
    }

    @Test
    fun `toggleClaim does not toggle required claim`() = runTest {
        coEvery { vault.listIdentities() } returns listOf(testIdentity)
        val vm = createViewModel()
        advanceUntilIdle()
        vm.toggleClaim("name") // required
        assertThat(vm.selectedClaims.value).containsEntry("name", true)
    }

    @Test
    fun `state transitions to Ready after init`() = runTest {
        coEvery { vault.listIdentities() } returns listOf(testIdentity)
        val vm = createViewModel()
        advanceUntilIdle()
        assertThat(vm.state.value).isEqualTo(ConsentState.Ready)
    }

    @Test
    fun `hasCallback returns true when callbackUrl set`() = runTest {
        coEvery { vault.listIdentities() } returns listOf(testIdentity)
        val vm = createViewModel(callbackUrl = "myapp://cb")
        assertThat(vm.hasCallback).isTrue()
    }

    @Test
    fun `isWebFlow returns true when sessionId set`() = runTest {
        coEvery { vault.listIdentities() } returns listOf(testIdentity)
        val vm = createViewModel(sessionId = "abc-123")
        assertThat(vm.isWebFlow).isTrue()
    }

    @Test
    fun `buildCallbackUri includes session_token and did`() = runTest {
        coEvery { vault.listIdentities() } returns listOf(testIdentity)
        val vm = createViewModel(callbackUrl = "myapp://cb")
        advanceUntilIdle()
        val uri = vm.buildCallbackUri("tok-123")
        assertThat(uri).isNotNull()
        assertThat(uri.toString()).contains("session_token=tok-123")
        assertThat(uri.toString()).contains("did=did")
    }

    @Test
    fun `buildDeclineCallbackUri includes error param`() = runTest {
        coEvery { vault.listIdentities() } returns listOf(testIdentity)
        val vm = createViewModel(callbackUrl = "myapp://cb")
        val uri = vm.buildDeclineCallbackUri()
        assertThat(uri).isNotNull()
        assertThat(uri.toString()).contains("error=user_declined")
    }
}
