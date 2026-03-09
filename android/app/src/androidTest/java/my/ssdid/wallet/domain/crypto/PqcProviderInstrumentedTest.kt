package my.ssdid.wallet.domain.crypto

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import my.ssdid.wallet.domain.model.Algorithm
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for ML-DSA (FIPS 204) and SLH-DSA (FIPS 205) via PqcProvider.
 * Runs on device/emulator to verify BouncyCastle JCA works on Android runtime.
 */
@RunWith(AndroidJUnit4::class)
class PqcProviderInstrumentedTest {

    private val provider = PqcProvider()

    // ==================== ML-DSA (FIPS 204) ====================

    @Test
    fun mlDsa44_keygenSignVerify() {
        val kp = provider.generateKeyPair(Algorithm.ML_DSA_44)
        assertThat(kp.publicKey).isNotEmpty()
        assertThat(kp.privateKey).isNotEmpty()

        val data = "ML-DSA-44 on Android".toByteArray()
        val sig = provider.sign(Algorithm.ML_DSA_44, kp.privateKey, data)
        assertThat(sig).isNotEmpty()

        assertThat(provider.verify(Algorithm.ML_DSA_44, kp.publicKey, sig, data)).isTrue()
    }

    @Test
    fun mlDsa65_keygenSignVerify() {
        val kp = provider.generateKeyPair(Algorithm.ML_DSA_65)
        assertThat(kp.publicKey).isNotEmpty()
        assertThat(kp.privateKey).isNotEmpty()

        val data = "ML-DSA-65 on Android".toByteArray()
        val sig = provider.sign(Algorithm.ML_DSA_65, kp.privateKey, data)
        assertThat(sig).isNotEmpty()

        assertThat(provider.verify(Algorithm.ML_DSA_65, kp.publicKey, sig, data)).isTrue()
    }

    @Test
    fun mlDsa87_keygenSignVerify() {
        val kp = provider.generateKeyPair(Algorithm.ML_DSA_87)
        assertThat(kp.publicKey).isNotEmpty()
        assertThat(kp.privateKey).isNotEmpty()

        val data = "ML-DSA-87 on Android".toByteArray()
        val sig = provider.sign(Algorithm.ML_DSA_87, kp.privateKey, data)
        assertThat(sig).isNotEmpty()

        assertThat(provider.verify(Algorithm.ML_DSA_87, kp.publicKey, sig, data)).isTrue()
    }

    @Test
    fun mlDsa_verifyRejectsWrongData() {
        val kp = provider.generateKeyPair(Algorithm.ML_DSA_44)
        val sig = provider.sign(Algorithm.ML_DSA_44, kp.privateKey, "correct".toByteArray())
        assertThat(provider.verify(Algorithm.ML_DSA_44, kp.publicKey, sig, "wrong".toByteArray())).isFalse()
    }

    @Test
    fun mlDsa_verifyRejectsWrongKey() {
        val kp1 = provider.generateKeyPair(Algorithm.ML_DSA_44)
        val kp2 = provider.generateKeyPair(Algorithm.ML_DSA_44)
        val data = "cross-key test".toByteArray()
        val sig = provider.sign(Algorithm.ML_DSA_44, kp1.privateKey, data)
        assertThat(provider.verify(Algorithm.ML_DSA_44, kp2.publicKey, sig, data)).isFalse()
    }

    @Test
    fun mlDsa_emptyMessage() {
        val kp = provider.generateKeyPair(Algorithm.ML_DSA_44)
        val data = byteArrayOf()
        val sig = provider.sign(Algorithm.ML_DSA_44, kp.privateKey, data)
        assertThat(provider.verify(Algorithm.ML_DSA_44, kp.publicKey, sig, data)).isTrue()
    }

    @Test
    fun mlDsa_largeMessage() {
        val kp = provider.generateKeyPair(Algorithm.ML_DSA_44)
        val data = ByteArray(100_000) { it.toByte() }
        val sig = provider.sign(Algorithm.ML_DSA_44, kp.privateKey, data)
        assertThat(provider.verify(Algorithm.ML_DSA_44, kp.publicKey, sig, data)).isTrue()
    }

