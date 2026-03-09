package my.ssdid.wallet.domain.crypto

import my.ssdid.wallet.domain.crypto.kazsign.KazSigner
import my.ssdid.wallet.domain.crypto.kazsign.SecurityLevel
import my.ssdid.wallet.domain.model.Algorithm
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

class PqcProvider : CryptoProvider {

    init {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    override fun supportsAlgorithm(algorithm: Algorithm): Boolean = algorithm.isPostQuantum

    override fun generateKeyPair(algorithm: Algorithm): KeyPairResult {
        require(supportsAlgorithm(algorithm)) { "Unsupported: $algorithm" }
        return when {
            algorithm.isKazSign -> generateKazSign(algorithm)
            algorithm.isMlDsa -> generateJca("ML-DSA", algorithm.mlDsaParamSpec())
            algorithm.isSlhDsa -> generateJca("SLH-DSA", algorithm.slhDsaParamSpec())
            else -> throw IllegalArgumentException("Unsupported PQC algorithm: $algorithm")
        }
    }

    override fun sign(algorithm: Algorithm, privateKey: ByteArray, data: ByteArray): ByteArray {
        require(supportsAlgorithm(algorithm)) { "Unsupported: $algorithm" }
        return when {
            algorithm.isKazSign -> signKazSign(algorithm, privateKey, data)
            algorithm.isMlDsa -> signJca("ML-DSA", privateKey, data)
            algorithm.isSlhDsa -> signJca("SLH-DSA", privateKey, data)
            else -> throw IllegalArgumentException("Unsupported PQC algorithm: $algorithm")
        }
    }

    override fun verify(algorithm: Algorithm, publicKey: ByteArray, signature: ByteArray, data: ByteArray): Boolean {
        require(supportsAlgorithm(algorithm)) { "Unsupported: $algorithm" }
        return when {
            algorithm.isKazSign -> verifyKazSign(publicKey, signature, data)
            algorithm.isMlDsa -> verifyJca("ML-DSA", publicKey, signature, data)
            algorithm.isSlhDsa -> verifyJca("SLH-DSA", publicKey, signature, data)
            else -> false
        }
    }

    // --- KAZ-Sign (native JNI) ---

    private fun generateKazSign(algorithm: Algorithm): KeyPairResult {
        val level = algorithm.toSecurityLevel()
        KazSigner(level).use { signer ->
            val kp = signer.generateKeyPair()
            return KeyPairResult(publicKey = kp.publicKey, privateKey = kp.secretKey)
        }
    }

    private fun signKazSign(algorithm: Algorithm, privateKey: ByteArray, data: ByteArray): ByteArray {
        val level = algorithm.toSecurityLevel()
        KazSigner(level).use { signer ->
            return signer.signDetached(data, privateKey)
        }
    }

    /**
     * Verifies KAZ-Sign signature, detecting security level from public key size.
     */
    private fun verifyKazSign(publicKey: ByteArray, signature: ByteArray, data: ByteArray): Boolean {
        val level = SecurityLevel.entries.firstOrNull { it.publicKeyBytes == publicKey.size }
            ?: throw IllegalArgumentException("Unknown KAZ-Sign public key size: ${publicKey.size}")
        KazSigner(level).use { signer ->
            return signer.verifyDetached(data, signature, publicKey)
        }
    }

    // --- ML-DSA / SLH-DSA (BouncyCastle PQC via JCA) ---

    private fun generateJca(jcaAlgorithm: String, paramSpec: java.security.spec.AlgorithmParameterSpec): KeyPairResult {
        val kpg = KeyPairGenerator.getInstance(jcaAlgorithm, "BC")
        kpg.initialize(paramSpec)
        val kp = kpg.generateKeyPair()
        return KeyPairResult(publicKey = kp.public.encoded, privateKey = kp.private.encoded)
    }

    private fun signJca(jcaAlgorithm: String, privateKey: ByteArray, data: ByteArray): ByteArray {
        val kf = KeyFactory.getInstance(jcaAlgorithm, "BC")
        val privKey = kf.generatePrivate(PKCS8EncodedKeySpec(privateKey))
        val sig = Signature.getInstance(jcaAlgorithm, "BC")
        sig.initSign(privKey)
        sig.update(data)
        return sig.sign()
    }

    private fun verifyJca(jcaAlgorithm: String, publicKey: ByteArray, signature: ByteArray, data: ByteArray): Boolean {
        val kf = KeyFactory.getInstance(jcaAlgorithm, "BC")
        val pubKey = kf.generatePublic(X509EncodedKeySpec(publicKey))
        val sig = Signature.getInstance(jcaAlgorithm, "BC")
        sig.initVerify(pubKey)
        sig.update(data)
        return sig.verify(signature)
    }

    // --- Parameter spec mappings ---

    private fun Algorithm.toSecurityLevel(): SecurityLevel = when (this) {
        Algorithm.KAZ_SIGN_128 -> SecurityLevel.LEVEL_128
        Algorithm.KAZ_SIGN_192 -> SecurityLevel.LEVEL_192
        Algorithm.KAZ_SIGN_256 -> SecurityLevel.LEVEL_256
        else -> throw IllegalArgumentException("Not a KAZ-Sign algorithm: $this")
    }

    private fun Algorithm.mlDsaParamSpec(): java.security.spec.AlgorithmParameterSpec = when (this) {
        Algorithm.ML_DSA_44 -> org.bouncycastle.jcajce.spec.MLDSAParameterSpec.ml_dsa_44
        Algorithm.ML_DSA_65 -> org.bouncycastle.jcajce.spec.MLDSAParameterSpec.ml_dsa_65
        Algorithm.ML_DSA_87 -> org.bouncycastle.jcajce.spec.MLDSAParameterSpec.ml_dsa_87
        else -> throw IllegalArgumentException("Not an ML-DSA algorithm: $this")
    }

    private fun Algorithm.slhDsaParamSpec(): java.security.spec.AlgorithmParameterSpec = when (this) {
        Algorithm.SLH_DSA_SHA2_128S -> org.bouncycastle.jcajce.spec.SLHDSAParameterSpec.slh_dsa_sha2_128s
        Algorithm.SLH_DSA_SHA2_128F -> org.bouncycastle.jcajce.spec.SLHDSAParameterSpec.slh_dsa_sha2_128f
        Algorithm.SLH_DSA_SHA2_192S -> org.bouncycastle.jcajce.spec.SLHDSAParameterSpec.slh_dsa_sha2_192s
        Algorithm.SLH_DSA_SHA2_192F -> org.bouncycastle.jcajce.spec.SLHDSAParameterSpec.slh_dsa_sha2_192f
        Algorithm.SLH_DSA_SHA2_256S -> org.bouncycastle.jcajce.spec.SLHDSAParameterSpec.slh_dsa_sha2_256s
        Algorithm.SLH_DSA_SHA2_256F -> org.bouncycastle.jcajce.spec.SLHDSAParameterSpec.slh_dsa_sha2_256f
        Algorithm.SLH_DSA_SHAKE_128S -> org.bouncycastle.jcajce.spec.SLHDSAParameterSpec.slh_dsa_shake_128s
        Algorithm.SLH_DSA_SHAKE_128F -> org.bouncycastle.jcajce.spec.SLHDSAParameterSpec.slh_dsa_shake_128f
        Algorithm.SLH_DSA_SHAKE_192S -> org.bouncycastle.jcajce.spec.SLHDSAParameterSpec.slh_dsa_shake_192s
        Algorithm.SLH_DSA_SHAKE_192F -> org.bouncycastle.jcajce.spec.SLHDSAParameterSpec.slh_dsa_shake_192f
        Algorithm.SLH_DSA_SHAKE_256S -> org.bouncycastle.jcajce.spec.SLHDSAParameterSpec.slh_dsa_shake_256s
        Algorithm.SLH_DSA_SHAKE_256F -> org.bouncycastle.jcajce.spec.SLHDSAParameterSpec.slh_dsa_shake_256f
        else -> throw IllegalArgumentException("Not an SLH-DSA algorithm: $this")
    }
}
