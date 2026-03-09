package my.ssdid.wallet.domain.model

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

    @Test
    fun `kazSignFromKeySize returns correct algorithm for exact sizes`() {
        assertThat(Algorithm.kazSignFromKeySize(54)).isEqualTo(Algorithm.KAZ_SIGN_128)
        assertThat(Algorithm.kazSignFromKeySize(88)).isEqualTo(Algorithm.KAZ_SIGN_192)
        assertThat(Algorithm.kazSignFromKeySize(118)).isEqualTo(Algorithm.KAZ_SIGN_256)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `kazSignFromKeySize rejects invalid key size`() {
        Algorithm.kazSignFromKeySize(200)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `kazSignFromKeySize rejects zero key size`() {
        Algorithm.kazSignFromKeySize(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `kazSignFromKeySize rejects size between valid levels`() {
        Algorithm.kazSignFromKeySize(70)
    }

    @Test
    fun `ml-dsa algorithms are post-quantum`() {
        assertThat(Algorithm.ML_DSA_44.isPostQuantum).isTrue()
        assertThat(Algorithm.ML_DSA_44.isMlDsa).isTrue()
        assertThat(Algorithm.ML_DSA_65.isMlDsa).isTrue()
        assertThat(Algorithm.ML_DSA_87.isMlDsa).isTrue()
    }

    @Test
    fun `slh-dsa algorithms are post-quantum`() {
        assertThat(Algorithm.SLH_DSA_SHA2_128S.isPostQuantum).isTrue()
        assertThat(Algorithm.SLH_DSA_SHA2_128S.isSlhDsa).isTrue()
        assertThat(Algorithm.SLH_DSA_SHAKE_256F.isSlhDsa).isTrue()
    }

    @Test
    fun `isKazSign returns true only for kaz-sign algorithms`() {
        assertThat(Algorithm.KAZ_SIGN_128.isKazSign).isTrue()
        assertThat(Algorithm.ED25519.isKazSign).isFalse()
        assertThat(Algorithm.ML_DSA_44.isKazSign).isFalse()
    }

    @Test
    fun `kazSignLevel is null for non-KazSign algorithms`() {
        assertThat(Algorithm.ED25519.kazSignLevel).isNull()
        assertThat(Algorithm.ECDSA_P256.kazSignLevel).isNull()
        assertThat(Algorithm.ECDSA_P384.kazSignLevel).isNull()
        assertThat(Algorithm.ML_DSA_44.kazSignLevel).isNull()
        assertThat(Algorithm.ML_DSA_65.kazSignLevel).isNull()
        assertThat(Algorithm.ML_DSA_87.kazSignLevel).isNull()
        assertThat(Algorithm.SLH_DSA_SHA2_128S.kazSignLevel).isNull()
        assertThat(Algorithm.SLH_DSA_SHAKE_256F.kazSignLevel).isNull()
    }

    @Test
    fun `all slh-dsa variants have isSlhDsa true`() {
        val slhDsaAlgorithms = Algorithm.entries.filter { it.name.startsWith("SLH_DSA") }
        assertThat(slhDsaAlgorithms).hasSize(12)
        slhDsaAlgorithms.forEach { algo ->
            assertThat(algo.isSlhDsa).isTrue()
            assertThat(algo.isPostQuantum).isTrue()
            assertThat(algo.isKazSign).isFalse()
            assertThat(algo.isMlDsa).isFalse()
        }
    }

    @Test
    fun `all ml-dsa variants have isMlDsa true`() {
        val mlDsaAlgorithms = Algorithm.entries.filter { it.name.startsWith("ML_DSA") }
        assertThat(mlDsaAlgorithms).hasSize(3)
        mlDsaAlgorithms.forEach { algo ->
            assertThat(algo.isMlDsa).isTrue()
            assertThat(algo.isPostQuantum).isTrue()
            assertThat(algo.isKazSign).isFalse()
            assertThat(algo.isSlhDsa).isFalse()
        }
    }

    @Test
    fun `proofType values are correct`() {
        assertThat(Algorithm.ED25519.proofType).isEqualTo("Ed25519Signature2020")
        assertThat(Algorithm.ECDSA_P256.proofType).isEqualTo("EcdsaSecp256r1Signature2019")
        assertThat(Algorithm.ECDSA_P384.proofType).isEqualTo("EcdsaSecp384Signature2019")
        assertThat(Algorithm.KAZ_SIGN_128.proofType).isEqualTo("KazSignSignature2024")
        assertThat(Algorithm.KAZ_SIGN_192.proofType).isEqualTo("KazSignSignature2024")
        assertThat(Algorithm.KAZ_SIGN_256.proofType).isEqualTo("KazSignSignature2024")
        assertThat(Algorithm.ML_DSA_44.proofType).isEqualTo("MlDsa44Signature2024")
        assertThat(Algorithm.ML_DSA_65.proofType).isEqualTo("MlDsa65Signature2024")
        assertThat(Algorithm.ML_DSA_87.proofType).isEqualTo("MlDsa87Signature2024")
    }

    @Test
    fun `fromW3cType returns correct algorithm`() {
        assertThat(Algorithm.fromW3cType("Ed25519VerificationKey2020")).isEqualTo(Algorithm.ED25519)
        assertThat(Algorithm.fromW3cType("EcdsaSecp256r1VerificationKey2019")).isEqualTo(Algorithm.ECDSA_P256)
        assertThat(Algorithm.fromW3cType("EcdsaSecp384VerificationKey2019")).isEqualTo(Algorithm.ECDSA_P384)
        assertThat(Algorithm.fromW3cType("MlDsa44VerificationKey2024")).isEqualTo(Algorithm.ML_DSA_44)
        assertThat(Algorithm.fromW3cType("MlDsa65VerificationKey2024")).isEqualTo(Algorithm.ML_DSA_65)
        assertThat(Algorithm.fromW3cType("MlDsa87VerificationKey2024")).isEqualTo(Algorithm.ML_DSA_87)
        // KAZ-Sign shared type returns first match (KAZ_SIGN_128 by enum order)
        assertThat(Algorithm.fromW3cType("KazSignVerificationKey2024")).isEqualTo(Algorithm.KAZ_SIGN_128)
    }

    @Test
    fun `fromW3cType returns null for unknown type`() {
        assertThat(Algorithm.fromW3cType("UnknownType2024")).isNull()
        assertThat(Algorithm.fromW3cType("")).isNull()
    }
}
