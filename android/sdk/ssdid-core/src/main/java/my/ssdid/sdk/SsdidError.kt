package my.ssdid.sdk

sealed class SsdidError : Exception() {
    data class NetworkError(override val cause: Throwable) : SsdidError()
    data class Timeout(val url: String) : SsdidError()
    data class ServerError(val statusCode: Int, val body: String?) : SsdidError()
    data class UnsupportedAlgorithm(val algorithm: String) : SsdidError()
    data class SigningFailed(val reason: String) : SsdidError()
    data class VerificationFailed(val reason: String) : SsdidError()
    data class StorageError(override val cause: Throwable) : SsdidError()
    data class IdentityNotFound(val did: String) : SsdidError()
    data class CredentialNotFound(val id: String) : SsdidError()
    data class DidResolutionFailed(val did: String, val reason: String) : SsdidError()
    data class IssuanceFailed(val reason: String) : SsdidError()
    data class PresentationFailed(val reason: String) : SsdidError()
    data class NoMatchingCredentials(val requestId: String) : SsdidError()
    data class RecoveryFailed(val reason: String) : SsdidError()
    data class RotationFailed(val reason: String) : SsdidError()
    data class BackupFailed(val reason: String) : SsdidError()
}
