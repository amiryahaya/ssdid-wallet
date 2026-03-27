package my.ssdid.wallet.domain.verifier.offline

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import my.ssdid.sdk.domain.model.CredentialSubject
import my.ssdid.sdk.domain.model.Proof
import my.ssdid.sdk.domain.model.VerifiableCredential
import org.junit.Before
import org.junit.Test

/**
 * Tests CredentialRepository contract using an in-memory implementation.
 * The encrypted DataStoreCredentialRepository requires AndroidKeyStore
 * which is not available in Robolectric — tested via instrumented tests instead.
 */
private class InMemoryCredentialRepository : CredentialRepository {
    private val credentials = mutableMapOf<String, VerifiableCredential>()

    override suspend fun saveCredential(credential: VerifiableCredential) {
        credentials[credential.id] = credential
    }

    override suspend fun getHeldCredentials(): List<VerifiableCredential> =
        credentials.values.toList()

    override suspend fun getUniqueIssuerDids(): List<String> =
        credentials.values.map { it.issuer }.distinct()

    override suspend fun deleteCredential(credentialId: String) {
        credentials.remove(credentialId)
    }
}

class CredentialRepositoryTest {

    private lateinit var repository: CredentialRepository

    @Before
    fun setup() {
        repository = InMemoryCredentialRepository()
    }

    @Test
    fun `saveCredential and getHeldCredentials roundtrip`() = runTest {
        val credential = makeTestCredential(issuer = "did:ssdid:issuer1", id = "urn:uuid:cred-1")

        repository.saveCredential(credential)
        val held = repository.getHeldCredentials()

        assertThat(held).hasSize(1)
        val stored = held.first()
        assertThat(stored.id).isEqualTo("urn:uuid:cred-1")
        assertThat(stored.issuer).isEqualTo("did:ssdid:issuer1")
        assertThat(stored.type).isEqualTo(listOf("VerifiableCredential"))
        assertThat(stored.issuanceDate).isEqualTo("2026-01-01T00:00:00Z")
        assertThat(stored.credentialSubject.id).isEqualTo("did:ssdid:holder1")
        assertThat(stored.proof.proofValue).isEqualTo("zFakeSignature")
    }

    @Test
    fun `getUniqueIssuerDids returns deduplicated issuers`() = runTest {
        val cred1 = makeTestCredential(issuer = "did:ssdid:issuer1", id = "urn:uuid:cred-1")
        val cred2 = makeTestCredential(issuer = "did:ssdid:issuer1", id = "urn:uuid:cred-2")
        val cred3 = makeTestCredential(issuer = "did:ssdid:issuer2", id = "urn:uuid:cred-3")

        repository.saveCredential(cred1)
        repository.saveCredential(cred2)
        repository.saveCredential(cred3)

        val issuers = repository.getUniqueIssuerDids()

        assertThat(issuers).hasSize(2)
        assertThat(issuers).containsExactly("did:ssdid:issuer1", "did:ssdid:issuer2")
    }

    @Test
    fun `deleteCredential removes from store`() = runTest {
        val cred1 = makeTestCredential(issuer = "did:ssdid:issuer1", id = "urn:uuid:cred-1")
        val cred2 = makeTestCredential(issuer = "did:ssdid:issuer2", id = "urn:uuid:cred-2")

        repository.saveCredential(cred1)
        repository.saveCredential(cred2)
        assertThat(repository.getHeldCredentials()).hasSize(2)

        repository.deleteCredential("urn:uuid:cred-1")

        val remaining = repository.getHeldCredentials()
        assertThat(remaining).hasSize(1)
        assertThat(remaining.first().id).isEqualTo("urn:uuid:cred-2")
    }

    private fun makeTestCredential(issuer: String, id: String) = VerifiableCredential(
        id = id,
        type = listOf("VerifiableCredential"),
        issuer = issuer,
        issuanceDate = "2026-01-01T00:00:00Z",
        credentialSubject = CredentialSubject(id = "did:ssdid:holder1"),
        proof = Proof(
            type = "Ed25519Signature2020",
            created = "2026-01-01T00:00:00Z",
            verificationMethod = "$issuer#key-1",
            proofPurpose = "assertionMethod",
            proofValue = "zFakeSignature"
        )
    )
}
