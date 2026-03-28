package my.ssdid.wallet.domain.crypto.kazsign

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import my.ssdid.sdk.pqc.kazsign.KazSigner
import my.ssdid.sdk.pqc.kazsign.SecurityLevel
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for KAZ-Sign native library (v2.0).
 * Runs on device/emulator where the JNI library is available.
 */
@RunWith(AndroidJUnit4::class)
class KazSignInstrumentedTest {

    @After
    fun cleanup() {
        KazSigner.clearAll()
    }

    // ---------- Version ----------

    @Test
    fun versionReturnsNonEmpty() {
        val version = KazSigner.version
        assertThat(version).isNotEmpty()
    }

    @Test
    fun versionNumberIsPositive() {
        val versionNumber = KazSigner.versionNumber
        assertThat(versionNumber).isGreaterThan(0)
    }

    // ---------- Key Generation (all levels) ----------

    @Test
    fun generateKeyPair128() {
        KazSigner(SecurityLevel.LEVEL_128).use { signer ->
            val kp = signer.generateKeyPair()
            assertThat(kp.publicKey.size).isEqualTo(54)
            assertThat(kp.secretKey.size).isEqualTo(32)
            assertThat(kp.level).isEqualTo(128)
        }
    }

    @Test
    fun generateKeyPair192() {
        KazSigner(SecurityLevel.LEVEL_192).use { signer ->
            val kp = signer.generateKeyPair()
            assertThat(kp.publicKey.size).isEqualTo(88)
            assertThat(kp.secretKey.size).isEqualTo(50)
            assertThat(kp.level).isEqualTo(192)
        }
    }

    @Test
    fun generateKeyPair256() {
        KazSigner(SecurityLevel.LEVEL_256).use { signer ->
            val kp = signer.generateKeyPair()
            assertThat(kp.publicKey.size).isEqualTo(118)
            assertThat(kp.secretKey.size).isEqualTo(64)
            assertThat(kp.level).isEqualTo(256)
        }
    }

    // ---------- Sign + Verify ----------

    @Test
    fun signAndVerify128() {
        KazSigner(SecurityLevel.LEVEL_128).use { signer ->
            val kp = signer.generateKeyPair()
            val message = "Hello KAZ-Sign v2.0!".toByteArray()
            val sigResult = signer.sign(message, kp.secretKey)

            assertThat(sigResult.signature.size).isEqualTo(message.size + 162)

            val verifyResult = signer.verify(sigResult.signature, kp.publicKey)
            assertThat(verifyResult.isValid).isTrue()
            assertThat(verifyResult.message).isEqualTo(message)
        }
    }

    @Test
    fun signAndVerify192() {
        KazSigner(SecurityLevel.LEVEL_192).use { signer ->
            val kp = signer.generateKeyPair()
            val message = "PQC 192-bit test".toByteArray()
            val sigResult = signer.sign(message, kp.secretKey)

            assertThat(sigResult.signature.size).isEqualTo(message.size + 264)

            val verifyResult = signer.verify(sigResult.signature, kp.publicKey)
            assertThat(verifyResult.isValid).isTrue()
            assertThat(verifyResult.message).isEqualTo(message)
        }
    }

    @Test
    fun signAndVerify256() {
        KazSigner(SecurityLevel.LEVEL_256).use { signer ->
            val kp = signer.generateKeyPair()
            val message = "PQC 256-bit test".toByteArray()
            val sigResult = signer.sign(message, kp.secretKey)

            assertThat(sigResult.signature.size).isEqualTo(message.size + 354)

            val verifyResult = signer.verify(sigResult.signature, kp.publicKey)
            assertThat(verifyResult.isValid).isTrue()
            assertThat(verifyResult.message).isEqualTo(message)
        }
    }

    // ---------- Detached Sign + Verify ----------

    @Test
    fun detachedSignAndVerify128() {
        KazSigner(SecurityLevel.LEVEL_128).use { signer ->
            val kp = signer.generateKeyPair()
            val data = "Detached signature test".toByteArray()
            val sig = signer.signDetached(data, kp.secretKey)

            assertThat(sig.size).isEqualTo(162)

            val valid = signer.verifyDetached(data, sig, kp.publicKey)
            assertThat(valid).isTrue()
        }
    }

