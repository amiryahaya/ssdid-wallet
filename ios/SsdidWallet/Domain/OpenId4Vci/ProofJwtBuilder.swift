import Foundation

/// Builds proof-of-possession JWTs for OpenID4VCI credential requests.
///
/// The proof JWT demonstrates that the wallet controls the key material
/// associated with the DID, as required by the OID4VCI specification.
enum ProofJwtBuilder {

    /// Builds a proof JWT with the OpenID4VCI proof type.
    ///
    /// - Parameters:
    ///   - algorithm: JWA algorithm name (e.g., "EdDSA", "ES256").
    ///   - keyId: DID URL identifying the key (e.g., "did:ssdid:abc#key-1").
    ///   - walletDid: The wallet's DID (iss claim).
    ///   - issuerUrl: The credential issuer URL (aud claim).
    ///   - nonce: The c_nonce from the token/credential response.
    ///   - signer: Function that signs the input bytes with the holder's private key.
    ///   - issuedAt: Unix timestamp for the iat claim.
    /// - Returns: Compact JWS string (header.payload.signature).
    static func build(
        algorithm: String,
        keyId: String,
        walletDid: String,
        issuerUrl: String,
        nonce: String,
        signer: (Data) -> Data,
        issuedAt: Int64 = Int64(Date().timeIntervalSince1970)
    ) throws -> String {
        let header: [String: Any] = [
            "typ": "openid4vci-proof+jwt",
            "alg": algorithm,
            "kid": keyId
        ]

        let payload: [String: Any] = [
            "iss": walletDid,
            "aud": issuerUrl,
            "iat": issuedAt,
            "nonce": nonce
        ]

        let headerData = try JSONSerialization.data(withJSONObject: header, options: .sortedKeys)
        let payloadData = try JSONSerialization.data(withJSONObject: payload, options: .sortedKeys)

        let headerB64 = base64UrlEncode(headerData)
        let payloadB64 = base64UrlEncode(payloadData)

        let signingInput = "\(headerB64).\(payloadB64)"
        guard let signingInputData = signingInput.data(using: .utf8) else {
            throw OpenId4VciError.proofError("Failed to encode signing input")
        }

        let signature = signer(signingInputData)
        let signatureB64 = base64UrlEncode(signature)

        return "\(headerB64).\(payloadB64).\(signatureB64)"
    }

    // MARK: - Private

    private static func base64UrlEncode(_ data: Data) -> String {
        data.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
}
