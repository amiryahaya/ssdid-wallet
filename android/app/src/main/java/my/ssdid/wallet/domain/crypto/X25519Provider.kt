package my.ssdid.wallet.domain.crypto

import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.security.SecureRandom

class X25519Provider : KeyAgreementProvider {
    override fun generateKeyPair(): KeyPairResult {
        val generator = X25519KeyPairGenerator()
        generator.init(X25519KeyGenerationParameters(SecureRandom()))
        val keyPair = generator.generateKeyPair()
        val privateKey = (keyPair.private as X25519PrivateKeyParameters).encoded
        val publicKey = (keyPair.public as X25519PublicKeyParameters).encoded
        return KeyPairResult(publicKey = publicKey, privateKey = privateKey)
    }

    override fun deriveSharedSecret(privateKey: ByteArray, peerPublicKey: ByteArray): ByteArray {
        val agreement = X25519Agreement()
        agreement.init(X25519PrivateKeyParameters(privateKey, 0))
        val secret = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(X25519PublicKeyParameters(peerPublicKey, 0), secret, 0)
        return secret
    }
}
