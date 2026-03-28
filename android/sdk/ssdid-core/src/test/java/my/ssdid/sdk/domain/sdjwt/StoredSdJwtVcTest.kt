package my.ssdid.sdk.domain.sdjwt

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import my.ssdid.sdk.domain.sdjwt.StoredSdJwtVc
import org.junit.Test

class StoredSdJwtVcTest {

    // --- Serialization round-trip ---
    @Test
    fun `serialization round-trip preserves all fields`() {
        val original = StoredSdJwtVc(
            id = "vc-001",
            compact = "header.payload.sig~disc1~disc2~",
            issuer = "did:key:z6MkIssuer",
            subject = "did:key:z6MkHolder",
            type = "VerifiedEmployee",
            claims = mapOf("name" to "Ahmad", "dept" to "Engineering"),
            disclosableClaims = listOf("name", "dept"),
            issuedAt = 1719792000,
            expiresAt = 4102444800
        )

        val json = Json.encodeToString(original)
        val deserialized = Json.decodeFromString<StoredSdJwtVc>(json)

        assertThat(deserialized).isEqualTo(original)
        assertThat(deserialized.id).isEqualTo("vc-001")
        assertThat(deserialized.claims).containsEntry("name", "Ahmad")
        assertThat(deserialized.disclosableClaims).containsExactly("name", "dept")
        assertThat(deserialized.expiresAt).isEqualTo(4102444800)
    }

    // --- Null expiresAt ---
    @Test
    fun `serialization round-trip with null expiresAt`() {
        val original = StoredSdJwtVc(
            id = "vc-002",
            compact = "header.payload.sig~",
            issuer = "did:key:z6MkIssuer",
            subject = "did:key:z6MkHolder",
            type = "VerifiableCredential",
            claims = mapOf("email" to "test@example.com"),
            disclosableClaims = listOf("email"),
            issuedAt = 1719792000,
            expiresAt = null
        )

        val json = Json.encodeToString(original)
        val deserialized = Json.decodeFromString<StoredSdJwtVc>(json)

        assertThat(deserialized).isEqualTo(original)
        assertThat(deserialized.expiresAt).isNull()
    }
}
