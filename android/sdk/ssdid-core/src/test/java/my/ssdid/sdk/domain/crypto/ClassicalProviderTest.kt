package my.ssdid.sdk.domain.crypto

import com.google.common.truth.Truth.assertThat
import my.ssdid.sdk.domain.model.Algorithm
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
        // DER-encoded ECDSA signature (variable length, typically 70-72 bytes for P-256)
        assertThat(signature.size).isIn(68..73)
        val valid = provider.verify(Algorithm.ECDSA_P256, keyPair.publicKey, signature, message)
        assertThat(valid).isTrue()
    }

    @Test
    fun `ecdsa p384 sign-verify round trip`() {
        val keyPair = provider.generateKeyPair(Algorithm.ECDSA_P384)
        val message = "Hello SSDID".toByteArray()
        val signature = provider.sign(Algorithm.ECDSA_P384, keyPair.privateKey, message)
        // DER-encoded ECDSA signature (variable length, typically 102-104 bytes for P-384)
        assertThat(signature.size).isIn(100..106)
        val valid = provider.verify(Algorithm.ECDSA_P384, keyPair.publicKey, signature, message)
        assertThat(valid).isTrue()
    }

    @Test
    fun `ecdsa p256 sign-verify round trip with 64-byte pre-hashed payload`() {
        // Simulates the real W3C Data Integrity proof payload:
        // SHA3-256(proofOptions) || SHA3-256(document) = 64 bytes.
        // Verified that both SHA256withECDSA and NONEwithECDSA produce valid
        // round-trip signatures locally for this payload size.
        val keyPair = provider.generateKeyPair(Algorithm.ECDSA_P256)
        val payload = ByteArray(64) { it.toByte() }
        val signature = provider.sign(Algorithm.ECDSA_P256, keyPair.privateKey, payload)
        val valid = provider.verify(Algorithm.ECDSA_P256, keyPair.publicKey, signature, payload)
        assertThat(valid).isTrue()
    }

    @Test
    fun `ecdsa p384 sign-verify round trip with 64-byte pre-hashed payload`() {
        // Simulates the real W3C Data Integrity proof payload:
        // SHA3-256(proofOptions) || SHA3-256(document) = 64 bytes.
        val keyPair = provider.generateKeyPair(Algorithm.ECDSA_P384)
        val payload = ByteArray(64) { it.toByte() }
        val signature = provider.sign(Algorithm.ECDSA_P384, keyPair.privateKey, payload)
        val valid = provider.verify(Algorithm.ECDSA_P384, keyPair.publicKey, signature, payload)
        assertThat(valid).isTrue()
    }

    @Test
    fun `ecdsa p384 verify rejects tampered message`() {
        val keyPair = provider.generateKeyPair(Algorithm.ECDSA_P384)
        val message = "Hello SSDID".toByteArray()
        val signature = provider.sign(Algorithm.ECDSA_P384, keyPair.privateKey, message)
        val valid = provider.verify(Algorithm.ECDSA_P384, keyPair.publicKey, signature, "Tampered".toByteArray())
        assertThat(valid).isFalse()
    }

    @Test
    fun `ecdsa p384 cross-key verification fails`() {
        val kp1 = provider.generateKeyPair(Algorithm.ECDSA_P384)
        val kp2 = provider.generateKeyPair(Algorithm.ECDSA_P384)
        val message = "Test".toByteArray()
        val sig = provider.sign(Algorithm.ECDSA_P384, kp1.privateKey, message)
        val valid = provider.verify(Algorithm.ECDSA_P384, kp2.publicKey, sig, message)
        assertThat(valid).isFalse()
    }

    @Test
    fun `ecdsa p256 verify rejects tampered message`() {
        val keyPair = provider.generateKeyPair(Algorithm.ECDSA_P256)
        val message = "Hello SSDID".toByteArray()
        val signature = provider.sign(Algorithm.ECDSA_P256, keyPair.privateKey, message)
        val valid = provider.verify(Algorithm.ECDSA_P256, keyPair.publicKey, signature, "Tampered".toByteArray())
        assertThat(valid).isFalse()
    }

    @Test
    fun `ecdsa p256 cross-key verification fails`() {
        val kp1 = provider.generateKeyPair(Algorithm.ECDSA_P256)
        val kp2 = provider.generateKeyPair(Algorithm.ECDSA_P256)
        val message = "Test".toByteArray()
        val sig = provider.sign(Algorithm.ECDSA_P256, kp1.privateKey, message)
        val valid = provider.verify(Algorithm.ECDSA_P256, kp2.publicKey, sig, message)
        assertThat(valid).isFalse()
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

    @Test(expected = IllegalArgumentException::class)
    fun `verify throws for unsupported algorithm`() {
        provider.verify(Algorithm.KAZ_SIGN_128, ByteArray(54), ByteArray(162), ByteArray(10))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `sign throws for unsupported algorithm`() {
        provider.sign(Algorithm.KAZ_SIGN_128, ByteArray(32), ByteArray(10))
    }

    @Test
    fun `ed25519 key wrapping produces valid JCA keys`() {
        // Multiple keygen+sign+verify round-trips exercise DER wrapping correctness
        repeat(3) {
            val kp = provider.generateKeyPair(Algorithm.ED25519)
            val msg = "DER round-trip $it".toByteArray()
            val sig = provider.sign(Algorithm.ED25519, kp.privateKey, msg)
            assertThat(provider.verify(Algorithm.ED25519, kp.publicKey, sig, msg)).isTrue()
        }
    }

    @Test
    fun `ecdsa p256 key sizes are correct`() {
        val kp = provider.generateKeyPair(Algorithm.ECDSA_P256)
        // Uncompressed EC point: 0x04 || x(32) || y(32) = 65 bytes
        assertThat(kp.publicKey).hasLength(65)
        // Raw scalar: 32 bytes
        assertThat(kp.privateKey).hasLength(32)
    }

    @Test
    fun `ecdsa p384 key sizes are correct`() {
        val kp = provider.generateKeyPair(Algorithm.ECDSA_P384)
        // Uncompressed EC point: 0x04 || x(48) || y(48) = 97 bytes
        assertThat(kp.publicKey).hasLength(97)
        // Raw scalar: 48 bytes
        assertThat(kp.privateKey).hasLength(48)
    }

    @Test
    fun `BouncyCastle provider is installed after construction`() {
        // ClassicalProvider init calls BouncyCastleInstaller.ensureInstalled()
        val bc = java.security.Security.getProvider("BC")
        assertThat(bc).isNotNull()
        assertThat(bc.javaClass.name).contains("BouncyCastle")
    }
}
