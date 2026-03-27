package my.ssdid.sdk.domain.crypto

data class KeyPairResult(
    val publicKey: ByteArray,
    val privateKey: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KeyPairResult) return false
        return publicKey.contentEquals(other.publicKey) && privateKey.contentEquals(other.privateKey)
    }
    override fun hashCode(): Int = publicKey.contentHashCode() * 31 + privateKey.contentHashCode()
}
