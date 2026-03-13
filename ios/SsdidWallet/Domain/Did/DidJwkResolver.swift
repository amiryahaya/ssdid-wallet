import Foundation

final class DidJwkResolver: DidResolver {
    func resolve(did: String) async throws -> DidDocument {
        guard did.hasPrefix("did:jwk:") else {
            throw DidResolutionError.unsupportedMethod(did)
        }
        let encoded = String(did.dropFirst("did:jwk:".count))
        guard Data(base64Encoded: base64UrlToBase64(encoded)) != nil else {
            throw DidResolutionError.invalidJwk
        }
        let keyId = "\(did)#0"
        let vm = VerificationMethod(
            id: keyId, type: "JsonWebKey2020", controller: did, publicKeyMultibase: ""
        )
        return DidDocument(
            id: did, controller: did, verificationMethod: [vm],
            authentication: [keyId], assertionMethod: [keyId], capabilityInvocation: [keyId]
        )
    }

    private func base64UrlToBase64(_ input: String) -> String {
        var s = input.replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        let remainder = s.count % 4
        if remainder > 0 { s += String(repeating: "=", count: 4 - remainder) }
        return s
    }
}
