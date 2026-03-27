package my.ssdid.wallet.platform.storage

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import my.ssdid.sdk.domain.model.Identity
import my.ssdid.sdk.domain.model.VerifiableCredential
import my.ssdid.wallet.domain.rotation.RotationEntry
import my.ssdid.wallet.domain.sdjwt.StoredSdJwtVc
import my.ssdid.wallet.domain.vault.PreRotatedKeyData
import my.ssdid.wallet.domain.vault.VaultStorage
import java.io.File
import java.util.Base64

// Identity metadata (DID, name, public keys) and VCs are non-secret and stored in plaintext.
// Private keys are stored separately in filesDir/keys/, encrypted with Android Keystore AES-256-GCM.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ssdid_vault")

class DataStoreVaultStorage(private val context: Context) : VaultStorage, OnboardingStorage {

    private val json = Json { ignoreUnknownKeys = true }

    private val identitiesKey = stringPreferencesKey("identities")
    private val credentialsKey = stringPreferencesKey("credentials")
    private val sdJwtVcsKey = stringPreferencesKey("sd_jwt_vcs")

    private val keysDir: File
        get() = File(context.filesDir, "keys").also { it.mkdirs() }

    // ---------- Identity ----------

    override suspend fun saveIdentity(identity: Identity, encryptedPrivateKey: ByteArray) {
        saveKeyFile(identity.keyId, encryptedPrivateKey)  // key file first
        val identities = listIdentities().toMutableList()
        identities.removeAll { it.keyId == identity.keyId }
        identities.add(identity)
        context.dataStore.edit { prefs ->
            prefs[identitiesKey] = json.encodeToString(identities)
        }
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

    // ---------- SD-JWT VCs ----------

    override suspend fun saveSdJwtVc(sdJwtVc: StoredSdJwtVc) {
        val vcs = listSdJwtVcs().toMutableList()
        vcs.removeAll { it.id == sdJwtVc.id }
        vcs.add(sdJwtVc)
        context.dataStore.edit { prefs ->
            prefs[sdJwtVcsKey] = json.encodeToString(vcs)
        }
    }

    override suspend fun listSdJwtVcs(): List<StoredSdJwtVc> {
        val jsonStr = context.dataStore.data.map { it[sdJwtVcsKey] }.first() ?: return emptyList()
        return try {
            json.decodeFromString(jsonStr)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deserialize SD-JWT VCs — data may be corrupted", e)
            emptyList()
        }
    }

    override suspend fun deleteSdJwtVc(id: String) {
        val vcs = listSdJwtVcs().filter { it.id != id }
        context.dataStore.edit { prefs ->
            prefs[sdJwtVcsKey] = json.encodeToString(vcs)
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

    // ---------- Onboarding state ----------

    private val onboardingCompletedKey = booleanPreferencesKey("onboarding_completed")

    override suspend fun isOnboardingCompleted(): Boolean {
        return context.dataStore.data.map { it[onboardingCompletedKey] }.first() == true
    }

    override suspend fun setOnboardingCompleted() {
        context.dataStore.edit { prefs ->
            prefs[onboardingCompletedKey] = true
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
