package my.ssdid.wallet.platform.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import my.ssdid.wallet.domain.verifier.offline.BundleStore
import my.ssdid.wallet.domain.verifier.offline.VerificationBundle
import java.io.File
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * File-based BundleStore that persists verification bundles as AES-256-GCM encrypted JSON files.
 * Each issuer DID gets its own encrypted file in the app's internal storage.
 * Bundles are also HMAC-SHA256 verified before deserialization to detect tampering.
 *
 * Security model:
 *  - AES-256-GCM encryption key: Android Keystore hardware-backed (alias "bundle_enc_key")
 *  - HMAC-SHA256 key: randomly generated, stored in SharedPreferences under "bundle_integrity"
 *    (protects against file-level tampering even if encryption is bypassed in rooted devices)
 */
class DataStoreBundleStore(private val context: Context) : BundleStore {
    private val dir = File(context.filesDir, "verification_bundles").also { it.mkdirs() }
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

    private val macKey: ByteArray by lazy {
        val prefs = context.getSharedPreferences("bundle_integrity", Context.MODE_PRIVATE)
        val existing = prefs.getString("bundle_mac_key", null)
        if (existing != null) {
            Base64.getDecoder().decode(existing)
        } else {
            val key = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
            prefs.edit().putString("bundle_mac_key", Base64.getEncoder().encodeToString(key)).apply()
            key
        }
    }

    private fun computeHmac(data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(macKey, "HmacSHA256"))
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

    private fun fileFor(issuerDid: String): File {
        val hash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(issuerDid.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16)
        val safe = issuerDid.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(48)
        return File(dir, "${safe}_${hash}.enc")
    }

    private fun macFileFor(bundleFile: File): File = File(bundleFile.parent, bundleFile.name + ".mac")

    // ---- BundleStore implementation ----

    override suspend fun saveBundle(bundle: VerificationBundle) {
        val target = fileFor(bundle.issuerDid)
        val tmp = File(target.parent, target.name + ".tmp")
        val macTmp = File(target.parent, target.name + ".mac.tmp")

        val plaintext = json.encodeToString(bundle).toByteArray(Charsets.UTF_8)
        val encrypted = encrypt(plaintext)
        val mac = computeHmac(encrypted)

        tmp.writeBytes(encrypted)
        macTmp.writeBytes(mac)

        // Atomic rename both files
        tmp.renameTo(target)
        macTmp.renameTo(macFileFor(target))
    }

    override suspend fun getBundle(issuerDid: String): VerificationBundle? {
        val file = fileFor(issuerDid)
        val macFile = macFileFor(file)
        if (!file.exists() || !macFile.exists()) return null
        return try {
            val encrypted = file.readBytes()
            val storedMac = macFile.readBytes()
            if (!verifyHmac(encrypted, storedMac)) return null // tampered
            val plaintext = decrypt(encrypted)
            json.decodeFromString<VerificationBundle>(plaintext.toString(Charsets.UTF_8))
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun deleteBundle(issuerDid: String) {
        val file = fileFor(issuerDid)
        file.delete()
        macFileFor(file).delete()
    }

    override suspend fun listBundles(): List<VerificationBundle> {
        return dir.listFiles()
            ?.filter { it.extension == "enc" }
            ?.mapNotNull { file ->
                val macFile = macFileFor(file)
                if (!macFile.exists()) return@mapNotNull null
                try {
                    val encrypted = file.readBytes()
                    val storedMac = macFile.readBytes()
                    if (!verifyHmac(encrypted, storedMac)) return@mapNotNull null
                    val plaintext = decrypt(encrypted)
                    json.decodeFromString<VerificationBundle>(plaintext.toString(Charsets.UTF_8))
                } catch (_: Exception) {
                    null
                }
            } ?: emptyList()
    }

    companion object {
        private const val ENC_KEY_ALIAS = "bundle_enc_key"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_BITS = 128
    }
}
