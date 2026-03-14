package my.ssdid.wallet.domain.oid4vp

import com.google.common.truth.Truth.assertThat
import my.ssdid.wallet.domain.sdjwt.StoredSdJwtVc
import org.junit.Test

class PresentationDefinitionMatcherTest {

    private val matcher = PresentationDefinitionMatcher()

    private val employeeVc = StoredSdJwtVc(
        id = "vc-1",
        compact = "eyJ...",
        issuer = "did:ssdid:issuer1",
        subject = "did:ssdid:holder1",
        type = "VerifiedEmployee",
        claims = mapOf("name" to "Ahmad", "department" to "Engineering", "employeeId" to "EMP-1234"),
        disclosableClaims = listOf("name", "department"),
        issuedAt = 1719792000
    )

    @Test
    fun `matches credential by vct and returns required and optional claims`() {
        val pd = """
        {
          "id": "req-1",
          "input_descriptors": [{
            "id": "emp-cred",
            "format": { "vc+sd-jwt": { "alg": ["EdDSA"] } },
            "constraints": {
              "fields": [
                { "path": ["$.vct"], "filter": { "const": "VerifiedEmployee" } },
                { "path": ["$.name"] },
                { "path": ["$.department"], "optional": true }
              ]
            }
          }]
        }
        """.trimIndent()

        val results = matcher.match(pd, listOf(employeeVc))
        assertThat(results).hasSize(1)
        assertThat(results[0].descriptorId).isEqualTo("emp-cred")
        assertThat(results[0].credentialId).isEqualTo("vc-1")

        val claims = results[0].availableClaims
        assertThat(claims["name"]?.required).isTrue()
        assertThat(claims["name"]?.available).isTrue()
        assertThat(claims["department"]?.required).isFalse()
        assertThat(claims["department"]?.available).isTrue()
    }

    @Test
    fun `returns empty when no credential matches vct filter`() {
        val pd = """
        {
          "id": "req-2",
          "input_descriptors": [{
            "id": "gov-id",
            "format": { "vc+sd-jwt": {} },
            "constraints": {
              "fields": [
                { "path": ["$.vct"], "filter": { "const": "GovernmentId" } }
              ]
            }
          }]
        }
        """.trimIndent()

        val results = matcher.match(pd, listOf(employeeVc))
        assertThat(results).isEmpty()
    }

    @Test
    fun `returns empty when required claim not available`() {
        val pd = """
        {
          "id": "req-3",
          "input_descriptors": [{
            "id": "id-cred",
            "format": { "vc+sd-jwt": {} },
            "constraints": {
              "fields": [
                { "path": ["$.vct"], "filter": { "const": "VerifiedEmployee" } },
                { "path": ["$.national_id"] }
              ]
            }
          }]
        }
        """.trimIndent()

        val results = matcher.match(pd, listOf(employeeVc))
        assertThat(results).isEmpty()
    }

    @Test
    fun `selects correct credential from multiple stored`() {
        val degreeVc = StoredSdJwtVc(
            id = "vc-2", compact = "eyJ...", issuer = "did:ssdid:uni",
            subject = "did:ssdid:holder1", type = "UniversityDegree",
            claims = mapOf("degree" to "BSc"), disclosableClaims = listOf("degree"),
            issuedAt = 1719792000
        )

        val pd = """
        {
          "id": "req-4",
          "input_descriptors": [{
            "id": "emp-cred",
            "format": { "vc+sd-jwt": {} },
            "constraints": {
              "fields": [
                { "path": ["$.vct"], "filter": { "const": "VerifiedEmployee" } },
                { "path": ["$.name"] }
              ]
            }
          }]
        }
        """.trimIndent()

        val results = matcher.match(pd, listOf(degreeVc, employeeVc))
        assertThat(results).hasSize(1)
        assertThat(results[0].credentialId).isEqualTo("vc-1")
    }

    @Test
    fun `converts to CredentialQuery`() {
        val pd = """
        {
          "id": "req-1",
          "input_descriptors": [{
            "id": "emp-cred",
            "format": { "vc+sd-jwt": {} },
            "constraints": {
              "fields": [
                { "path": ["$.vct"], "filter": { "const": "VerifiedEmployee" } },
                { "path": ["$.name"] },
                { "path": ["$.department"], "optional": true }
              ]
            }
          }]
        }
        """.trimIndent()

        val query = matcher.toCredentialQuery(pd)
        assertThat(query.descriptors).hasSize(1)
        assertThat(query.descriptors[0].vctFilter).isEqualTo("VerifiedEmployee")
        assertThat(query.descriptors[0].requiredClaims).containsExactly("name")
        assertThat(query.descriptors[0].optionalClaims).containsExactly("department")
    }
}
