package my.ssdid.sdk.testing

import my.ssdid.sdk.domain.vault.KeystoreManager

class FakeKeystoreManager : KeystoreManager {
    private val keys = mutableMapOf<String, ByteArray>()

    override fun generateWrappingKey(alias: String) {
        keys[alias] = ByteArray(32) { it.toByte() }
    }

    override fun encrypt(alias: String, data: ByteArray): ByteArray {
        // Simple XOR "encryption" for testing — NOT secure
        val key = keys[alias] ?: throw IllegalStateException("No key for alias: $alias")
        return data.mapIndexed { i, b -> (b.toInt() xor key[i % key.size].toInt()).toByte() }.toByteArray()
    }

    override fun decrypt(alias: String, encryptedData: ByteArray): ByteArray {
        // XOR is its own inverse
        return encrypt(alias, encryptedData)
    }

    override fun deleteKey(alias: String) { keys.remove(alias) }
    override fun hasKey(alias: String): Boolean = keys.containsKey(alias)
}
