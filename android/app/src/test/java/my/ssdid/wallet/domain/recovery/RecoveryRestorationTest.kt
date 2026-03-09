package my.ssdid.wallet.domain.recovery

import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import my.ssdid.wallet.domain.crypto.ClassicalProvider
import my.ssdid.wallet.domain.crypto.CryptoProvider
import my.ssdid.wallet.domain.model.Algorithm
import my.ssdid.wallet.domain.vault.FakeVaultStorage
import my.ssdid.wallet.domain.vault.VaultImpl
import my.ssdid.wallet.domain.vault.KeystoreManager
import org.junit.Before
import org.junit.Test
import java.util.Base64

class RecoveryRestorationTest {
    private lateinit var recoveryManager: RecoveryManager
    private lateinit var vault: VaultImpl
    private lateinit var storage: FakeVaultStorage
    private lateinit var keystore: KeystoreManager
    private lateinit var classicalProvider: ClassicalProvider

    @Before
    fun setup() {
        keystore = mockk(relaxed = true)
        every { keystore.encrypt(any(), any()) } answers { secondArg<ByteArray>() }
        every { keystore.decrypt(any(), any()) } answers { secondArg<ByteArray>() }

        storage = FakeVaultStorage()
        val pqcProvider = mockk<CryptoProvider>()
        every { pqcProvider.supportsAlgorithm(any()) } returns false

        classicalProvider = ClassicalProvider()

        vault = VaultImpl(classicalProvider, pqcProvider, keystore, storage)

        recoveryManager = RecoveryManager(
            vault = vault,
            storage = storage,
            classicalProvider = classicalProvider,
            pqcProvider = pqcProvider,
            keystoreManager = keystore
        )
    }

    @Test
    fun `restoreWithRecoveryKey creates identity with correct DID`() = runTest {
        val did = "did:ssdid:test123"
        val fakeRecoveryKey = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })

        val result = recoveryManager.restoreWithRecoveryKey(
            did = did,
            recoveryPrivateKeyBase64 = fakeRecoveryKey,
            name = "Restored",
            algorithm = Algorithm.ED25519
        )

        assertThat(result.isSuccess).isTrue()
        val identity = result.getOrThrow()
        assertThat(identity.did).isEqualTo(did)
        assertThat(identity.name).isEqualTo("Restored")
        assertThat(identity.algorithm).isEqualTo(Algorithm.ED25519)
    }

    @Test
    fun `restoreWithRecoveryKey assigns correct algorithm`() = runTest {
        val did = "did:ssdid:algo-test"
        val fakeRecoveryKey = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })

        val result = recoveryManager.restoreWithRecoveryKey(
            did = did,
            recoveryPrivateKeyBase64 = fakeRecoveryKey,
            name = "ECDSA Identity",
            algorithm = Algorithm.ECDSA_P256
        )

        assertThat(result.isSuccess).isTrue()
        val identity = result.getOrThrow()
        assertThat(identity.algorithm).isEqualTo(Algorithm.ECDSA_P256)
    }

    @Test
    fun `restoreWithRecoveryKey generates keyId containing DID`() = runTest {
        val did = "did:ssdid:keyid-test"
        val fakeRecoveryKey = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })

        val result = recoveryManager.restoreWithRecoveryKey(
            did = did,
            recoveryPrivateKeyBase64 = fakeRecoveryKey,
            name = "Test",
            algorithm = Algorithm.ED25519
        )

        assertThat(result.isSuccess).isTrue()
        val identity = result.getOrThrow()
        assertThat(identity.keyId).startsWith("$did#")
    }

    @Test
    fun `restoreWithRecoveryKey stores identity in storage`() = runTest {
        val did = "did:ssdid:storage-test"
        val fakeRecoveryKey = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })

        val result = recoveryManager.restoreWithRecoveryKey(
            did = did,
            recoveryPrivateKeyBase64 = fakeRecoveryKey,
            name = "Stored",
            algorithm = Algorithm.ED25519
        )

        val identity = result.getOrThrow()
        val storedIdentity = storage.getIdentity(identity.keyId)
        assertThat(storedIdentity).isNotNull()
        assertThat(storedIdentity!!.did).isEqualTo(did)
        assertThat(storedIdentity.name).isEqualTo("Stored")
    }

    @Test
    fun `restoreWithRecoveryKey stores encrypted private key`() = runTest {
        val did = "did:ssdid:key-storage-test"
        val fakeRecoveryKey = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })

        val result = recoveryManager.restoreWithRecoveryKey(
            did = did,
            recoveryPrivateKeyBase64 = fakeRecoveryKey,
            name = "Test",
            algorithm = Algorithm.ED25519
        )

        val identity = result.getOrThrow()
        val encryptedKey = storage.getEncryptedPrivateKey(identity.keyId)
        assertThat(encryptedKey).isNotNull()
        assertThat(encryptedKey).isNotEmpty()
    }

    @Test
    fun `restoreWithRecoveryKey generates wrapping key in keystore`() = runTest {
        val did = "did:ssdid:wrapping-test"
        val fakeRecoveryKey = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })

        recoveryManager.restoreWithRecoveryKey(
            did = did,
            recoveryPrivateKeyBase64 = fakeRecoveryKey,
            name = "Test",
            algorithm = Algorithm.ED25519
        )

        verify { keystore.generateWrappingKey(any()) }
        verify { keystore.encrypt(any(), any()) }
    }

    @Test
    fun `restoreWithRecoveryKey has non-empty publicKeyMultibase`() = runTest {
        val did = "did:ssdid:multibase-test"
        val fakeRecoveryKey = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })

        val result = recoveryManager.restoreWithRecoveryKey(
            did = did,
            recoveryPrivateKeyBase64 = fakeRecoveryKey,
            name = "Test",
            algorithm = Algorithm.ED25519
        )

        val identity = result.getOrThrow()
        assertThat(identity.publicKeyMultibase).startsWith("u")
        assertThat(identity.publicKeyMultibase.length).isGreaterThan(1)
    }

    @Test
    fun `restoreWithRecoveryKey fails with invalid base64`() = runTest {
        val result = recoveryManager.restoreWithRecoveryKey(
            did = "did:ssdid:bad-key",
            recoveryPrivateKeyBase64 = "not-valid-base64!!!",
            name = "Test",
            algorithm = Algorithm.ED25519
        )

        assertThat(result.isFailure).isTrue()
    }
}