    @Test
    fun detachedVerifyRejectsWrongKey() {
        KazSigner(SecurityLevel.LEVEL_128).use { signer ->
            val kp1 = signer.generateKeyPair()
            val kp2 = signer.generateKeyPair()
            val data = "Wrong key test".toByteArray()
            val sig = signer.signDetached(data, kp1.secretKey)

            val valid = signer.verifyDetached(data, sig, kp2.publicKey)
            assertThat(valid).isFalse()
        }
    }

    @Test
    fun detachedVerifyRejectsTamperedData() {
        KazSigner(SecurityLevel.LEVEL_128).use { signer ->
            val kp = signer.generateKeyPair()
            val data = "Original data".toByteArray()
            val sig = signer.signDetached(data, kp.secretKey)

            val tampered = "Tampered data".toByteArray()
            val valid = signer.verifyDetached(tampered, sig, kp.publicKey)
            assertThat(valid).isFalse()
        }
    }

    // ---------- Hash ----------

    @Test
    fun hashProduces32BytesForLevel128() {
        KazSigner(SecurityLevel.LEVEL_128).use { signer ->
            val hash = signer.hash("test data")
            assertThat(hash.size).isEqualTo(32)
        }
    }

    @Test
    fun hashIsDeterministic() {
        KazSigner(SecurityLevel.LEVEL_128).use { signer ->
            val h1 = signer.hash("same input")
            val h2 = signer.hash("same input")
            assertThat(h1).isEqualTo(h2)
        }
    }

    @Test
    fun hashDifferentInputsProduceDifferentHashes() {
        KazSigner(SecurityLevel.LEVEL_128).use { signer ->
            val h1 = signer.hash("input A")
            val h2 = signer.hash("input B")
            assertThat(h1).isNotEqualTo(h2)
        }
    }

    // ---------- SHA3-256 ----------

    @Test
    fun sha3_256Produces32Bytes() {
        val hash = KazSigner.sha3_256("hello".toByteArray())
        assertThat(hash.size).isEqualTo(32)
    }

    // ---------- DER Key Encoding ----------

    @Test
    fun publicKeyDerRoundTrip() {
        KazSigner(SecurityLevel.LEVEL_128).use { signer ->
            val kp = signer.generateKeyPair()
            val der = signer.publicKeyToDer(kp.publicKey)
            val decoded = signer.publicKeyFromDer(der)
            assertThat(decoded).isEqualTo(kp.publicKey)
        }
    }

    @Test
    fun privateKeyDerRoundTrip() {
        KazSigner(SecurityLevel.LEVEL_128).use { signer ->
            val kp = signer.generateKeyPair()
            val der = signer.privateKeyToDer(kp.secretKey)
            val decoded = signer.privateKeyFromDer(der)
            assertThat(decoded).isEqualTo(kp.secretKey)
        }
    }

    // ---------- Native Parameter API ----------

    @Test
    fun signerPropertiesMatchSecurityLevel() {
        val level = SecurityLevel.LEVEL_128
        KazSigner(level).use { signer ->
            assertThat(signer.secretKeyBytes).isEqualTo(level.secretKeyBytes)
            assertThat(signer.publicKeyBytes).isEqualTo(level.publicKeyBytes)
            assertThat(signer.signatureOverhead).isEqualTo(level.signatureOverhead)
            assertThat(signer.hashBytes).isEqualTo(level.hashBytes)
        }
    }

    // ---------- Empty message ----------

    @Test
    fun signAndVerifyEmptyMessage() {
        KazSigner(SecurityLevel.LEVEL_128).use { signer ->
            val kp = signer.generateKeyPair()
            val message = byteArrayOf()
            val sigResult = signer.sign(message, kp.secretKey)

            assertThat(sigResult.signature.size).isEqualTo(162)

            val verifyResult = signer.verify(sigResult.signature, kp.publicKey)
            assertThat(verifyResult.isValid).isTrue()
            // Native library returns null for empty message extraction
            val recovered = verifyResult.message
            assertThat(recovered == null || recovered.isEmpty()).isTrue()
        }
    }
}
