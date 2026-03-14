package my.ssdid.wallet.domain.oid4vp

import com.google.common.truth.Truth.assertThat
import com.upokecenter.cbor.CBORObject
import my.ssdid.wallet.domain.mdoc.StoredMDoc
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MDocVpTokenBuilderTest {

    private fun buildTestStoredMDoc(): StoredMDoc {
        // Build a minimal IssuerSigned CBOR with test data
        val nameSpaces = CBORObject.NewMap()
        val items = CBORObject.NewArray()

        val item1 = CBORObject.NewMap()
        item1["digestID"] = CBORObject.FromObject(0)
        item1["random"] = CBORObject.FromObject(byteArrayOf(0xA, 0xB))
        item1["elementIdentifier"] = CBORObject.FromObject("family_name")
        item1["elementValue"] = CBORObject.FromObject("Smith")
        items.Add(CBORObject.FromObjectAndTag(item1.EncodeToBytes(), 24))

        val item2 = CBORObject.NewMap()
        item2["digestID"] = CBORObject.FromObject(1)
        item2["random"] = CBORObject.FromObject(byteArrayOf(0xC, 0xD))
        item2["elementIdentifier"] = CBORObject.FromObject("given_name")
        item2["elementValue"] = CBORObject.FromObject("Alice")
        items.Add(CBORObject.FromObjectAndTag(item2.EncodeToBytes(), 24))

        nameSpaces["org.iso.18013.5.1"] = items

        // Minimal COSE_Sign1 for issuerAuth
        val coseSign1 = CBORObject.NewArray()
        coseSign1.Add(CBORObject.FromObject(byteArrayOf()))
        coseSign1.Add(CBORObject.NewMap())
        val mso = CBORObject.NewMap()
        mso["version"] = CBORObject.FromObject("1.0")
        mso["digestAlgorithm"] = CBORObject.FromObject("SHA-256")
        mso["valueDigests"] = CBORObject.NewMap()
        val dki = CBORObject.NewMap()
        dki["deviceKey"] = CBORObject.FromObject(byteArrayOf(1, 2, 3))
        mso["deviceKeyInfo"] = dki
        val vi = CBORObject.NewMap()
        vi["signed"] = CBORObject.FromObject("2024-01-01T00:00:00Z")
        vi["validFrom"] = CBORObject.FromObject("2024-01-01T00:00:00Z")
        vi["validUntil"] = CBORObject.FromObject("2025-01-01T00:00:00Z")
        mso["validityInfo"] = vi
        coseSign1.Add(CBORObject.FromObject(mso.EncodeToBytes()))
        coseSign1.Add(CBORObject.FromObject(byteArrayOf()))

        val issuerSigned = CBORObject.NewMap()
        issuerSigned["nameSpaces"] = nameSpaces
        issuerSigned["issuerAuth"] = CBORObject.FromObjectAndTag(coseSign1, 18)

        return StoredMDoc(
            id = "mdoc-001",
            docType = "org.iso.18013.5.1.mDL",
            issuerSignedCbor = issuerSigned.EncodeToBytes(),
            deviceKeyId = "key-1",
            issuedAt = 1700000000L,
            nameSpaces = mapOf("org.iso.18013.5.1" to listOf("family_name", "given_name"))
        )
    }

    @Test
    fun buildProducesValidDeviceResponse() {
        val mdoc = buildTestStoredMDoc()
        val requested = mapOf("org.iso.18013.5.1" to listOf("family_name"))

        val vpToken = MDocVpTokenBuilder.build(
            storedMDoc = mdoc,
            requestedElements = requested,
            clientId = "verifier.example",
            responseUri = "https://verifier.example/response",
            nonce = "nonce123",
            signer = { data -> byteArrayOf(0xDE.toByte(), 0xAD.toByte()) }
        )

        // Decode base64url and verify structure
        val bytes = android.util.Base64.decode(vpToken, android.util.Base64.URL_SAFE)
        val response = CBORObject.DecodeFromBytes(bytes)

        assertThat(response["version"].AsString()).isEqualTo("1.0")
        assertThat(response["status"].AsInt32Value()).isEqualTo(0)
        assertThat(response["documents"].size()).isEqualTo(1)

        val doc = response["documents"][0]
        assertThat(doc["docType"].AsString()).isEqualTo("org.iso.18013.5.1.mDL")
        assertThat(doc["issuerSigned"]).isNotNull()
        assertThat(doc["deviceSigned"]).isNotNull()
    }

    @Test
    fun buildAppliesSelectiveDisclosure() {
        val mdoc = buildTestStoredMDoc()
        val requested = mapOf("org.iso.18013.5.1" to listOf("family_name"))

        val vpToken = MDocVpTokenBuilder.build(
            storedMDoc = mdoc,
            requestedElements = requested,
            clientId = "verifier.example",
            responseUri = "https://verifier.example/response",
            nonce = "nonce123",
            signer = { byteArrayOf(0xDE.toByte()) }
        )

        val bytes = android.util.Base64.decode(vpToken, android.util.Base64.URL_SAFE)
        val response = CBORObject.DecodeFromBytes(bytes)
        val doc = response["documents"][0]
        val ns = doc["issuerSigned"]["nameSpaces"]["org.iso.18013.5.1"]

        // Should only have 1 item (family_name), not 2
        assertThat(ns.size()).isEqualTo(1)
    }
}
