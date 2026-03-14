package my.ssdid.wallet.domain.mdoc

import com.google.common.truth.Truth.assertThat
import com.upokecenter.cbor.CBORObject
import org.junit.Test

class MsoParserTest {

    private fun buildTestIssuerSignedItem(
        digestId: Int,
        elementId: String,
        elementValue: String
    ): CBORObject {
        val item = CBORObject.NewMap()
        item["digestID"] = CBORObject.FromObject(digestId)
        item["random"] = CBORObject.FromObject(byteArrayOf(0xA, 0xB, 0xC))
        item["elementIdentifier"] = CBORObject.FromObject(elementId)
        item["elementValue"] = CBORObject.FromObject(elementValue)
        return item
    }

    private fun buildTestIssuerSigned(): ByteArray {
        val nameSpaces = CBORObject.NewMap()
        val items = CBORObject.NewArray()
        items.Add(CBORObject.FromObjectAndTag(
            buildTestIssuerSignedItem(0, "family_name", "Smith").EncodeToBytes(), 24
        ))
        items.Add(CBORObject.FromObjectAndTag(
            buildTestIssuerSignedItem(1, "given_name", "Alice").EncodeToBytes(), 24
        ))
        nameSpaces["org.iso.18013.5.1"] = items

        val coseSign1 = CBORObject.NewArray()
        coseSign1.Add(CBORObject.FromObject(byteArrayOf()))
        coseSign1.Add(CBORObject.NewMap())
        val mso = CBORObject.NewMap()
        mso["version"] = CBORObject.FromObject("1.0")
        mso["digestAlgorithm"] = CBORObject.FromObject("SHA-256")
        val digests = CBORObject.NewMap()
        val nsDigests = CBORObject.NewMap()
        nsDigests[CBORObject.FromObject(0)] = CBORObject.FromObject(byteArrayOf(1, 2, 3))
        nsDigests[CBORObject.FromObject(1)] = CBORObject.FromObject(byteArrayOf(4, 5, 6))
        digests["org.iso.18013.5.1"] = nsDigests
        mso["valueDigests"] = digests
        val deviceKeyInfo = CBORObject.NewMap()
        deviceKeyInfo["deviceKey"] = CBORObject.FromObject(byteArrayOf(7, 8, 9))
        mso["deviceKeyInfo"] = deviceKeyInfo
        val validityInfo = CBORObject.NewMap()
        validityInfo["signed"] = CBORObject.FromObject("2024-01-01T00:00:00Z")
        validityInfo["validFrom"] = CBORObject.FromObject("2024-01-01T00:00:00Z")
        validityInfo["validUntil"] = CBORObject.FromObject("2025-01-01T00:00:00Z")
        mso["validityInfo"] = validityInfo
        coseSign1.Add(CBORObject.FromObject(mso.EncodeToBytes()))
        coseSign1.Add(CBORObject.FromObject(byteArrayOf()))

        val issuerSigned = CBORObject.NewMap()
        issuerSigned["nameSpaces"] = nameSpaces
        issuerSigned["issuerAuth"] = CBORObject.FromObjectAndTag(coseSign1, 18)

        return issuerSigned.EncodeToBytes()
    }

    @Test
    fun parseIssuerSignedExtractsNamespaces() {
        val cbor = buildTestIssuerSigned()
        val parsed = MsoParser.parseIssuerSigned(cbor)
        assertThat(parsed.nameSpaces).containsKey("org.iso.18013.5.1")
        assertThat(parsed.nameSpaces["org.iso.18013.5.1"]).hasSize(2)
    }

    @Test
    fun parseIssuerSignedExtractsElementIdentifiers() {
        val cbor = buildTestIssuerSigned()
        val parsed = MsoParser.parseIssuerSigned(cbor)
        val items = parsed.nameSpaces["org.iso.18013.5.1"]!!
        assertThat(items.map { it.elementIdentifier }).containsExactly("family_name", "given_name")
    }

    @Test
    fun parseMsoFromIssuerAuth() {
        val cbor = buildTestIssuerSigned()
        val parsed = MsoParser.parseIssuerSigned(cbor)
        val mso = MsoParser.parseMso(parsed.issuerAuth)
        assertThat(mso.version).isEqualTo("1.0")
        assertThat(mso.digestAlgorithm).isEqualTo("SHA-256")
        assertThat(mso.valueDigests).containsKey("org.iso.18013.5.1")
    }

    @Test
    fun parseMsoExtractsValidityInfo() {
        val cbor = buildTestIssuerSigned()
        val parsed = MsoParser.parseIssuerSigned(cbor)
        val mso = MsoParser.parseMso(parsed.issuerAuth)
        assertThat(mso.validityInfo.validFrom).isEqualTo("2024-01-01T00:00:00Z")
        assertThat(mso.validityInfo.validUntil).isEqualTo("2025-01-01T00:00:00Z")
    }

    // --- Error path tests for null-safety guards ---

