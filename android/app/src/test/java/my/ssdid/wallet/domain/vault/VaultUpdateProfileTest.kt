package my.ssdid.wallet.domain.vault

import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import my.ssdid.wallet.domain.model.Algorithm
import my.ssdid.wallet.domain.model.Identity
import org.junit.Before
import org.junit.Test

class VaultUpdateProfileTest {

    private lateinit var storage: VaultStorage
    private lateinit var vault: VaultImpl

    @Before
    fun setup() {
        storage = mockk(relaxed = true)
        val classicalProvider = mockk<my.ssdid.wallet.domain.crypto.CryptoProvider>()
        val pqcProvider = mockk<my.ssdid.wallet.domain.crypto.CryptoProvider>()
        val keystoreManager = mockk<KeystoreManager>()
        vault = VaultImpl(classicalProvider, pqcProvider, keystoreManager, storage)
    }

    @Test
    fun `updateIdentityProfile updates profileName and email`() = runTest {
        val identity = Identity(
            name = "Work", did = "did:ssdid:abc", keyId = "did:ssdid:abc#key-1",
            algorithm = Algorithm.ED25519, publicKeyMultibase = "z6Mk...",
            createdAt = "2026-03-16T00:00:00Z"
        )
        coEvery { storage.getIdentity("did:ssdid:abc#key-1") } returns identity
        coEvery { storage.getEncryptedPrivateKey("did:ssdid:abc#key-1") } returns ByteArray(32)

        val result = vault.updateIdentityProfile(
            "did:ssdid:abc#key-1",
            profileName = "Amir Yahaya",
            email = "amir@acme.com"
        )

        assertThat(result.isSuccess).isTrue()
        coVerify {
            storage.saveIdentity(match {
                it.profileName == "Amir Yahaya" && it.email == "amir@acme.com" && it.keyId == "did:ssdid:abc#key-1"
            }, any())
        }
    }

    @Test
    fun `updateIdentityProfile preserves existing fields when nulls passed`() = runTest {
        val identity = Identity(
            name = "Work", did = "did:ssdid:abc", keyId = "did:ssdid:abc#key-1",
            algorithm = Algorithm.ED25519, publicKeyMultibase = "z6Mk...",
            createdAt = "2026-03-16T00:00:00Z",
            profileName = "Amir", email = "amir@acme.com"
        )
        coEvery { storage.getIdentity("did:ssdid:abc#key-1") } returns identity
        coEvery { storage.getEncryptedPrivateKey("did:ssdid:abc#key-1") } returns ByteArray(32)

        val result = vault.updateIdentityProfile("did:ssdid:abc#key-1", email = "new@acme.com")

        assertThat(result.isSuccess).isTrue()
        coVerify {
            storage.saveIdentity(match {
                it.profileName == "Amir" && it.email == "new@acme.com"
            }, any())
        }
    }

    @Test
    fun `updateIdentityProfile fails for nonexistent identity`() = runTest {
        coEvery { storage.getIdentity("missing") } returns null

        val result = vault.updateIdentityProfile("missing", email = "x@y.com")

        assertThat(result.isFailure).isTrue()
    }
}
