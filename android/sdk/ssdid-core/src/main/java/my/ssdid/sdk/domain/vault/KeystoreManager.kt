package my.ssdid.sdk.domain.vault

interface KeystoreManager {
    fun generateWrappingKey(alias: String)
    fun encrypt(alias: String, data: ByteArray): ByteArray
    fun decrypt(alias: String, encryptedData: ByteArray): ByteArray
    fun deleteKey(alias: String)
    fun hasKey(alias: String): Boolean
}
