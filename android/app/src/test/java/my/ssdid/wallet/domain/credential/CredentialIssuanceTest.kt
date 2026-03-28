package my.ssdid.wallet.domain.credential

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import my.ssdid.sdk.domain.model.*
import my.ssdid.sdk.domain.transport.IssuerApi
import my.ssdid.sdk.domain.transport.SsdidHttpClient
import my.ssdid.sdk.domain.transport.dto.CredentialAcceptResponse
import my.ssdid.sdk.domain.transport.dto.CredentialOfferResponse
import my.ssdid.sdk.domain.vault.Vault
import org.junit.Before
import org.junit.Test

class CredentialIssuanceTest {

    private lateinit var vault: Vault
    private lateinit var httpClient: SsdidHttpClient
    private lateinit var issuerApi: IssuerApi
    private lateinit var manager: CredentialIssuanceManager

    private val testIdentity = Identity(
        name = "Test",
        did = "did:ssdid:abc123",
        keyId = "key-1",
        algorithm = Algorithm.ED25519,
        publicKeyMultibase = "uPublicKey",
        createdAt = "2024-01-01T00:00:00Z"
    )

    private val testOffer = CredentialOfferResponse(
        offer_id = "offer-1",
        issuer_did = "did:ssdid:issuer1",
        credential_type = "VerifiedEmployee",
        claims = mapOf("name" to "Alice", "role" to "Engineer"),
        expires_at = "2025-12-31T23:59:59Z"
    )

    private val testCredential = VerifiableCredential(
        id = "urn:uuid:cred-1",
        type = listOf("VerifiableCredential", "VerifiedEmployee"),
        issuer = "did:ssdid:issuer1",
        issuanceDate = "2024-06-01T00:00:00Z",
        credentialSubject = CredentialSubject(
            id = "did:ssdid:abc123",
            claims = mapOf("name" to "Alice", "role" to "Engineer")
        ),
        proof = Proof(
            type = "Ed25519Signature2020",
            created = "2024-06-01T00:00:00Z",
            verificationMethod = "did:ssdid:issuer1#key-1",
            proofPurpose = "assertionMethod",
            proofValue = "uSignatureValue"
        )
    )

    @Before
    fun setup() {
        vault = mockk()
        httpClient = mockk()
        issuerApi = mockk()
        every { httpClient.issuerApi("https://issuer.example.com") } returns issuerApi
        manager = CredentialIssuanceManager(vault, httpClient)
    }

    @Test
    fun `fetchOffer returns offer on success`() = runTest {
        coEvery { issuerApi.getOffer("offer-1") } returns testOffer

        val result = manager.fetchOffer("https://issuer.example.com", "offer-1")

        assertThat(result.isSuccess).isTrue()
        val offer = result.getOrThrow()
        assertThat(offer.offer_id).isEqualTo("offer-1")
        assertThat(offer.issuer_did).isEqualTo("did:ssdid:issuer1")
        assertThat(offer.credential_type).isEqualTo("VerifiedEmployee")
        assertThat(offer.claims).containsEntry("name", "Alice")
    }

    @Test
    fun `fetchOffer returns failure on network error`() = runTest {
        coEvery { issuerApi.getOffer("offer-1") } throws RuntimeException("Network error")

        val result = manager.fetchOffer("https://issuer.example.com", "offer-1")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).isEqualTo("Network error")
    }

    @Test
    fun `acceptOffer signs acceptance and stores credential`() = runTest {
        val sigBytes = byteArrayOf(1, 2, 3, 4)
        coEvery { vault.sign("key-1", any()) } returns Result.success(sigBytes)
        coEvery { issuerApi.acceptOffer("offer-1", any()) } returns CredentialAcceptResponse(testCredential)
        coEvery { vault.storeCredential(testCredential) } returns Result.success(Unit)

        val result = manager.acceptOffer("https://issuer.example.com", "offer-1", testIdentity)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow().id).isEqualTo("urn:uuid:cred-1")

        coVerify { vault.sign("key-1", "accept:offer-1".toByteArray()) }
        coVerify { vault.storeCredential(testCredential) }
    }

    @Test
    fun `acceptOffer returns failure when signing fails`() = runTest {
        coEvery { vault.sign("key-1", any()) } returns Result.failure(RuntimeException("Key not found"))

        val result = manager.acceptOffer("https://issuer.example.com", "offer-1", testIdentity)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).isEqualTo("Key not found")
    }

    @Test
    fun `acceptOffer returns failure when API call fails`() = runTest {
        coEvery { vault.sign("key-1", any()) } returns Result.success(byteArrayOf(1, 2, 3))
        coEvery { issuerApi.acceptOffer("offer-1", any()) } throws RuntimeException("Server error")

        val result = manager.acceptOffer("https://issuer.example.com", "offer-1", testIdentity)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).isEqualTo("Server error")
    }

    @Test
    fun `acceptOffer returns failure when credential storage fails`() = runTest {
        coEvery { vault.sign("key-1", any()) } returns Result.success(byteArrayOf(1, 2, 3))
        coEvery { issuerApi.acceptOffer("offer-1", any()) } returns CredentialAcceptResponse(testCredential)
        coEvery { vault.storeCredential(testCredential) } returns Result.failure(RuntimeException("Storage full"))

        val result = manager.acceptOffer("https://issuer.example.com", "offer-1", testIdentity)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).isEqualTo("Storage full")
    }
}
