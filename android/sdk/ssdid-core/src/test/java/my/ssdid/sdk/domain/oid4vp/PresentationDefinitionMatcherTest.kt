package my.ssdid.sdk.domain.oid4vp

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import my.ssdid.sdk.domain.sdjwt.StoredSdJwtVc
import org.junit.Test

class PresentationDefinitionMatcherTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val matcher = PresentationDefinitionMatcher()

    private val credential = StoredSdJwtVc(
        id = "vc-1", compact = "eyJ...~disc1~disc2~",
        issuer = "did:ssdid:issuer1", subject = "did:ssdid:holder1",
        type = "IdentityCredential",
        claims = mapOf("name" to "Ahmad", "email" to "ahmad@example.com"),
        disclosableClaims = listOf("name", "email"),
        issuedAt = 1700000000L
    )

    @Test
    fun matchByVct() {
        val pd = json.parseToJsonElement("""{"id":"pd-1","input_descriptors":[{"id":"id-1","format":{"vc+sd-jwt":{}},"constraints":{"fields":[{"path":["$.vct"],"filter":{"const":"IdentityCredential"}}]}}]}""") as JsonObject
        val results = matcher.match(pd, listOf(credential))
        assertThat(results).hasSize(1)
        assertThat(results[0].credential.id).isEqualTo("vc-1")
        assertThat(results[0].descriptorId).isEqualTo("id-1")
    }

    @Test
    fun noMatchWhenVctDiffers() {
        val pd = json.parseToJsonElement("""{"id":"pd-1","input_descriptors":[{"id":"id-1","format":{"vc+sd-jwt":{}},"constraints":{"fields":[{"path":["$.vct"],"filter":{"const":"DriverLicense"}}]}}]}""") as JsonObject
        val results = matcher.match(pd, listOf(credential))
        assertThat(results).isEmpty()
    }

    @Test
    fun matchWithClaimFields() {
        val pd = json.parseToJsonElement("""{"id":"pd-1","input_descriptors":[{"id":"id-1","format":{"vc+sd-jwt":{}},"constraints":{"fields":[{"path":["$.vct"],"filter":{"const":"IdentityCredential"}},{"path":["$.name"]},{"path":["$.email"],"optional":true}]}}]}""") as JsonObject
        val results = matcher.match(pd, listOf(credential))
        assertThat(results).hasSize(1)
        assertThat(results[0].requiredClaims).contains("name")
        assertThat(results[0].optionalClaims).contains("email")
    }

    @Test
    fun skipNonSdJwtFormat() {
        val pd = json.parseToJsonElement("""{"id":"pd-1","input_descriptors":[{"id":"id-1","format":{"jwt_vp":{}},"constraints":{"fields":[{"path":["$.vct"],"filter":{"const":"IdentityCredential"}}]}}]}""") as JsonObject
        val results = matcher.match(pd, listOf(credential))
        assertThat(results).isEmpty()
    }

    @Test
    fun missingDescriptorId() {
        val pd = json.parseToJsonElement("""{"id":"pd-1","input_descriptors":[{"format":{"vc+sd-jwt":{}},"constraints":{"fields":[]}}]}""") as JsonObject
        val results = matcher.match(pd, listOf(credential))
        assertThat(results).isEmpty()
    }

    @Test
    fun multipleCredentials() {
        val cred2 = StoredSdJwtVc(id = "vc-2", compact = "eyJ2...~", issuer = "did:ssdid:i2", subject = "did:ssdid:h", type = "DriverLicense", claims = mapOf("license_number" to "DL123"), disclosableClaims = listOf("license_number"), issuedAt = 1700000000L)
        val pd = json.parseToJsonElement("""{"id":"pd-1","input_descriptors":[{"id":"id-1","format":{"vc+sd-jwt":{}},"constraints":{"fields":[{"path":["$.vct"],"filter":{"const":"DriverLicense"}}]}}]}""") as JsonObject
        val results = matcher.match(pd, listOf(credential, cred2))
        assertThat(results).hasSize(1)
        assertThat(results[0].credential.id).isEqualTo("vc-2")
    }
}
