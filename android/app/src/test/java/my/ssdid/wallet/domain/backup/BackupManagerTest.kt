package my.ssdid.wallet.domain.backup

import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import my.ssdid.wallet.domain.history.ActivityRepository
import my.ssdid.wallet.domain.model.Algorithm
import my.ssdid.wallet.domain.model.Identity
import my.ssdid.wallet.domain.vault.Vault
import my.ssdid.wallet.domain.vault.VaultStorage
import my.ssdid.wallet.platform.keystore.KeystoreManager
import org.junit.Before
import org.junit.Test
import java.util.Base64

class BackupManagerTest {

    private lateinit var vault: Vault
    private lateinit var storage: VaultStorage
    private lateinit var keystoreManager: KeystoreManager
    private lateinit var activityRepo: ActivityRepository
    private lateinit var backupManager: BackupManager

    private val testIdentity = Identity(
        name = "Test Identity",
        did = "did:ssdid:testId123",
        keyId = "did:ssdid:testId123#key-1",
        algorithm = Algorithm.ED25519,
        publicKeyMultibase = "uPublicKeyBase64",
        createdAt = "2026-03-07T00:00:00Z"
    )

    private val fakePrivateKey = ByteArray(32) { it.toByte() }

    @Before
    fun setup() {
        vault = mockk()
        storage = mockk(relaxed = true)
        keystoreManager = mockk(relaxed = true)
        activityRepo = mockk(relaxed = true)

        // Vault returns one identity
        coEvery { vault.listIdentities() } returns listOf(testIdentity)

        // Storage returns an encrypted private key (in this mock, same bytes)
        coEvery { storage.getEncryptedPrivateKey(testIdentity.keyId) } returns fakePrivateKey.copyOf()

        // Keystore decrypt returns the raw private key
        every { keystoreManager.decrypt(any(), any()) } returns fakePrivateKey.copyOf()

        // Keystore encrypt returns the input as-is (mock wrapping)
        every { keystoreManager.encrypt(any(), any()) } answers { secondArg<ByteArray>().copyOf() }

        backupManager = BackupManager(vault, storage, keystoreManager, activityRepo)
    }

    @Test
    fun `createBackup produces non-empty output`() = runTest {
        val result = backupManager.createBackup("testPassphrase123")
        assertThat(result.isSuccess).isTrue()
        val backupData = result.getOrThrow()
        assertThat(backupData).isNotEmpty()

        // Verify it is valid JSON containing expected fields
        val jsonStr = String(backupData, Charsets.UTF_8)
        assertThat(jsonStr).contains("\"version\"")
        assertThat(jsonStr).contains("\"salt\"")
        assertThat(jsonStr).contains("\"nonce\"")
        assertThat(jsonStr).contains("\"ciphertext\"")
        assertThat(jsonStr).contains("\"hmac\"")
        assertThat(jsonStr).contains("ED25519")
        assertThat(jsonStr).contains("did:ssdid:testId123")
    }

    @Test
    fun `restoreBackup with wrong passphrase fails`() = runTest {
        val backupResult = backupManager.createBackup("correctPassphrase")
        assertThat(backupResult.isSuccess).isTrue()
        val backupData = backupResult.getOrThrow()

        val restoreResult = backupManager.restoreBackup(backupData, "wrongPassphrase")
        assertThat(restoreResult.isFailure).isTrue()
        val error = restoreResult.exceptionOrNull()
        assertThat(error).isNotNull()
    }

    @Test
    fun `HMAC catches tampering`() = runTest {
        val backupResult = backupManager.createBackup("mySecurePassphrase")
        assertThat(backupResult.isSuccess).isTrue()
        val backupData = backupResult.getOrThrow()

        // Tamper with the ciphertext field in the JSON
        val jsonStr = String(backupData, Charsets.UTF_8)
        val tamperedJson = jsonStr.replace("\"ciphertext\":\"", "\"ciphertext\":\"AAAA")
        val tamperedData = tamperedJson.toByteArray(Charsets.UTF_8)

        val restoreResult = backupManager.restoreBackup(tamperedData, "mySecurePassphrase")
        assertThat(restoreResult.isFailure).isTrue()
        val error = restoreResult.exceptionOrNull()
        assertThat(error).isNotNull()
        assertThat(error?.message).contains("HMAC verification failed")
    }

    @Test
    fun `createBackup and restoreBackup round trip`() = runTest {
        val passphrase = "strongPassphrase123!"
        val backupResult = backupManager.createBackup(passphrase)
        assertThat(backupResult.isSuccess).isTrue()
        val backupData = backupResult.getOrThrow()

        val restoreResult = backupManager.restoreBackup(backupData, passphrase)
        assertThat(restoreResult.isSuccess).isTrue()
        assertThat(restoreResult.getOrThrow()).isEqualTo(1)

        // Verify storage.saveIdentity was called during restore
        coVerify { storage.saveIdentity(any(), any()) }
        // Verify keystore wrapping key was generated for restored identity
        verify { keystoreManager.generateWrappingKey("ssdid_wrap_testId123") }
    }
}
