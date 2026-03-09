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
        assertThat(level.secretKeyBytes).isEqualTo(32)    // s(16) + t(16)
        assertThat(level.publicKeyBytes).isEqualTo(54)     // v
        assertThat(level.signatureOverhead).isEqualTo(162) // S1(54) + S2(54) + S3(54)
        assertThat(level.hashBytes).isEqualTo(32)
        assertThat(level.algorithmName).isEqualTo("KAZ-SIGN-128")
    }

    @Test
    fun `SecurityLevel LEVEL_192 has correct properties`() {
        val level = SecurityLevel.LEVEL_192
        assertThat(level.value).isEqualTo(192)
        assertThat(level.secretKeyBytes).isEqualTo(50)     // s(25) + t(25)
        assertThat(level.publicKeyBytes).isEqualTo(88)     // v
        assertThat(level.signatureOverhead).isEqualTo(264) // S1(88) + S2(88) + S3(88)
        assertThat(level.hashBytes).isEqualTo(48)
        assertThat(level.algorithmName).isEqualTo("KAZ-SIGN-192")
    }

    @Test
    fun `SecurityLevel LEVEL_256 has correct properties`() {
        val level = SecurityLevel.LEVEL_256
        assertThat(level.value).isEqualTo(256)
        assertThat(level.secretKeyBytes).isEqualTo(64)     // s(32) + t(32)
        assertThat(level.publicKeyBytes).isEqualTo(118)    // v
        assertThat(level.signatureOverhead).isEqualTo(354) // S1(118) + S2(118) + S3(118)
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
        val pk = ByteArray(54) { it.toByte() }
        val sk = ByteArray(32) { it.toByte() }
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
        val signature = ByteArray(162 + message.size) { 0 }
        val result = SignatureResult(signature = signature, message = message, level = 128)

        assertThat(result.overhead).isEqualTo(162)
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
