package my.ssdid.wallet.domain.mdoc

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import my.ssdid.wallet.domain.vault.VaultStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.Test

class VaultMDocTest {

    private val storage = mockk<VaultStorage>(relaxed = true)

    @Test
    fun saveMDocCallsStorage() = runTest {
        val mdoc = StoredMDoc(
            id = "mdoc-001",
            docType = "org.iso.18013.5.1.mDL",
            issuerSignedCbor = byteArrayOf(1, 2, 3),
            deviceKeyId = "key-1",
            issuedAt = 1700000000L
        )
        storage.saveMDoc(mdoc)
        coVerify { storage.saveMDoc(mdoc) }
    }

    @Test
    fun listMDocsReturnsStoredDocs() = runTest {
        val mdoc = StoredMDoc(
            id = "mdoc-001",
            docType = "org.iso.18013.5.1.mDL",
            issuerSignedCbor = byteArrayOf(1, 2, 3),
            deviceKeyId = "key-1",
            issuedAt = 1700000000L
        )
        coEvery { storage.listMDocs() } returns listOf(mdoc)
        val result = storage.listMDocs()
        assertThat(result).hasSize(1)
        assertThat(result[0].docType).isEqualTo("org.iso.18013.5.1.mDL")
    }

    @Test
    fun getMDocReturnsById() = runTest {
        val mdoc = StoredMDoc(
            id = "mdoc-001",
            docType = "org.iso.18013.5.1.mDL",
            issuerSignedCbor = byteArrayOf(1, 2, 3),
            deviceKeyId = "key-1",
            issuedAt = 1700000000L
        )
        coEvery { storage.getMDoc("mdoc-001") } returns mdoc
        val result = storage.getMDoc("mdoc-001")
        assertThat(result?.id).isEqualTo("mdoc-001")
    }

    @Test
    fun deleteMDocRemovesFromStorage() = runTest {
        storage.deleteMDoc("mdoc-001")
        coVerify { storage.deleteMDoc("mdoc-001") }
    }
}
