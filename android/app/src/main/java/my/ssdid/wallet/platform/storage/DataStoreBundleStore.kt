package my.ssdid.wallet.platform.storage

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import my.ssdid.wallet.domain.verifier.offline.BundleStore
import my.ssdid.wallet.domain.verifier.offline.VerificationBundle
import java.io.File

/**
 * File-based BundleStore that persists verification bundles as JSON files.
 * Each issuer DID gets its own file in the app's internal storage.
 */
class DataStoreBundleStore(context: Context) : BundleStore {
    private val dir = File(context.filesDir, "verification_bundles").also { it.mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun fileFor(issuerDid: String): File {
        val hash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(issuerDid.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16)
        val safe = issuerDid.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(48)
        return File(dir, "${safe}_${hash}.json")
    }

    override suspend fun saveBundle(bundle: VerificationBundle) {
        val target = fileFor(bundle.issuerDid)
        val tmp = File(target.parent, target.name + ".tmp")
        tmp.writeText(json.encodeToString(bundle))
        tmp.renameTo(target)
    }

    override suspend fun getBundle(issuerDid: String): VerificationBundle? {
        val file = fileFor(issuerDid)
        if (!file.exists()) return null
        return try {
            json.decodeFromString<VerificationBundle>(file.readText())
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun deleteBundle(issuerDid: String) {
        fileFor(issuerDid).delete()
    }

    override suspend fun listBundles(): List<VerificationBundle> {
        return dir.listFiles()?.mapNotNull { file ->
            try { json.decodeFromString<VerificationBundle>(file.readText()) }
            catch (_: Exception) { null }
        } ?: emptyList()
    }
}
