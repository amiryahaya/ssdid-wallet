package my.ssdid.wallet.domain.profile

import io.mockk.*
import kotlinx.coroutines.test.runTest
import my.ssdid.sdk.domain.model.*
import my.ssdid.sdk.domain.vault.Vault
import org.junit.Before
import org.junit.Test

class ProfileMigrationTest {

    private lateinit var vault: Vault
    private lateinit var migration: ProfileMigration

    @Before
    fun setup() {
        vault = mockk(relaxed = true)
        migration = ProfileMigration(vault)
    }

    private fun profileVc(name: String = "Amir", email: String = "amir@acme.com") = VerifiableCredential(
        id = "urn:ssdid:profile",
        type = listOf("VerifiableCredential", "ProfileCredential"),
        issuer = "did:ssdid:self",
        issuanceDate = "2026-03-16T00:00:00Z",
        credentialSubject = CredentialSubject(
            id = "did:ssdid:self",
            claims = mapOf("name" to name, "email" to email)
        ),
        proof = Proof(type = "SelfIssued2024", created = "2026-03-16T00:00:00Z",
            verificationMethod = "did:ssdid:self", proofPurpose = "selfAssertion", proofValue = "")
    )

    private fun identity(keyId: String = "did:ssdid:abc#key-1", email: String? = null, profileName: String? = null) = Identity(
        name = "Work", did = "did:ssdid:abc", keyId = keyId,
        algorithm = Algorithm.ED25519, publicKeyMultibase = "z6Mk...",
        createdAt = "2026-03-16T00:00:00Z", profileName = profileName, email = email
    )

    @Test
    fun `migrate copies name and email to first identity and deletes profile VC`() = runTest {
        coEvery { vault.listCredentials() } returns listOf(profileVc())
        coEvery { vault.listIdentities() } returns listOf(identity())
        coEvery { vault.updateIdentityProfile(any(), profileName = any(), email = any()) } returns Result.success(Unit)
        coEvery { vault.deleteCredential(any()) } returns Result.success(Unit)

        migration.migrateIfNeeded()

        coVerify { vault.updateIdentityProfile("did:ssdid:abc#key-1", profileName = "Amir", email = "amir@acme.com") }
        coVerify { vault.deleteCredential("urn:ssdid:profile") }
    }

    @Test
    fun `migrate does nothing when no profile VC exists`() = runTest {
        coEvery { vault.listCredentials() } returns emptyList()

        migration.migrateIfNeeded()

        coVerify(exactly = 0) { vault.updateIdentityProfile(any(), profileName = any(), email = any()) }
        coVerify(exactly = 0) { vault.deleteCredential(any()) }
    }

    @Test
    fun `migrate skips identity that already has email but still deletes profile VC`() = runTest {
        coEvery { vault.listCredentials() } returns listOf(profileVc())
        coEvery { vault.listIdentities() } returns listOf(identity(email = "amir@acme.com", profileName = "Amir"))
        coEvery { vault.deleteCredential(any()) } returns Result.success(Unit)

        migration.migrateIfNeeded()

        coVerify(exactly = 0) { vault.updateIdentityProfile(any(), profileName = any(), email = any()) }
        coVerify { vault.deleteCredential("urn:ssdid:profile") }
    }

    @Test
    fun `migrate preserves profile VC when no identities exist`() = runTest {
        coEvery { vault.listCredentials() } returns listOf(profileVc())
        coEvery { vault.listIdentities() } returns emptyList()

        migration.migrateIfNeeded()

        coVerify(exactly = 0) { vault.updateIdentityProfile(any(), profileName = any(), email = any()) }
        coVerify(exactly = 0) { vault.deleteCredential(any()) }
    }

    @Test
    fun `migrate with multiple identities only updates first without email`() = runTest {
        val id1 = identity(keyId = "did:ssdid:abc#key-1", email = "existing@acme.com", profileName = "Existing")
        val id2 = Identity(
            name = "Personal", did = "did:ssdid:def", keyId = "did:ssdid:def#key-1",
            algorithm = Algorithm.ED25519, publicKeyMultibase = "z6Mk...",
            createdAt = "2026-03-16T00:00:00Z"
        )

        coEvery { vault.listCredentials() } returns listOf(profileVc())
        coEvery { vault.listIdentities() } returns listOf(id1, id2)
        coEvery { vault.updateIdentityProfile(any(), profileName = any(), email = any()) } returns Result.success(Unit)
        coEvery { vault.deleteCredential(any()) } returns Result.success(Unit)

        migration.migrateIfNeeded()

        coVerify { vault.updateIdentityProfile("did:ssdid:def#key-1", profileName = "Amir", email = "amir@acme.com") }
        coVerify { vault.deleteCredential("urn:ssdid:profile") }
    }
}
