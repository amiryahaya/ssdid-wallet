package my.ssdid.sdk.api.model

/**
 * Represents an authenticated session returned after successful authentication.
 *
 * @property sessionToken opaque token used to authorize subsequent API calls
 * @property serverDid the DID of the authenticating server
 */
data class AuthSession(
    val sessionToken: String,
    val serverDid: String
)
