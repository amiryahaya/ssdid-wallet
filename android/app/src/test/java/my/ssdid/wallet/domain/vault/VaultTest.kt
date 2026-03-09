package my.ssdid.wallet.domain.vault

import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import my.ssdid.wallet.domain.crypto.ClassicalProvider
import my.ssdid.wallet.domain.crypto.CryptoProvider
import my.ssdid.wallet.domain.model.*
import my.ssdid.wallet.domain.vault.KeystoreManager
import org.junit.Before
import org.junit.Test

class VaultTest {
    private lateinit var vault: VaultImpl
    private lateinit var keystore: KeystoreManager
    private lateinit var storage: FakeVaultStorage

    @Before
    fun setup() {
        keystore = mockk(relaxed = true)
        every { keystore.encrypt(any(), any()) } answers { secondArg<ByteArray>() }
        every { keystore.decrypt(any(), any()) } answers { secondArg<ByteArray>() }

        storage = FakeVaultStorage()
        val pqcProvider = mockk<CryptoProvider>()
        every { pqcProvider.supportsAlgorithm(any()) } returns false

        vault = VaultImpl(
            classicalProvider = ClassicalProvider(),
            pqcProvider = pqcProvider,
            keystoreManager = keystore,
            storage = storage
        )
    }

    @Test
    fun `createIdentity generates DID and stores key`() = runTest {
        val identity = vault.createIdentity("Test", Algorithm.ED25519).getOrThrow()
        assertThat(identity.did).startsWith("did:ssdid:")
        assertThat(identity.keyId).contains("#key-1")
        assertThat(identity.algorithm).isEqualTo(Algorithm.ED25519)
        assertThat(identity.publicKeyMultibase).startsWith("u")
        assertThat(identity.name).isEqualTo("Test")
        val retrieved = vault.getIdentity(identity.keyId)
        assertThat(retrieved).isEqualTo(identity)
        verify { keystore.generateWrappingKey(any()) }
    }

    @Test
    fun `sign produces valid signature`() = runTest {
        val identity = vault.createIdentity("Test", Algorithm.ED25519).getOrThrow()
        val message = "Hello".toByteArray()
        val signature = vault.sign(identity.keyId, message).getOrThrow()
        assertThat(signature).isNotEmpty()
    }

    @Test
    fun `listIdentities returns all created`() = runTest {
        vault.createIdentity("ID1", Algorithm.ED25519)
        vault.createIdentity("ID2", Algorithm.ECDSA_P256)
        val identities = vault.listIdentities()
        assertThat(identities).hasSize(2)
    }

    @Test
    fun `deleteIdentity removes from storage and keystore`() = runTest {
        val identity = vault.createIdentity("Test", Algorithm.ED25519).getOrThrow()
        vault.deleteIdentity(identity.keyId)
        assertThat(vault.getIdentity(identity.keyId)).isNull()
        verify { keystore.deleteKey(any()) }
    }

    @Test
    fun `buildDidDocument creates W3C compliant doc`() = runTest {
        val identity = vault.createIdentity("Test", Algorithm.ED25519).getOrThrow()
        val doc = vault.buildDidDocument(identity.keyId).getOrThrow()
        assertThat(doc.id).isEqualTo(identity.did)
        assertThat(doc.verificationMethod[0].type).isEqualTo("Ed25519VerificationKey2020")
    }

    @Test
    fun `storeCredential and listCredentials round trip`() = runTest {
        val vc = VerifiableCredential(
            id = "urn:uuid:test",
            type = listOf("VerifiableCredential"),
            issuer = "did:ssdid:issuer",
            issuanceDate = "2026-03-06T00:00:00Z",
            credentialSubject = CredentialSubject(id = "did:ssdid:subject"),
            proof = Proof(
                type = "Ed25519Signature2020", created = "2026-03-06T00:00:00Z",
                verificationMethod = "did:ssdid:issuer#key-1",
                proofPurpose = "assertionMethod", proofValue = "uABC123"
            )
        )
        vault.storeCredential(vc)
        assertThat(vault.listCredentials()).hasSize(1)
        assertThat(vault.listCredentials()[0].id).isEqualTo("urn:uuid:test")
    }

    @Test
    fun `getCredentialForDid finds matching credential`() = runTest {
        val vc = VerifiableCredential(
            id = "urn:uuid:test",
            type = listOf("VerifiableCredential"),
            issuer = "did:ssdid:issuer",
            issuanceDate = "2026-03-06T00:00:00Z",
            credentialSubject = CredentialSubject(id = "did:ssdid:myDid"),
            proof = Proof(
                type = "Ed25519Signature2020", created = "2026-03-06T00:00:00Z",
                verificationMethod = "did:ssdid:issuer#key-1",
                proofPurpose = "assertionMethod", proofValue = "uABC123"
            )
        )
        vault.storeCredential(vc)
        val found = vault.getCredentialForDid("did:ssdid:myDid")
        assertThat(found).isNotNull()
        assertThat(found?.id).isEqualTo("urn:uuid:test")
        assertThat(vault.getCredentialForDid("did:ssdid:other")).isNull()
    }
}
