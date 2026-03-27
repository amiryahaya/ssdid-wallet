package my.ssdid.wallet.feature.identity

import com.google.common.truth.Truth.assertThat
import my.ssdid.sdk.domain.model.CredentialSubject
import my.ssdid.sdk.domain.model.Proof
import my.ssdid.sdk.domain.model.VerifiableCredential
import my.ssdid.wallet.domain.revocation.RevocationStatus
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class VcDisplayStatusTest {

    private val proof = Proof(
        type = "Ed25519Signature2020",
        created = "2026-01-01T00:00:00Z",
        verificationMethod = "did:ssdid:test#key-1",
        proofPurpose = "assertionMethod",
        proofValue = "zFake"
    )

    private fun vc(expirationDate: String? = null) = VerifiableCredential(
        id = "urn:uuid:test-vc-1",
        type = listOf("VerifiableCredential"),
        issuer = "did:ssdid:issuer",
        issuanceDate = "2026-01-01T00:00:00Z",
        expirationDate = expirationDate,
        credentialSubject = CredentialSubject(id = "did:ssdid:holder"),
        proof = proof
    )

    // --- Tests for vcDisplayStatus (without revocation) ---

    @Test
    fun `active when no expiration date`() {
        assertThat(vcDisplayStatus(vc())).isEqualTo(VcDisplayStatus.ACTIVE)
    }

    @Test
    fun `expired when expiration date is in the past`() {
        val past = Instant.now().minus(1, ChronoUnit.DAYS).toString()
        assertThat(vcDisplayStatus(vc(expirationDate = past))).isEqualTo(VcDisplayStatus.EXPIRED)
    }

    @Test
    fun `expiring when expiration is within 30 days`() {
        val soon = Instant.now().plus(15, ChronoUnit.DAYS).toString()
        assertThat(vcDisplayStatus(vc(expirationDate = soon))).isEqualTo(VcDisplayStatus.EXPIRING)
    }

    @Test
    fun `active when expiration is more than 30 days away`() {
        val farFuture = Instant.now().plus(90, ChronoUnit.DAYS).toString()
        assertThat(vcDisplayStatus(vc(expirationDate = farFuture))).isEqualTo(VcDisplayStatus.ACTIVE)
    }

    // --- Tests for resolveDisplayStatus (with revocation) ---

    @Test
    fun `revoked overrides active`() {
        val status = resolveDisplayStatus(vc(), RevocationStatus.REVOKED)
        assertThat(status).isEqualTo(VcDisplayStatus.REVOKED)
    }

    @Test
    fun `revoked overrides expired`() {
        val past = Instant.now().minus(1, ChronoUnit.DAYS).toString()
        val status = resolveDisplayStatus(vc(expirationDate = past), RevocationStatus.REVOKED)
        assertThat(status).isEqualTo(VcDisplayStatus.REVOKED)
    }

    @Test
    fun `revoked overrides expiring`() {
        val soon = Instant.now().plus(15, ChronoUnit.DAYS).toString()
        val status = resolveDisplayStatus(vc(expirationDate = soon), RevocationStatus.REVOKED)
        assertThat(status).isEqualTo(VcDisplayStatus.REVOKED)
    }

    @Test
    fun `valid revocation falls through to expiration check`() {
        val past = Instant.now().minus(1, ChronoUnit.DAYS).toString()
        val status = resolveDisplayStatus(vc(expirationDate = past), RevocationStatus.VALID)
        assertThat(status).isEqualTo(VcDisplayStatus.EXPIRED)
    }

    @Test
    fun `unknown revocation falls through to expiration check`() {
        val soon = Instant.now().plus(15, ChronoUnit.DAYS).toString()
        val status = resolveDisplayStatus(vc(expirationDate = soon), RevocationStatus.UNKNOWN)
        assertThat(status).isEqualTo(VcDisplayStatus.EXPIRING)
    }

    @Test
    fun `valid revocation and no expiration yields active`() {
        val status = resolveDisplayStatus(vc(), RevocationStatus.VALID)
        assertThat(status).isEqualTo(VcDisplayStatus.ACTIVE)
    }
}
