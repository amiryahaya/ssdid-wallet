package my.ssdid.wallet.domain.crypto.kazsign

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for KazSigner that do not require the native library.
 *
 * KazSignNative loads a JNI library via System.loadLibrary("kazsign") in its
 * class initializer, which is not available in unit tests. Mocking System.loadLibrary
 * with mockkStatic can cause JVM hangs, so we test what we can without native code:
 *
 * - SecurityLevel enum behavior
 * - KazSigner constructor validation (int-based)
 * - KazSignException error code mapping
 * - SignatureResult/VerificationResult/KeyPair data classes
 */
class KazSignerTest {

    // ---------- SecurityLevel ----------

    @Test
    fun `SecurityLevel LEVEL_128 has correct properties`() {
        val level = SecurityLevel.LEVEL_128
        assertThat(level.value).isEqualTo(128)
        assertThat(level.secretKeyBytes).isEqualTo(98)
        assertThat(level.publicKeyBytes).isEqualTo(49)
        assertThat(level.signatureOverhead).isEqualTo(57)
        assertThat(level.hashBytes).isEqualTo(32)
        assertThat(level.algorithmName).isEqualTo("KAZ-SIGN-128")
    }

    @Test
    fun `SecurityLevel LEVEL_192 has correct properties`() {
        val level = SecurityLevel.LEVEL_192
        assertThat(level.value).isEqualTo(192)
        assertThat(level.secretKeyBytes).isEqualTo(146)
        assertThat(level.publicKeyBytes).isEqualTo(73)
        assertThat(level.signatureOverhead).isEqualTo(81)
        assertThat(level.hashBytes).isEqualTo(48)
        assertThat(level.algorithmName).isEqualTo("KAZ-SIGN-192")
    }

    @Test
    fun `SecurityLevel LEVEL_256 has correct properties`() {
        val level = SecurityLevel.LEVEL_256
        assertThat(level.value).isEqualTo(256)
        assertThat(level.secretKeyBytes).isEqualTo(194)
        assertThat(level.publicKeyBytes).isEqualTo(97)
        assertThat(level.signatureOverhead).isEqualTo(105)
        assertThat(level.hashBytes).isEqualTo(64)
        assertThat(level.algorithmName).isEqualTo("KAZ-SIGN-256")
    }

