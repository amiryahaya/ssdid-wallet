package my.ssdid.wallet.domain.crypto

import my.ssdid.wallet.domain.model.Algorithm

interface CryptoProvider {
    fun supportsAlgorithm(algorithm: Algorithm): Boolean
    fun generateKeyPair(algorithm: Algorithm): KeyPairResult
    fun sign(algorithm: Algorithm, privateKey: ByteArray, data: ByteArray): ByteArray
    fun verify(algorithm: Algorithm, publicKey: ByteArray, signature: ByteArray, data: ByteArray): Boolean
}
