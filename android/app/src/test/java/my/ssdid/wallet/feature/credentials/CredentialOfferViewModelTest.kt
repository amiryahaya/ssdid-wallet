package my.ssdid.wallet.feature.credentials

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import my.ssdid.sdk.domain.model.Algorithm
import my.ssdid.sdk.domain.model.Identity
import my.ssdid.sdk.domain.oid4vci.CredentialOffer
import my.ssdid.sdk.domain.oid4vci.CredentialOfferReview
import my.ssdid.sdk.domain.oid4vci.IssuanceResult
import my.ssdid.sdk.domain.oid4vci.IssuerMetadata
import my.ssdid.sdk.domain.oid4vci.OpenId4VciHandler
import my.ssdid.sdk.domain.sdjwt.StoredSdJwtVc
import my.ssdid.sdk.domain.vault.Vault
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CredentialOfferViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var handler: OpenId4VciHandler
    private lateinit var vault: Vault
    private lateinit var savedStateHandle: SavedStateHandle

    private val testIdentity = Identity(
        name = "Test",
        did = "did:ssdid:test",
        keyId = "key-1",
        algorithm = Algorithm.ED25519,
        publicKeyMultibase = "z6Mk...",
        createdAt = "2024-01-01T00:00:00Z"
    )

    private val testMetadata = IssuerMetadata(
        credentialIssuer = "https://issuer.example.com",
        credentialEndpoint = "https://issuer.example.com/credential",
        credentialConfigurationsSupported = emptyMap(),
        tokenEndpoint = "https://issuer.example.com/token",
        authorizationEndpoint = null
    )

    private val testOffer = CredentialOffer(
        credentialIssuer = "https://issuer.example.com",
        credentialConfigurationIds = listOf("UniversityDegree"),
        preAuthorizedCode = "pre-auth-123"
    )

    private val testReview = CredentialOfferReview(
        offer = testOffer,
        metadata = testMetadata,
        credentialConfigNames = listOf("UniversityDegree")
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        handler = mockk()
        vault = mockk()
        savedStateHandle = SavedStateHandle(mapOf<String, Any>())
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): CredentialOfferViewModel {
        return CredentialOfferViewModel(handler, vault, savedStateHandle)
    }

    @Test
    fun initialStateIsLoading() {
        val viewModel = createViewModel()
        assertThat(viewModel.state.value).isInstanceOf(CredentialOfferUiState.Loading::class.java)
    }

    @Test
    fun processOfferTransitionsToReviewingOffer() = runTest(testDispatcher) {
        coEvery { vault.listIdentities() } returns listOf(testIdentity)
        every { handler.processOffer(any()) } returns Result.success(testReview)

        val viewModel = createViewModel()
        viewModel.processOffer("openid-credential-offer://?credential_offer={}")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state).isInstanceOf(CredentialOfferUiState.ReviewingOffer::class.java)
        val reviewing = state as CredentialOfferUiState.ReviewingOffer
        assertThat(reviewing.issuerName).isEqualTo("https://issuer.example.com")
        assertThat(reviewing.credentialTypes).containsExactly("UniversityDegree")
        assertThat(reviewing.selectedIdentity).isEqualTo(testIdentity)
    }

    @Test
    fun processOfferFailureTransitionsToError() = runTest(testDispatcher) {
        every { handler.processOffer(any()) } returns Result.failure(RuntimeException("Parse error"))

        val viewModel = createViewModel()
        viewModel.processOffer("bad-uri")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state).isInstanceOf(CredentialOfferUiState.Error::class.java)
        assertThat((state as CredentialOfferUiState.Error).message).isEqualTo("Parse error")
    }

    @Test
    fun acceptOfferWithPreAuthCodeTransitionsToSuccess() = runTest(testDispatcher) {
        coEvery { vault.listIdentities() } returns listOf(testIdentity)
        every { handler.processOffer(any()) } returns Result.success(testReview)

        val storedVc = StoredSdJwtVc(
            id = "vc-1",
            compact = "eyJ.eyJ.sig",
            issuer = "https://issuer.example.com",
            subject = "did:ssdid:test",
            type = "UniversityDegree",
            claims = emptyMap(),
            disclosableClaims = emptyList(),
            issuedAt = 1700000000L
        )
        coEvery {
            handler.acceptOffer(
                offer = any(),
                metadata = any(),
                selectedConfigId = any(),
                txCode = any(),
                walletDid = any(),
                keyId = any(),
                algorithm = any(),
                signer = any()
            )
        } returns Result.success(IssuanceResult.Success(storedVc))

        val viewModel = createViewModel()
        viewModel.processOffer("openid-credential-offer://?credential_offer={}")
        advanceUntilIdle()

        viewModel.acceptOffer()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state).isInstanceOf(CredentialOfferUiState.Success::class.java)
    }

    @Test
    fun acceptOfferDeferredTransitionsToDeferredState() = runTest(testDispatcher) {
        coEvery { vault.listIdentities() } returns listOf(testIdentity)
        every { handler.processOffer(any()) } returns Result.success(testReview)
        coEvery {
            handler.acceptOffer(
                offer = any(),
                metadata = any(),
                selectedConfigId = any(),
                txCode = any(),
                walletDid = any(),
                keyId = any(),
                algorithm = any(),
                signer = any()
            )
        } returns Result.success(
            IssuanceResult.Deferred(
                transactionId = "tx-123",
                deferredEndpoint = "https://issuer.example.com/deferred",
                accessToken = "token"
            )
        )

        val viewModel = createViewModel()
        viewModel.processOffer("openid-credential-offer://?credential_offer={}")
        advanceUntilIdle()

        viewModel.acceptOffer()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state).isInstanceOf(CredentialOfferUiState.Deferred::class.java)
        assertThat((state as CredentialOfferUiState.Deferred).transactionId).isEqualTo("tx-123")
    }

    @Test
    fun acceptOfferWithTxCodeTransitionsToPinEntry() = runTest(testDispatcher) {
        val offerWithTxCode = testOffer.copy(
            txCode = my.ssdid.sdk.domain.oid4vci.TxCodeRequirement(
                inputMode = "numeric",
                length = 6,
                description = "Enter the PIN from your email"
            )
        )
        val reviewWithTxCode = CredentialOfferReview(
            offer = offerWithTxCode,
            metadata = testMetadata,
            credentialConfigNames = listOf("UniversityDegree")
        )

        coEvery { vault.listIdentities() } returns listOf(testIdentity)
        every { handler.processOffer(any()) } returns Result.success(reviewWithTxCode)

        val viewModel = createViewModel()
        viewModel.processOffer("openid-credential-offer://?credential_offer={}")
        advanceUntilIdle()

        viewModel.acceptOffer()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state).isInstanceOf(CredentialOfferUiState.PinEntry::class.java)
        val pinEntry = state as CredentialOfferUiState.PinEntry
        assertThat(pinEntry.length).isEqualTo(6)
        assertThat(pinEntry.inputMode).isEqualTo("numeric")
        assertThat(pinEntry.description).isEqualTo("Enter the PIN from your email")
    }

    @Test
    fun declineSetsErrorState() {
        val viewModel = createViewModel()
        viewModel.decline()

        val state = viewModel.state.value
        assertThat(state).isInstanceOf(CredentialOfferUiState.Error::class.java)
        assertThat((state as CredentialOfferUiState.Error).message).isEqualTo("Declined by user")
    }

    @Test
    fun selectIdentityUpdatesReviewingState() = runTest(testDispatcher) {
        val secondIdentity = testIdentity.copy(keyId = "key-2", name = "Second")
        coEvery { vault.listIdentities() } returns listOf(testIdentity, secondIdentity)
        every { handler.processOffer(any()) } returns Result.success(testReview)

        val viewModel = createViewModel()
        viewModel.processOffer("openid-credential-offer://?credential_offer={}")
        advanceUntilIdle()

        viewModel.selectIdentity(secondIdentity)

        val state = viewModel.state.value as CredentialOfferUiState.ReviewingOffer
        assertThat(state.selectedIdentity).isEqualTo(secondIdentity)
    }
}
