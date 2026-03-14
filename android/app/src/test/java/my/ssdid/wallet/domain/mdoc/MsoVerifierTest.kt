package my.ssdid.wallet.domain.mdoc

import com.google.common.truth.Truth.assertThat
import com.upokecenter.cbor.CBORObject
import org.junit.Test
import java.security.MessageDigest

class MsoVerifierTest {

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    /**
     * Build the CBOR encoding of an IssuerSignedItem and wrap it in tag 24,
     * matching how ISO 18013-5 computes the digest.
     */
    private fun buildTaggedItemBytes(digestId: Int, elementId: String, value: String): ByteArray {
        val item = CBORObject.NewMap()
        item["digestID"] = CBORObject.FromObject(digestId)
        item["random"] = CBORObject.FromObject(byteArrayOf(0xA, 0xB))
        item["elementIdentifier"] = CBORObject.FromObject(elementId)
        item["elementValue"] = CBORObject.FromObject(value)
        val itemBytes = item.EncodeToBytes()
        // Wrap in CBOR tag 24 (encoded CBOR data item)
        val tagged = CBORObject.FromObjectAndTag(itemBytes, 24)
        return tagged.EncodeToBytes()
    }

    @Test
    fun verifyDigestPassesForValidItem() {
        val taggedBytes = buildTaggedItemBytes(0, "family_name", "Smith")
        val digest = sha256(taggedBytes)

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
        val taggedBytes = buildTaggedItemBytes(0, "family_name", "Smith")
        val digest = sha256(taggedBytes)

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

    // --- G2: SHA-384/512, unknown algo, missing namespace, future validFrom, malformed dates ---

    @Test
    fun verifyDigestPassesForSha384() {
        val taggedBytes = buildTaggedItemBytes(0, "family_name", "Smith")
        val digest = MessageDigest.getInstance("SHA-384").digest(taggedBytes)

        val item = IssuerSignedItem(0, byteArrayOf(0xA, 0xB), "family_name", "Smith")
        val mso = MobileSecurityObject(
            version = "1.0",
            digestAlgorithm = "SHA-384",
            valueDigests = mapOf("ns1" to mapOf(0 to digest)),
            deviceKeyInfo = DeviceKeyInfo(byteArrayOf()),
            validityInfo = ValidityInfo("2024-01-01T00:00:00Z", "2024-01-01T00:00:00Z", "2099-01-01T00:00:00Z")
        )

        assertThat(MsoVerifier.verifyDigest(item, mso, "ns1")).isTrue()
    }

    @Test
    fun verifyDigestPassesForSha512() {
        val taggedBytes = buildTaggedItemBytes(0, "family_name", "Smith")
        val digest = MessageDigest.getInstance("SHA-512").digest(taggedBytes)

        val item = IssuerSignedItem(0, byteArrayOf(0xA, 0xB), "family_name", "Smith")
        val mso = MobileSecurityObject(
            version = "1.0",
            digestAlgorithm = "SHA-512",
            valueDigests = mapOf("ns1" to mapOf(0 to digest)),
            deviceKeyInfo = DeviceKeyInfo(byteArrayOf()),
            validityInfo = ValidityInfo("2024-01-01T00:00:00Z", "2024-01-01T00:00:00Z", "2099-01-01T00:00:00Z")
        )

        assertThat(MsoVerifier.verifyDigest(item, mso, "ns1")).isTrue()
    }

    @Test
    fun verifyDigestReturnsFalseForUnknownAlgorithm() {
        val item = IssuerSignedItem(0, byteArrayOf(0xA, 0xB), "family_name", "Smith")
        val mso = MobileSecurityObject(
            version = "1.0",
            digestAlgorithm = "SHA-999",
            valueDigests = mapOf("ns1" to mapOf(0 to byteArrayOf(1, 2, 3))),
            deviceKeyInfo = DeviceKeyInfo(byteArrayOf()),
            validityInfo = ValidityInfo("2024-01-01T00:00:00Z", "2024-01-01T00:00:00Z", "2099-01-01T00:00:00Z")
        )

        assertThat(MsoVerifier.verifyDigest(item, mso, "ns1")).isFalse()
    }

    @Test
    fun verifyDigestReturnsFalseForMissingNamespace() {
        val item = IssuerSignedItem(0, byteArrayOf(0xA, 0xB), "family_name", "Smith")
        val mso = MobileSecurityObject(
            version = "1.0",
            digestAlgorithm = "SHA-256",
            valueDigests = mapOf("other_ns" to mapOf(0 to byteArrayOf(1, 2, 3))),
            deviceKeyInfo = DeviceKeyInfo(byteArrayOf()),
            validityInfo = ValidityInfo("2024-01-01T00:00:00Z", "2024-01-01T00:00:00Z", "2099-01-01T00:00:00Z")
        )

        assertThat(MsoVerifier.verifyDigest(item, mso, "ns1")).isFalse()
    }

    @Test
    fun verifyValidityFailsForFutureValidFrom() {
        val mso = MobileSecurityObject(
            version = "1.0",
            digestAlgorithm = "SHA-256",
            valueDigests = emptyMap(),
            deviceKeyInfo = DeviceKeyInfo(byteArrayOf()),
            validityInfo = ValidityInfo("2099-01-01T00:00:00Z", "2099-01-01T00:00:00Z", "2100-01-01T00:00:00Z")
        )
        assertThat(MsoVerifier.verifyValidity(mso)).isFalse()
    }

    @Test
    fun verifyValidityReturnsFalseForMalformedDates() {
        val mso = MobileSecurityObject(
            version = "1.0",
            digestAlgorithm = "SHA-256",
            valueDigests = emptyMap(),
            deviceKeyInfo = DeviceKeyInfo(byteArrayOf()),
            validityInfo = ValidityInfo("2024-01-01T00:00:00Z", "not-a-date", "also-not-a-date")
        )
        assertThat(MsoVerifier.verifyValidity(mso)).isFalse()
    }

    @Test
    fun verifyValidityHandlesZonedDateTimeFormat() {
        val mso = MobileSecurityObject(
            version = "1.0",
            digestAlgorithm = "SHA-256",
            valueDigests = emptyMap(),
            deviceKeyInfo = DeviceKeyInfo(byteArrayOf()),
            validityInfo = ValidityInfo("2024-01-01T00:00:00Z", "2024-01-01T00:00:00+08:00", "2099-01-01T00:00:00+08:00")
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
