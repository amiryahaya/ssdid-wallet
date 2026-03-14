package my.ssdid.wallet.domain.mdoc

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MDocModelTest {

    @Test
    fun storedMDocHasCorrectFields() {
        val mdoc = StoredMDoc(
            id = "mdoc-001",
            docType = "org.iso.18013.5.1.mDL",
            issuerSignedCbor = byteArrayOf(1, 2, 3),
            deviceKeyId = "key-1",
            issuedAt = 1700000000L,
            expiresAt = 1731536000L,
            nameSpaces = mapOf(
                "org.iso.18013.5.1" to listOf("family_name", "given_name", "birth_date")
            )
        )
        assertThat(mdoc.docType).isEqualTo("org.iso.18013.5.1.mDL")
        assertThat(mdoc.nameSpaces).containsKey("org.iso.18013.5.1")
        assertThat(mdoc.nameSpaces["org.iso.18013.5.1"]).hasSize(3)
    }

    @Test
    fun issuerSignedItemHoldsElements() {
        val item = IssuerSignedItem(
            digestId = 0,
            random = byteArrayOf(0xA, 0xB),
            elementIdentifier = "family_name",
            elementValue = "Smith"
        )
        assertThat(item.elementIdentifier).isEqualTo("family_name")
        assertThat(item.elementValue).isEqualTo("Smith")
    }

    @Test
    fun validityInfoHoldsDateStrings() {
        val validity = ValidityInfo(
            signed = "2024-01-01T00:00:00Z",
            validFrom = "2024-01-01T00:00:00Z",
            validUntil = "2025-01-01T00:00:00Z"
        )
        assertThat(validity.validFrom).contains("2024")
    }

    @Test
    fun mobileSecurityObjectHoldsDigests() {
        val mso = MobileSecurityObject(
            version = "1.0",
            digestAlgorithm = "SHA-256",
            valueDigests = mapOf(
                "org.iso.18013.5.1" to mapOf(0 to byteArrayOf(1, 2, 3))
            ),
            deviceKeyInfo = DeviceKeyInfo(deviceKey = byteArrayOf(4, 5, 6)),
            validityInfo = ValidityInfo(
                signed = "2024-01-01T00:00:00Z",
                validFrom = "2024-01-01T00:00:00Z",
                validUntil = "2025-01-01T00:00:00Z"
            )
        )
        assertThat(mso.digestAlgorithm).isEqualTo("SHA-256")
        assertThat(mso.valueDigests).containsKey("org.iso.18013.5.1")
    }

    @Test
    fun issuerSignedHoldsNamespacesAndAuth() {
        val issuerSigned = IssuerSigned(
            nameSpaces = mapOf(
                "org.iso.18013.5.1" to listOf(
                    IssuerSignedItem(0, byteArrayOf(), "family_name", "Smith")
                )
            ),
            issuerAuth = byteArrayOf(0xD2.toByte())
        )
        assertThat(issuerSigned.nameSpaces).hasSize(1)
        assertThat(issuerSigned.issuerAuth).isNotEmpty()
    }
}
