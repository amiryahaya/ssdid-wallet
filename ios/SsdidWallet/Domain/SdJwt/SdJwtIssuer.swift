import Foundation
import CryptoKit

class SdJwtIssuer {
    let signer: (Data) -> Data
    let algorithm: String

    init(signer: @escaping (Data) -> Data, algorithm: String) {
        self.signer = signer
        self.algorithm = algorithm
    }

    func issue(
        issuer: String,
        subject: String,
        type: [String],
        claims: [String: String],
        disclosable: Set<String>,
        holderKeyJwk: [String: String]? = nil,
        issuedAt: Int = Int(Date().timeIntervalSince1970),
        expiresAt: Int? = nil
    ) -> SdJwtVc {
        var disclosures: [Disclosure] = []
        var sdHashes: [String] = []
        var visibleClaims: [String: String] = [:]

        for (name, value) in claims {
            if disclosable.contains(name) {
                let salt = generateSalt()
                let disclosure = Disclosure(salt: salt, claimName: name, claimValue: value)
                disclosures.append(disclosure)
                sdHashes.append(disclosure.hash())
            } else {
                visibleClaims[name] = value
            }
        }

        var payloadDict: [String: Any] = [
            "iss": issuer,
            "sub": subject,
            "vct": type.last ?? "VerifiableCredential",
            "iat": issuedAt,
            "_sd_alg": "sha-256",
            "_sd": sdHashes
        ]
        if let exp = expiresAt { payloadDict["exp"] = exp }
        for (k, v) in visibleClaims { payloadDict[k] = v }
        if let jwk = holderKeyJwk { payloadDict["cnf"] = ["jwk": jwk] }

        let headerDict: [String: String] = ["alg": algorithm, "typ": "vc+sd-jwt"]

        let headerB64 = try! JSONSerialization.data(withJSONObject: headerDict).base64URLEncodedString()
        let payloadB64 = try! JSONSerialization.data(withJSONObject: payloadDict).base64URLEncodedString()
        let signingInput = "\(headerB64).\(payloadB64)"
        let signature = signer(Data(signingInput.utf8))
        let signatureB64 = signature.base64URLEncodedString()

        return SdJwtVc(
            issuerJwt: "\(headerB64).\(payloadB64).\(signatureB64)",
            disclosures: disclosures,
            keyBindingJwt: nil
        )
    }

    private func generateSalt() -> String {
        var bytes = [UInt8](repeating: 0, count: 16)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        return Data(bytes).base64URLEncodedString()
    }
}
