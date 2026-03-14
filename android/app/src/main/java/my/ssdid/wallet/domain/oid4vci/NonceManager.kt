package my.ssdid.wallet.domain.oid4vci

/**
 * Thread-safe c_nonce storage with expiry tracking for OpenID4VCI.
 *
 * The issuer returns a c_nonce in the token response (or credential response)
 * that the wallet must include in subsequent proof-of-possession JWTs.
 */
class NonceManager {
    @Volatile private var nonce: String? = null
    @Volatile private var expiresAt: Long = 0L

    /**
     * Updates the stored nonce and its expiry.
     *
     * @param nonce The c_nonce value from the issuer
     * @param expiresIn Nonce lifetime in seconds
     */
    fun update(nonce: String, expiresIn: Int) {
        this.nonce = nonce
        this.expiresAt = System.currentTimeMillis() / 1000 + expiresIn
    }

    /** Returns the current nonce, or null if none has been set. */
    fun current(): String? = nonce

    /** Returns true if the nonce is missing or has expired. */
    fun isExpired(): Boolean {
        if (nonce == null) return true
        return System.currentTimeMillis() / 1000 >= expiresAt
    }

    /** Clears the stored nonce. */
    fun clear() {
        nonce = null
        expiresAt = 0L
    }
}
