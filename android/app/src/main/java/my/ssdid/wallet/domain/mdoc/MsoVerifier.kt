package my.ssdid.wallet.domain.mdoc

import com.upokecenter.cbor.CBORObject
import java.security.MessageDigest
import java.time.Instant

object MsoVerifier {

    fun verifyDigest(
        item: IssuerSignedItem,
        mso: MobileSecurityObject,
        namespace: String
    ): Boolean {
        val expectedDigest = mso.valueDigests[namespace]?.get(item.digestId)
            ?: return false

        val itemCbor = CBORObject.NewMap()
        itemCbor["digestID"] = CBORObject.FromObject(item.digestId)
        itemCbor["random"] = CBORObject.FromObject(item.random)
        itemCbor["elementIdentifier"] = CBORObject.FromObject(item.elementIdentifier)
        itemCbor["elementValue"] = CborCodec.toCborObject(item.elementValue)
        val itemBytes = itemCbor.EncodeToBytes()

        val digest = when (mso.digestAlgorithm) {
            "SHA-256" -> MessageDigest.getInstance("SHA-256").digest(itemBytes)
            "SHA-384" -> MessageDigest.getInstance("SHA-384").digest(itemBytes)
            "SHA-512" -> MessageDigest.getInstance("SHA-512").digest(itemBytes)
            else -> return false
        }

        return digest.contentEquals(expectedDigest)
    }

    fun verifyValidity(mso: MobileSecurityObject): Boolean {
        val now = Instant.now()
        val validFrom = Instant.parse(mso.validityInfo.validFrom)
        val validUntil = Instant.parse(mso.validityInfo.validUntil)
        return !now.isBefore(validFrom) && !now.isAfter(validUntil)
    }
}
