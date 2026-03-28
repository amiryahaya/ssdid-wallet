package my.ssdid.wallet.ui.offline

import my.ssdid.sdk.domain.verifier.offline.BundleStore
import my.ssdid.sdk.domain.verifier.offline.VerificationBundle

class InMemoryBundleStore : BundleStore {
    private val bundles = mutableMapOf<String, VerificationBundle>()

    override suspend fun saveBundle(bundle: VerificationBundle) {
        bundles[bundle.issuerDid] = bundle
    }

    override suspend fun getBundle(issuerDid: String): VerificationBundle? {
        return bundles[issuerDid]
    }

    override suspend fun deleteBundle(issuerDid: String) {
        bundles.remove(issuerDid)
    }

    override suspend fun listBundles(): List<VerificationBundle> {
        return bundles.values.toList()
    }

    fun clear() { bundles.clear() }
}
