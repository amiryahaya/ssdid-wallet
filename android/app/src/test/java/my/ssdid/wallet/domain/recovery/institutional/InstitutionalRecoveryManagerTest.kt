package my.ssdid.wallet.domain.recovery.institutional

import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import my.ssdid.wallet.domain.model.Algorithm
import my.ssdid.wallet.domain.model.Identity
import my.ssdid.wallet.domain.recovery.RecoveryManager
import org.junit.Before
import org.junit.Test

class InstitutionalRecoveryManagerTest {

    private lateinit var recoveryManager: RecoveryManager
    private lateinit var storage: InstitutionalRecoveryStorage
    private lateinit var manager: InstitutionalRecoveryManager

    private val testIdentity = Identity(
        name = "Test User",
        did = "did:ssdid:user123",
        keyId = "did:ssdid:user123#key-1",
        algorithm = Algorithm.ED25519,
        publicKeyMultibase = "uPubKey",
        createdAt = "2026-03-10T00:00:00Z",
        hasRecoveryKey = true
    )

    @Before
    fun setup() {
        recoveryManager = mockk()
        storage = mockk(relaxed = true)
        manager = InstitutionalRecoveryManager(recoveryManager, storage)
    }

    @Test
    fun `enrollOrganization stores config`() = runTest {
        coEvery { recoveryManager.hasRecoveryKey(testIdentity.keyId) } returns true

        val encryptedKey = ByteArray(32) { it.toByte() }
        val result = manager.enrollOrganization(
            testIdentity, "did:ssdid:org-corp", "Corp Inc.", encryptedKey
        )

        assertThat(result.isSuccess).isTrue()
        val config = result.getOrThrow()
        assertThat(config.orgDid).isEqualTo("did:ssdid:org-corp")
        assertThat(config.orgName).isEqualTo("Corp Inc.")
        assertThat(config.userDid).isEqualTo(testIdentity.did)
        coVerify { storage.saveOrgRecoveryConfig(any()) }
    }

    @Test
    fun `enrollOrganization fails without recovery key`() = runTest {
        coEvery { recoveryManager.hasRecoveryKey(testIdentity.keyId) } returns false

        val result = manager.enrollOrganization(
            testIdentity, "did:ssdid:org", "Org", ByteArray(32)
        )

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("recovery key")
    }

    @Test
    fun `enrollOrganization rejects invalid DID`() = runTest {
        coEvery { recoveryManager.hasRecoveryKey(testIdentity.keyId) } returns true

        val result = manager.enrollOrganization(
            testIdentity, "not-a-did", "Org", ByteArray(32)
        )

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Invalid")
    }

    @Test
    fun `recoverWithOrgAssistance delegates to RecoveryManager`() = runTest {
        val config = OrgRecoveryConfig(
            userDid = testIdentity.did, orgDid = "did:ssdid:org",
            orgName = "Org", encryptedRecoveryKey = "encrypted",
            enrolledAt = "2026-03-10T00:00:00Z"
        )
        coEvery { storage.getOrgRecoveryConfig(testIdentity.did) } returns config
        coEvery {
            recoveryManager.restoreWithRecoveryKey(testIdentity.did, "recoveryKeyBase64", "Restored", Algorithm.ED25519)
        } returns Result.success(testIdentity)

        val result = manager.recoverWithOrgAssistance(
            testIdentity.did, "recoveryKeyBase64", "Restored", Algorithm.ED25519
        )

        assertThat(result.isSuccess).isTrue()
        coVerify { recoveryManager.restoreWithRecoveryKey(testIdentity.did, "recoveryKeyBase64", "Restored", Algorithm.ED25519) }
    }

    @Test
    fun `recoverWithOrgAssistance fails without config`() = runTest {
        coEvery { storage.getOrgRecoveryConfig(any()) } returns null

        val result = manager.recoverWithOrgAssistance(
            "did:ssdid:unknown", "key", "Name", Algorithm.ED25519
        )

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("No institutional recovery")
    }

    @Test
    fun `hasOrgRecovery returns true when config exists`() = runTest {
        coEvery { storage.getOrgRecoveryConfig(testIdentity.did) } returns OrgRecoveryConfig(
            userDid = testIdentity.did, orgDid = "did:ssdid:org",
            orgName = "Org", encryptedRecoveryKey = "enc",
            enrolledAt = "2026-03-10T00:00:00Z"
        )
        assertThat(manager.hasOrgRecovery(testIdentity.did)).isTrue()
    }

    @Test
    fun `hasOrgRecovery returns false when no config`() = runTest {
        coEvery { storage.getOrgRecoveryConfig(any()) } returns null
        assertThat(manager.hasOrgRecovery("did:ssdid:unknown")).isFalse()
    }
}
