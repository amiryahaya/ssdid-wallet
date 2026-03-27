package my.ssdid.wallet.domain.recovery.social

import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import my.ssdid.sdk.domain.model.Algorithm
import my.ssdid.sdk.domain.model.Identity
import my.ssdid.wallet.domain.recovery.RecoveryManager
import org.junit.Before
import org.junit.Test
import java.util.Base64

class SocialRecoveryManagerTest {

    private lateinit var recoveryManager: RecoveryManager
    private lateinit var storage: SocialRecoveryStorage
    private lateinit var manager: SocialRecoveryManager

    private val testIdentity = Identity(
        name = "Test",
        did = "did:ssdid:test123",
        keyId = "did:ssdid:test123#key-1",
        algorithm = Algorithm.ED25519,
        publicKeyMultibase = "uPublicKey",
        createdAt = "2026-03-10T00:00:00Z"
    )

    @Before
    fun setup() {
        recoveryManager = mockk()
        storage = mockk(relaxed = true)
        manager = SocialRecoveryManager(recoveryManager, storage)
    }

    @Test
    fun `setupSocialRecovery generates shares for each guardian`() = runTest {
        val recoveryKey = ByteArray(32) { it.toByte() }
        coEvery { recoveryManager.generateRecoveryKey(testIdentity) } returns Result.success(recoveryKey)

        val guardians = listOf(
            "Alice" to "did:ssdid:alice",
            "Bob" to "did:ssdid:bob",
            "Carol" to "did:ssdid:carol"
        )

        val result = manager.setupSocialRecovery(testIdentity, guardians, threshold = 2)

        assertThat(result.isSuccess).isTrue()
        val shares = result.getOrThrow()
        assertThat(shares).hasSize(3)

        // Verify config was saved
        coVerify { storage.saveSocialRecoveryConfig(match { it.threshold == 2 && it.totalShares == 3 }) }
    }

    @Test
    fun `setupSocialRecovery clears recovery key from memory`() = runTest {
        val recoveryKey = ByteArray(32) { 42 }
        coEvery { recoveryManager.generateRecoveryKey(testIdentity) } returns Result.success(recoveryKey)

        val guardians = listOf("A" to "did:a", "B" to "did:b")
        manager.setupSocialRecovery(testIdentity, guardians, threshold = 2)

        // Recovery key should be zeroed
        assertThat(recoveryKey.all { it == 0.toByte() }).isTrue()
    }

    @Test
    fun `recoverWithShares reconstructs and delegates to RecoveryManager`() = runTest {
        // Setup: create shares from a known recovery key
        val recoveryKey = ByteArray(32) { (it * 3).toByte() }
        val shares = ShamirSecretSharing.split(recoveryKey, k = 2, n = 3)

        val config = SocialRecoveryConfig(
            did = testIdentity.did,
            threshold = 2,
            totalShares = 3,
            guardians = emptyList(),
            createdAt = "2026-03-10T00:00:00Z"
        )
        coEvery { storage.getSocialRecoveryConfig(testIdentity.did) } returns config

        val recoveryKeyBase64 = Base64.getEncoder().encodeToString(recoveryKey)
        coEvery {
            recoveryManager.restoreWithRecoveryKey(testIdentity.did, recoveryKeyBase64, "Restored", Algorithm.ED25519)
        } returns Result.success(testIdentity)

        val collectedShares = mapOf(
            shares[0].index to Base64.getUrlEncoder().withoutPadding().encodeToString(shares[0].data),
            shares[2].index to Base64.getUrlEncoder().withoutPadding().encodeToString(shares[2].data)
        )

        val result = manager.recoverWithShares(testIdentity.did, collectedShares, "Restored", Algorithm.ED25519)

        assertThat(result.isSuccess).isTrue()
        coVerify { recoveryManager.restoreWithRecoveryKey(testIdentity.did, any(), "Restored", Algorithm.ED25519) }
    }

    @Test
    fun `recoverWithShares fails with insufficient shares`() = runTest {
        val config = SocialRecoveryConfig(
            did = testIdentity.did, threshold = 3, totalShares = 5,
            guardians = emptyList(), createdAt = "2026-03-10T00:00:00Z"
        )
        coEvery { storage.getSocialRecoveryConfig(testIdentity.did) } returns config

        val result = manager.recoverWithShares(
            testIdentity.did,
            mapOf(1 to "share1", 2 to "share2"), // only 2, need 3
            "Restored", Algorithm.ED25519
        )

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("at least 3")
    }

    @Test
    fun `recoverWithShares fails when no config exists`() = runTest {
        coEvery { storage.getSocialRecoveryConfig(any()) } returns null

        val result = manager.recoverWithShares(
            "did:ssdid:unknown", mapOf(1 to "a", 2 to "b"), "Name", Algorithm.ED25519
        )

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("No social recovery")
    }

    @Test
    fun `setupSocialRecovery fails with threshold less than 2`() = runTest {
        val guardians = listOf("A" to "did:a", "B" to "did:b")
        val result = manager.setupSocialRecovery(testIdentity, guardians, threshold = 1)
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `hasSocialRecovery returns true when config exists`() = runTest {
        coEvery { storage.getSocialRecoveryConfig("did:ssdid:test123") } returns SocialRecoveryConfig(
            did = "did:ssdid:test123", threshold = 2, totalShares = 3,
            guardians = emptyList(), createdAt = "2026-03-10T00:00:00Z"
        )
        assertThat(manager.hasSocialRecovery("did:ssdid:test123")).isTrue()
    }

    @Test
    fun `hasSocialRecovery returns false when no config`() = runTest {
        coEvery { storage.getSocialRecoveryConfig(any()) } returns null
        assertThat(manager.hasSocialRecovery("did:ssdid:unknown")).isFalse()
    }
}
