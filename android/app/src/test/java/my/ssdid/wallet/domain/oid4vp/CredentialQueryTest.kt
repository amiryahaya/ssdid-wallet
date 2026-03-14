package my.ssdid.wallet.domain.oid4vp

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CredentialQueryTest {

    @Test
    fun `credential query descriptor has required and optional claims`() {
        val descriptor = CredentialQueryDescriptor(
            id = "emp-cred",
            format = "vc+sd-jwt",
            vctFilter = "VerifiedEmployee",
            requiredClaims = listOf("name"),
            optionalClaims = listOf("department")
        )
        assertThat(descriptor.id).isEqualTo("emp-cred")
        assertThat(descriptor.requiredClaims).containsExactly("name")
        assertThat(descriptor.optionalClaims).containsExactly("department")
    }

    @Test
    fun `credential query contains multiple descriptors`() {
        val query = CredentialQuery(
            descriptors = listOf(
                CredentialQueryDescriptor("d1", "vc+sd-jwt", "TypeA", listOf("a"), emptyList()),
                CredentialQueryDescriptor("d2", "vc+sd-jwt", "TypeB", listOf("b"), listOf("c"))
            )
        )
        assertThat(query.descriptors).hasSize(2)
    }
}
