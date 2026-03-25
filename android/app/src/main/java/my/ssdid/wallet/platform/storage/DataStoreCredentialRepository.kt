package my.ssdid.wallet.platform.storage

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import my.ssdid.wallet.domain.model.VerifiableCredential
import my.ssdid.wallet.domain.verifier.offline.CredentialRepository
import java.io.File

class DataStoreCredentialRepository(context: Context) : CredentialRepository {
    private val dir = File(context.filesDir, "held_credentials").also { it.mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun saveCredential(credential: VerifiableCredential) {
        File(dir, sanitizeFilename(credential.id)).writeText(json.encodeToString(credential))
    }

    override suspend fun getHeldCredentials(): List<VerifiableCredential> {
        return dir.listFiles()?.mapNotNull { file ->
            try { json.decodeFromString<VerifiableCredential>(file.readText()) }
            catch (e: Exception) { null }
        } ?: emptyList()
    }

    override suspend fun getUniqueIssuerDids(): List<String> {
        return getHeldCredentials().map { it.issuer }.distinct()
    }

    override suspend fun deleteCredential(credentialId: String) {
        File(dir, sanitizeFilename(credentialId)).delete()
    }

    private fun sanitizeFilename(id: String): String =
        id.replace(Regex("[^a-zA-Z0-9_-]"), "_") + ".json"
}
