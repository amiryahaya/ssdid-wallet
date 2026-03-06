package my.ssdid.mobile.platform.storage

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import my.ssdid.mobile.domain.model.Identity
import my.ssdid.mobile.domain.model.VerifiableCredential
import my.ssdid.mobile.domain.rotation.RotationEntry
import my.ssdid.mobile.domain.vault.PreRotatedKeyData
import my.ssdid.mobile.domain.vault.VaultStorage
import java.io.File
import java.util.Base64

// Identity metadata (DID, name, public keys) and VCs are non-secret and stored in plaintext.
// Private keys are stored separately in filesDir/keys/, encrypted with Android Keystore AES-256-GCM.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ssdid_vault")

class DataStoreVaultStorage(private val context: Context) : VaultStorage {

    private val json = Json { ignoreUnknownKeys = true }

    private val identitiesKey = stringPreferencesKey("identities")
    private val credentialsKey = stringPreferencesKey("credentials")

    private val keysDir: File
        get() = File(context.filesDir, "keys").also { it.mkdirs() }

    // ---------- Identity ----------

    override suspend fun saveIdentity(identity: Identity, encryptedPrivateKey: ByteArray) {
        val identities = listIdentities().toMutableList()
        identities.removeAll { it.keyId == identity.keyId }
        identities.add(identity)
        context.dataStore.edit { prefs ->
            prefs[identitiesKey] = json.encodeToString(identities)
        }
        saveKeyFile(identity.keyId, encryptedPrivateKey)
    }

    override suspend fun getIdentity(keyId: String): Identity? {
        return listIdentities().find { it.keyId == keyId }
    }

    override suspend fun listIdentities(): List<Identity> {
        val jsonStr = context.dataStore.data.map { it[identitiesKey] }.first() ?: return emptyList()
        return try {
            json.decodeFromString(jsonStr)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deserialize identities — data may be corrupted", e)
            emptyList()
        }
    }

    override suspend fun deleteIdentity(keyId: String) {
        val identities = listIdentities().filter { it.keyId != keyId }
        context.dataStore.edit { prefs ->
            prefs[identitiesKey] = json.encodeToString(identities)
        }
        deleteKeyFile(keyId)
    }

    // ---------- Encrypted private keys (files) ----------

    override suspend fun getEncryptedPrivateKey(keyId: String): ByteArray? {
        val file = keyFile(keyId)
        return if (file.exists()) file.readBytes() else null
    }

    // ---------- Credentials ----------

    override suspend fun saveCredential(credential: VerifiableCredential) {
        val credentials = listCredentials().toMutableList()
        credentials.removeAll { it.id == credential.id }
        credentials.add(credential)
        context.dataStore.edit { prefs ->
            prefs[credentialsKey] = json.encodeToString(credentials)
        }
    }

    override suspend fun listCredentials(): List<VerifiableCredential> {
        val jsonStr = context.dataStore.data.map { it[credentialsKey] }.first() ?: return emptyList()
        return try {
            json.decodeFromString(jsonStr)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deserialize credentials — data may be corrupted", e)
            emptyList()
        }
    }

    override suspend fun deleteCredential(credentialId: String) {
        val credentials = listCredentials().filter { it.id != credentialId }
        context.dataStore.edit { prefs ->
            prefs[credentialsKey] = json.encodeToString(credentials)
        }
    }

    // ---------- Recovery keys ----------

    private val recoveryKeysDir: File
        get() = File(File(context.filesDir, "keys"), "recovery").also { it.mkdirs() }

    override suspend fun saveRecoveryPublicKey(keyId: String, encryptedPublicKey: ByteArray) {
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(keyId.toByteArray())
        File(recoveryKeysDir, encoded).writeBytes(encryptedPublicKey)
    }

    override suspend fun getRecoveryPublicKey(keyId: String): ByteArray? {
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(keyId.toByteArray())
        val file = File(recoveryKeysDir, encoded)
        return if (file.exists()) file.readBytes() else null
    }

    // ---------- Pre-rotated keys (KERI) ----------

    private val preRotatedKeysDir: File
        get() = File(File(context.filesDir, "keys"), "prerotated").also { it.mkdirs() }

    override suspend fun savePreRotatedKey(keyId: String, encryptedPrivateKey: ByteArray, publicKey: ByteArray) {
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(keyId.toByteArray())
        File(preRotatedKeysDir, "${encoded}_priv").writeBytes(encryptedPrivateKey)
        File(preRotatedKeysDir, "${encoded}_pub").writeBytes(publicKey)
    }

    override suspend fun getPreRotatedKey(keyId: String): PreRotatedKeyData? {
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(keyId.toByteArray())
        val privFile = File(preRotatedKeysDir, "${encoded}_priv")
        val pubFile = File(preRotatedKeysDir, "${encoded}_pub")
        return if (privFile.exists() && pubFile.exists()) {
            PreRotatedKeyData(privFile.readBytes(), pubFile.readBytes())
        } else null
    }

    override suspend fun deletePreRotatedKey(keyId: String) {
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(keyId.toByteArray())
        File(preRotatedKeysDir, "${encoded}_priv").delete()
        File(preRotatedKeysDir, "${encoded}_pub").delete()
    }

    // ---------- Rotation history ----------

    private val rotationHistoryKey = stringPreferencesKey("rotation_history")

    override suspend fun addRotationEntry(did: String, entry: RotationEntry) {
        val history = getAllRotationHistory().toMutableMap()
        val entries = history.getOrDefault(did, emptyList()).toMutableList()
        entries.add(0, entry)
        history[did] = entries
        context.dataStore.edit { prefs ->
            prefs[rotationHistoryKey] = json.encodeToString(history)
        }
    }

    override suspend fun getRotationHistory(did: String): List<RotationEntry> {
        return getAllRotationHistory()[did] ?: emptyList()
    }

    private suspend fun getAllRotationHistory(): Map<String, List<RotationEntry>> {
        val jsonStr = context.dataStore.data.map { it[rotationHistoryKey] }.first() ?: return emptyMap()
        return try {
            json.decodeFromString(jsonStr)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deserialize rotation history", e)
            emptyMap()
        }
    }

    // ---------- Helpers ----------

    private fun keyFile(keyId: String): File {
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(keyId.toByteArray())
        return File(keysDir, encoded)
    }

    private fun saveKeyFile(keyId: String, bytes: ByteArray) {
        keyFile(keyId).writeBytes(bytes)
    }

    private fun deleteKeyFile(keyId: String) {
        keyFile(keyId).delete()
    }

    companion object {
        private const val TAG = "DataStoreVaultStorage"
    }
}