    @Test(expected = IllegalArgumentException::class)
    fun `parseIssuerSigned throws on missing nameSpaces`() {
        val cbor = CBORObject.NewMap()
        cbor["issuerAuth"] = CBORObject.FromObject(byteArrayOf())
        MsoParser.parseIssuerSigned(cbor.EncodeToBytes())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parseIssuerSigned throws on missing issuerAuth`() {
        val cbor = CBORObject.NewMap()
        cbor["nameSpaces"] = CBORObject.NewMap()
        MsoParser.parseIssuerSigned(cbor.EncodeToBytes())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parseMso throws on missing version`() {
        val mso = CBORObject.NewMap()
        mso["digestAlgorithm"] = CBORObject.FromObject("SHA-256")
        mso["valueDigests"] = CBORObject.NewMap()
        val deviceKeyInfo = CBORObject.NewMap()
        deviceKeyInfo["deviceKey"] = CBORObject.FromObject(byteArrayOf())
        mso["deviceKeyInfo"] = deviceKeyInfo
        val validityInfo = CBORObject.NewMap()
        validityInfo["signed"] = CBORObject.FromObject("2024-01-01T00:00:00Z")
        validityInfo["validFrom"] = CBORObject.FromObject("2024-01-01T00:00:00Z")
        validityInfo["validUntil"] = CBORObject.FromObject("2025-01-01T00:00:00Z")
        mso["validityInfo"] = validityInfo

        val coseSign1 = CBORObject.NewArray()
        coseSign1.Add(CBORObject.FromObject(byteArrayOf()))
        coseSign1.Add(CBORObject.NewMap())
        coseSign1.Add(CBORObject.FromObject(mso.EncodeToBytes()))
        coseSign1.Add(CBORObject.FromObject(byteArrayOf()))

        MsoParser.parseMso(coseSign1.EncodeToBytes())
    }

    // --- G4: validityInfo sub-fields, valueDigests throws ---

    @Test(expected = IllegalArgumentException::class)
    fun `parseMso throws on missing validityInfo`() {
        val mso = CBORObject.NewMap()
        mso["version"] = CBORObject.FromObject("1.0")
        mso["digestAlgorithm"] = CBORObject.FromObject("SHA-256")
        mso["valueDigests"] = CBORObject.NewMap()
        val deviceKeyInfo = CBORObject.NewMap()
        deviceKeyInfo["deviceKey"] = CBORObject.FromObject(byteArrayOf())
        mso["deviceKeyInfo"] = deviceKeyInfo
        // validityInfo intentionally omitted

        val coseSign1 = CBORObject.NewArray()
        coseSign1.Add(CBORObject.FromObject(byteArrayOf()))
        coseSign1.Add(CBORObject.NewMap())
        coseSign1.Add(CBORObject.FromObject(mso.EncodeToBytes()))
        coseSign1.Add(CBORObject.FromObject(byteArrayOf()))

        MsoParser.parseMso(coseSign1.EncodeToBytes())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parseMso throws on missing digestAlgorithm`() {
        val mso = CBORObject.NewMap()
        mso["version"] = CBORObject.FromObject("1.0")
        // digestAlgorithm intentionally omitted
        mso["valueDigests"] = CBORObject.NewMap()
        val deviceKeyInfo = CBORObject.NewMap()
        deviceKeyInfo["deviceKey"] = CBORObject.FromObject(byteArrayOf())
        mso["deviceKeyInfo"] = deviceKeyInfo
        val validityInfo = CBORObject.NewMap()
        validityInfo["signed"] = CBORObject.FromObject("2024-01-01T00:00:00Z")
        validityInfo["validFrom"] = CBORObject.FromObject("2024-01-01T00:00:00Z")
        validityInfo["validUntil"] = CBORObject.FromObject("2025-01-01T00:00:00Z")
        mso["validityInfo"] = validityInfo

        val coseSign1 = CBORObject.NewArray()
        coseSign1.Add(CBORObject.FromObject(byteArrayOf()))
        coseSign1.Add(CBORObject.NewMap())
        coseSign1.Add(CBORObject.FromObject(mso.EncodeToBytes()))
        coseSign1.Add(CBORObject.FromObject(byteArrayOf()))

        MsoParser.parseMso(coseSign1.EncodeToBytes())
    }

    @Test
    fun parseIssuerSignedExtractsIssuerAuth() {
        val cbor = buildTestIssuerSigned()
        val parsed = MsoParser.parseIssuerSigned(cbor)
        assertThat(parsed.issuerAuth).isNotEmpty()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parseMso throws on missing deviceKeyInfo`() {
        val mso = CBORObject.NewMap()
        mso["version"] = CBORObject.FromObject("1.0")
        mso["digestAlgorithm"] = CBORObject.FromObject("SHA-256")
        mso["valueDigests"] = CBORObject.NewMap()
        val validityInfo = CBORObject.NewMap()
        validityInfo["signed"] = CBORObject.FromObject("2024-01-01T00:00:00Z")
        validityInfo["validFrom"] = CBORObject.FromObject("2024-01-01T00:00:00Z")
        validityInfo["validUntil"] = CBORObject.FromObject("2025-01-01T00:00:00Z")
        mso["validityInfo"] = validityInfo

        val coseSign1 = CBORObject.NewArray()
        coseSign1.Add(CBORObject.FromObject(byteArrayOf()))
        coseSign1.Add(CBORObject.NewMap())
        coseSign1.Add(CBORObject.FromObject(mso.EncodeToBytes()))
        coseSign1.Add(CBORObject.FromObject(byteArrayOf()))

        MsoParser.parseMso(coseSign1.EncodeToBytes())
    }
}
