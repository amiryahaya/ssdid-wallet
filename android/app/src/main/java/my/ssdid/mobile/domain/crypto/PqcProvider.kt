package my.ssdid.mobile.domain.crypto

import my.ssdid.mobile.domain.crypto.kazsign.KazSigner
import my.ssdid.mobile.domain.crypto.kazsign.SecurityLevel
import my.ssdid.mobile.domain.model.Algorithm

class PqcProvider : CryptoProvider {

    override fun supportsAlgorithm(algorithm: Algorithm): Boolean = algorithm.isPostQuantum

    override fun generateKeyPair(algorithm: Algorithm): KeyPairResult {
        require(supportsAlgorithm(algorithm)) { "Unsupported: $algorithm" }
        val level = algorithm.toSecurityLevel()
        KazSigner(level).use { signer ->
            val kp = signer.generateKeyPair()
            return KeyPairResult(publicKey = kp.publicKey, privateKey = kp.secretKey)
        }
    }

    override fun sign(algorithm: Algorithm, privateKey: ByteArray, data: ByteArray): ByteArray {
        require(supportsAlgorithm(algorithm)) { "Unsupported: $algorithm" }
        val level = algorithm.toSecurityLevel()
        KazSigner(level).use { signer ->
            return signer.signDetached(data, privateKey)
        }
    }

    override fun verify(algorithm: Algorithm, publicKey: ByteArray, signature: ByteArray, data: ByteArray): Boolean {
        require(supportsAlgorithm(algorithm)) { "Unsupported: $algorithm" }
        val level = algorithm.toSecurityLevel()
        KazSigner(level).use { signer ->
            return signer.verifyDetached(data, signature, publicKey)
        }
    }

    private fun Algorithm.toSecurityLevel(): SecurityLevel = when (this) {
        Algorithm.KAZ_SIGN_128 -> SecurityLevel.LEVEL_128
        Algorithm.KAZ_SIGN_192 -> SecurityLevel.LEVEL_192
        Algorithm.KAZ_SIGN_256 -> SecurityLevel.LEVEL_256
        else -> throw IllegalArgumentException("Not a KAZ-Sign algorithm: $this")
    }
}
