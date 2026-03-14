package my.ssdid.wallet.domain.oid4vp

import com.google.common.truth.Truth.assertThat
import my.ssdid.wallet.domain.sdjwt.StoredSdJwtVc
import org.junit.Test

class DcqlMatcherTest {

    private val matcher = DcqlMatcher()

    private val employeeVc = StoredSdJwtVc(
        id = "vc-1",
        compact = "eyJ...",
        issuer = "did:ssdid:issuer1",
        subject = "did:ssdid:holder1",
        type = "VerifiedEmployee",
        claims = mapOf("name" to "Ahmad", "department" to "Engineering"),
        disclosableClaims = listOf("name", "department"),
        issuedAt = 1719792000
    )

    @Test
    fun `matches credential by vct_values and returns claims`() {
        val dcql = """
        {
          "credentials": [{
            "id": "emp-cred",
            "format": "vc+sd-jwt",
            "meta": { "vct_values": ["VerifiedEmployee"] },
            "claims": [
              { "path": ["name"] },
              { "path": ["department"], "optional": true }
            ]
          }]
        }
        """.trimIndent()

        val results = matcher.match(dcql, listOf(employeeVc))
        assertThat(results).hasSize(1)
        assertThat(results[0].descriptorId).isEqualTo("emp-cred")
        assertThat(results[0].availableClaims["name"]?.required).isTrue()
        assertThat(results[0].availableClaims["department"]?.required).isFalse()
    }

    @Test
    fun `returns empty when required claim not available`() {
        val dcql = """
        {
          "credentials": [{
            "id": "id-cred",
            "format": "vc+sd-jwt",
            "meta": { "vct_values": ["VerifiedEmployee"] },
            "claims": [
              { "path": ["national_id"] }
            ]
          }]
        }
        """.trimIndent()

        val results = matcher.match(dcql, listOf(employeeVc))
        assertThat(results).isEmpty()
    }

    @Test
    fun `converts to CredentialQuery`() {
        val dcql = """
        {
          "credentials": [{
            "id": "emp-cred",
            "format": "vc+sd-jwt",
            "meta": { "vct_values": ["VerifiedEmployee"] },
            "claims": [
              { "path": ["name"] },
              { "path": ["department"], "optional": true }
            ]
          }]
        }
        """.trimIndent()

        val query = matcher.toCredentialQuery(dcql)
        assertThat(query.descriptors).hasSize(1)
        assertThat(query.descriptors[0].vctFilter).isEqualTo("VerifiedEmployee")
        assertThat(query.descriptors[0].requiredClaims).containsExactly("name")
        assertThat(query.descriptors[0].optionalClaims).containsExactly("department")
    }
}
