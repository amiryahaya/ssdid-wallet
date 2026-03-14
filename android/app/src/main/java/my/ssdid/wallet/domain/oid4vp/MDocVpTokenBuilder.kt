package my.ssdid.wallet.domain.oid4vp

import android.util.Base64
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

        val deviceAuthBytes = deviceAuth.EncodeToBytes()

        // Sign DeviceAuthentication
        val signature = signer(deviceAuthBytes)

        // Build COSE_Sign1 for deviceSignature
        val coseSign1 = CBORObject.NewArray()
        coseSign1.Add(CBORObject.FromObject(byteArrayOf())) // protected
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
        return Base64.encodeToString(responseBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
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
