package my.ssdid.wallet.domain.mdoc

import com.google.common.truth.Truth.assertThat
import com.upokecenter.cbor.CBORObject
import org.junit.Test
import java.security.MessageDigest

class MsoVerifierTest {

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    private fun buildItemCbor(digestId: Int, elementId: String, value: String): ByteArray {
        val item = CBORObject.NewMap()
        item["digestID"] = CBORObject.FromObject(digestId)
        item["random"] = CBORObject.FromObject(byteArrayOf(0xA, 0xB))
        item["elementIdentifier"] = CBORObject.FromObject(elementId)
        item["elementValue"] = CBORObject.FromObject(value)
        return item.EncodeToBytes()
    }

    @Test
    fun verifyDigestPassesForValidItem() {
        val itemCbor = buildItemCbor(0, "family_name", "Smith")
        val digest = sha256(itemCbor)

        val item = IssuerSignedItem(0, byteArrayOf(0xA, 0xB), "family_name", "Smith")
        val mso = MobileSecurityObject(
            version = "1.0",
            digestAlgorithm = "SHA-256",
            valueDigests = mapOf("ns1" to mapOf(0 to digest)),
            deviceKeyInfo = DeviceKeyInfo(byteArrayOf()),
            validityInfo = ValidityInfo("2024-01-01T00:00:00Z", "2024-01-01T00:00:00Z", "2099-01-01T00:00:00Z")
        )

        assertThat(MsoVerifier.verifyDigest(item, mso, "ns1")).isTrue()
    }

    @Test
    fun verifyDigestFailsForTamperedValue() {
        val itemCbor = buildItemCbor(0, "family_name", "Smith")
        val digest = sha256(itemCbor)

        val item = IssuerSignedItem(0, byteArrayOf(0xA, 0xB), "family_name", "Jones")
        val mso = MobileSecurityObject(
            version = "1.0",
            digestAlgorithm = "SHA-256",
            valueDigests = mapOf("ns1" to mapOf(0 to digest)),
            deviceKeyInfo = DeviceKeyInfo(byteArrayOf()),
            validityInfo = ValidityInfo("2024-01-01T00:00:00Z", "2024-01-01T00:00:00Z", "2099-01-01T00:00:00Z")
        )

        assertThat(MsoVerifier.verifyDigest(item, mso, "ns1")).isFalse()
    }

    @Test
    fun verifyValidityPassesForCurrentDate() {
        val mso = MobileSecurityObject(
            version = "1.0",
            digestAlgorithm = "SHA-256",
            valueDigests = emptyMap(),
            deviceKeyInfo = DeviceKeyInfo(byteArrayOf()),
            validityInfo = ValidityInfo("2024-01-01T00:00:00Z", "2024-01-01T00:00:00Z", "2099-01-01T00:00:00Z")
        )
        assertThat(MsoVerifier.verifyValidity(mso)).isTrue()
    }

    @Test
    fun verifyValidityFailsForExpiredMso() {
        val mso = MobileSecurityObject(
            version = "1.0",
            digestAlgorithm = "SHA-256",
            valueDigests = emptyMap(),
            deviceKeyInfo = DeviceKeyInfo(byteArrayOf()),
            validityInfo = ValidityInfo("2020-01-01T00:00:00Z", "2020-01-01T00:00:00Z", "2021-01-01T00:00:00Z")
        )
        assertThat(MsoVerifier.verifyValidity(mso)).isFalse()
    }
}
