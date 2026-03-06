package my.ssdid.mobile.domain.backup

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import my.ssdid.mobile.domain.model.Did
import my.ssdid.mobile.domain.vault.Vault
import my.ssdid.mobile.domain.vault.VaultStorage
import my.ssdid.mobile.platform.keystore.KeystoreManager
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    private val vault: Vault,
    private val storage: VaultStorage,
    private val keystoreManager: KeystoreManager
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val b64 = Base64.getUrlEncoder().withoutPadding()
    private val b64Decoder = Base64.getUrlDecoder()
    private val secureRandom = SecureRandom()

    companion object {
        private const val PBKDF2_ITERATIONS = 600_000
        private const val SALT_LENGTH = 32
        private const val NONCE_LENGTH = 12
        private const val GCM_TAG_BITS = 128
        private const val KEY_LENGTH_BITS = 256
    }

    suspend fun createBackup(passphrase: String): Result<ByteArray> = runCatching {
        val identities = vault.listIdentities()
        require(identities.isNotEmpty()) { "No identities to back up" }

        val backupIdentities = identities.map { identity ->
            val did = Did(identity.did)
            val wrappingAlias = "ssdid_wrap_${did.methodSpecificId()}"
            val encryptedPrivateKey = storage.getEncryptedPrivateKey(identity.keyId)
                ?: throw IllegalStateException("Private key not found for: ${identity.keyId}")
            val rawPrivateKey = keystoreManager.decrypt(wrappingAlias, encryptedPrivateKey)
            try {
                BackupIdentity(
                    keyId = identity.keyId,
                    did = identity.did,
                    name = identity.name,
                    algorithm = identity.algorithm.name,
                    privateKey = b64.encodeToString(rawPrivateKey),
                    publicKey = identity.publicKeyMultibase,
                    createdAt = identity.createdAt
                )
            } finally {
                rawPrivateKey.fill(0)
            }
        }

        val payload = BackupPayload(identities = backupIdentities)
        val payloadBytes = json.encodeToString(payload).toByteArray(Charsets.UTF_8)

        // Generate salt and derive keys
        val salt = ByteArray(SALT_LENGTH).also { secureRandom.nextBytes(it) }
        val backupKey = deriveBackupKey(passphrase, salt)
        val encKey = deriveSubKey(backupKey, "enc")
        val macKey = deriveSubKey(backupKey, "mac")
        backupKey.fill(0)

        // Encrypt payload with AES-256-GCM
        val nonce = ByteArray(NONCE_LENGTH).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(encKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        val ciphertext = cipher.doFinal(payloadBytes)
        encKey.fill(0)

        val now = DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC))

        // Build package without HMAC first
        val packageWithoutHmac = BackupPackage(
            salt = b64.encodeToString(salt),
            nonce = b64.encodeToString(nonce),
            ciphertext = b64.encodeToString(ciphertext),
            algorithms = identities.map { it.algorithm.name }.distinct(),
            dids = identities.map { it.did },
            createdAt = now,
            hmac = ""
        )
        val packageJsonForHmac = json.encodeToString(packageWithoutHmac)

        // Compute HMAC
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(macKey, "HmacSHA256"))
        val hmacBytes = mac.doFinal(packageJsonForHmac.toByteArray(Charsets.UTF_8))
        macKey.fill(0)

        // Build final package with HMAC
        val finalPackage = packageWithoutHmac.copy(hmac = b64.encodeToString(hmacBytes))
        json.encodeToString(finalPackage).toByteArray(Charsets.UTF_8)
    }

    suspend fun restoreBackup(backupData: ByteArray, passphrase: String): Result<Int> = runCatching {
        val backupPackage = json.decodeFromString<BackupPackage>(String(backupData, Charsets.UTF_8))

        val salt = b64Decoder.decode(backupPackage.salt)
        val backupKey = deriveBackupKey(passphrase, salt)
        val encKey = deriveSubKey(backupKey, "enc")
        val macKey = deriveSubKey(backupKey, "mac")
        backupKey.fill(0)

        // Verify HMAC before decryption
        val packageForHmac = backupPackage.copy(hmac = "")
        val packageJsonForHmac = json.encodeToString(packageForHmac)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(macKey, "HmacSHA256"))
        val expectedHmac = mac.doFinal(packageJsonForHmac.toByteArray(Charsets.UTF_8))
        macKey.fill(0)

        val actualHmac = b64Decoder.decode(backupPackage.hmac)
        require(expectedHmac.contentEquals(actualHmac)) { "HMAC verification failed: backup may be tampered with" }

        // Decrypt payload
        val nonce = b64Decoder.decode(backupPackage.nonce)
        val ciphertext = b64Decoder.decode(backupPackage.ciphertext)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(encKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        val payloadBytes = cipher.doFinal(ciphertext)
        encKey.fill(0)

        val payload = json.decodeFromString<BackupPayload>(String(payloadBytes, Charsets.UTF_8))

        // Restore each identity
        var restoredCount = 0
        for (backupIdentity in payload.identities) {
            val rawPrivateKey = b64Decoder.decode(backupIdentity.privateKey)
            try {
                val did = Did(backupIdentity.did)
                val wrappingAlias = "ssdid_wrap_${did.methodSpecificId()}"
                keystoreManager.generateWrappingKey(wrappingAlias)
                val encryptedPrivateKey = keystoreManager.encrypt(wrappingAlias, rawPrivateKey)

                val algorithm = my.ssdid.mobile.domain.model.Algorithm.valueOf(backupIdentity.algorithm)
                val identity = my.ssdid.mobile.domain.model.Identity(
                    name = backupIdentity.name,
                    did = backupIdentity.did,
                    keyId = backupIdentity.keyId,
                    algorithm = algorithm,
                    publicKeyMultibase = backupIdentity.publicKey,
                    createdAt = backupIdentity.createdAt
                )
                storage.saveIdentity(identity, encryptedPrivateKey)
                restoredCount++
            } finally {
                rawPrivateKey.fill(0)
            }
        }
        restoredCount
    }

    private fun deriveBackupKey(passphrase: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    private fun deriveSubKey(backupKey: ByteArray, purpose: String): ByteArray {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        md.update(backupKey)
        md.update(purpose.toByteArray(Charsets.UTF_8))
        return md.digest()
    }
}
