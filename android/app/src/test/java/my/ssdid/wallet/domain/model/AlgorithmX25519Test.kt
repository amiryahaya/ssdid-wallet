package my.ssdid.wallet.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AlgorithmX25519Test {

    @Test
    fun `X25519 has isKeyAgreement true`() {
        assertThat(Algorithm.X25519.isKeyAgreement).isTrue()
    }

    @Test
    fun `X25519 is not a signing algorithm`() {
        assertThat(Algorithm.X25519.proofType).isEmpty()
    }

    @Test
    fun `X25519 is not post-quantum`() {
        assertThat(Algorithm.X25519.isPostQuantum).isFalse()
    }

    @Test
    fun `X25519 has correct w3cType`() {
        assertThat(Algorithm.X25519.w3cType).isEqualTo("X25519KeyAgreementKey2020")
    }

    @Test
    fun `existing signing algorithms have isKeyAgreement false`() {
        val signingAlgorithms = Algorithm.entries.filter { it != Algorithm.X25519 }
        assertThat(signingAlgorithms).isNotEmpty()
        for (algo in signingAlgorithms) {
            assertThat(algo.isKeyAgreement).isFalse()
        }
    }

    @Test
    fun `X25519 JWA name is X25519`() {
        assertThat(Algorithm.X25519.toJwaName()).isEqualTo("X25519")
    }

    @Test
    fun `fromJwaName returns X25519`() {
        assertThat(Algorithm.fromJwaName("X25519")).isEqualTo(Algorithm.X25519)
    }
}
