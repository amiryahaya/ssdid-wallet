package my.ssdid.wallet.domain.profile

import my.ssdid.wallet.domain.model.CredentialSubject
import my.ssdid.wallet.domain.model.Proof
import my.ssdid.wallet.domain.model.VerifiableCredential
import my.ssdid.wallet.domain.vault.Vault
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class ProfileManager @Inject constructor(private val vault: Vault) {

    companion object {
        const val PROFILE_ID = "urn:ssdid:profile"
        const val SELF_ISSUER = "did:ssdid:self"
    }

    suspend fun saveProfile(name: String, email: String, phone: String): Result<Unit> = runCatching {
        // Delete existing profile if present
        val existing = vault.listCredentials().find { it.id == PROFILE_ID }
        if (existing != null) vault.deleteCredential(PROFILE_ID).getOrThrow()

        val now = DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC))
        val claims = mutableMapOf("name" to name, "email" to email)
        if (phone.isNotBlank()) claims["phone"] = phone

        val credential = VerifiableCredential(
            id = PROFILE_ID,
            type = listOf("VerifiableCredential", "ProfileCredential"),
            issuer = SELF_ISSUER,
            issuanceDate = now,
            credentialSubject = CredentialSubject(id = SELF_ISSUER, claims = claims),
            proof = Proof(
                type = "SelfIssued2024",
                created = now,
                verificationMethod = SELF_ISSUER,
                proofPurpose = "selfAssertion",
                proofValue = ""
            )
        )
        vault.storeCredential(credential).getOrThrow()
    }

    suspend fun getProfile(): VerifiableCredential? {
        return vault.listCredentials().find { it.id == PROFILE_ID }
    }

    suspend fun getProfileClaims(): Map<String, String> {
        return getProfile()?.credentialSubject?.claims ?: emptyMap()
    }
}
