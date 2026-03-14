package my.ssdid.wallet.domain.mdoc

import com.upokecenter.cbor.CBORObject

object MsoParser {

    fun parseIssuerSigned(cborBytes: ByteArray): IssuerSigned {
        val cbor = CBORObject.DecodeFromBytes(cborBytes)

        val nameSpacesMap = mutableMapOf<String, List<IssuerSignedItem>>()
        val nameSpacesCbor = cbor["nameSpaces"]
            ?: throw IllegalArgumentException("Missing 'nameSpaces' in IssuerSigned CBOR")

        for (nsKey in nameSpacesCbor.keys) {
            val namespace = nsKey.AsString()
            val itemsArray = nameSpacesCbor[nsKey]
            val items = mutableListOf<IssuerSignedItem>()
            for (i in 0 until itemsArray.size()) {
                val taggedItem = itemsArray[i]
                // Handle both tag-24-wrapped and unwrapped items
                val itemCbor = if (taggedItem.isTagged && taggedItem.mostInnerTag.ToInt32Checked() == 24) {
                    CBORObject.DecodeFromBytes(taggedItem.GetByteString())
                } else {
                    taggedItem
                }
                items.add(parseIssuerSignedItem(itemCbor))
            }
            nameSpacesMap[namespace] = items
        }

        val issuerAuth = cbor["issuerAuth"]
            ?: throw IllegalArgumentException("Missing 'issuerAuth' in IssuerSigned CBOR")

        return IssuerSigned(
            nameSpaces = nameSpacesMap,
            issuerAuth = issuerAuth.EncodeToBytes()
        )
    }

    fun parseMso(issuerAuthBytes: ByteArray): MobileSecurityObject {
        val coseSign1 = CBORObject.DecodeFromBytes(issuerAuthBytes)
        val inner = if (coseSign1.isTagged) coseSign1.UntagOne() else coseSign1

        val payload = inner[2] ?: throw IllegalArgumentException("Missing payload in COSE_Sign1")
        // MSO payload may be wrapped in tag 24; unwrap if present
        val payloadBytes = if (payload.isTagged) {
            payload.UntagOne().GetByteString()
        } else {
            payload.GetByteString()
        }
        val mso = CBORObject.DecodeFromBytes(payloadBytes)

        val version = mso["version"]?.AsString()
            ?: throw IllegalArgumentException("Missing 'version' in MSO")
        val digestAlgorithm = mso["digestAlgorithm"]?.AsString()
            ?: throw IllegalArgumentException("Missing 'digestAlgorithm' in MSO")

        val valueDigests = mutableMapOf<String, Map<Int, ByteArray>>()
        val digestsCbor = mso["valueDigests"]
            ?: throw IllegalArgumentException("Missing 'valueDigests' in MSO")

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

        val deviceKeyInfoCbor = mso["deviceKeyInfo"]
            ?: throw IllegalArgumentException("Missing 'deviceKeyInfo' in MSO")
        val deviceKeyCbor = deviceKeyInfoCbor["deviceKey"]
            ?: throw IllegalArgumentException("Missing 'deviceKey' in MSO deviceKeyInfo")
        val deviceKeyInfo = DeviceKeyInfo(
            deviceKey = deviceKeyCbor.GetByteString()
        )

        val validityInfoCbor = mso["validityInfo"]
            ?: throw IllegalArgumentException("Missing 'validityInfo' in MSO")
        val validityInfo = ValidityInfo(
            signed = validityInfoCbor["signed"]?.AsString()
                ?: throw IllegalArgumentException("Missing 'signed' in validityInfo"),
            validFrom = validityInfoCbor["validFrom"]?.AsString()
                ?: throw IllegalArgumentException("Missing 'validFrom' in validityInfo"),
            validUntil = validityInfoCbor["validUntil"]?.AsString()
                ?: throw IllegalArgumentException("Missing 'validUntil' in validityInfo")
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
            digestId = (cbor["digestID"] ?: throw IllegalArgumentException("Missing 'digestID'"))
                .AsInt32Value(),
            random = (cbor["random"] ?: throw IllegalArgumentException("Missing 'random'"))
                .GetByteString(),
            elementIdentifier = (cbor["elementIdentifier"]
                ?: throw IllegalArgumentException("Missing 'elementIdentifier'")).AsString(),
            elementValue = CborCodec.fromCborObject(
                cbor["elementValue"] ?: throw IllegalArgumentException("Missing 'elementValue'")
            )
        )
    }
}
