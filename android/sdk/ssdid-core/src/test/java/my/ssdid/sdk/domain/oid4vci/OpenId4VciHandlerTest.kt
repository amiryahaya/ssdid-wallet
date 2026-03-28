package my.ssdid.sdk.domain.oid4vci

import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import my.ssdid.sdk.domain.vault.Vault
import org.junit.Before
import org.junit.Test

class OpenId4VciHandlerTest {

    private lateinit var metadataResolver: IssuerMetadataResolver
    private lateinit var tokenClient: TokenClient
    private lateinit var nonceManager: NonceManager
    private lateinit var transport: OpenId4VciTransport
    private lateinit var vault: Vault
    private lateinit var handler: OpenId4VciHandler

    private val signer: (ByteArray) -> ByteArray = { ByteArray(64) }

    @Before
    fun setup() {
        metadataResolver = mockk()
        tokenClient = mockk()
        nonceManager = NonceManager()
        transport = mockk()
        vault = mockk(relaxed = true)
        handler = OpenId4VciHandler(metadataResolver, tokenClient, nonceManager, transport, vault)
    }

    @Test
    fun acceptOfferPreAuthorizedCodeSuccess() = runTest {
        val offer = CredentialOffer(
            credentialIssuer = "https://issuer.example.com",
            credentialConfigurationIds = listOf("UnivDegree"),
            preAuthorizedCode = "pre-code-1"
        )
        val metadata = IssuerMetadata(
            credentialIssuer = "https://issuer.example.com",
            credentialEndpoint = "https://issuer.example.com/credential",
            credentialConfigurationsSupported = emptyMap(),
            tokenEndpoint = "https://issuer.example.com/token",
            authorizationEndpoint = null
        )

        every {
            tokenClient.exchangePreAuthorizedCode(
                "https://issuer.example.com/token",
                "pre-code-1",
                null
            )
        } returns Result.success(TokenResponse("at-1", "Bearer", "c-nonce-1", 300))

        every {
            transport.postCredentialRequest(any(), eq("at-1"), any())
        } returns """{"credential":"eyJ.eyJ.sig~disc~","c_nonce":"c-nonce-2","c_nonce_expires_in":600}"""

        coEvery { vault.storeStoredSdJwtVc(any()) } returns Result.success(Unit)

        val result = handler.acceptOffer(
            offer = offer,
            metadata = metadata,
            selectedConfigId = "UnivDegree",
            walletDid = "did:ssdid:holder",
            keyId = "did:ssdid:holder#key-1",
            algorithm = "EdDSA",
            signer = signer
        )

        assertThat(result.isSuccess).isTrue()
        val issuance = result.getOrThrow()
        assertThat(issuance).isInstanceOf(IssuanceResult.Success::class.java)
        val success = issuance as IssuanceResult.Success
        assertThat(success.credential.compact).isEqualTo("eyJ.eyJ.sig~disc~")
        assertThat(success.credential.issuer).isEqualTo("https://issuer.example.com")
        assertThat(success.credential.type).isEqualTo("UnivDegree")
        coVerify { vault.storeStoredSdJwtVc(any()) }
    }

    @Test
    fun acceptOfferDeferredResult() = runTest {
        val offer = CredentialOffer(
            credentialIssuer = "https://issuer.example.com",
            credentialConfigurationIds = listOf("Diploma"),
            preAuthorizedCode = "pre-code-2"
        )
        val metadata = IssuerMetadata(
            credentialIssuer = "https://issuer.example.com",
            credentialEndpoint = "https://issuer.example.com/credential",
            credentialConfigurationsSupported = emptyMap(),
            tokenEndpoint = "https://issuer.example.com/token",
            authorizationEndpoint = null
        )

        every {
            tokenClient.exchangePreAuthorizedCode(any(), any(), any())
        } returns Result.success(TokenResponse("at-2", "Bearer", "cn-1", 300))

        every {
            transport.postCredentialRequest(any(), any(), any())
        } returns """{"transaction_id":"tx-123"}"""

        val result = handler.acceptOffer(
            offer = offer,
            metadata = metadata,
            selectedConfigId = "Diploma",
            walletDid = "did:ssdid:h",
            keyId = "did:ssdid:h#k-1",
            algorithm = "EdDSA",
            signer = signer
        )

        assertThat(result.isSuccess).isTrue()
        val issuance = result.getOrThrow()
        assertThat(issuance).isInstanceOf(IssuanceResult.Deferred::class.java)
        val deferred = issuance as IssuanceResult.Deferred
        assertThat(deferred.transactionId).isEqualTo("tx-123")
        assertThat(deferred.deferredEndpoint)
            .isEqualTo("https://issuer.example.com/deferred_credential")
        assertThat(deferred.accessToken).isEqualTo("at-2")
    }

