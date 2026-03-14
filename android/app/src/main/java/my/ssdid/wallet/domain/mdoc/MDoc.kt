package my.ssdid.wallet.domain.mdoc

data class StoredMDoc(
    val id: String,
    val docType: String,
    val issuerSignedCbor: ByteArray,
    val deviceKeyId: String,
    val issuedAt: Long,
    val expiresAt: Long? = null,
    val nameSpaces: Map<String, List<String>> = emptyMap()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StoredMDoc) return false
        return id == other.id
    }

    override fun hashCode() = id.hashCode()
}

data class IssuerSigned(
    val nameSpaces: Map<String, List<IssuerSignedItem>>,
    val issuerAuth: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IssuerSigned) return false
        return nameSpaces == other.nameSpaces && issuerAuth.contentEquals(other.issuerAuth)
    }

    override fun hashCode() = nameSpaces.hashCode()
}

data class IssuerSignedItem(
    val digestId: Int,
    val random: ByteArray,
    val elementIdentifier: String,
    val elementValue: Any
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IssuerSignedItem) return false
        return digestId == other.digestId && elementIdentifier == other.elementIdentifier
    }

    override fun hashCode() = 31 * digestId + elementIdentifier.hashCode()
}

data class MobileSecurityObject(
    val version: String,
    val digestAlgorithm: String,
    val valueDigests: Map<String, Map<Int, ByteArray>>,
    val deviceKeyInfo: DeviceKeyInfo,
    val validityInfo: ValidityInfo
)

data class DeviceKeyInfo(
    val deviceKey: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DeviceKeyInfo) return false
        return deviceKey.contentEquals(other.deviceKey)
    }

    override fun hashCode() = deviceKey.contentHashCode()
}

data class ValidityInfo(
    val signed: String,
    val validFrom: String,
    val validUntil: String
)
