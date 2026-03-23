package my.ssdid.wallet.domain.verifier.offline

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * File-based BundleStore that persists verification bundles as JSON files.
 * Each issuer DID gets its own file in the app's internal storage.
 */
class DataStoreBundleStore(context: Context) : BundleStore {
    private val dir = File(context.filesDir, "verification_bundles").also { it.mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun fileFor(issuerDid: String): File {
        val safe = issuerDid.replace(":", "_").replace("/", "_")
        return File(dir, "$safe.json")
    }

    override suspend fun saveBundle(bundle: VerificationBundle) {
        fileFor(bundle.issuerDid).writeText(json.encodeToString(bundle))
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
