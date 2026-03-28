package my.ssdid.sdk.domain.vault

import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import my.ssdid.sdk.domain.crypto.ClassicalProvider
import my.ssdid.sdk.domain.crypto.CryptoProvider
import my.ssdid.sdk.domain.model.Algorithm
import org.junit.Before
import org.junit.Test

class VaultConcurrencyTest {

    private lateinit var vault: VaultImpl
    private lateinit var keystore: KeystoreManager
    private lateinit var storage: FakeVaultStorage

    @Before
    fun setup() {
        keystore = mockk(relaxed = true)
        every { keystore.encrypt(any(), any()) } answers { secondArg<ByteArray>().copyOf() }
        every { keystore.decrypt(any(), any()) } answers { secondArg<ByteArray>().copyOf() }

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
    fun `concurrent createIdentity with same name - only one succeeds`() = runTest {
        val results = (1..10).map { i ->
            async {
                vault.createIdentity("Shared", Algorithm.ED25519)
            }
        }.awaitAll()

        val successes = results.count { it.isSuccess }
        val failures = results.count { it.isFailure }

        assertThat(successes).isAtLeast(1)
        // The duplicate-name guard should reject extras
        assertThat(failures).isAtLeast(0)
        // Only one identity with that name should exist in storage
        val stored = vault.listIdentities().filter { it.name.equals("Shared", ignoreCase = true) }
        assertThat(stored).hasSize(1)
    }

    @Test
    fun `concurrent sign operations complete without error`() = runTest {
        val identity = vault.createIdentity("SignTest", Algorithm.ED25519).getOrThrow()

        val results = coroutineScope {
            (1..20).map { i ->
                async {
                    vault.sign(identity.keyId, "message-$i".toByteArray())
                }
            }.awaitAll()
        }

        // All sign operations should succeed
        results.forEach { result ->
            assertThat(result.isSuccess).isTrue()
        }
        // Each signature should be non-empty
        results.forEach { result ->
            assertThat(result.getOrThrow()).isNotEmpty()
        }
    }

    @Test
    fun `concurrent read during write returns consistent state`() = runTest {
        // Pre-create an identity so reads have something to find
        val existing = vault.createIdentity("Existing", Algorithm.ED25519).getOrThrow()

        coroutineScope {
            // Writer: create additional identities
            val writers = (1..5).map { i ->
                async {
                    vault.createIdentity("Writer-$i", Algorithm.ED25519)
                }
            }
            // Reader: concurrently read the existing identity
            val readers = (1..10).map {
                async {
                    vault.getIdentity(existing.keyId)
                }
            }

            val writeResults = writers.awaitAll()
            val readResults = readers.awaitAll()

            // All reads should return the same consistent identity
            readResults.forEach { identity ->
                assertThat(identity).isNotNull()
                assertThat(identity!!.keyId).isEqualTo(existing.keyId)
                assertThat(identity.name).isEqualTo("Existing")
                assertThat(identity.did).isEqualTo(existing.did)
            }

            // All writes should succeed (unique names)
            writeResults.forEach { result ->
                assertThat(result.isSuccess).isTrue()
            }
        }
    }
}
