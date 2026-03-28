package my.ssdid.sdk.domain.oid4vp

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import my.ssdid.sdk.domain.sdjwt.StoredSdJwtVc
import org.junit.Test

class VpTokenBuilderTest {

    private val signer: (ByteArray) -> ByteArray = { data -> data.copyOf(64) } // dummy signer

    @Test
    fun buildVpTokenWithSelectedDisclosures() = runBlocking {
        val cred = StoredSdJwtVc(
            id = "vc-1",
            compact = "eyJhbGciOiJFZERTQSJ9.eyJpc3MiOiJkaWQ6c3NkaWQ6aXNzdWVyMSJ9.sig~WyJzYWx0MSIsIm5hbWUiLCJBaG1hZCJd~WyJzYWx0MiIsImVtYWlsIiwiYUBleC5jb20iXQ~",
            issuer = "did:ssdid:issuer1",
            subject = "did:ssdid:holder1",
            type = "IdentityCredential",
            claims = mapOf("name" to "Ahmad", "email" to "a@ex.com"),
            disclosableClaims = listOf("name", "email"),
            issuedAt = 1700000000L
        )

        val vpToken = VpTokenBuilder.build(
            storedSdJwtVc = cred,
            selectedClaims = listOf("name"),
            audience = "https://verifier.example.com",
            nonce = "nonce-123",
            algorithm = "EdDSA",
            signer = signer
        )

        val parts = vpToken.split("~")
        // issuerJwt + 1 disclosure + kbJwt = 3 parts
        assertThat(parts.size).isAtLeast(3)
        // First part is the issuer JWT (starts with base64url header)
        assertThat(parts[0]).startsWith("eyJ")
        // Last part is the KB-JWT (has 3 dot-separated segments)
        assertThat(parts.last().count { it == '.' }).isEqualTo(2)
    }

    @Test
    fun buildVpTokenWithAllDisclosures() = runBlocking {
        val cred = StoredSdJwtVc(
            id = "vc-1",
            compact = "eyJhbGciOiJFZERTQSJ9.eyJpc3MiOiJkaWQ6c3NkaWQ6aXNzdWVyMSJ9.sig~WyJzYWx0MSIsIm5hbWUiLCJBaG1hZCJd~WyJzYWx0MiIsImVtYWlsIiwiYUBleC5jb20iXQ~",
            issuer = "did:ssdid:issuer1",
            subject = "did:ssdid:holder1",
            type = "IdentityCredential",
            claims = mapOf("name" to "Ahmad", "email" to "a@ex.com"),
            disclosableClaims = listOf("name", "email"),
            issuedAt = 1700000000L
        )

        val vpToken = VpTokenBuilder.build(
            storedSdJwtVc = cred,
            selectedClaims = listOf("name", "email"),
            audience = "https://v.example.com",
            nonce = "n-1",
            algorithm = "EdDSA",
            signer = signer
        )

        val parts = vpToken.split("~")
        // issuerJwt + 2 disclosures + kbJwt = 4 parts
        assertThat(parts.size).isEqualTo(4)
    }

    @Test
    fun buildVpTokenContainsOnlySelectedDisclosures() = runBlocking {
        val cred = StoredSdJwtVc(
            id = "vc-1",
            compact = "eyJhbGciOiJFZERTQSJ9.eyJpc3MiOiJkaWQ6c3NkaWQ6aXNzdWVyMSJ9.sig~WyJzYWx0MSIsIm5hbWUiLCJBaG1hZCJd~WyJzYWx0MiIsImVtYWlsIiwiYUBleC5jb20iXQ~",
            issuer = "did:ssdid:issuer1",
            subject = "did:ssdid:holder1",
            type = "IdentityCredential",
            claims = mapOf("name" to "Ahmad", "email" to "a@ex.com"),
            disclosableClaims = listOf("name", "email"),
            issuedAt = 1700000000L
        )

        val vpToken = VpTokenBuilder.build(
            storedSdJwtVc = cred,
            selectedClaims = listOf("email"),
            audience = "https://verifier.example.com",
            nonce = "nonce-456",
            algorithm = "EdDSA",
            signer = signer
        )

        // Should contain the email disclosure but not the name disclosure
        assertThat(vpToken).contains("WyJzYWx0MiIsImVtYWlsIiwiYUBleC5jb20iXQ")
        assertThat(vpToken.contains("WyJzYWx0MSIsIm5hbWUiLCJBaG1hZCJd")).isFalse()
    }
}
