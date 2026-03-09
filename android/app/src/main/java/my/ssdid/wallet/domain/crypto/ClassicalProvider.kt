package my.ssdid.wallet.domain.crypto

import my.ssdid.wallet.domain.model.Algorithm
import java.security.*
import java.security.spec.*

class ClassicalProvider : CryptoProvider {

    init {
        BouncyCastleInstaller.ensureInstalled()
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
        require(supportsAlgorithm(algorithm)) { "Unsupported: $algorithm" }
        return when (algorithm) {
            Algorithm.ED25519 -> signEd25519(privateKey, data)
            // NOTE: Investigated NONEwithECDSA to avoid double-hashing the pre-hashed
            // W3C Data Integrity payload (SHA3-256(proofOptions) || SHA3-256(document) = 64 bytes).
            // NONEwithECDSA works locally (BouncyCastle accepts 64-byte input even for P-256),
            // but the registry still returns 401 with either variant. This confirms the registry
            // also uses SHA256withECDSA/SHA384withECDSA internally. The 401 root cause lies
            // elsewhere — likely canonical JSON serialization differences between the wallet
            // (kotlinx-serialization) and registry (Elixir/Jason). Keeping SHA*withECDSA to
            // match the registry's expected signature scheme.
            Algorithm.ECDSA_P256 -> signEcdsa("SHA256withECDSA", "secp256r1", privateKey, data)
            Algorithm.ECDSA_P384 -> signEcdsa("SHA384withECDSA", "secp384r1", privateKey, data)
            else -> throw IllegalArgumentException("Unsupported: $algorithm")
        }
    }

