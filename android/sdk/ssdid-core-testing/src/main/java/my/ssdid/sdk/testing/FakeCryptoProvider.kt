package my.ssdid.sdk.testing

import my.ssdid.sdk.domain.crypto.CryptoProvider
import my.ssdid.sdk.domain.crypto.KeyPairResult
import my.ssdid.sdk.domain.model.Algorithm
import java.security.MessageDigest

class FakeCryptoProvider(
    private val supportedAlgorithms: Set<Algorithm> = setOf(Algorithm.ED25519, Algorithm.ECDSA_P256)
) : CryptoProvider {
    override fun supportsAlgorithm(algorithm: Algorithm): Boolean = algorithm in supportedAlgorithms

    override fun generateKeyPair(algorithm: Algorithm): KeyPairResult {
        require(supportsAlgorithm(algorithm)) { "Unsupported algorithm: $algorithm" }
        val publicKey = ByteArray(32) { (it + 1).toByte() }
        val privateKey = ByteArray(64) { (it + 100).toByte() }
        return KeyPairResult(publicKey, privateKey)
    }

    override fun sign(algorithm: Algorithm, privateKey: ByteArray, data: ByteArray): ByteArray {
        // Deterministic fake signature: SHA-256(privateKey + data)
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(privateKey)
        digest.update(data)
        return digest.digest()
    }

    override fun verify(algorithm: Algorithm, publicKey: ByteArray, signature: ByteArray, data: ByteArray): Boolean {
        // Always returns true for testing
        return true
    }
}
