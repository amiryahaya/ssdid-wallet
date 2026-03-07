package my.ssdid.wallet.domain.rotation

import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import my.ssdid.wallet.domain.crypto.CryptoProvider
import my.ssdid.wallet.domain.crypto.KeyPairResult
import my.ssdid.wallet.domain.model.Algorithm
import my.ssdid.wallet.domain.model.Identity
import my.ssdid.wallet.domain.vault.PreRotatedKeyData
import my.ssdid.wallet.domain.vault.VaultStorage
import my.ssdid.wallet.platform.keystore.KeystoreManager
import org.junit.Before
import org.junit.Test

class KeyRotationManagerTest {

    private val storage = mockk<VaultStorage>(relaxed = true)
    private val classicalProvider = mockk<CryptoProvider>()
    private val pqcProvider = mockk<CryptoProvider>()
    private val keystoreManager = mockk<KeystoreManager>(relaxed = true)

    private lateinit var manager: KeyRotationManager

    private val testIdentity = Identity(
        name = "Test",
        did = "did:ssdid:abc123",
        keyId = "did:ssdid:abc123#key-1",
        algorithm = Algorithm.ED25519,
        publicKeyMultibase = "uAAAA",
        createdAt = "2026-01-01T00:00:00Z"
    )

    @Before
    fun setup() {
        manager = KeyRotationManager(storage, classicalProvider, pqcProvider, keystoreManager)
    }

    @Test
    fun `prepareRotation generates pre-commitment hash`() = runTest {
        val nextPub = ByteArray(32) { it.toByte() }
        val nextPriv = ByteArray(64) { it.toByte() }
        coEvery { classicalProvider.generateKeyPair(Algorithm.ED25519) } returns KeyPairResult(nextPub, nextPriv)
        coEvery { keystoreManager.encrypt(any(), any()) } returns ByteArray(80)
        coEvery { storage.getEncryptedPrivateKey(testIdentity.keyId) } returns ByteArray(80)

        val result = manager.prepareRotation(testIdentity)

        assertThat(result.isSuccess).isTrue()
        val hash = result.getOrNull()!!
        assertThat(hash).startsWith("u")
        assertThat(hash.length).isGreaterThan(10)

        coVerify { storage.savePreRotatedKey(any(), any(), nextPub) }
        coVerify { storage.saveIdentity(match { it.preRotatedKeyId != null }, any()) }
    }

    @Test
    fun `executeRotation fails without pre-commitment`() = runTest {
        val result = manager.executeRotation(testIdentity)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("No pre-committed key")
    }

    @Test
    fun `executeRotation promotes pre-rotated key`() = runTest {
        val identityWithPreRot = testIdentity.copy(preRotatedKeyId = "did:ssdid:abc123#key-1-prerotated")
        val preRotPub = ByteArray(32) { (it + 10).toByte() }
        val preRotEncPriv = ByteArray(80) { (it + 20).toByte() }

        coEvery { storage.getPreRotatedKey("did:ssdid:abc123#key-1-prerotated") } returns
            PreRotatedKeyData(preRotEncPriv, preRotPub)

        val result = manager.executeRotation(identityWithPreRot)

        assertThat(result.isSuccess).isTrue()
        val newIdentity = result.getOrNull()!!
        assertThat(newIdentity.preRotatedKeyId).isNull()
        assertThat(newIdentity.keyId).isNotEqualTo(testIdentity.keyId)

        coVerify { storage.saveIdentity(match { it.preRotatedKeyId == null }, preRotEncPriv) }
        coVerify { storage.addRotationEntry(eq("did:ssdid:abc123"), any()) }
        coVerify { storage.deleteIdentity("did:ssdid:abc123#key-1") }
        coVerify { storage.deletePreRotatedKey("did:ssdid:abc123#key-1-prerotated") }
    }

    @Test
    fun `getRotationStatus returns correct state`() = runTest {
        coEvery { storage.getRotationHistory("did:ssdid:abc123") } returns emptyList()

        val status = manager.getRotationStatus(testIdentity)

        assertThat(status.hasPreCommitment).isFalse()
        assertThat(status.nextKeyHash).isNull()
        assertThat(status.rotationHistory).isEmpty()
    }
}
