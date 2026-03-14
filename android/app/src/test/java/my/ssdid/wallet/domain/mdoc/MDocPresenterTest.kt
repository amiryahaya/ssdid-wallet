package my.ssdid.wallet.domain.mdoc

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MDocPresenterTest {

    private val fullIssuerSigned = IssuerSigned(
        nameSpaces = mapOf(
            "org.iso.18013.5.1" to listOf(
                IssuerSignedItem(0, byteArrayOf(1), "family_name", "Smith"),
                IssuerSignedItem(1, byteArrayOf(2), "given_name", "Alice"),
                IssuerSignedItem(2, byteArrayOf(3), "birth_date", "1990-01-01"),
                IssuerSignedItem(3, byteArrayOf(4), "issue_date", "2024-01-01")
            )
        ),
        issuerAuth = byteArrayOf(0xD2.toByte())
    )

    @Test
    fun presentReturnsOnlyRequestedElements() {
        val requested = mapOf("org.iso.18013.5.1" to listOf("family_name", "birth_date"))
        val presented = MDocPresenter.present(fullIssuerSigned, requested)

        val items = presented.nameSpaces["org.iso.18013.5.1"]!!
        assertThat(items).hasSize(2)
        assertThat(items.map { it.elementIdentifier }).containsExactly("family_name", "birth_date")
    }

    @Test
    fun presentPreservesIssuerAuth() {
        val requested = mapOf("org.iso.18013.5.1" to listOf("given_name"))
        val presented = MDocPresenter.present(fullIssuerSigned, requested)
        assertThat(presented.issuerAuth).isEqualTo(fullIssuerSigned.issuerAuth)
    }

    @Test
    fun presentExcludesUnrequestedElements() {
        val requested = mapOf("org.iso.18013.5.1" to listOf("family_name"))
        val presented = MDocPresenter.present(fullIssuerSigned, requested)

        val items = presented.nameSpaces["org.iso.18013.5.1"]!!
        assertThat(items.map { it.elementIdentifier }).doesNotContain("given_name")
        assertThat(items.map { it.elementIdentifier }).doesNotContain("birth_date")
    }

    @Test
    fun presentEmptyRequestedReturnsEmptyNamespace() {
        val requested = mapOf("org.iso.18013.5.1" to emptyList<String>())
        val presented = MDocPresenter.present(fullIssuerSigned, requested)
        assertThat(presented.nameSpaces["org.iso.18013.5.1"]).isEmpty()
    }

    @Test
    fun presentUnknownNamespaceIgnored() {
        val requested = mapOf("unknown.namespace" to listOf("field"))
        val presented = MDocPresenter.present(fullIssuerSigned, requested)
        assertThat(presented.nameSpaces).doesNotContainKey("unknown.namespace")
    }
}
