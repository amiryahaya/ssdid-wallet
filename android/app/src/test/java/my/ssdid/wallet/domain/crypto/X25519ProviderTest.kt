package my.ssdid.wallet.domain.crypto

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class X25519ProviderTest {

    private lateinit var provider: X25519Provider

    @Before
    fun setUp() {
        BouncyCastleInstaller.install()
        provider = X25519Provider()
    }

    @Test
    fun `generateKeyPair produces 32-byte keys`() {
        val keyPair = provider.generateKeyPair()
        assertThat(keyPair.publicKey).hasLength(32)
        assertThat(keyPair.privateKey).hasLength(32)
    }

    @Test
    fun `generateKeyPair produces unique keys each time`() {
        val kp1 = provider.generateKeyPair()
        val kp2 = provider.generateKeyPair()
        assertThat(kp1.publicKey).isNotEqualTo(kp2.publicKey)
        assertThat(kp1.privateKey).isNotEqualTo(kp2.privateKey)
    }

    @Test
    fun `two parties derive the same shared secret`() {
        val alice = provider.generateKeyPair()
        val bob = provider.generateKeyPair()

        val sharedSecretAlice = provider.deriveSharedSecret(alice.privateKey, bob.publicKey)
        val sharedSecretBob = provider.deriveSharedSecret(bob.privateKey, alice.publicKey)

        assertThat(sharedSecretAlice).hasLength(32)
        assertThat(sharedSecretAlice).isEqualTo(sharedSecretBob)
    }

    @Test
    fun `different key pairs produce different shared secrets`() {
        val alice = provider.generateKeyPair()
        val bob1 = provider.generateKeyPair()
        val bob2 = provider.generateKeyPair()

        val secret1 = provider.deriveSharedSecret(alice.privateKey, bob1.publicKey)
        val secret2 = provider.deriveSharedSecret(alice.privateKey, bob2.publicKey)

        assertThat(secret1).isNotEqualTo(secret2)
    }
}
