package my.ssdid.mobile.domain.crypto

import my.ssdid.mobile.domain.model.Algorithm
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.*
import java.security.spec.*

class ClassicalProvider : CryptoProvider {

    init {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    override fun supportsAlgorithm(algorithm: Algorithm): Boolean = !algorithm.isPostQuantum

    override fun generateKeyPair(algorithm: Algorithm): KeyPairResult {
        require(supportsAlgorithm(algorithm)) { "Unsupported: $algorithm" }
        return when (algorithm) {
            Algorithm.ED25519 -> generateEd25519()
            Algorithm.ECDSA_P256 -> generateEcdsa("secp256r1")
            Algorithm.ECDSA_P384 -> generateEcdsa("secp384r1")
            else -> throw IllegalArgumentException("Unsupported: $algorithm")
        }
    }

    override fun sign(algorithm: Algorithm, privateKey: ByteArray, data: ByteArray): ByteArray {
        return when (algorithm) {
            Algorithm.ED25519 -> signEd25519(privateKey, data)
            Algorithm.ECDSA_P256 -> signEcdsa("SHA256withECDSA", privateKey, data)
            Algorithm.ECDSA_P384 -> signEcdsa("SHA384withECDSA", privateKey, data)
            else -> throw IllegalArgumentException("Unsupported: $algorithm")
        }
    }

    override fun verify(algorithm: Algorithm, publicKey: ByteArray, signature: ByteArray, data: ByteArray): Boolean {
        return when (algorithm) {
            Algorithm.ED25519 -> verifyEd25519(publicKey, signature, data)
            Algorithm.ECDSA_P256 -> verifyEcdsa("SHA256withECDSA", publicKey, signature, data)
            Algorithm.ECDSA_P384 -> verifyEcdsa("SHA384withECDSA", publicKey, signature, data)
            else -> false
        }
    }

    private fun generateEd25519(): KeyPairResult {
        val kpg = KeyPairGenerator.getInstance("Ed25519", "BC")
        val kp = kpg.generateKeyPair()
        // Extract raw 32-byte keys from encoded format using ASN.1 structure
        val pubBytes = extractEd25519PublicKeyRaw(kp.public.encoded)
        val privBytes = extractEd25519PrivateKeyRaw(kp.private.encoded)
        return KeyPairResult(publicKey = pubBytes, privateKey = privBytes)
    }

    /**
     * X.509 SubjectPublicKeyInfo for Ed25519:
     * SEQUENCE { SEQUENCE { OID 1.3.101.112 }, BIT STRING { 0x00, raw[32] } }
     * The raw key starts after the BIT STRING tag, length, and 0x00 padding byte.
     */
    private fun extractEd25519PublicKeyRaw(encoded: ByteArray): ByteArray {
        // Find BIT STRING (tag 0x03) containing the raw key
        var i = 0
        // Skip outer SEQUENCE
        require(encoded[i] == 0x30.toByte()) { "Expected SEQUENCE at start of X.509" }
        i++ ; i += derLengthSize(encoded, i)
        // Skip algorithm SEQUENCE
        require(encoded[i] == 0x30.toByte()) { "Expected algorithm SEQUENCE" }
        i++ ; val algoLen = derReadLength(encoded, i); i += derLengthSize(encoded, i) + algoLen
        // BIT STRING
        require(encoded[i] == 0x03.toByte()) { "Expected BIT STRING" }
        i++ ; val bsLen = derReadLength(encoded, i); i += derLengthSize(encoded, i)
        // Skip the 0x00 padding byte
        require(encoded[i] == 0x00.toByte()) { "Expected 0x00 BIT STRING padding" }
        i++
        val keyLen = bsLen - 1
        require(keyLen == 32) { "Expected 32-byte Ed25519 public key, got $keyLen" }
        return encoded.copyOfRange(i, i + keyLen)
    }

    /**
     * PKCS#8 for Ed25519:
     * SEQUENCE { INTEGER(0), SEQUENCE { OID }, OCTET STRING { OCTET STRING { raw[32] } } }
     */
    private fun extractEd25519PrivateKeyRaw(encoded: ByteArray): ByteArray {
        var i = 0
        // Skip outer SEQUENCE
        require(encoded[i] == 0x30.toByte()) { "Expected SEQUENCE" }
        i++ ; i += derLengthSize(encoded, i)
        // Skip version INTEGER
        require(encoded[i] == 0x02.toByte()) { "Expected INTEGER (version)" }
        i++ ; val intLen = derReadLength(encoded, i); i += derLengthSize(encoded, i) + intLen
        // Skip algorithm SEQUENCE
        require(encoded[i] == 0x30.toByte()) { "Expected algorithm SEQUENCE" }
        i++ ; val algoLen = derReadLength(encoded, i); i += derLengthSize(encoded, i) + algoLen
        // Outer OCTET STRING
        require(encoded[i] == 0x04.toByte()) { "Expected OCTET STRING (outer)" }
        i++ ; i += derLengthSize(encoded, i)
        // Inner OCTET STRING containing raw key
        require(encoded[i] == 0x04.toByte()) { "Expected OCTET STRING (inner)" }
        i++ ; val keyLen = derReadLength(encoded, i); i += derLengthSize(encoded, i)
        require(keyLen == 32) { "Expected 32-byte Ed25519 private key, got $keyLen" }
        return encoded.copyOfRange(i, i + keyLen)
    }

    private fun derReadLength(data: ByteArray, offset: Int): Int {
        val b = data[offset].toInt() and 0xFF
        return if (b < 0x80) b
        else {
            val numBytes = b and 0x7F
            var len = 0
            for (j in 1..numBytes) len = (len shl 8) or (data[offset + j].toInt() and 0xFF)
            len
        }
    }

    private fun derLengthSize(data: ByteArray, offset: Int): Int {
        val b = data[offset].toInt() and 0xFF
        return if (b < 0x80) 1 else 1 + (b and 0x7F)
    }

    private fun signEd25519(privateKey: ByteArray, data: ByteArray): ByteArray {
        val pkcs8 = wrapEd25519PrivateKey(privateKey)
        val keySpec = PKCS8EncodedKeySpec(pkcs8)
        val kf = KeyFactory.getInstance("Ed25519", "BC")
        val privKey = kf.generatePrivate(keySpec)
        val sig = Signature.getInstance("Ed25519", "BC")
        sig.initSign(privKey)
        sig.update(data)
        return sig.sign()
    }

    private fun verifyEd25519(publicKey: ByteArray, signature: ByteArray, data: ByteArray): Boolean {
        val x509 = wrapEd25519PublicKey(publicKey)
        val keySpec = X509EncodedKeySpec(x509)
        val kf = KeyFactory.getInstance("Ed25519", "BC")
        val pubKey = kf.generatePublic(keySpec)
        val sig = Signature.getInstance("Ed25519", "BC")
        sig.initVerify(pubKey)
        sig.update(data)
        return sig.verify(signature)
    }

    // Ed25519 ASN.1 DER wrapping
    private fun wrapEd25519PrivateKey(raw: ByteArray): ByteArray {
        // PKCS#8: SEQUENCE { INTEGER(0), SEQUENCE { OID 1.3.101.112 }, OCTET STRING { OCTET STRING { raw } } }
        val version = byteArrayOf(0x02, 0x01, 0x00) // INTEGER 0
        val oid = byteArrayOf(0x06, 0x03, 0x2b, 0x65, 0x70)
        val algoSeq = byteArrayOf(0x30, oid.size.toByte()) + oid
        val innerOctet = byteArrayOf(0x04, raw.size.toByte()) + raw
        val outerOctet = byteArrayOf(0x04, innerOctet.size.toByte()) + innerOctet
        val total = version + algoSeq + outerOctet
        return byteArrayOf(0x30, total.size.toByte()) + total
    }

    private fun wrapEd25519PublicKey(raw: ByteArray): ByteArray {
        // X.509 SubjectPublicKeyInfo: SEQUENCE { SEQUENCE { OID }, BIT STRING { raw } }
        val oid = byteArrayOf(0x06, 0x03, 0x2b, 0x65, 0x70)
        val algoSeq = byteArrayOf(0x30, oid.size.toByte()) + oid
        val bitString = byteArrayOf(0x03, (raw.size + 1).toByte(), 0x00) + raw
        val total = algoSeq + bitString
        return byteArrayOf(0x30, total.size.toByte()) + total
    }

    private fun generateEcdsa(curveName: String): KeyPairResult {
        val kpg = KeyPairGenerator.getInstance("EC", "BC")
        kpg.initialize(ECGenParameterSpec(curveName))
        val kp = kpg.generateKeyPair()
        return KeyPairResult(publicKey = kp.public.encoded, privateKey = kp.private.encoded)
    }

    private fun signEcdsa(sigAlgo: String, privateKey: ByteArray, data: ByteArray): ByteArray {
        val keySpec = PKCS8EncodedKeySpec(privateKey)
        val kf = KeyFactory.getInstance("EC", "BC")
        val privKey = kf.generatePrivate(keySpec)
        val sig = Signature.getInstance(sigAlgo, "BC")
        sig.initSign(privKey)
        sig.update(data)
        return sig.sign()
    }

    private fun verifyEcdsa(sigAlgo: String, publicKey: ByteArray, signature: ByteArray, data: ByteArray): Boolean {
        val keySpec = X509EncodedKeySpec(publicKey)
        val kf = KeyFactory.getInstance("EC", "BC")
        val pubKey = kf.generatePublic(keySpec)
        val sig = Signature.getInstance(sigAlgo, "BC")
        sig.initVerify(pubKey)
        sig.update(data)
        return sig.verify(signature)
    }
}