    // ==================== SLH-DSA SHA-2 (FIPS 205) ====================

    @Test
    fun slhDsaSha2_128s_keygenSignVerify() {
        val kp = provider.generateKeyPair(Algorithm.SLH_DSA_SHA2_128S)
        assertThat(kp.publicKey).isNotEmpty()
        assertThat(kp.privateKey).isNotEmpty()

        val data = "SLH-DSA-SHA2-128s on Android".toByteArray()
        val sig = provider.sign(Algorithm.SLH_DSA_SHA2_128S, kp.privateKey, data)
        assertThat(sig).isNotEmpty()

        assertThat(provider.verify(Algorithm.SLH_DSA_SHA2_128S, kp.publicKey, sig, data)).isTrue()
    }

    @Test
    fun slhDsaSha2_128f_keygenSignVerify() {
        val kp = provider.generateKeyPair(Algorithm.SLH_DSA_SHA2_128F)
        val data = "SLH-DSA-SHA2-128f on Android".toByteArray()
        val sig = provider.sign(Algorithm.SLH_DSA_SHA2_128F, kp.privateKey, data)
        assertThat(provider.verify(Algorithm.SLH_DSA_SHA2_128F, kp.publicKey, sig, data)).isTrue()
    }

    @Test
    fun slhDsaSha2_192s_keygenSignVerify() {
        val kp = provider.generateKeyPair(Algorithm.SLH_DSA_SHA2_192S)
        val data = "SLH-DSA-SHA2-192s on Android".toByteArray()
        val sig = provider.sign(Algorithm.SLH_DSA_SHA2_192S, kp.privateKey, data)
        assertThat(provider.verify(Algorithm.SLH_DSA_SHA2_192S, kp.publicKey, sig, data)).isTrue()
    }

    @Test
    fun slhDsaSha2_192f_keygenSignVerify() {
        val kp = provider.generateKeyPair(Algorithm.SLH_DSA_SHA2_192F)
        val data = "SLH-DSA-SHA2-192f on Android".toByteArray()
        val sig = provider.sign(Algorithm.SLH_DSA_SHA2_192F, kp.privateKey, data)
        assertThat(provider.verify(Algorithm.SLH_DSA_SHA2_192F, kp.publicKey, sig, data)).isTrue()
    }

    @Test
    fun slhDsaSha2_256s_keygenSignVerify() {
        val kp = provider.generateKeyPair(Algorithm.SLH_DSA_SHA2_256S)
        val data = "SLH-DSA-SHA2-256s on Android".toByteArray()
        val sig = provider.sign(Algorithm.SLH_DSA_SHA2_256S, kp.privateKey, data)
        assertThat(provider.verify(Algorithm.SLH_DSA_SHA2_256S, kp.publicKey, sig, data)).isTrue()
    }

    @Test
    fun slhDsaSha2_256f_keygenSignVerify() {
        val kp = provider.generateKeyPair(Algorithm.SLH_DSA_SHA2_256F)
        val data = "SLH-DSA-SHA2-256f on Android".toByteArray()
        val sig = provider.sign(Algorithm.SLH_DSA_SHA2_256F, kp.privateKey, data)
        assertThat(provider.verify(Algorithm.SLH_DSA_SHA2_256F, kp.publicKey, sig, data)).isTrue()
    }

    // ==================== SLH-DSA SHAKE (FIPS 205) ====================

    @Test
    fun slhDsaShake_128s_keygenSignVerify() {
        val kp = provider.generateKeyPair(Algorithm.SLH_DSA_SHAKE_128S)
        val data = "SLH-DSA-SHAKE-128s on Android".toByteArray()
        val sig = provider.sign(Algorithm.SLH_DSA_SHAKE_128S, kp.privateKey, data)
        assertThat(provider.verify(Algorithm.SLH_DSA_SHAKE_128S, kp.publicKey, sig, data)).isTrue()
    }

    @Test
    fun slhDsaShake_128f_keygenSignVerify() {
        val kp = provider.generateKeyPair(Algorithm.SLH_DSA_SHAKE_128F)
        val data = "SLH-DSA-SHAKE-128f on Android".toByteArray()
        val sig = provider.sign(Algorithm.SLH_DSA_SHAKE_128F, kp.privateKey, data)
        assertThat(provider.verify(Algorithm.SLH_DSA_SHAKE_128F, kp.publicKey, sig, data)).isTrue()
    }

