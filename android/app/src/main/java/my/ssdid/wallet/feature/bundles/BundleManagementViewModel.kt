package my.ssdid.wallet.feature.bundles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import my.ssdid.wallet.domain.settings.TtlProvider
import my.ssdid.wallet.domain.verifier.offline.BundleManager
import my.ssdid.wallet.domain.verifier.offline.BundleStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class BundleUiItem(
    val issuerDid: String,
    val displayName: String,
    val fetchedAt: String,
    val freshnessRatio: Double
)

@HiltViewModel
class BundleManagementViewModel @Inject constructor(
    private val bundleStore: BundleStore,
    private val bundleManager: BundleManager,
    private val ttlProvider: TtlProvider
) : ViewModel() {

    private val _bundles = MutableStateFlow<List<BundleUiItem>>(emptyList())
    val bundles: StateFlow<List<BundleUiItem>> = _bundles

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())

    init {
        loadBundles()
    }

    fun loadBundles() {
        viewModelScope.launch {
            val rawBundles = bundleStore.listBundles()
            _bundles.value = rawBundles.map { bundle ->
                val ratio = try {
                    ttlProvider.freshnessRatio(bundle.fetchedAt)
                } catch (_: Exception) {
                    1.0
                }
                val formattedDate = try {
                    dateFormatter.format(Instant.parse(bundle.fetchedAt))
                } catch (_: Exception) {
                    bundle.fetchedAt
                }
                val shortDid = bundle.issuerDid.let { did ->
                    if (did.length > 20) did.take(12) + "..." + did.takeLast(8) else did
                }
                BundleUiItem(
                    issuerDid = bundle.issuerDid,
                    displayName = shortDid,
                    fetchedAt = formattedDate,
                    freshnessRatio = ratio
                )
            }
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _error.value = null
            try {
                bundleManager.refreshStaleBundles()
                loadBundles()
            } catch (e: Exception) {
                _error.value = "Refresh failed: ${e.message}"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun deleteBundle(issuerDid: String) {
        viewModelScope.launch {
            try {
                bundleStore.deleteBundle(issuerDid)
                loadBundles()
            } catch (e: Exception) {
                _error.value = "Delete failed: ${e.message}"
            }
        }
    }

    fun addByDid(did: String) {
        val trimmed = did.trim()
        if (!trimmed.startsWith("did:")) {
            _error.value = "Invalid DID format — must start with 'did:'"
            return
        }
        viewModelScope.launch {
            _isRefreshing.value = true
            _error.value = null
            try {
                bundleManager.prefetchBundle(trimmed).getOrThrow()
                loadBundles()
            } catch (e: Exception) {
                _error.value = "Failed to add issuer: ${e.message}"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