    @Test
    fun `SecurityLevel fromValue resolves correctly`() {
        assertThat(SecurityLevel.fromValue(128)).isEqualTo(SecurityLevel.LEVEL_128)
        assertThat(SecurityLevel.fromValue(192)).isEqualTo(SecurityLevel.LEVEL_192)
        assertThat(SecurityLevel.fromValue(256)).isEqualTo(SecurityLevel.LEVEL_256)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `SecurityLevel fromValue throws for invalid level`() {
        SecurityLevel.fromValue(512)
    }

    // ---------- KazSignException ----------

    @Test
    fun `KazSignException fromErrorCode maps known error codes`() {
        val memoryError = KazSignException.fromErrorCode(-1)
        assertThat(memoryError.errorCode).isEqualTo(KazSignException.ErrorCode.MEMORY_ERROR)
        assertThat(memoryError.message).isEqualTo("Memory allocation failed")

        val rngError = KazSignException.fromErrorCode(-2)
        assertThat(rngError.errorCode).isEqualTo(KazSignException.ErrorCode.RNG_ERROR)

        val paramError = KazSignException.fromErrorCode(-3)
        assertThat(paramError.errorCode).isEqualTo(KazSignException.ErrorCode.INVALID_PARAMETER)

        val verifyError = KazSignException.fromErrorCode(-4)
        assertThat(verifyError.errorCode).isEqualTo(KazSignException.ErrorCode.VERIFICATION_FAILED)
    }

    @Test
    fun `KazSignException fromErrorCode maps unknown codes to UNKNOWN`() {
        val unknown = KazSignException.fromErrorCode(-999)
        assertThat(unknown.errorCode).isEqualTo(KazSignException.ErrorCode.UNKNOWN)
        assertThat(unknown.message).contains("-999")
    }

    @Test
    fun `KazSignException ErrorCode fromValue maps all standard codes`() {
        assertThat(KazSignException.ErrorCode.fromValue(0)).isEqualTo(KazSignException.ErrorCode.SUCCESS)
        assertThat(KazSignException.ErrorCode.fromValue(-5)).isEqualTo(KazSignException.ErrorCode.DER_ERROR)
        assertThat(KazSignException.ErrorCode.fromValue(-6)).isEqualTo(KazSignException.ErrorCode.X509_ERROR)
        assertThat(KazSignException.ErrorCode.fromValue(-7)).isEqualTo(KazSignException.ErrorCode.P12_ERROR)
        assertThat(KazSignException.ErrorCode.fromValue(-8)).isEqualTo(KazSignException.ErrorCode.HASH_ERROR)
        assertThat(KazSignException.ErrorCode.fromValue(-9)).isEqualTo(KazSignException.ErrorCode.BUFFER_ERROR)
    }

    // ---------- KeyPair data class ----------

    @Test
    fun `KeyPair stores keys and level correctly`() {
        val pk = ByteArray(49) { it.toByte() }
        val sk = ByteArray(98) { it.toByte() }
        val keyPair = KeyPair(publicKey = pk, secretKey = sk, level = 128)

        assertThat(keyPair.publicKey).isEqualTo(pk)
        assertThat(keyPair.secretKey).isEqualTo(sk)
        assertThat(keyPair.level).isEqualTo(128)
        assertThat(keyPair.securityLevel).isEqualTo(SecurityLevel.LEVEL_128)
    }

    @Test
    fun `KeyPair equality works based on content`() {
        val kp1 = KeyPair(byteArrayOf(1, 2, 3), byteArrayOf(4, 5, 6), 128)
        val kp2 = KeyPair(byteArrayOf(1, 2, 3), byteArrayOf(4, 5, 6), 128)
        val kp3 = KeyPair(byteArrayOf(9, 9, 9), byteArrayOf(4, 5, 6), 128)

        assertThat(kp1).isEqualTo(kp2)
        assertThat(kp1).isNotEqualTo(kp3)
    }

    @Test
    fun `KeyPair toString redacts secret key`() {
        val kp = KeyPair(byteArrayOf(1, 2, 3), byteArrayOf(4, 5, 6), 128)
        val str = kp.toString()

        assertThat(str).contains("REDACTED")
        assertThat(str).contains("level=128")
    }

    // ---------- SignatureResult data class ----------

    @Test
    fun `SignatureResult computes overhead correctly`() {
        val message = "Hello".toByteArray()
        val signature = ByteArray(57 + message.size) { 0 }
        val result = SignatureResult(signature = signature, message = message, level = 128)

        assertThat(result.overhead).isEqualTo(57)
        assertThat(result.securityLevel).isEqualTo(SecurityLevel.LEVEL_128)
    }

    @Test
    fun `SignatureResult equality uses content comparison`() {
        val msg = byteArrayOf(1, 2)
        val sig = byteArrayOf(3, 4, 5)
        val r1 = SignatureResult(sig, msg, 128)
        val r2 = SignatureResult(sig.copyOf(), msg.copyOf(), 128)

        assertThat(r1).isEqualTo(r2)
    }

    // ---------- VerificationResult data class ----------

    @Test
    fun `VerificationResult valid result returns message`() {
        val message = "Hello".toByteArray()
        val result = VerificationResult(isValid = true, message = message, level = 128)

        assertThat(result.isValid).isTrue()
        assertThat(result.getMessageAsString()).isEqualTo("Hello")
        assertThat(result.securityLevel).isEqualTo(SecurityLevel.LEVEL_128)
    }

    @Test
    fun `VerificationResult invalid result has null message`() {
        val result = VerificationResult(isValid = false, message = null, level = 128)

        assertThat(result.isValid).isFalse()
        assertThat(result.getMessageAsString()).isNull()
        assertThat(result.getMessageAsHex()).isNull()
    }

    @Test
    fun `VerificationResult equality uses content comparison`() {
        val msg = byteArrayOf(1, 2, 3)
        val r1 = VerificationResult(true, msg, 128)
        val r2 = VerificationResult(true, msg.copyOf(), 128)
        val r3 = VerificationResult(false, null, 128)

        assertThat(r1).isEqualTo(r2)
        assertThat(r1).isNotEqualTo(r3)
    }
}