    @Test
    fun slhDsaShake_192s_keygenSignVerify() {
        val kp = provider.generateKeyPair(Algorithm.SLH_DSA_SHAKE_192S)
        val data = "SLH-DSA-SHAKE-192s on Android".toByteArray()
        val sig = provider.sign(Algorithm.SLH_DSA_SHAKE_192S, kp.privateKey, data)
        assertThat(provider.verify(Algorithm.SLH_DSA_SHAKE_192S, kp.publicKey, sig, data)).isTrue()
    }

    @Test
    fun slhDsaShake_192f_keygenSignVerify() {
        val kp = provider.generateKeyPair(Algorithm.SLH_DSA_SHAKE_192F)
        val data = "SLH-DSA-SHAKE-192f on Android".toByteArray()
        val sig = provider.sign(Algorithm.SLH_DSA_SHAKE_192F, kp.privateKey, data)
        assertThat(provider.verify(Algorithm.SLH_DSA_SHAKE_192F, kp.publicKey, sig, data)).isTrue()
    }

    @Test
    fun slhDsaShake_256s_keygenSignVerify() {
        val kp = provider.generateKeyPair(Algorithm.SLH_DSA_SHAKE_256S)
        val data = "SLH-DSA-SHAKE-256s on Android".toByteArray()
        val sig = provider.sign(Algorithm.SLH_DSA_SHAKE_256S, kp.privateKey, data)
        assertThat(provider.verify(Algorithm.SLH_DSA_SHAKE_256S, kp.publicKey, sig, data)).isTrue()
    }

    @Test
    fun slhDsaShake_256f_keygenSignVerify() {
        val kp = provider.generateKeyPair(Algorithm.SLH_DSA_SHAKE_256F)
        val data = "SLH-DSA-SHAKE-256f on Android".toByteArray()
        val sig = provider.sign(Algorithm.SLH_DSA_SHAKE_256F, kp.privateKey, data)
        assertThat(provider.verify(Algorithm.SLH_DSA_SHAKE_256F, kp.publicKey, sig, data)).isTrue()
    }

    // ==================== SLH-DSA rejection tests ====================

    @Test
    fun slhDsa_verifyRejectsWrongData() {
        val kp = provider.generateKeyPair(Algorithm.SLH_DSA_SHA2_128F)
        val sig = provider.sign(Algorithm.SLH_DSA_SHA2_128F, kp.privateKey, "correct".toByteArray())
        assertThat(provider.verify(Algorithm.SLH_DSA_SHA2_128F, kp.publicKey, sig, "wrong".toByteArray())).isFalse()
    }

    @Test
    fun slhDsa_verifyRejectsWrongKey() {
        val kp1 = provider.generateKeyPair(Algorithm.SLH_DSA_SHA2_128F)
        val kp2 = provider.generateKeyPair(Algorithm.SLH_DSA_SHA2_128F)
        val data = "cross-key test".toByteArray()
        val sig = provider.sign(Algorithm.SLH_DSA_SHA2_128F, kp1.privateKey, data)
        assertThat(provider.verify(Algorithm.SLH_DSA_SHA2_128F, kp2.publicKey, sig, data)).isFalse()
    }

    // ==================== KAZ-Sign via PqcProvider ====================

    @Test
    fun kazSign128_viaPqcProvider() {
        val kp = provider.generateKeyPair(Algorithm.KAZ_SIGN_128)
        assertThat(kp.publicKey.size).isEqualTo(54)
        assertThat(kp.privateKey.size).isEqualTo(32)

        val data = "KAZ-Sign via PqcProvider".toByteArray()
        val sig = provider.sign(Algorithm.KAZ_SIGN_128, kp.privateKey, data)
        assertThat(sig.size).isEqualTo(162) // detached signature overhead

        assertThat(provider.verify(Algorithm.KAZ_SIGN_128, kp.publicKey, sig, data)).isTrue()
    }
}
