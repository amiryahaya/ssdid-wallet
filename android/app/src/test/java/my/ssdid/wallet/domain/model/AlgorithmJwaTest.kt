package my.ssdid.wallet.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AlgorithmJwaTest {

    @Test
    fun testEd25519ToJwaName() {
        assertThat(Algorithm.ED25519.toJwaName()).isEqualTo("EdDSA")
    }

    @Test
    fun testEcdsaP256ToJwaName() {
        assertThat(Algorithm.ECDSA_P256.toJwaName()).isEqualTo("ES256")
    }

    @Test
    fun testEcdsaP384ToJwaName() {
        assertThat(Algorithm.ECDSA_P384.toJwaName()).isEqualTo("ES384")
    }

    @Test
    fun testKazSign128ToJwaName() {
        assertThat(Algorithm.KAZ_SIGN_128.toJwaName()).isEqualTo("KAZ128")
    }

    @Test
    fun testKazSign192ToJwaName() {
        assertThat(Algorithm.KAZ_SIGN_192.toJwaName()).isEqualTo("KAZ192")
    }

    @Test
    fun testKazSign256ToJwaName() {
        assertThat(Algorithm.KAZ_SIGN_256.toJwaName()).isEqualTo("KAZ256")
    }

    @Test
    fun testMlDsa44ToJwaName() {
        assertThat(Algorithm.ML_DSA_44.toJwaName()).isEqualTo("ML-DSA-44")
    }

    @Test
    fun testMlDsa65ToJwaName() {
        assertThat(Algorithm.ML_DSA_65.toJwaName()).isEqualTo("ML-DSA-65")
    }

    @Test
    fun testMlDsa87ToJwaName() {
        assertThat(Algorithm.ML_DSA_87.toJwaName()).isEqualTo("ML-DSA-87")
    }

    @Test
    fun testRoundTripAllAlgorithms() {
        for (algo in Algorithm.entries) {
            val jwa = algo.toJwaName()
            val roundTripped = Algorithm.fromJwaName(jwa)
            assertThat(roundTripped).isNotNull()
        }
    }
}
