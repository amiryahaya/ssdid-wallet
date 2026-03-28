package my.ssdid.sdk.domain.oid4vci

/**
 * Thread-safe c_nonce storage with expiry tracking for OpenID4VCI.
 *
 * The issuer returns a c_nonce in the token response (or credential response)
 * that the wallet must include in subsequent proof-of-possession JWTs.
 */
class NonceManager {
    private data class NonceState(val value: String, val expiresAt: Long)

    @Volatile private var state: NonceState? = null

    /**
     * Updates the stored nonce and its expiry.
     *
     * @param nonce The c_nonce value from the issuer
     * @param expiresIn Nonce lifetime in seconds
     */
    fun update(nonce: String, expiresIn: Int) {
        state = NonceState(nonce, System.currentTimeMillis() / 1000 + expiresIn)
    }

    /** Returns the current nonce, or null if none has been set. */
    fun current(): String? = state?.value

    /** Returns true if the nonce is missing or has expired. */
    fun isExpired(): Boolean {
        val s = state ?: return true
        return System.currentTimeMillis() / 1000 >= s.expiresAt
    }

    /** Clears the stored nonce. */
    fun clear() {
        state = null
    }
}
