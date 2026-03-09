package my.ssdid.wallet.domain.crypto

import com.google.common.truth.Truth.assertThat
import my.ssdid.wallet.domain.model.Algorithm
import org.junit.Test

class PqcProviderTest {
    private val provider = PqcProvider()

    @Test
    fun `supports only post-quantum algorithms`() {
        assertThat(provider.supportsAlgorithm(Algorithm.KAZ_SIGN_128)).isTrue()
        assertThat(provider.supportsAlgorithm(Algorithm.KAZ_SIGN_192)).isTrue()
        assertThat(provider.supportsAlgorithm(Algorithm.KAZ_SIGN_256)).isTrue()
        assertThat(provider.supportsAlgorithm(Algorithm.ML_DSA_44)).isTrue()
        assertThat(provider.supportsAlgorithm(Algorithm.ML_DSA_65)).isTrue()
        assertThat(provider.supportsAlgorithm(Algorithm.ML_DSA_87)).isTrue()
        assertThat(provider.supportsAlgorithm(Algorithm.SLH_DSA_SHA2_128S)).isTrue()
        assertThat(provider.supportsAlgorithm(Algorithm.ED25519)).isFalse()
        assertThat(provider.supportsAlgorithm(Algorithm.ECDSA_P256)).isFalse()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects non-PQC algorithm for key generation`() {
        provider.generateKeyPair(Algorithm.ED25519)
    }

    // --- ML-DSA ---

    @Test
    fun `ML-DSA-44 keygen sign verify round-trip`() {
        val kp = provider.generateKeyPair(Algorithm.ML_DSA_44)
        assertThat(kp.publicKey).isNotEmpty()
        assertThat(kp.privateKey).isNotEmpty()

        val data = "ML-DSA-44 test message".toByteArray()
        val sig = provider.sign(Algorithm.ML_DSA_44, kp.privateKey, data)
        assertThat(sig).isNotEmpty()

        val valid = provider.verify(Algorithm.ML_DSA_44, kp.publicKey, sig, data)
        assertThat(valid).isTrue()
    }

    @Test
    fun `ML-DSA-65 keygen sign verify round-trip`() {
        val kp = provider.generateKeyPair(Algorithm.ML_DSA_65)
        val data = "ML-DSA-65 test".toByteArray()
        val sig = provider.sign(Algorithm.ML_DSA_65, kp.privateKey, data)
        assertThat(provider.verify(Algorithm.ML_DSA_65, kp.publicKey, sig, data)).isTrue()
    }

    @Test
    fun `ML-DSA-87 keygen sign verify round-trip`() {
        val kp = provider.generateKeyPair(Algorithm.ML_DSA_87)
        val data = "ML-DSA-87 test".toByteArray()
        val sig = provider.sign(Algorithm.ML_DSA_87, kp.privateKey, data)
        assertThat(provider.verify(Algorithm.ML_DSA_87, kp.publicKey, sig, data)).isTrue()
    }

    @Test
    fun `ML-DSA verify fails with wrong data`() {
        val kp = provider.generateKeyPair(Algorithm.ML_DSA_44)
        val sig = provider.sign(Algorithm.ML_DSA_44, kp.privateKey, "correct".toByteArray())
        assertThat(provider.verify(Algorithm.ML_DSA_44, kp.publicKey, sig, "wrong".toByteArray())).isFalse()
    }

    @Test
    fun `ML-DSA verify fails with wrong key`() {
        val kp1 = provider.generateKeyPair(Algorithm.ML_DSA_44)
        val kp2 = provider.generateKeyPair(Algorithm.ML_DSA_44)
        val data = "test".toByteArray()
        val sig = provider.sign(Algorithm.ML_DSA_44, kp1.privateKey, data)
        assertThat(provider.verify(Algorithm.ML_DSA_44, kp2.publicKey, sig, data)).isFalse()
    }

    // --- SLH-DSA ---

    @Test
    fun `SLH-DSA-SHA2-128s keygen sign verify round-trip`() {
        val kp = provider.generateKeyPair(Algorithm.SLH_DSA_SHA2_128S)
        assertThat(kp.publicKey).isNotEmpty()

        val data = "SLH-DSA test".toByteArray()
        val sig = provider.sign(Algorithm.SLH_DSA_SHA2_128S, kp.privateKey, data)
        assertThat(provider.verify(Algorithm.SLH_DSA_SHA2_128S, kp.publicKey, sig, data)).isTrue()
    }

    @Test
    fun `SLH-DSA-SHAKE-128f keygen sign verify round-trip`() {
        val kp = provider.generateKeyPair(Algorithm.SLH_DSA_SHAKE_128F)
        val data = "SLH-DSA SHAKE test".toByteArray()
        val sig = provider.sign(Algorithm.SLH_DSA_SHAKE_128F, kp.privateKey, data)
        assertThat(provider.verify(Algorithm.SLH_DSA_SHAKE_128F, kp.publicKey, sig, data)).isTrue()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `verify throws for unsupported classical algorithm`() {
        provider.verify(Algorithm.ED25519, ByteArray(32), ByteArray(64), ByteArray(10))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `sign throws for unsupported classical algorithm`() {
        provider.sign(Algorithm.ED25519, ByteArray(32), ByteArray(10))
    }

    @Test
    fun `BouncyCastle provider is installed after construction`() {
        val bc = java.security.Security.getProvider("BC")
        assertThat(bc).isNotNull()
        assertThat(bc.javaClass.name).contains("BouncyCastle")
    }

    @Test
    fun `BouncyCastle installer is idempotent`() {
        // Creating multiple providers should not register duplicate BC instances
        val p1 = PqcProvider()
        val p2 = PqcProvider()
        val providers = java.security.Security.getProviders().filter { it.name == "BC" }
        assertThat(providers).hasSize(1)
    }
}
