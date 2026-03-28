package my.ssdid.wallet.domain.recovery

import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import my.ssdid.sdk.domain.crypto.ClassicalProvider
import my.ssdid.sdk.domain.crypto.CryptoProvider
import my.ssdid.sdk.domain.model.Algorithm
import my.ssdid.sdk.domain.model.Identity
import my.ssdid.sdk.domain.vault.FakeVaultStorage
import my.ssdid.sdk.domain.vault.VaultImpl
import my.ssdid.sdk.domain.vault.KeystoreManager
import org.junit.Before
import org.junit.Test

class RecoveryManagerTest {
    private lateinit var recoveryManager: RecoveryManager
    private lateinit var vault: VaultImpl
    private lateinit var storage: FakeVaultStorage
    private lateinit var keystore: KeystoreManager

    @Before
    fun setup() {
        keystore = mockk(relaxed = true)
        every { keystore.encrypt(any(), any()) } answers { secondArg<ByteArray>().copyOf() }
        every { keystore.decrypt(any(), any()) } answers { secondArg<ByteArray>().copyOf() }

        storage = FakeVaultStorage()
        val pqcProvider = mockk<CryptoProvider>()
        every { pqcProvider.supportsAlgorithm(any()) } returns false

        val classicalProvider = ClassicalProvider()

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
    fun `generateRecoveryKey returns non-empty bytes`() = runTest {
        val identity = vault.createIdentity("Test", Algorithm.ED25519).getOrThrow()

        val result = recoveryManager.generateRecoveryKey(identity)
        assertThat(result.isSuccess).isTrue()

        val recoveryKey = result.getOrThrow()
        assertThat(recoveryKey).isNotEmpty()
    }

    @Test
    fun `hasRecoveryKey returns true after generation`() = runTest {
        val identity = vault.createIdentity("Test", Algorithm.ED25519).getOrThrow()
        assertThat(recoveryManager.hasRecoveryKey(identity.keyId)).isFalse()

        recoveryManager.generateRecoveryKey(identity)
        assertThat(recoveryManager.hasRecoveryKey(identity.keyId)).isTrue()
    }

    @Test
    fun `generateRecoveryKey stores recovery public key in storage`() = runTest {
        val identity = vault.createIdentity("Test", Algorithm.ED25519).getOrThrow()

        recoveryManager.generateRecoveryKey(identity)

        val recoveryKeyId = "${identity.keyId}-recovery"
        val storedKey = storage.getRecoveryPublicKey(recoveryKeyId)
        assertThat(storedKey).isNotNull()
        assertThat(storedKey).isNotEmpty()
    }

    @Test
    fun `generateRecoveryKey updates identity metadata`() = runTest {
        val identity = vault.createIdentity("Test", Algorithm.ED25519).getOrThrow()

        recoveryManager.generateRecoveryKey(identity)

        val updatedIdentity = storage.getIdentity(identity.keyId)
        assertThat(updatedIdentity).isNotNull()
        assertThat(updatedIdentity!!.hasRecoveryKey).isTrue()
        assertThat(updatedIdentity.recoveryKeyId).isEqualTo("${identity.keyId}-recovery")
    }

    @Test
    fun `hasRecoveryKey returns false for unknown keyId`() = runTest {
        assertThat(recoveryManager.hasRecoveryKey("nonexistent")).isFalse()
    }

    @Test
    fun `restoreWithRecoveryKey creates new identity with same DID`() = runTest {
        val identity = vault.createIdentity("Test", Algorithm.ED25519).getOrThrow()
        val recoveryKey = recoveryManager.generateRecoveryKey(identity).getOrThrow()
        val recoveryKeyBase64 = java.util.Base64.getEncoder().encodeToString(recoveryKey)

        val restored = recoveryManager.restoreWithRecoveryKey(
            did = identity.did,
            recoveryPrivateKeyBase64 = recoveryKeyBase64,
            algorithm = Algorithm.ED25519,
            name = "Restored"
        ).getOrThrow()

        assertThat(restored.did).isEqualTo(identity.did)
        assertThat(restored.name).isEqualTo("Restored")
        assertThat(restored.keyId).isNotEqualTo(identity.keyId)
        assertThat(restored.publicKeyMultibase).isNotEqualTo(identity.publicKeyMultibase)
    }

    @Test
    fun `restoreWithRecoveryKey fails with wrong key`() = runTest {
        val identity = vault.createIdentity("Test", Algorithm.ED25519).getOrThrow()
        recoveryManager.generateRecoveryKey(identity)

        // Generate a different key and try to restore with it
        val wrongKeyPair = ClassicalProvider().generateKeyPair(Algorithm.ED25519)
        val wrongKeyBase64 = java.util.Base64.getEncoder().encodeToString(wrongKeyPair.privateKey)

        val result = recoveryManager.restoreWithRecoveryKey(
            did = identity.did,
            recoveryPrivateKeyBase64 = wrongKeyBase64,
            algorithm = Algorithm.ED25519,
            name = "Restored"
        )

        assertThat(result.isFailure).isTrue()
    }
}
