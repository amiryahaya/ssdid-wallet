package my.ssdid.wallet.domain.oid4vp

import com.upokecenter.cbor.CBORObject
import my.ssdid.wallet.domain.mdoc.CborCodec
import my.ssdid.wallet.domain.mdoc.IssuerSigned
import my.ssdid.wallet.domain.mdoc.MDocPresenter
import my.ssdid.wallet.domain.mdoc.MsoParser
import my.ssdid.wallet.domain.mdoc.StoredMDoc

/**
 * Builds a CBOR DeviceResponse for mdoc presentation per ISO 18013-5.
 *
 * DeviceResponse = {
 *   "version": "1.0",
 *   "documents": [{ docType, issuerSigned, deviceSigned }],
 *   "status": 0
 * }
 */
object MDocVpTokenBuilder {

    fun build(
        storedMDoc: StoredMDoc,
        requestedElements: Map<String, List<String>>,
        clientId: String,
        responseUri: String,
        nonce: String,
        signer: (ByteArray) -> ByteArray
    ): String {
        // Parse the stored IssuerSigned CBOR
        val issuerSigned = MsoParser.parseIssuerSigned(storedMDoc.issuerSignedCbor)

        // Apply selective disclosure
        val presented = MDocPresenter.present(issuerSigned, requestedElements)

        // Build SessionTranscript
        val sessionTranscript = SessionTranscript.build(clientId, responseUri, nonce)

        // Build DeviceAuthentication = ["DeviceAuthentication", SessionTranscript, docType, {}]
        val deviceAuth = CBORObject.NewArray()
        deviceAuth.Add(CBORObject.FromObject("DeviceAuthentication"))
        deviceAuth.Add(CBORObject.DecodeFromBytes(sessionTranscript))
        deviceAuth.Add(CBORObject.FromObject(storedMDoc.docType))
        deviceAuth.Add(CBORObject.NewMap()) // empty DeviceNameSpaces

        // Wrap DeviceAuthentication in tag 24 (encoded CBOR data item) per ISO 18013-5 §9.1.3
        val deviceAuthTagged = CBORObject.FromObjectAndTag(deviceAuth.EncodeToBytes(), 24)

        // Build COSE Sig_structure per RFC 9052 §4.4 and ISO 18013-5 §9.1.3:
        // Sig_structure = ["Signature1", body_protected, external_aad, payload]
        // - external_aad is empty (SessionTranscript is already in DeviceAuthentication)
        // - payload is the tag-24-wrapped DeviceAuthentication bstr
        val protectedHeaders = byteArrayOf() // empty protected headers
        val sigStructure = CBORObject.NewArray()
        sigStructure.Add(CBORObject.FromObject("Signature1"))
        sigStructure.Add(CBORObject.FromObject(protectedHeaders))
        sigStructure.Add(CBORObject.FromObject(byteArrayOf())) // external_aad: empty
        sigStructure.Add(CBORObject.FromObject(deviceAuthTagged.EncodeToBytes())) // payload: #6.24(DeviceAuthentication)

        // Sign the Sig_structure
        val signature = signer(sigStructure.EncodeToBytes())

        // Build COSE_Sign1 for deviceSignature
        val coseSign1 = CBORObject.NewArray()
        coseSign1.Add(CBORObject.FromObject(protectedHeaders)) // protected
        coseSign1.Add(CBORObject.NewMap()) // unprotected
        coseSign1.Add(CBORObject.Null) // payload is detached
        coseSign1.Add(CBORObject.FromObject(signature))

        // Build DeviceSigned
        val deviceSigned = CBORObject.NewMap()
        deviceSigned["nameSpaces"] = CBORObject.FromObjectAndTag(
            CBORObject.NewMap().EncodeToBytes(), 24
        )
        val deviceAuthMap = CBORObject.NewMap()
        deviceAuthMap["deviceSignature"] = CBORObject.FromObjectAndTag(coseSign1, 18)
        deviceSigned["deviceAuth"] = deviceAuthMap

        // Build the IssuerSigned CBOR for the response
        val issuerSignedCbor = encodeIssuerSigned(presented)

        // Build Document
        val document = CBORObject.NewMap()
        document["docType"] = CBORObject.FromObject(storedMDoc.docType)
        document["issuerSigned"] = issuerSignedCbor
        document["deviceSigned"] = deviceSigned

        // Build DeviceResponse
        val deviceResponse = CBORObject.NewMap()
        deviceResponse["version"] = CBORObject.FromObject("1.0")
        val documents = CBORObject.NewArray()
        documents.Add(document)
        deviceResponse["documents"] = documents
        deviceResponse["status"] = CBORObject.FromObject(0)

        // Base64url encode the CBOR bytes
        val responseBytes = deviceResponse.EncodeToBytes()
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(responseBytes)
    }

    private fun encodeIssuerSigned(issuerSigned: IssuerSigned): CBORObject {
        val cbor = CBORObject.NewMap()
        val nameSpaces = CBORObject.NewMap()
        for ((ns, items) in issuerSigned.nameSpaces) {
            val array = CBORObject.NewArray()
            for (item in items) {
                val itemCbor = CBORObject.NewMap()
                itemCbor["digestID"] = CBORObject.FromObject(item.digestId)
                itemCbor["random"] = CBORObject.FromObject(item.random)
                itemCbor["elementIdentifier"] = CBORObject.FromObject(item.elementIdentifier)
                itemCbor["elementValue"] = CborCodec.toCborObject(item.elementValue)
                array.Add(CBORObject.FromObjectAndTag(itemCbor.EncodeToBytes(), 24))
            }
            nameSpaces[ns] = array
        }
        cbor["nameSpaces"] = nameSpaces
        cbor["issuerAuth"] = CBORObject.DecodeFromBytes(issuerSigned.issuerAuth)
        return cbor
    }
}
