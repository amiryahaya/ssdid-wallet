package my.ssdid.mobile.domain.crypto

import com.google.common.truth.Truth.assertThat
import my.ssdid.mobile.domain.model.Algorithm
import org.junit.Test

class ClassicalProviderTest {
    private val provider = ClassicalProvider()

    @Test
    fun `supports classical algorithms only`() {
        assertThat(provider.supportsAlgorithm(Algorithm.ED25519)).isTrue()
        assertThat(provider.supportsAlgorithm(Algorithm.ECDSA_P256)).isTrue()
        assertThat(provider.supportsAlgorithm(Algorithm.ECDSA_P384)).isTrue()
        assertThat(provider.supportsAlgorithm(Algorithm.KAZ_SIGN_192)).isFalse()
    }

    @Test
    fun `ed25519 generate returns 32-byte keys`() {
        val keyPair = provider.generateKeyPair(Algorithm.ED25519)
        assertThat(keyPair.publicKey).hasLength(32)
        assertThat(keyPair.privateKey).hasLength(32)
    }

    @Test
    fun `ed25519 sign-verify round trip`() {
        val keyPair = provider.generateKeyPair(Algorithm.ED25519)
        val message = "Hello SSDID".toByteArray()
        val signature = provider.sign(Algorithm.ED25519, keyPair.privateKey, message)
        assertThat(signature).hasLength(64)
        val valid = provider.verify(Algorithm.ED25519, keyPair.publicKey, signature, message)
        assertThat(valid).isTrue()
    }

    @Test
    fun `ed25519 verify rejects tampered message`() {
        val keyPair = provider.generateKeyPair(Algorithm.ED25519)
        val message = "Hello SSDID".toByteArray()
        val signature = provider.sign(Algorithm.ED25519, keyPair.privateKey, message)
        val valid = provider.verify(Algorithm.ED25519, keyPair.publicKey, signature, "Tampered".toByteArray())
        assertThat(valid).isFalse()
    }

    @Test
    fun `ecdsa p256 sign-verify round trip`() {
        val keyPair = provider.generateKeyPair(Algorithm.ECDSA_P256)
        val message = "Hello SSDID".toByteArray()
        val signature = provider.sign(Algorithm.ECDSA_P256, keyPair.privateKey, message)
        val valid = provider.verify(Algorithm.ECDSA_P256, keyPair.publicKey, signature, message)
        assertThat(valid).isTrue()
    }

    @Test
    fun `ecdsa p384 sign-verify round trip`() {
        val keyPair = provider.generateKeyPair(Algorithm.ECDSA_P384)
        val message = "Hello SSDID".toByteArray()
        val signature = provider.sign(Algorithm.ECDSA_P384, keyPair.privateKey, message)
        val valid = provider.verify(Algorithm.ECDSA_P384, keyPair.publicKey, signature, message)
        assertThat(valid).isTrue()
    }

    @Test
    fun `different keys produce different signatures`() {
        val kp1 = provider.generateKeyPair(Algorithm.ED25519)
        val kp2 = provider.generateKeyPair(Algorithm.ED25519)
        val message = "Test".toByteArray()
        val sig1 = provider.sign(Algorithm.ED25519, kp1.privateKey, message)
        val sig2 = provider.sign(Algorithm.ED25519, kp2.privateKey, message)
        assertThat(sig1).isNotEqualTo(sig2)
    }

    @Test
    fun `cross-key verification fails`() {
        val kp1 = provider.generateKeyPair(Algorithm.ED25519)
        val kp2 = provider.generateKeyPair(Algorithm.ED25519)
        val message = "Test".toByteArray()
        val sig = provider.sign(Algorithm.ED25519, kp1.privateKey, message)
        val valid = provider.verify(Algorithm.ED25519, kp2.publicKey, sig, message)
        assertThat(valid).isFalse()
    }
}
