package my.ssdid.wallet.feature.verification

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import my.ssdid.sdk.domain.model.CredentialSubject
import my.ssdid.sdk.domain.model.Proof
import my.ssdid.sdk.domain.model.VerifiableCredential
import my.ssdid.wallet.domain.verifier.offline.CheckStatus
import my.ssdid.wallet.domain.verifier.offline.CheckType
import my.ssdid.wallet.domain.verifier.offline.UnifiedVerificationResult
import my.ssdid.wallet.domain.verifier.offline.VerificationCheck
import my.ssdid.wallet.domain.verifier.offline.VerificationOrchestrator
import my.ssdid.wallet.domain.verifier.offline.VerificationSource
import my.ssdid.wallet.domain.verifier.offline.VerificationStatus
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VerificationResultViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var orchestrator: VerificationOrchestrator

    private val testCredential = VerifiableCredential(
        id = "https://example.com/credentials/1",
        type = listOf("VerifiableCredential"),
        issuer = "did:ssdid:issuer123",
        issuanceDate = "2024-01-01T00:00:00Z",
        credentialSubject = CredentialSubject(id = "did:ssdid:subject123"),
        proof = Proof(
            type = "Ed25519Signature2020",
            created = "2024-01-01T00:00:00Z",
            verificationMethod = "did:ssdid:issuer123#key-1",
            proofPurpose = "assertionMethod",
            proofValue = "zFakeProofValue"
        )
    )

    private val verifiedResult = UnifiedVerificationResult(
        status = VerificationStatus.VERIFIED,
        checks = listOf(
            VerificationCheck(
                type = CheckType.SIGNATURE,
                status = CheckStatus.PASS,
                message = "Signature verified online"
            )
        ),
        source = VerificationSource.ONLINE
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        orchestrator = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `verify sets result from orchestrator`() = runTest {
        coEvery { orchestrator.verify(testCredential) } returns verifiedResult

        val vm = VerificationResultViewModel(orchestrator, mockk(relaxed = true))
        vm.verify(testCredential)

        val result = vm.result.value
        assertThat(result).isNotNull()
        assertThat(result!!.status).isEqualTo(VerificationStatus.VERIFIED)
        assertThat(result.source).isEqualTo(VerificationSource.ONLINE)
        assertThat(result.checks).hasSize(1)
        assertThat(result.checks[0].status).isEqualTo(CheckStatus.PASS)
        assertThat(vm.isLoading.value).isFalse()
    }
}
