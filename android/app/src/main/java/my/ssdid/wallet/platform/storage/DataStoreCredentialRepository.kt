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
        val target = File(dir, sanitizeFilename(credential.id))
        val tmp = File(dir, sanitizeFilename(credential.id) + ".tmp")
        tmp.writeText(json.encodeToString(credential))
        tmp.renameTo(target)
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

    private fun sanitizeFilename(id: String): String {
        val hash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(id.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16)
        val safe = id.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(48)
        return "${safe}_${hash}.json"
    }
}
