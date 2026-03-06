package my.ssdid.mobile.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AlgorithmTest {
    @Test
    fun `classical algorithms are not post-quantum`() {
        assertThat(Algorithm.ED25519.isPostQuantum).isFalse()
        assertThat(Algorithm.ECDSA_P256.isPostQuantum).isFalse()
        assertThat(Algorithm.ECDSA_P384.isPostQuantum).isFalse()
    }

    @Test
    fun `kaz-sign algorithms are post-quantum with correct levels`() {
        assertThat(Algorithm.KAZ_SIGN_128.isPostQuantum).isTrue()
        assertThat(Algorithm.KAZ_SIGN_128.kazSignLevel).isEqualTo(128)
        assertThat(Algorithm.KAZ_SIGN_192.kazSignLevel).isEqualTo(192)
        assertThat(Algorithm.KAZ_SIGN_256.kazSignLevel).isEqualTo(256)
    }

    @Test
    fun `w3c types are correct`() {
        assertThat(Algorithm.ED25519.w3cType).isEqualTo("Ed25519VerificationKey2020")
        assertThat(Algorithm.KAZ_SIGN_192.w3cType).isEqualTo("KazSignVerificationKey2024")
    }
}
