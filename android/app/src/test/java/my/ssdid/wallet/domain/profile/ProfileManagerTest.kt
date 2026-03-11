package my.ssdid.wallet.domain.profile

import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import my.ssdid.wallet.domain.model.CredentialSubject
import my.ssdid.wallet.domain.model.Proof
import my.ssdid.wallet.domain.model.VerifiableCredential
import my.ssdid.wallet.domain.vault.Vault
import org.junit.Before
import org.junit.Test

class ProfileManagerTest {

    private lateinit var vault: Vault
    private lateinit var manager: ProfileManager

    @Before
    fun setup() {
        vault = mockk()
        manager = ProfileManager(vault)
    }

    @Test
    fun `saveProfile creates credential with correct structure`() = runTest {
        coEvery { vault.storeCredential(any()) } returns Result.success(Unit)
        coEvery { vault.deleteCredential(any()) } returns Result.success(Unit)
        coEvery { vault.listCredentials() } returns emptyList()

        val result = manager.saveProfile("Alice", "alice@example.com")

        assertThat(result.isSuccess).isTrue()
        coVerify {
            vault.storeCredential(match { vc ->
                vc.id == "urn:ssdid:profile"
                    && vc.issuer == "did:ssdid:self"
                    && vc.type.contains("ProfileCredential")
                    && vc.credentialSubject.id == "did:ssdid:self"
                    && vc.credentialSubject.claims["name"] == "Alice"
                    && vc.credentialSubject.claims["email"] == "alice@example.com"
                    && !vc.credentialSubject.claims.containsKey("phone")
                    && vc.proof.type == "SelfIssued2024"
            })
        }
    }

    @Test
    fun `saveProfile deletes existing profile before saving`() = runTest {
        val existing = VerifiableCredential(
            id = "urn:ssdid:profile",
            type = listOf("VerifiableCredential", "ProfileCredential"),
            issuer = "did:ssdid:self",
            issuanceDate = "2026-03-11T00:00:00Z",
            credentialSubject = CredentialSubject(id = "did:ssdid:self", claims = mapOf("name" to "Old")),
            proof = Proof(type = "SelfIssued2024", created = "2026-03-11T00:00:00Z",
                verificationMethod = "did:ssdid:self", proofPurpose = "selfAssertion", proofValue = "")
        )
        coEvery { vault.listCredentials() } returns listOf(existing)
        coEvery { vault.deleteCredential("urn:ssdid:profile") } returns Result.success(Unit)
        coEvery { vault.storeCredential(any()) } returns Result.success(Unit)

        manager.saveProfile("New Name", "new@example.com")

        coVerifyOrder {
            vault.deleteCredential("urn:ssdid:profile")
            vault.storeCredential(any())
        }
    }

    @Test
    fun `getProfile returns profile credential`() = runTest {
        val profile = VerifiableCredential(
            id = "urn:ssdid:profile",
            type = listOf("VerifiableCredential", "ProfileCredential"),
            issuer = "did:ssdid:self",
            issuanceDate = "2026-03-11T00:00:00Z",
            credentialSubject = CredentialSubject(
                id = "did:ssdid:self",
                claims = mapOf("name" to "Alice", "email" to "alice@example.com")
            ),
            proof = Proof(type = "SelfIssued2024", created = "2026-03-11T00:00:00Z",
                verificationMethod = "did:ssdid:self", proofPurpose = "selfAssertion", proofValue = "")
        )
        coEvery { vault.listCredentials() } returns listOf(profile)

        val result = manager.getProfile()
        assertThat(result).isNotNull()
        assertThat(result!!.credentialSubject.claims["name"]).isEqualTo("Alice")
    }

    @Test
    fun `getProfile returns null when no profile exists`() = runTest {
        coEvery { vault.listCredentials() } returns emptyList()
        assertThat(manager.getProfile()).isNull()
    }

    @Test
    fun `getProfileClaims returns claims map`() = runTest {
        val profile = VerifiableCredential(
            id = "urn:ssdid:profile",
            type = listOf("VerifiableCredential", "ProfileCredential"),
            issuer = "did:ssdid:self",
            issuanceDate = "2026-03-11T00:00:00Z",
            credentialSubject = CredentialSubject(
                id = "did:ssdid:self",
                claims = mapOf("name" to "Alice", "email" to "alice@example.com")
            ),
            proof = Proof(type = "SelfIssued2024", created = "2026-03-11T00:00:00Z",
                verificationMethod = "did:ssdid:self", proofPurpose = "selfAssertion", proofValue = "")
        )
        coEvery { vault.listCredentials() } returns listOf(profile)

        val claims = manager.getProfileClaims()
        assertThat(claims).containsEntry("name", "Alice")
        assertThat(claims).containsEntry("email", "alice@example.com")
    }

    @Test
    fun `getProfileClaims returns empty map when no profile`() = runTest {
        coEvery { vault.listCredentials() } returns emptyList()
        assertThat(manager.getProfileClaims()).isEmpty()
    }
}
