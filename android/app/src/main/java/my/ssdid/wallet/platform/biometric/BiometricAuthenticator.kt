package my.ssdid.wallet.platform.biometric

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

sealed class BiometricResult {
    object Success : BiometricResult()
    data class Error(val code: Int, val message: String) : BiometricResult()
    object Cancelled : BiometricResult()
}

enum class BiometricState {
    AVAILABLE,       // Biometric enrolled and ready
    NOT_ENROLLED,    // Hardware present, no biometric enrolled
    NO_HARDWARE      // No biometric hardware at all
}

class BiometricAuthenticator {

    fun getBiometricState(activity: FragmentActivity): BiometricState {
        val biometricManager = BiometricManager.from(activity)
        // Use BIOMETRIC_WEAK for state detection to cover all enrolled biometrics
        // (face, fingerprint, iris). BIOMETRIC_STRONG is reserved for actual authentication.
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricState.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricState.NOT_ENROLLED
            else -> BiometricState.NO_HARDWARE
        }
    }

    fun canAuthenticate(activity: FragmentActivity): Boolean {
        val biometricManager = BiometricManager.from(activity)
        return biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun canAuthenticateWithFallback(activity: FragmentActivity): Boolean {
        val biometricManager = BiometricManager.from(activity)
        return biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Authenticate using BIOMETRIC_STRONG or DEVICE_CREDENTIAL as fallback.
     * Delegates to [authenticateWithFallback] — kept for call-site compatibility.
     */
    suspend fun authenticate(
        activity: FragmentActivity,
        title: String = "SSDID Authentication",
        subtitle: String = "Verify your identity to continue"
    ): BiometricResult = authenticateWithFallback(activity, title, subtitle)

    /**
     * Authenticate using BIOMETRIC_STRONG or DEVICE_CREDENTIAL as fallback.
     * Used when biometric is not enrolled or hardware is unavailable — falls back to passcode.
     */
    suspend fun authenticateWithFallback(
        activity: FragmentActivity,
        title: String = "SSDID Authentication",
        subtitle: String = "Verify your identity to continue"
    ): BiometricResult = suspendCancellableCoroutine { continuation ->
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (continuation.isActive) continuation.resume(BiometricResult.Success)
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (continuation.isActive) {
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED) {
                        continuation.resume(BiometricResult.Cancelled)
                    } else {
                        continuation.resume(BiometricResult.Error(errorCode, errString.toString()))
                    }
                }
            }
            override fun onAuthenticationFailed() {
                // Don't resume — the system shows "try again" automatically
            }
        }

        val biometricPrompt = BiometricPrompt(activity, executor, callback)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        biometricPrompt.authenticate(promptInfo)

        continuation.invokeOnCancellation {
            biometricPrompt.cancelAuthentication()
        }
    }

    /**
     * Authenticate with a CryptoObject for hardware-backed key operations.
     * Used when the keystore key requires user authentication.
     */
    suspend fun authenticateWithCrypto(
        activity: FragmentActivity,
        cryptoObject: BiometricPrompt.CryptoObject,
        title: String = "SSDID Authentication",
        subtitle: String = "Verify your identity to continue"
    ): BiometricResult = suspendCancellableCoroutine { continuation ->
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (continuation.isActive) continuation.resume(BiometricResult.Success)
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (continuation.isActive) {
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED) {
                        continuation.resume(BiometricResult.Cancelled)
                    } else {
                        continuation.resume(BiometricResult.Error(errorCode, errString.toString()))
                    }
                }
            }
            override fun onAuthenticationFailed() {
                // System handles retry UI
            }
        }

        val biometricPrompt = BiometricPrompt(activity, executor, callback)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText("Cancel")
            .build()

        biometricPrompt.authenticate(promptInfo, cryptoObject)

        continuation.invokeOnCancellation {
            biometricPrompt.cancelAuthentication()
        }
    }
}
