package my.ssdid.wallet.feature.presentation

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import my.ssdid.wallet.domain.model.Algorithm
import my.ssdid.wallet.domain.model.Identity
import my.ssdid.wallet.domain.oid4vp.AuthorizationRequest
import my.ssdid.wallet.domain.oid4vp.CredentialRef
import my.ssdid.wallet.domain.oid4vp.MatchResult
import my.ssdid.wallet.domain.oid4vp.OpenId4VpHandler
import my.ssdid.wallet.domain.oid4vp.PresentationReviewResult
import my.ssdid.wallet.domain.sdjwt.StoredSdJwtVc
import my.ssdid.wallet.domain.vault.Vault
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PresentationRequestViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var handler: OpenId4VpHandler
    private lateinit var vault: Vault
    private lateinit var viewModel: PresentationRequestViewModel

    private val testCred = StoredSdJwtVc(
        id = "vc-1",
        compact = "eyJ.eyJ.sig~d1~",
        issuer = "did:ssdid:i",
        subject = "did:ssdid:h",
        type = "IdCred",
        claims = mapOf("name" to "Ahmad"),
        disclosableClaims = listOf("name"),
        issuedAt = 1700000000L
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        handler = mockk()
        vault = mockk()
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialStateIsLoading() {
        viewModel = PresentationRequestViewModel(handler, vault)
        assertThat(viewModel.state.value).isInstanceOf(
            PresentationRequestViewModel.UiState.Loading::class.java
        )
    }

    @Test
    fun setReviewResultTransitionsToCredentialMatch() {
        viewModel = PresentationRequestViewModel(handler, vault)
        val match = MatchResult(CredentialRef.SdJwt(testCred), "id-1", listOf("name"), listOf("email"))
        val authReq = AuthorizationRequest(clientId = "https://v.example.com", nonce = "n")

        viewModel.setReviewResult(PresentationReviewResult(authReq, listOf(match)))

        val state = viewModel.state.value
        assertThat(state).isInstanceOf(PresentationRequestViewModel.UiState.CredentialMatch::class.java)
        val credMatch = state as PresentationRequestViewModel.UiState.CredentialMatch
        assertThat(credMatch.verifierName).isEqualTo("https://v.example.com")
        assertThat(credMatch.claims).hasSize(2)
    }

    @Test
    fun requiredClaimsAreMarkedRequired() {
        viewModel = PresentationRequestViewModel(handler, vault)
        val match = MatchResult(CredentialRef.SdJwt(testCred), "id-1", listOf("name"), listOf("email"))
        val authReq = AuthorizationRequest(clientId = "https://v.example.com", nonce = "n")

        viewModel.setReviewResult(PresentationReviewResult(authReq, listOf(match)))

        val state = viewModel.state.value as PresentationRequestViewModel.UiState.CredentialMatch
        val nameClaim = state.claims.find { it.name == "name" }!!
        assertThat(nameClaim.required).isTrue()
        assertThat(nameClaim.selected).isTrue()
    }

    @Test
    fun optionalClaimsDefaultToUnselected() {
        viewModel = PresentationRequestViewModel(handler, vault)
        val match = MatchResult(CredentialRef.SdJwt(testCred), "id-1", listOf("name"), listOf("email"))
        val authReq = AuthorizationRequest(clientId = "https://v.example.com", nonce = "n")

        viewModel.setReviewResult(PresentationReviewResult(authReq, listOf(match)))

        val state = viewModel.state.value as PresentationRequestViewModel.UiState.CredentialMatch
        val emailClaim = state.claims.find { it.name == "email" }!!
        assertThat(emailClaim.required).isFalse()
        assertThat(emailClaim.selected).isFalse()
    }

    @Test
    fun toggleClaimFlipsOptionalSelection() {
        viewModel = PresentationRequestViewModel(handler, vault)
        val match = MatchResult(CredentialRef.SdJwt(testCred), "id-1", listOf("name"), listOf("email"))
        val authReq = AuthorizationRequest(clientId = "https://v.example.com", nonce = "n")

        viewModel.setReviewResult(PresentationReviewResult(authReq, listOf(match)))
        viewModel.toggleClaim("email")

        val state = viewModel.state.value as PresentationRequestViewModel.UiState.CredentialMatch
        val emailClaim = state.claims.find { it.name == "email" }!!
        assertThat(emailClaim.selected).isTrue()
    }

    @Test
    fun toggleClaimDoesNotFlipRequired() {
        viewModel = PresentationRequestViewModel(handler, vault)
        val match = MatchResult(CredentialRef.SdJwt(testCred), "id-1", listOf("name"), listOf("email"))
        val authReq = AuthorizationRequest(clientId = "https://v.example.com", nonce = "n")

        viewModel.setReviewResult(PresentationReviewResult(authReq, listOf(match)))
        viewModel.toggleClaim("name")

        val state = viewModel.state.value as PresentationRequestViewModel.UiState.CredentialMatch
        val nameClaim = state.claims.find { it.name == "name" }!!
        assertThat(nameClaim.selected).isTrue()
    }

    @Test
    fun declineSetsErrorState() {
        viewModel = PresentationRequestViewModel(handler, vault)
        viewModel.decline()

        val state = viewModel.state.value
        assertThat(state).isInstanceOf(PresentationRequestViewModel.UiState.Error::class.java)
        assertThat((state as PresentationRequestViewModel.UiState.Error).message)
            .isEqualTo("Declined by user")
    }

    @Test
    fun approveWithNoIdentitySetsError() = runTest(testDispatcher) {
        viewModel = PresentationRequestViewModel(handler, vault)
        val match = MatchResult(CredentialRef.SdJwt(testCred), "id-1", listOf("name"), emptyList())
        val authReq = AuthorizationRequest(clientId = "https://v.example.com", nonce = "n")

        viewModel.setReviewResult(PresentationReviewResult(authReq, listOf(match)))

        coEvery { vault.listIdentities() } returns emptyList()

        viewModel.approve()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state).isInstanceOf(PresentationRequestViewModel.UiState.Error::class.java)
        assertThat((state as PresentationRequestViewModel.UiState.Error).message)
            .isEqualTo("No identity available")
    }

    @Test
    fun approveSuccessTransitionsToSuccess() = runTest(testDispatcher) {
        viewModel = PresentationRequestViewModel(handler, vault)
        val match = MatchResult(CredentialRef.SdJwt(testCred), "id-1", listOf("name"), emptyList())
        val authReq = AuthorizationRequest(
            clientId = "https://v.example.com",
            nonce = "n",
            responseUri = "https://v.example.com/response",
            responseMode = "direct_post"
        )

        viewModel.setReviewResult(PresentationReviewResult(authReq, listOf(match)))

        val testIdentity = Identity(
            name = "Test",
            did = "did:ssdid:test",
            keyId = "key-1",
            algorithm = Algorithm.ED25519,
            publicKeyMultibase = "z6Mk...",
            createdAt = "2024-01-01T00:00:00Z"
        )
        coEvery { vault.listIdentities() } returns listOf(testIdentity)
        coEvery { vault.sign(any(), any()) } returns Result.success(ByteArray(64))
        coEvery {
            handler.submitPresentation(
                authRequest = any(),
                matchResult = any(),
                selectedClaims = any(),
                algorithm = any(),
                signer = any()
            )
        } returns Result.success(Unit)

        viewModel.approve()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state).isInstanceOf(PresentationRequestViewModel.UiState.Success::class.java)
    }
}
