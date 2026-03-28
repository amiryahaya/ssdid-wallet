import Foundation

/// Thread-safe c_nonce storage with expiry tracking for OpenID4VCI.
///
/// The issuer returns a c_nonce in the token response (or credential response)
/// that the wallet must include in subsequent proof-of-possession JWTs.
public actor NonceManager {
    private var nonce: String?
    private var expiresAt: Int64 = 0

    public init() {}

    /// Updates the stored nonce and its expiry.
    ///
    /// - Parameters:
    ///   - nonce: The c_nonce value from the issuer.
    ///   - expiresIn: Nonce lifetime in seconds.
    public     func update(nonce: String, expiresIn: Int) {
        self.nonce = nonce
        self.expiresAt = Int64(Date().timeIntervalSince1970) + Int64(expiresIn)
    }

    /// Returns the current nonce, or nil if none has been set.
    public     func current() -> String? {
        return nonce
    }

    /// Returns true if the nonce is missing or has expired.
    public     func isExpired() -> Bool {
        guard nonce != nil else { return true }
        return Int64(Date().timeIntervalSince1970) >= expiresAt
    }

    /// Clears the stored nonce.
    public     func clear() {
        nonce = nil
        expiresAt = 0
    }
}
