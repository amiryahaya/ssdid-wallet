package my.ssdid.wallet.domain.mdoc

import com.upokecenter.cbor.CBORObject

object MsoParser {

    fun parseIssuerSigned(cborBytes: ByteArray): IssuerSigned {
        val cbor = CBORObject.DecodeFromBytes(cborBytes)

        val nameSpacesMap = mutableMapOf<String, List<IssuerSignedItem>>()
        val nameSpacesCbor = cbor["nameSpaces"]
        for (nsKey in nameSpacesCbor.keys) {
            val namespace = nsKey.AsString()
            val itemsArray = nameSpacesCbor[nsKey]
            val items = mutableListOf<IssuerSignedItem>()
            for (i in 0 until itemsArray.size()) {
                val taggedItem = itemsArray[i]
                val itemBytes = taggedItem.GetByteString()
                val itemCbor = CBORObject.DecodeFromBytes(itemBytes)
                items.add(parseIssuerSignedItem(itemCbor))
            }
            nameSpacesMap[namespace] = items
        }

        val issuerAuth = cbor["issuerAuth"]
        return IssuerSigned(
            nameSpaces = nameSpacesMap,
            issuerAuth = issuerAuth.EncodeToBytes()
        )
    }

    fun parseMso(issuerAuthBytes: ByteArray): MobileSecurityObject {
        val coseSign1 = CBORObject.DecodeFromBytes(issuerAuthBytes)
        val inner = if (coseSign1.isTagged) coseSign1.UntagOne() else coseSign1
        val payloadBytes = inner[2].GetByteString()
        val mso = CBORObject.DecodeFromBytes(payloadBytes)

        val version = mso["version"].AsString()
        val digestAlgorithm = mso["digestAlgorithm"].AsString()

        val valueDigests = mutableMapOf<String, Map<Int, ByteArray>>()
        val digestsCbor = mso["valueDigests"]
        for (nsKey in digestsCbor.keys) {
            val namespace = nsKey.AsString()
            val nsDigests = mutableMapOf<Int, ByteArray>()
            val nsDigestsCbor = digestsCbor[nsKey]
            for (dKey in nsDigestsCbor.keys) {
                val digestId = dKey.AsInt32Value()
                nsDigests[digestId] = nsDigestsCbor[dKey].GetByteString()
            }
            valueDigests[namespace] = nsDigests
        }

        val deviceKeyInfo = DeviceKeyInfo(
            deviceKey = mso["deviceKeyInfo"]["deviceKey"].GetByteString()
        )

        val validityInfoCbor = mso["validityInfo"]
        val validityInfo = ValidityInfo(
            signed = validityInfoCbor["signed"].AsString(),
            validFrom = validityInfoCbor["validFrom"].AsString(),
            validUntil = validityInfoCbor["validUntil"].AsString()
        )

        return MobileSecurityObject(
            version = version,
            digestAlgorithm = digestAlgorithm,
            valueDigests = valueDigests,
            deviceKeyInfo = deviceKeyInfo,
            validityInfo = validityInfo
        )
    }

    private fun parseIssuerSignedItem(cbor: CBORObject): IssuerSignedItem {
        return IssuerSignedItem(
            digestId = cbor["digestID"].AsInt32Value(),
            random = cbor["random"].GetByteString(),
            elementIdentifier = cbor["elementIdentifier"].AsString(),
            elementValue = CborCodec.fromCborObject(cbor["elementValue"])
        )
    }
}
