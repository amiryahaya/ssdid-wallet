package my.ssdid.mobile.platform.biometric

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

class BiometricAuthenticator {

    fun canAuthenticate(activity: FragmentActivity): Boolean {
        val biometricManager = BiometricManager.from(activity)
        return biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    suspend fun authenticate(
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
