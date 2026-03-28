import Foundation
import CryptoKit

public enum KeyBindingJwt {
    public static func create(
        sdJwtWithDisclosures: String,
        audience: String,
        nonce: String,
        algorithm: String,
        signer: (Data) -> Data,
        issuedAt: Int = Int(Date().timeIntervalSince1970)
    ) throws -> String {
        let sdHash = computeSdHash(sdJwtWithDisclosures)

        let header: [String: String] = ["alg": algorithm, "typ": "kb+jwt"]
        let payload: [String: Any] = [
            "aud": audience,
            "nonce": nonce,
            "iat": issuedAt,
            "sd_hash": sdHash
        ]

        let headerB64 = try JSONSerialization.data(withJSONObject: header).base64URLEncodedString()
        let payloadB64 = try JSONSerialization.data(withJSONObject: payload).base64URLEncodedString()
        let signingInput = "\(headerB64).\(payloadB64)"
        let signature = signer(Data(signingInput.utf8))
        let signatureB64 = signature.base64URLEncodedString()

        return "\(headerB64).\(payloadB64).\(signatureB64)"
    }

    private static func computeSdHash(_ input: String) -> String {
        let digest = SHA256.hash(data: Data(input.utf8))
        return Data(digest).base64URLEncodedString()
    }
}
