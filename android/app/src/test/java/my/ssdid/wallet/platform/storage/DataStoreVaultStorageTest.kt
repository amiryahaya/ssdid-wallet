package my.ssdid.wallet.platform.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import my.ssdid.sdk.domain.model.Algorithm
import my.ssdid.sdk.domain.model.Identity
import my.ssdid.sdk.domain.model.CredentialSubject
import my.ssdid.sdk.domain.model.Proof
import my.ssdid.sdk.domain.model.VerifiableCredential
import my.ssdid.sdk.domain.rotation.RotationEntry
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataStoreVaultStorageTest {

    private lateinit var storage: DataStoreVaultStorage

    private fun testIdentity(
        name: String = "Test",
        did: String = "did:ssdid:abc123",
        keyId: String = "did:ssdid:abc123#key-1",
        algorithm: Algorithm = Algorithm.ED25519
    ) = Identity(
        name = name,
        did = did,
        keyId = keyId,
        algorithm = algorithm,
        publicKeyMultibase = "z6Mkf5rGMoatrSj1f4CyvuHBeXJELe9RPdzo2PKGNCKVtZxP",
        createdAt = "2024-01-01T00:00:00Z"
    )

    private fun testCredential(
        id: String = "urn:uuid:test-cred-1",
        did: String = "did:ssdid:abc123"
    ) = VerifiableCredential(
        id = id,
        type = listOf("VerifiableCredential", "TestCredential"),
        issuer = "did:ssdid:issuer",
        issuanceDate = "2024-01-01T00:00:00Z",
        credentialSubject = CredentialSubject(id = did, claims = mapOf("name" to "Test User")),
        proof = Proof(
            type = "Ed25519Signature2020",
            created = "2024-01-01T00:00:00Z",
            verificationMethod = "did:ssdid:issuer#key-1",
            proofPurpose = "assertionMethod",
            proofValue = "z3abc123"
        )
    )

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        storage = DataStoreVaultStorage(context)
    }

    // ---------- Identity round-trip ----------

    @Test
    fun `save and retrieve identity round-trip`() = runTest {
        val identity = testIdentity()
        val keyData = byteArrayOf(1, 2, 3, 4, 5)

        storage.saveIdentity(identity, keyData)

        val retrieved = storage.getIdentity(identity.keyId)
        assertThat(retrieved).isNotNull()
        assertThat(retrieved!!.did).isEqualTo(identity.did)
        assertThat(retrieved.name).isEqualTo(identity.name)
        assertThat(retrieved.algorithm).isEqualTo(identity.algorithm)
        assertThat(retrieved.publicKeyMultibase).isEqualTo(identity.publicKeyMultibase)
    }

    @Test
    fun `list identities returns all saved identities`() = runTest {
        val id1 = testIdentity(name = "First", did = "did:ssdid:aaa", keyId = "did:ssdid:aaa#key-1")
        val id2 = testIdentity(name = "Second", did = "did:ssdid:bbb", keyId = "did:ssdid:bbb#key-1")

        storage.saveIdentity(id1, byteArrayOf(1))
        storage.saveIdentity(id2, byteArrayOf(2))

        val list = storage.listIdentities()
        assertThat(list).hasSize(2)
        assertThat(list.map { it.name }).containsExactly("First", "Second")
    }

    @Test
    fun `delete identity removes it`() = runTest {
        val identity = testIdentity()
        storage.saveIdentity(identity, byteArrayOf(10, 20))

        storage.deleteIdentity(identity.keyId)

        assertThat(storage.getIdentity(identity.keyId)).isNull()
        assertThat(storage.listIdentities()).isEmpty()
    }

    // ---------- Encrypted key data ----------

    @Test
    fun `save and retrieve encrypted key data`() = runTest {
        val identity = testIdentity()
        val keyData = byteArrayOf(99, 88, 77, 66, 55)

        storage.saveIdentity(identity, keyData)

        val retrieved = storage.getEncryptedPrivateKey(identity.keyId)
        assertThat(retrieved).isNotNull()
        assertThat(retrieved).isEqualTo(keyData)
    }

    @Test
    fun `get encrypted key for unknown keyId returns null`() = runTest {
        assertThat(storage.getEncryptedPrivateKey("unknown-key")).isNull()
    }

    // ---------- Credentials ----------

    @Test
    fun `store and list credentials`() = runTest {
        val cred1 = testCredential(id = "urn:uuid:cred-1")
        val cred2 = testCredential(id = "urn:uuid:cred-2")

        storage.saveCredential(cred1)
        storage.saveCredential(cred2)

        val credentials = storage.listCredentials()
        assertThat(credentials).hasSize(2)
        assertThat(credentials.map { it.id }).containsExactly("urn:uuid:cred-1", "urn:uuid:cred-2")
    }

    @Test
    fun `delete credential removes it from list`() = runTest {
        val cred = testCredential(id = "urn:uuid:to-delete")
        storage.saveCredential(cred)

        val beforeDelete = storage.listCredentials()
        assertThat(beforeDelete.map { it.id }).contains("urn:uuid:to-delete")

        storage.deleteCredential(cred.id)

        val afterDelete = storage.listCredentials()
        assertThat(afterDelete.map { it.id }).doesNotContain("urn:uuid:to-delete")
    }

    // ---------- Onboarding state ----------

    @Test
    fun `onboarding state defaults to false and persists after set`() = runTest {
        assertThat(storage.isOnboardingCompleted()).isFalse()

        storage.setOnboardingCompleted()

        assertThat(storage.isOnboardingCompleted()).isTrue()
    }

    // ---------- Recovery keys ----------

    @Test
    fun `save and retrieve recovery public key`() = runTest {
        val keyId = "did:ssdid:abc123#key-1"
        val publicKey = byteArrayOf(11, 22, 33)

        storage.saveRecoveryPublicKey(keyId, publicKey)

        val retrieved = storage.getRecoveryPublicKey(keyId)
        assertThat(retrieved).isNotNull()
        assertThat(retrieved).isEqualTo(publicKey)
    }

    @Test
    fun `get recovery key for unknown keyId returns null`() = runTest {
        assertThat(storage.getRecoveryPublicKey("unknown")).isNull()
    }

    // ---------- Pre-rotated keys ----------

    @Test
    fun `save and retrieve pre-rotated key`() = runTest {
        val keyId = "did:ssdid:abc123#key-1"
        val privKey = byteArrayOf(1, 2, 3)
        val pubKey = byteArrayOf(4, 5, 6)

        storage.savePreRotatedKey(keyId, privKey, pubKey)

        val retrieved = storage.getPreRotatedKey(keyId)
        assertThat(retrieved).isNotNull()
        assertThat(retrieved!!.encryptedPrivateKey).isEqualTo(privKey)
        assertThat(retrieved.publicKey).isEqualTo(pubKey)
    }

    @Test
    fun `delete pre-rotated key removes it`() = runTest {
        val keyId = "did:ssdid:abc123#key-1"
        storage.savePreRotatedKey(keyId, byteArrayOf(1), byteArrayOf(2))

        storage.deletePreRotatedKey(keyId)

        assertThat(storage.getPreRotatedKey(keyId)).isNull()
    }

    // ---------- Rotation history ----------

    @Test
    fun `add and retrieve rotation history`() = runTest {
        val did = "did:ssdid:abc123"
        val entry = RotationEntry(
            timestamp = "2024-01-01T00:00:00Z",
            oldKeyIdFragment = "#key-1",
            newKeyIdFragment = "#key-2"
        )

        storage.addRotationEntry(did, entry)

        val history = storage.getRotationHistory(did)
        assertThat(history).hasSize(1)
        assertThat(history[0].oldKeyIdFragment).isEqualTo("#key-1")
        assertThat(history[0].newKeyIdFragment).isEqualTo("#key-2")
    }

    @Test
    fun `rotation history for unknown DID returns empty list`() = runTest {
        assertThat(storage.getRotationHistory("did:ssdid:unknown")).isEmpty()
    }

    // ---------- Identity upsert ----------

    @Test
    fun `saving identity with same keyId overwrites previous`() = runTest {
        val identity = testIdentity(name = "Original")
        storage.saveIdentity(identity, byteArrayOf(1))

        val updated = identity.copy(name = "Updated")
        storage.saveIdentity(updated, byteArrayOf(2))

        val list = storage.listIdentities()
        assertThat(list).hasSize(1)
        assertThat(list[0].name).isEqualTo("Updated")
    }
}
