package my.ssdid.wallet.domain.backup

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import my.ssdid.wallet.domain.history.ActivityRepository
import my.ssdid.wallet.domain.model.ActivityRecord
import my.ssdid.wallet.domain.model.ActivityStatus
import my.ssdid.wallet.domain.model.ActivityType
import my.ssdid.wallet.domain.model.Did
import my.ssdid.wallet.domain.vault.Vault
import my.ssdid.wallet.domain.vault.KeystoreManager
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
class BackupManager(
    private val vault: Vault,
    private val keystoreManager: KeystoreManager,
    private val activityRepo: ActivityRepository
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
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

    suspend fun createBackup(passphrase: CharArray): Result<ByteArray> {
        try {
            return runCatching {
                val identities = vault.listIdentities()
                require(identities.isNotEmpty()) { "No identities to back up" }

                val backupIdentities = identities.map { identity ->
                    val did = Did(identity.did)
                    val wrappingAlias = "ssdid_wrap_${did.methodSpecificId()}"
                    val encryptedPrivateKey = vault.getEncryptedPrivateKey(identity.keyId)
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

                // Compute HMAC over deterministic binary: salt || nonce || ciphertext
                val mac = Mac.getInstance("HmacSHA256")
                mac.init(SecretKeySpec(macKey, "HmacSHA256"))
                mac.update(salt)
                mac.update(nonce)
                val hmacBytes = mac.doFinal(ciphertext)
                macKey.fill(0)

                // Build final package
                val finalPackage = BackupPackage(
                    salt = b64.encodeToString(salt),
                    nonce = b64.encodeToString(nonce),
                    ciphertext = b64.encodeToString(ciphertext),
                    algorithms = identities.map { it.algorithm.name }.distinct(),
                    dids = identities.map { it.did },
                    createdAt = now,
                    hmac = b64.encodeToString(hmacBytes)
                )
                val result = json.encodeToString(finalPackage).toByteArray(Charsets.UTF_8)

                for (identity in identities) {
                    try {
                        activityRepo.addActivity(ActivityRecord(
                            id = UUID.randomUUID().toString(),
                            type = ActivityType.BACKUP_CREATED,
                            did = identity.did,
                            timestamp = Instant.now().toString(),
                            status = ActivityStatus.SUCCESS,
                            details = mapOf("algorithm" to identity.algorithm.name)
                        ))
                    } catch (_: Exception) {
                        // Activity logging should never break the main flow
                    }
                }

                result
            }
        } finally {
            passphrase.fill('\u0000')
        }
    }

    suspend fun restoreBackup(backupData: ByteArray, passphrase: CharArray): Result<Int> {
        try {
            return runCatching {
                val backupPackage = json.decodeFromString<BackupPackage>(String(backupData, Charsets.UTF_8))

                val salt = b64Decoder.decode(backupPackage.salt)
                val backupKey = deriveBackupKey(passphrase, salt)
                val encKey = deriveSubKey(backupKey, "enc")
                val macKey = deriveSubKey(backupKey, "mac")
                backupKey.fill(0)

                val payloadBytes: ByteArray
                try {
                    // Verify HMAC over deterministic binary: salt || nonce || ciphertext
                    val nonce = b64Decoder.decode(backupPackage.nonce)
                    val ciphertext = b64Decoder.decode(backupPackage.ciphertext)
                    val mac = Mac.getInstance("HmacSHA256")
                    mac.init(SecretKeySpec(macKey, "HmacSHA256"))
                    mac.update(salt)
                    mac.update(nonce)
                    val expectedHmac = mac.doFinal(ciphertext)
                    macKey.fill(0)

                    val actualHmac = b64Decoder.decode(backupPackage.hmac)
                    require(MessageDigest.isEqual(expectedHmac, actualHmac)) { "HMAC verification failed: backup may be tampered with" }

                    // Decrypt payload
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(encKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
                    payloadBytes = cipher.doFinal(ciphertext)
                } finally {
                    encKey.fill(0)
                    macKey.fill(0)
                }

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

                        val algorithm = my.ssdid.wallet.domain.model.Algorithm.valueOf(backupIdentity.algorithm)
                        val identity = my.ssdid.wallet.domain.model.Identity(
                            name = backupIdentity.name,
                            did = backupIdentity.did,
                            keyId = backupIdentity.keyId,
                            algorithm = algorithm,
                            publicKeyMultibase = backupIdentity.publicKey,
                            createdAt = backupIdentity.createdAt
                        )
                        vault.saveIdentity(identity, encryptedPrivateKey)
                        restoredCount++
                    } finally {
                        rawPrivateKey.fill(0)
                    }
                }
                restoredCount
            }
        } finally {
            passphrase.fill('\u0000')
        }
    }

    private fun deriveBackupKey(passphrase: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            return factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /**
     * HKDF-Expand (RFC 5869) using HMAC-SHA256.
     * PRK = backupKey (already derived via PBKDF2), info = purpose label.
     * Outputs exactly 32 bytes (one HMAC block).
     */
    private fun deriveSubKey(prk: ByteArray, info: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val infoBytes = info.toByteArray(Charsets.UTF_8)
        mac.update(infoBytes)
        mac.update(0x01) // counter byte for first (and only) block
        return mac.doFinal()
    }
}
