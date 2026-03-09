package my.ssdid.wallet.domain.transport

sealed class NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>()
    data class ServerError(val code: Int, val message: String) : NetworkResult<Nothing>()
    data class NetworkError(val cause: Throwable) : NetworkResult<Nothing>()
    object Timeout : NetworkResult<Nothing>()

    /** Map HTTP status codes to typed errors per SSDID HTTP API spec (doc 08). */
    companion object {
        fun fromHttpError(code: Int, message: String): NetworkResult<Nothing> = when (code) {
            400 -> InvalidRequest(message)
            401 -> Unauthorized(message)
            404 -> NotFound(message)
            409 -> AlreadyExists(message)
            410 -> Deactivated(message)
            422 -> ChallengeRequired(message)
            else -> ServerError(code, message)
        }
    }

    data class InvalidRequest(val message: String) : NetworkResult<Nothing>()
    data class Unauthorized(val message: String) : NetworkResult<Nothing>()
    data class NotFound(val message: String) : NetworkResult<Nothing>()
    data class AlreadyExists(val message: String) : NetworkResult<Nothing>()
    data class Deactivated(val message: String) : NetworkResult<Nothing>()
    data class ChallengeRequired(val message: String) : NetworkResult<Nothing>()
}
