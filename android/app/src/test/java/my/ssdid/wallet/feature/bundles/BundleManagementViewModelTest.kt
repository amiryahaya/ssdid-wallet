package my.ssdid.wallet.feature.bundles

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import my.ssdid.wallet.domain.settings.TtlProvider
import my.ssdid.wallet.domain.verifier.offline.BundleManager
import my.ssdid.wallet.domain.verifier.offline.BundleStore
import my.ssdid.wallet.domain.verifier.offline.VerificationBundle
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BundleManagementViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var bundleStore: BundleStore
    private lateinit var bundleManager: BundleManager
    private lateinit var ttlProvider: TtlProvider

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        bundleStore = mockk(relaxed = true)
        bundleManager = mockk(relaxed = true)
        ttlProvider = mockk(relaxed = true)
        // Default: listBundles returns empty list
        coEvery { bundleStore.listBundles() } returns emptyList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): BundleManagementViewModel {
        return BundleManagementViewModel(bundleStore, bundleManager, ttlProvider)
    }

    @Test
    fun `addByDid rejects invalid DID format`() = runTest {
        val vm = createViewModel()

        vm.addByDid("not-a-did")

        val error = vm.error.value
        assertThat(error).isNotNull()
        assertThat(error).contains("Invalid")
    }

    @Test
    fun `addByDid accepts valid DID and calls prefetch`() = runTest {
        val fakeBundle = mockk<VerificationBundle>(relaxed = true)
        coEvery { bundleManager.prefetchBundle("did:ssdid:test123") } returns Result.success(fakeBundle)

        val vm = createViewModel()
        vm.addByDid("did:ssdid:test123")

        coVerify { bundleManager.prefetchBundle("did:ssdid:test123") }
        assertThat(vm.error.value).isNull()
    }

    @Test
    fun `deleteBundle removes from store and reloads`() = runTest {
        val vm = createViewModel()

        vm.deleteBundle("did:ssdid:test")

        coVerify { bundleStore.deleteBundle("did:ssdid:test") }
    }

    @Test
    fun `refreshAll sets isRefreshing and reloads bundles`() = runTest {
        coEvery { bundleStore.listBundles() } returns emptyList()
        coEvery { bundleManager.refreshStaleBundles() } returns 0
        val vm = createViewModel()

        vm.refreshAll()

        coVerify { bundleManager.refreshStaleBundles() }
        assertThat(vm.isRefreshing.value).isFalse()
    }

    @Test
    fun `addByDid shows error on prefetch failure`() = runTest {
        coEvery { bundleStore.listBundles() } returns emptyList()
        coEvery { bundleManager.prefetchBundle("did:ssdid:fail") } returns Result.failure(IOException("network"))
        val vm = createViewModel()

        vm.addByDid("did:ssdid:fail")

        assertThat(vm.error.value).isNotNull()
        assertThat(vm.error.value).contains("Failed")
    }
}
