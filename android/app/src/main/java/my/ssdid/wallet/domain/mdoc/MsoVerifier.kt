package my.ssdid.wallet.domain.mdoc

import com.upokecenter.cbor.CBORObject
import java.security.MessageDigest
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException

object MsoVerifier {

    fun verifyDigest(
        item: IssuerSignedItem,
        mso: MobileSecurityObject,
        namespace: String
    ): Boolean {
        val expectedDigest = mso.valueDigests[namespace]?.get(item.digestId)
            ?: return false

        // Per ISO 18013-5, the digest is computed over the tag-24-wrapped
        // CBOR encoding of the IssuerSignedItem
        val itemCbor = CBORObject.NewMap()
        itemCbor["digestID"] = CBORObject.FromObject(item.digestId)
        itemCbor["random"] = CBORObject.FromObject(item.random)
        itemCbor["elementIdentifier"] = CBORObject.FromObject(item.elementIdentifier)
        itemCbor["elementValue"] = CborCodec.toCborObject(item.elementValue)
        val itemBytes = itemCbor.EncodeToBytes()

        // Wrap in tag 24 (encoded CBOR data item) before hashing
        val tagged = CBORObject.FromObjectAndTag(itemBytes, 24)
        val taggedBytes = tagged.EncodeToBytes()

        val digest = when (mso.digestAlgorithm) {
            "SHA-256" -> MessageDigest.getInstance("SHA-256").digest(taggedBytes)
            "SHA-384" -> MessageDigest.getInstance("SHA-384").digest(taggedBytes)
            "SHA-512" -> MessageDigest.getInstance("SHA-512").digest(taggedBytes)
            else -> return false
        }

        return digest.contentEquals(expectedDigest)
    }

    fun verifyValidity(mso: MobileSecurityObject): Boolean {
        val now = Instant.now()
        return try {
            val validFrom = parseInstant(mso.validityInfo.validFrom)
            val validUntil = parseInstant(mso.validityInfo.validUntil)
            !now.isBefore(validFrom) && !now.isAfter(validUntil)
        } catch (_: Exception) {
            false
        }
    }

    private fun parseInstant(s: String): Instant =
        try { Instant.parse(s) }
        catch (_: DateTimeParseException) { ZonedDateTime.parse(s).toInstant() }
}