    override fun verify(algorithm: Algorithm, publicKey: ByteArray, signature: ByteArray, data: ByteArray): Boolean {
        require(supportsAlgorithm(algorithm)) { "Unsupported: $algorithm" }
        return when (algorithm) {
            Algorithm.ED25519 -> verifyEd25519(publicKey, signature, data)
            Algorithm.ECDSA_P256 -> verifyEcdsa("SHA256withECDSA", "secp256r1", publicKey, signature, data)
            Algorithm.ECDSA_P384 -> verifyEcdsa("SHA384withECDSA", "secp384r1", publicKey, signature, data)
            else -> throw IllegalArgumentException("Unsupported classical algorithm for verify: $algorithm")
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

    // ASN.1 DER length encoding (supports lengths > 127)
    private fun derEncodeLength(len: Int): ByteArray = when {
        len < 0x80 -> byteArrayOf(len.toByte())
        len <= 0xFF -> byteArrayOf(0x81.toByte(), len.toByte())
        else -> byteArrayOf(0x82.toByte(), (len ushr 8).toByte(), len.toByte())
    }

    private fun derSequence(content: ByteArray): ByteArray =
        byteArrayOf(0x30) + derEncodeLength(content.size) + content

    private fun derOctetString(content: ByteArray): ByteArray =
        byteArrayOf(0x04) + derEncodeLength(content.size) + content

    private fun derBitString(content: ByteArray): ByteArray =
        byteArrayOf(0x03) + derEncodeLength(content.size + 1) + byteArrayOf(0x00) + content

    // Ed25519 ASN.1 DER wrapping
    private fun wrapEd25519PrivateKey(raw: ByteArray): ByteArray {
        // PKCS#8: SEQUENCE { INTEGER(0), SEQUENCE { OID 1.3.101.112 }, OCTET STRING { OCTET STRING { raw } } }
        val version = byteArrayOf(0x02, 0x01, 0x00) // INTEGER 0
        val oid = byteArrayOf(0x06, 0x03, 0x2b, 0x65, 0x70)
        val algoSeq = derSequence(oid)
        val innerOctet = derOctetString(raw)
        val outerOctet = derOctetString(innerOctet)
        val total = version + algoSeq + outerOctet
        return derSequence(total)
    }

    private fun wrapEd25519PublicKey(raw: ByteArray): ByteArray {
        // X.509 SubjectPublicKeyInfo: SEQUENCE { SEQUENCE { OID }, BIT STRING { raw } }
        val oid = byteArrayOf(0x06, 0x03, 0x2b, 0x65, 0x70)
        val algoSeq = derSequence(oid)
        val bitString = derBitString(raw)
        val total = algoSeq + bitString
        return derSequence(total)
    }

    private fun generateEcdsa(curveName: String): KeyPairResult {
        val kpg = KeyPairGenerator.getInstance("EC", "BC")
        kpg.initialize(ECGenParameterSpec(curveName))
        val kp = kpg.generateKeyPair()
        val pubBytes = extractEcPublicKeyRaw(kp.public.encoded)
        val privBytes = extractEcPrivateKeyRaw(kp.private.encoded)
        return KeyPairResult(publicKey = pubBytes, privateKey = privBytes)
    }

    /**
     * X.509 SubjectPublicKeyInfo for EC:
     * SEQUENCE { SEQUENCE { OID ecPublicKey, OID curve }, BIT STRING { 0x00, 0x04 || x || y } }
     * Extracts the raw uncompressed EC point (65 bytes P-256, 97 bytes P-384).
     */
    private fun extractEcPublicKeyRaw(encoded: ByteArray): ByteArray {
        var i = 0
        require(encoded[i] == 0x30.toByte()) { "Expected SEQUENCE" }
        i++; i += derLengthSize(encoded, i)
        require(encoded[i] == 0x30.toByte()) { "Expected algorithm SEQUENCE" }
        i++; val algoLen = derReadLength(encoded, i); i += derLengthSize(encoded, i) + algoLen
        require(encoded[i] == 0x03.toByte()) { "Expected BIT STRING" }
        i++; val bsLen = derReadLength(encoded, i); i += derLengthSize(encoded, i)
        require(encoded[i] == 0x00.toByte()) { "Expected 0x00 BIT STRING padding" }
        i++
        val keyLen = bsLen - 1
        return encoded.copyOfRange(i, i + keyLen)
    }

    /**
     * PKCS#8 for EC:
     * SEQUENCE { INTEGER(0), SEQUENCE { OID ecPublicKey, OID curve }, OCTET STRING { ECPrivateKey } }
     * ECPrivateKey: SEQUENCE { INTEGER(1), OCTET STRING { raw secret } [, context-tagged public key] }
     * Extracts the raw secret scalar (32 bytes P-256, 48 bytes P-384).
     */
    private fun extractEcPrivateKeyRaw(encoded: ByteArray): ByteArray {
        var i = 0
        require(encoded[i] == 0x30.toByte()) { "Expected outer SEQUENCE" }
        i++; i += derLengthSize(encoded, i)
        require(encoded[i] == 0x02.toByte()) { "Expected INTEGER (version)" }
        i++; val intLen = derReadLength(encoded, i); i += derLengthSize(encoded, i) + intLen
        require(encoded[i] == 0x30.toByte()) { "Expected algorithm SEQUENCE" }
        i++; val algoLen = derReadLength(encoded, i); i += derLengthSize(encoded, i) + algoLen
        require(encoded[i] == 0x04.toByte()) { "Expected OCTET STRING (ECPrivateKey wrapper)" }
        i++; i += derLengthSize(encoded, i)
        // Now inside ECPrivateKey SEQUENCE
        require(encoded[i] == 0x30.toByte()) { "Expected ECPrivateKey SEQUENCE" }
        i++; i += derLengthSize(encoded, i)
        require(encoded[i] == 0x02.toByte()) { "Expected INTEGER (version 1)" }
        i++; val verLen = derReadLength(encoded, i); i += derLengthSize(encoded, i) + verLen
        require(encoded[i] == 0x04.toByte()) { "Expected OCTET STRING (private key)" }
        i++; val keyLen = derReadLength(encoded, i); i += derLengthSize(encoded, i)
        return encoded.copyOfRange(i, i + keyLen)
    }

    private fun signEcdsa(sigAlgo: String, curveName: String, privateKey: ByteArray, data: ByteArray): ByteArray {
        val pkcs8 = wrapEcPrivateKey(privateKey, curveName)
        val keySpec = PKCS8EncodedKeySpec(pkcs8)
        val kf = KeyFactory.getInstance("EC", "BC")
        val privKey = kf.generatePrivate(keySpec)
        val sig = Signature.getInstance(sigAlgo, "BC")
        sig.initSign(privKey)
        sig.update(data)
        return sig.sign()
    }

    private fun verifyEcdsa(sigAlgo: String, curveName: String, publicKey: ByteArray, signature: ByteArray, data: ByteArray): Boolean {
        val x509 = wrapEcPublicKey(publicKey, curveName)
        val keySpec = X509EncodedKeySpec(x509)
        val kf = KeyFactory.getInstance("EC", "BC")
        val pubKey = kf.generatePublic(keySpec)
        val sig = Signature.getInstance(sigAlgo, "BC")
        sig.initVerify(pubKey)
        sig.update(data)
        return sig.verify(signature)
    }

    // EC ASN.1 DER wrapping — curve OID bytes
    private val secp256r1Oid = byteArrayOf(0x06, 0x08, 0x2a, 0x86.toByte(), 0x48, 0xce.toByte(), 0x3d, 0x03, 0x01, 0x07)
    private val secp384r1Oid = byteArrayOf(0x06, 0x05, 0x2b, 0x81.toByte(), 0x04, 0x00, 0x22)
    private val ecPublicKeyOid = byteArrayOf(0x06, 0x07, 0x2a, 0x86.toByte(), 0x48, 0xce.toByte(), 0x3d, 0x02, 0x01)

    private fun curveOid(curveName: String): ByteArray = when (curveName) {
        "secp256r1" -> secp256r1Oid
        "secp384r1" -> secp384r1Oid
        else -> throw IllegalArgumentException("Unsupported curve: $curveName")
    }

    private fun wrapEcPublicKey(raw: ByteArray, curveName: String): ByteArray {
        // X.509: SEQUENCE { SEQUENCE { OID ecPublicKey, OID curve }, BIT STRING { 0x00, raw } }
        val curveOidBytes = curveOid(curveName)
        val algoSeqContent = ecPublicKeyOid + curveOidBytes
        val algoSeq = derSequence(algoSeqContent)
        val bitString = derBitString(raw)
        val total = algoSeq + bitString
        return derSequence(total)
    }

    private fun wrapEcPrivateKey(raw: ByteArray, curveName: String): ByteArray {
        // PKCS#8: SEQUENCE { INTEGER(0), SEQUENCE { OID ecPublicKey, OID curve }, OCTET STRING { ECPrivateKey } }
        // ECPrivateKey: SEQUENCE { INTEGER(1), OCTET STRING { raw } }
        val version = byteArrayOf(0x02, 0x01, 0x00)
        val curveOidBytes = curveOid(curveName)
        val algoSeqContent = ecPublicKeyOid + curveOidBytes
        val algoSeq = derSequence(algoSeqContent)
        val ecVersion = byteArrayOf(0x02, 0x01, 0x01)
        val privOctet = derOctetString(raw)
        val ecPrivKeyContent = ecVersion + privOctet
        val ecPrivKey = derSequence(ecPrivKeyContent)
        val outerOctet = derOctetString(ecPrivKey)
        val total = version + algoSeq + outerOctet
        return derSequence(total)
    }
}
