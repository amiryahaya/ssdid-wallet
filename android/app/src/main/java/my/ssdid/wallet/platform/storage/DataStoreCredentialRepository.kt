package my.ssdid.wallet.platform.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import my.ssdid.wallet.domain.model.VerifiableCredential
import my.ssdid.wallet.domain.verifier.offline.CredentialRepository
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * File-based CredentialRepository that persists held credentials as AES-256-GCM encrypted JSON
 * files. Each credential gets its own encrypted file in the app's internal storage.
 * Files are also HMAC-SHA256 verified before deserialization to detect tampering.
 *
 * Security model mirrors DataStoreBundleStore:
 *  - AES-256-GCM encryption key: Android Keystore hardware-backed (alias "cred_enc_key")
 *  - HMAC-SHA256 key: Android Keystore hardware-backed (alias "cred_mac_key")
 */
class DataStoreCredentialRepository(context: Context) : CredentialRepository {
    private val dir = File(context.filesDir, "held_credentials").also { it.mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ---- Keystore AES-256-GCM (encryption at rest) ----

    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun ensureEncKey() {
        if (keyStore.containsAlias(ENC_KEY_ALIAS)) return
        val spec = KeyGenParameterSpec.Builder(
            ENC_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        keyGen.init(spec)
        keyGen.generateKey()
    }

    private fun encrypt(plaintext: ByteArray): ByteArray {
        ensureEncKey()
        val key = keyStore.getKey(ENC_KEY_ALIAS, null)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv // 12 bytes
        val ciphertext = cipher.doFinal(plaintext)
        return iv + ciphertext // prepend IV
    }

    private fun decrypt(data: ByteArray): ByteArray {
        ensureEncKey()
        val key = keyStore.getKey(ENC_KEY_ALIAS, null)
        val iv = data.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = data.copyOfRange(GCM_IV_LENGTH, data.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    // ---- HMAC-SHA256 (integrity verification) ----

    private val macKey: SecretKey by lazy {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        ks.getKey(MAC_KEY_ALIAS, null) as? SecretKey ?: run {
            val keyGen = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore"
            )
            keyGen.init(
                KeyGenParameterSpec.Builder(MAC_KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
                    .build()
            )
            keyGen.generateKey()
        }
    }

    private fun computeHmac(data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256").apply { init(macKey) }
        return mac.doFinal(data)
    }

    private fun verifyHmac(data: ByteArray, expectedMac: ByteArray): Boolean {
        val actualMac = computeHmac(data)
        if (actualMac.size != expectedMac.size) return false
        var diff = 0
        for (i in actualMac.indices) diff = diff or (actualMac[i].toInt() xor expectedMac[i].toInt())
        return diff == 0 // constant-time comparison
    }

    // ---- File naming ----

    private fun fileFor(credentialId: String): File {
        val hash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(credentialId.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16)
        val safe = credentialId.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(48)
        return File(dir, "${safe}_${hash}.enc")
    }

    private fun macFileFor(credFile: File): File = File(credFile.parent, credFile.name + ".mac")

    // ---- CredentialRepository implementation ----

    override suspend fun saveCredential(credential: VerifiableCredential) {
        val target = fileFor(credential.id)
        val tmp = File(target.parent, target.name + ".tmp")
        val macTmp = File(target.parent, target.name + ".mac.tmp")

        val plaintext = json.encodeToString(credential).toByteArray(Charsets.UTF_8)
        val encrypted = encrypt(plaintext)
        val mac = computeHmac(encrypted)

        tmp.writeBytes(encrypted)
        macTmp.writeBytes(mac)

        // Atomic rename both files
        tmp.renameTo(target)
        macTmp.renameTo(macFileFor(target))
    }

    override suspend fun getHeldCredentials(): List<VerifiableCredential> {
        return dir.listFiles()
            ?.filter { it.extension == "enc" }
            ?.mapNotNull { file ->
                val macFile = macFileFor(file)
                if (!macFile.exists()) return@mapNotNull null
                try {
                    val encrypted = file.readBytes()
                    val storedMac = macFile.readBytes()
                    if (!verifyHmac(encrypted, storedMac)) return@mapNotNull null // tampered
                    val plaintext = decrypt(encrypted)
                    json.decodeFromString<VerifiableCredential>(plaintext.toString(Charsets.UTF_8))
                } catch (_: Exception) {
                    null
                }
            } ?: emptyList()
    }

    override suspend fun getUniqueIssuerDids(): List<String> {
        return getHeldCredentials().map { it.issuer }.distinct()
    }

    override suspend fun deleteCredential(credentialId: String) {
        val file = fileFor(credentialId)
        file.delete()
        macFileFor(file).delete()
    }

    companion object {
        private const val ENC_KEY_ALIAS = "cred_enc_key"
        private const val MAC_KEY_ALIAS = "cred_mac_key"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_BITS = 128
    }
}
