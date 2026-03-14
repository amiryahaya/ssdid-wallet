package my.ssdid.wallet.domain.crypto

interface KeyAgreementProvider {
    fun generateKeyPair(): KeyPairResult
    fun deriveSharedSecret(privateKey: ByteArray, peerPublicKey: ByteArray): ByteArray
}
