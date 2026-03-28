import Foundation

/// Assembles SD-JWT VP tokens with selective disclosure and Key Binding JWT.
///
/// Given a stored SD-JWT VC, selected claims, and verifier parameters,
/// builds a presentation token: issuerJwt~disclosure1~...~disclosureN~kbJwt
public enum VpTokenBuilder {

    /// Builds a VP token from a stored SD-JWT VC with selective disclosure.
    ///
    /// - Parameters:
    ///   - storedSdJwtVc: The stored credential
    ///   - selectedClaims: Claim names the holder consents to disclose
    ///   - audience: The verifier's identifier (aud claim in KB-JWT)
    ///   - nonce: Verifier-provided nonce
    ///   - algorithm: JWA algorithm name (e.g., "EdDSA", "ES256")
    ///   - signer: Function that signs data with the holder's private key
    ///   - issuedAt: Unix timestamp for the KB-JWT (defaults to current time)
    /// - Returns: The assembled VP token string
    public static func build(
        storedSdJwtVc: StoredSdJwtVc,
        selectedClaims: [String],
        audience: String,
        nonce: String,
        algorithm: String,
        signer: (Data) -> Data,
        issuedAt: Int = Int(Date().timeIntervalSince1970)
    ) throws -> String {
        let parsed = try SdJwtParser.parse(storedSdJwtVc.compact)

        let selectedDisclosures = parsed.disclosures.filter { selectedClaims.contains($0.claimName) }

        let resolvedNames = Set(selectedDisclosures.map { $0.claimName })
        let missing = Set(selectedClaims).subtracting(resolvedNames)
        guard missing.isEmpty else {
            throw OpenId4VpError.invalidRequest(
                "Requested claims not found in credential disclosures: \(missing)"
            )
        }

        // Build the SD-JWT with selected disclosures (without KB-JWT)
        var sdJwtWithDisclosures = parsed.issuerJwt + "~"
        for disc in selectedDisclosures {
            sdJwtWithDisclosures += disc.encoded + "~"
        }

        let kbJwt = try KeyBindingJwt.create(
            sdJwtWithDisclosures: sdJwtWithDisclosures,
            audience: audience,
            nonce: nonce,
            algorithm: algorithm,
            signer: signer,
            issuedAt: issuedAt
        )

        return sdJwtWithDisclosures + kbJwt
    }
}
