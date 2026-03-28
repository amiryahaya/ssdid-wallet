package my.ssdid.sdk.domain.vault

import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import my.ssdid.sdk.domain.model.*
import org.junit.Before
import org.junit.Test

class VaultGetCredentialsTest {

    private lateinit var storage: VaultStorage
    private lateinit var vault: VaultImpl

    @Before
    fun setup() {
        storage = mockk(relaxed = true)
        vault = VaultImpl(mockk(), mockk(), mockk(), storage)
    }

    private fun vc(id: String, subjectDid: String, issuer: String = "did:ssdid:server") = VerifiableCredential(
        id = id,
        type = listOf("VerifiableCredential"),
        issuer = issuer,
        issuanceDate = "2026-03-16T00:00:00Z",
        credentialSubject = CredentialSubject(id = subjectDid),
        proof = Proof(type = "Ed25519Signature2020", created = "2026-03-16T00:00:00Z",
            verificationMethod = "did:ssdid:server#key-1", proofPurpose = "assertionMethod", proofValue = "z...")
    )

    @Test
    fun `getCredentialsForDid returns all matching credentials`() = runTest {
        coEvery { storage.listCredentials() } returns listOf(
            vc("vc-1", "did:ssdid:alice"),
            vc("vc-2", "did:ssdid:alice", issuer = "did:ssdid:drive"),
            vc("vc-3", "did:ssdid:bob")
        )
        val result = vault.getCredentialsForDid("did:ssdid:alice")
        assertThat(result).hasSize(2)
        assertThat(result.map { it.id }).containsExactly("vc-1", "vc-2")
    }

    @Test
    fun `getCredentialsForDid returns empty for unknown DID`() = runTest {
        coEvery { storage.listCredentials() } returns listOf(vc("vc-1", "did:ssdid:alice"))
        assertThat(vault.getCredentialsForDid("did:ssdid:unknown")).isEmpty()
    }
}