    @Test
    fun acceptOfferFailsWithNoPreAuthCode() = runTest {
        val offer = CredentialOffer(
            credentialIssuer = "https://issuer.example.com",
            credentialConfigurationIds = listOf("x"),
            authorizationCodeGrant = true
        )
        val metadata = IssuerMetadata(
            credentialIssuer = "https://issuer.example.com",
            credentialEndpoint = "https://issuer.example.com/credential",
            credentialConfigurationsSupported = emptyMap(),
            tokenEndpoint = "https://issuer.example.com/token",
            authorizationEndpoint = "https://issuer.example.com/authorize"
        )

        val result = handler.acceptOffer(
            offer = offer,
            metadata = metadata,
            selectedConfigId = "x",
            walletDid = "did:ssdid:h",
            keyId = "did:ssdid:h#k-1",
            algorithm = "EdDSA",
            signer = signer
        )

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull())
            .isInstanceOf(UnsupportedOperationException::class.java)
    }

    @Test
    fun acceptOfferFailsWhenNonceUnavailable() = runTest {
        val offer = CredentialOffer(
            credentialIssuer = "https://issuer.example.com",
            credentialConfigurationIds = listOf("Test"),
            preAuthorizedCode = "pre-code-3"
        )
        val metadata = IssuerMetadata(
            credentialIssuer = "https://issuer.example.com",
            credentialEndpoint = "https://issuer.example.com/credential",
            credentialConfigurationsSupported = emptyMap(),
            tokenEndpoint = "https://issuer.example.com/token",
            authorizationEndpoint = null
        )

        // Token response without c_nonce
        every {
            tokenClient.exchangePreAuthorizedCode(any(), any(), any())
        } returns Result.success(TokenResponse("at-3", "Bearer", null, null))

        val result = handler.acceptOffer(
            offer = offer,
            metadata = metadata,
            selectedConfigId = "Test",
            walletDid = "did:ssdid:h",
            keyId = "did:ssdid:h#k-1",
            algorithm = "EdDSA",
            signer = signer
        )

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull())
            .isInstanceOf(IllegalStateException::class.java)
        assertThat(result.exceptionOrNull()?.message)
            .contains("No c_nonce available")
    }

    @Test
    fun acceptOfferUnexpectedResponseReturnsFailed() = runTest {
        val offer = CredentialOffer(
            credentialIssuer = "https://issuer.example.com",
            credentialConfigurationIds = listOf("Test"),
            preAuthorizedCode = "pre-code-4"
        )
        val metadata = IssuerMetadata(
            credentialIssuer = "https://issuer.example.com",
            credentialEndpoint = "https://issuer.example.com/credential",
            credentialConfigurationsSupported = emptyMap(),
            tokenEndpoint = "https://issuer.example.com/token",
            authorizationEndpoint = null
        )

        every {
            tokenClient.exchangePreAuthorizedCode(any(), any(), any())
        } returns Result.success(TokenResponse("at-4", "Bearer", "cn-4", 300))

        every {
            transport.postCredentialRequest(any(), any(), any())
        } returns """{"status":"pending"}"""

        val result = handler.acceptOffer(
            offer = offer,
            metadata = metadata,
            selectedConfigId = "Test",
            walletDid = "did:ssdid:h",
            keyId = "did:ssdid:h#k-1",
            algorithm = "EdDSA",
            signer = signer
        )

        assertThat(result.isSuccess).isTrue()
        val issuance = result.getOrThrow()
        assertThat(issuance).isInstanceOf(IssuanceResult.Failed::class.java)
        assertThat((issuance as IssuanceResult.Failed).error)
            .contains("Unexpected response")
    }
}
