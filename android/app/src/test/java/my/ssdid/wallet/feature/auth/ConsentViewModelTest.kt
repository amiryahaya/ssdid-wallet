package my.ssdid.wallet.feature.auth

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import my.ssdid.wallet.domain.model.Algorithm
import my.ssdid.wallet.domain.model.Identity
import my.ssdid.wallet.domain.model.CredentialSubject
import my.ssdid.wallet.domain.model.Proof
import my.ssdid.wallet.domain.model.VerifiableCredential
import my.ssdid.wallet.domain.transport.ServerApi
import my.ssdid.wallet.domain.transport.SsdidHttpClient
import my.ssdid.wallet.domain.transport.dto.AuthChallengeResponse
import my.ssdid.wallet.domain.transport.dto.AuthVerifyResponse
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

    private val testCredential = VerifiableCredential(
        id = "urn:uuid:test",
        type = listOf("VerifiableCredential"),
        issuer = "did:ssdid:issuer",
        issuanceDate = "2026-03-10T00:00:00Z",
        credentialSubject = CredentialSubject(
            id = "did:ssdid:user1",
            claims = mapOf("name" to "Amir Rudin", "email" to "amir@example.com", "phone" to "+60123456789")
        ),
        proof = Proof(
            type = "Ed25519Signature2020",
            created = "2026-03-10T00:00:00Z",
            verificationMethod = "did:ssdid:issuer#key-1",
            proofPurpose = "assertionMethod",
            proofValue = "uABC123"
        )
    )

    private val challengeResponse = AuthChallengeResponse(
        challenge = "test-challenge",
        serverName = "TestApp",
        serverDid = "did:ssdid:server1",
        serverKeyId = "did:ssdid:server1#key-1"
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

    private fun stubInitChallenge() {
        coEvery { serverApi.getAuthChallenge() } returns challengeResponse
    }

    private fun stubInitChallengeFails() {
        coEvery { serverApi.getAuthChallenge() } throws RuntimeException("network error")
    }

    private fun stubApproveFlow() {
        coEvery { vault.sign(any(), any()) } returns Result.success(ByteArray(64))
        coEvery { vault.getCredentialForDid(any()) } returns testCredential
        coEvery { serverApi.verifyAuth(any()) } returns AuthVerifyResponse(
            sessionToken = "tok-123",
            serverDid = "did:ssdid:server1",
            serverKeyId = "did:ssdid:server1#key-1",
            serverSignature = "uSig"
        )
        coEvery { verifier.verifyChallengeResponse(any(), any(), any(), any()) } returns Result.success(true)
    }

    // --- Init / parsing tests ---

    @Test
    fun `parses requested claims from saved state`() = runTest {
        coEvery { vault.listIdentities() } returns listOf(testIdentity)
        stubInitChallenge()
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
        stubInitChallenge()
        val vm = createViewModel(acceptedAlgorithms = """["KAZ_SIGN_192"]""")
        advanceUntilIdle()
        assertThat(vm.identities.value).hasSize(1)
        assertThat(vm.identities.value[0].algorithm).isEqualTo(Algorithm.KAZ_SIGN_192)
    }

    @Test
    fun `no algorithm filter shows all identities`() = runTest {
        coEvery { vault.listIdentities() } returns listOf(testIdentity, pqcIdentity)
        stubInitChallenge()
        val vm = createViewModel(acceptedAlgorithms = "")
        advanceUntilIdle()
        assertThat(vm.identities.value).hasSize(2)
    }

    @Test
    fun `selects first identity by default`() = runTest {
        coEvery { vault.listIdentities() } returns listOf(testIdentity, pqcIdentity)
        stubInitChallenge()
        val vm = createViewModel()
        advanceUntilIdle()
        assertThat(vm.selectedIdentity.value).isEqualTo(testIdentity)
    }

    @Test
    fun `selectIdentity changes selection`() = runTest {
        coEvery { vault.listIdentities() } returns listOf(testIdentity, pqcIdentity)
        stubInitChallenge()
        val vm = createViewModel()
        advanceUntilIdle()
        vm.selectIdentity(pqcIdentity)
        assertThat(vm.selectedIdentity.value).isEqualTo(pqcIdentity)
    }

    @Test
    fun `all claims start selected`() = runTest {
        coEvery { vault.listIdentities() } returns listOf(testIdentity)
        stubInitChallenge()
        val vm = createViewModel()
        advanceUntilIdle()
        assertThat(vm.selectedClaims.value).contains("name")
        assertThat(vm.selectedClaims.value).contains("phone")
    }

    @Test
    fun `toggleClaim toggles optional claim`() = runTest {
        coEvery { vault.listIdentities() } returns listOf(testIdentity)
        stubInitChallenge()
        val vm = createViewModel()
        advanceUntilIdle()
        vm.toggleClaim("phone")
        assertThat(vm.selectedClaims.value).doesNotContain("phone")
        vm.toggleClaim("phone")
        assertThat(vm.selectedClaims.value).contains("phone")
    }

    @Test
    fun `toggleClaim does not toggle required claim`() = runTest {
        coEvery { vault.listIdentities() } returns listOf(testIdentity)
        stubInitChallenge()
        val vm = createViewModel()
        advanceUntilIdle()
        vm.toggleClaim("name")
        assertThat(vm.selectedClaims.value).contains("name")
    }

    @Test
    fun `state transitions to Ready after init`() = runTest {
        coEvery { vault.listIdentities() } returns listOf(testIdentity)
        stubInitChallenge()
        val vm = createViewModel()
        advanceUntilIdle()
        assertThat(vm.state.value).isEqualTo(ConsentState.Ready)
    }

    @Test
    fun `state transitions to Ready even when challenge fetch fails`() = runTest {
        coEvery { vault.listIdentities() } returns listOf(testIdentity)
        stubInitChallengeFails()
        val vm = createViewModel()
        advanceUntilIdle()
        assertThat(vm.state.value).isEqualTo(ConsentState.Ready)
        assertThat(vm.serverName.value).isEmpty()
    }

    @Test
    fun `hasCallback returns true when callbackUrl set`() = runTest {
        coEvery { vault.listIdentities() } returns listOf(testIdentity)
        stubInitChallenge()
        val vm = createViewModel(callbackUrl = "myapp://cb")
        advanceUntilIdle()
        assertThat(vm.hasCallback).isTrue()
    }

    @Test
    fun `isWebFlow returns true when sessionId set`() = runTest {
        coEvery { vault.listIdentities() } returns listOf(testIdentity)
        stubInitChallenge()
        val vm = createViewModel(sessionId = "abc-123")
        advanceUntilIdle()
        assertThat(vm.isWebFlow).isTrue()
    }

    @Test
    fun `buildCallbackUri includes session_token and did`() = runTest {
        coEvery { vault.listIdentities() } returns listOf(testIdentity)
        stubInitChallenge()
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
        stubInitChallenge()
        val vm = createViewModel(callbackUrl = "myapp://cb")
        advanceUntilIdle()
        val uri = vm.buildDeclineCallbackUri()
        assertThat(uri).isNotNull()
        assertThat(uri.toString()).contains("error=user_declined")
    }

    @Test
    fun `init fetches challenge and sets server name`() = runTest {
        coEvery { vault.listIdentities() } returns listOf(testIdentity)
        stubInitChallenge()
        val vm = createViewModel()
        advanceUntilIdle()
        assertThat(vm.serverName.value).isEqualTo("TestApp")
    }

    @Test
    fun `requestedClaims capped at MAX_REQUESTED_CLAIMS`() = runTest {
        val claims = (1..25).map { """{"key":"claim$it","required":false}""" }.joinToString(",")
        coEvery { vault.listIdentities() } returns listOf(testIdentity)
        stubInitChallenge()
        val vm = createViewModel(requestedClaims = "[$claims]")
        advanceUntilIdle()
        assertThat(vm.requestedClaims).hasSize(20)
    }

    // --- hasAllRequiredClaims tests ---

    @Test
    fun `hasAllRequiredClaims is true when all required claims present`() = runTest {
        coEvery { vault.listIdentities() } returns listOf(testIdentity)
        coEvery { vault.getCredentialForDid("did:ssdid:user1") } returns testCredential
        stubInitChallenge()
        val vm = createViewModel()
        advanceUntilIdle()
        assertThat(vm.hasAllRequiredClaims.value).isTrue()
    }

    @Test
    fun `hasAllRequiredClaims is false when required claim missing`() = runTest {
        val credentialMissingName = testCredential.copy(
            credentialSubject = CredentialSubject(
                id = "did:ssdid:user1",
                claims = mapOf("phone" to "+60123456789")
            )
        )
        coEvery { vault.listIdentities() } returns listOf(testIdentity)
        coEvery { vault.getCredentialForDid("did:ssdid:user1") } returns credentialMissingName
        stubInitChallenge()
        val vm = createViewModel()
        advanceUntilIdle()
        assertThat(vm.hasAllRequiredClaims.value).isFalse()
    }

    @Test
    fun `hasAllRequiredClaims is false when no identity selected`() = runTest {
        coEvery { vault.listIdentities() } returns emptyList()
        stubInitChallenge()
        val vm = createViewModel()
        advanceUntilIdle()
        assertThat(vm.hasAllRequiredClaims.value).isFalse()
    }

    @Test
    fun `hasAllRequiredClaims is false when credential is null`() = runTest {
        coEvery { vault.listIdentities() } returns listOf(testIdentity)
        coEvery { vault.getCredentialForDid(any()) } returns null
        stubInitChallenge()
        val vm = createViewModel()
        advanceUntilIdle()
        assertThat(vm.hasAllRequiredClaims.value).isFalse()
    }

    @Test
    fun `hasAllRequiredClaims is true when no required claims in request`() = runTest {
        coEvery { vault.listIdentities() } returns listOf(testIdentity)
        stubInitChallenge()
        val vm = createViewModel(requestedClaims = """[{"key":"phone","required":false}]""")
        advanceUntilIdle()
        assertThat(vm.hasAllRequiredClaims.value).isTrue()
    }

    // --- approve() tests ---

    @Test
    fun `approve transitions to Success on happy path`() = runTest {
        coEvery { vault.listIdentities() } returns listOf(testIdentity)
        stubInitChallenge()
        stubApproveFlow()
        val vm = createViewModel()
        advanceUntilIdle()
        vm.approve(biometricUsed = true)
        advanceUntilIdle()
        assertThat(vm.state.value).isInstanceOf(ConsentState.Success::class.java)
        assertThat((vm.state.value as ConsentState.Success).sessionToken).isEqualTo("tok-123")
    }

    @Test
    fun `approve transitions to Error when server verification fails`() = runTest {
        coEvery { vault.listIdentities() } returns listOf(testIdentity)
        stubInitChallenge()
        stubApproveFlow()
        coEvery { verifier.verifyChallengeResponse(any(), any(), any(), any()) } returns Result.success(false)
        val vm = createViewModel()
        advanceUntilIdle()
        vm.approve(biometricUsed = true)
        advanceUntilIdle()
        assertThat(vm.state.value).isInstanceOf(ConsentState.Error::class.java)
        assertThat((vm.state.value as ConsentState.Error).message).contains("Service authentication failed")
    }

    @Test
    fun `approve transitions to Error when vault sign fails`() = runTest {
        coEvery { vault.listIdentities() } returns listOf(testIdentity)
        stubInitChallenge()
        stubApproveFlow()
        coEvery { vault.sign(any(), any()) } returns Result.failure(RuntimeException("Key invalidated"))
        val vm = createViewModel()
        advanceUntilIdle()
        vm.approve(biometricUsed = true)
        advanceUntilIdle()
        assertThat(vm.state.value).isInstanceOf(ConsentState.Error::class.java)
        assertThat((vm.state.value as ConsentState.Error).message).contains("Key invalidated")
    }

    @Test
    fun `approve fails when required claim is missing`() = runTest {
        coEvery { vault.listIdentities() } returns listOf(testIdentity)
        stubInitChallenge()
        stubApproveFlow()
        coEvery { vault.getCredentialForDid(any()) } returns VerifiableCredential(
            id = "urn:uuid:test",
            type = listOf("VerifiableCredential"),
            issuer = "did:ssdid:issuer",
            issuanceDate = "2026-03-10T00:00:00Z",
            credentialSubject = CredentialSubject(id = "did:ssdid:user1", claims = mapOf("phone" to "+60123456789")),
            proof = Proof(
                type = "Ed25519Signature2020", created = "2026-03-10T00:00:00Z",
                verificationMethod = "did:ssdid:issuer#key-1",
                proofPurpose = "assertionMethod", proofValue = "uABC123"
            )
        )
        val vm = createViewModel()
        advanceUntilIdle()
        vm.approve(biometricUsed = true)
        advanceUntilIdle()
        assertThat(vm.state.value).isInstanceOf(ConsentState.Error::class.java)
        assertThat((vm.state.value as ConsentState.Error).message).contains("Missing required claims: name")
    }

    @Test
    fun `approve re-fetches challenge when init fetch failed`() = runTest {
        coEvery { vault.listIdentities() } returns listOf(testIdentity)
        // Init challenge fails
        stubInitChallengeFails()
        stubApproveFlow()
        val vm = createViewModel()
        advanceUntilIdle()

        // Now stub challenge to succeed for the approve re-fetch
        coEvery { serverApi.getAuthChallenge() } returns challengeResponse
        vm.approve(biometricUsed = true)
        advanceUntilIdle()
        assertThat(vm.state.value).isInstanceOf(ConsentState.Success::class.java)
        assertThat(vm.serverName.value).isEqualTo("TestApp")
    }

    @Test
    fun `approve includes session_id for web flow`() = runTest {
        coEvery { vault.listIdentities() } returns listOf(testIdentity)
        stubInitChallenge()
        stubApproveFlow()
        val vm = createViewModel(sessionId = "web-sess")
        advanceUntilIdle()
        vm.approve(biometricUsed = false)
        advanceUntilIdle()
        coVerify {
            serverApi.verifyAuth(match { it.sessionId == "web-sess" })
        }
    }

    @Test
    fun `approve sends amr with bio when biometric used`() = runTest {
        coEvery { vault.listIdentities() } returns listOf(testIdentity)
        stubInitChallenge()
        stubApproveFlow()
        val vm = createViewModel()
        advanceUntilIdle()
        vm.approve(biometricUsed = true)
        advanceUntilIdle()
        coVerify {
            serverApi.verifyAuth(match { it.amr == listOf("hwk", "bio") })
        }
    }

    @Test
    fun `approve sends amr without bio when biometric not used`() = runTest {
        coEvery { vault.listIdentities() } returns listOf(testIdentity)
        stubInitChallenge()
        stubApproveFlow()
        val vm = createViewModel()
        advanceUntilIdle()
        vm.approve(biometricUsed = false)
        advanceUntilIdle()
        coVerify {
            serverApi.verifyAuth(match { it.amr == listOf("hwk") })
        }
    }

    @Test
    fun `approve sends shared claims for selected claims only`() = runTest {
        coEvery { vault.listIdentities() } returns listOf(testIdentity)
        stubInitChallenge()
        stubApproveFlow()
        val vm = createViewModel()
        advanceUntilIdle()
        // Deselect optional phone claim
        vm.toggleClaim("phone")
        vm.approve(biometricUsed = true)
        advanceUntilIdle()
        coVerify {
            serverApi.verifyAuth(match {
                it.sharedClaims.containsKey("name") && !it.sharedClaims.containsKey("phone")
            })
        }
    }

    @Test
    fun `approve shows error when no identity selected`() = runTest {
        coEvery { vault.listIdentities() } returns emptyList()
        stubInitChallenge()
        val vm = createViewModel()
        advanceUntilIdle()
        vm.approve(biometricUsed = true)
        advanceUntilIdle()
        assertThat(vm.state.value).isInstanceOf(ConsentState.Error::class.java)
        assertThat((vm.state.value as ConsentState.Error).message).contains("No identity selected")
    }
}
