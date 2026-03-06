package my.ssdid.mobile.domain.crypto

import com.google.common.truth.Truth.assertThat
import my.ssdid.mobile.domain.model.Algorithm
import org.junit.Test

class PqcProviderTest {
    @Test
    fun `supports only post-quantum algorithms`() {
        val provider = PqcProvider()
        assertThat(provider.supportsAlgorithm(Algorithm.KAZ_SIGN_128)).isTrue()
        assertThat(provider.supportsAlgorithm(Algorithm.KAZ_SIGN_192)).isTrue()
        assertThat(provider.supportsAlgorithm(Algorithm.KAZ_SIGN_256)).isTrue()
        assertThat(provider.supportsAlgorithm(Algorithm.ED25519)).isFalse()
        assertThat(provider.supportsAlgorithm(Algorithm.ECDSA_P256)).isFalse()
        assertThat(provider.supportsAlgorithm(Algorithm.ECDSA_P384)).isFalse()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects non-PQC algorithm for key generation`() {
        val provider = PqcProvider()
        provider.generateKeyPair(Algorithm.ED25519)
    }
}
