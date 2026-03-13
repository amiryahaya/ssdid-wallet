import Foundation

final class DidJwkResolver: DidResolver {
    func resolve(did: String) async throws -> DidDocument {
        guard did.hasPrefix("did:jwk:") else {
            throw DidResolutionError.unsupportedMethod(did)
        }
        let encoded = String(did.dropFirst("did:jwk:".count))
        guard let jwkData = Data(base64Encoded: base64UrlToBase64(encoded)),
              let jwkDict = try? JSONSerialization.jsonObject(with: jwkData) as? [String: Any]
        else {
            throw DidResolutionError.invalidJwk
        }

        // Convert JWK dictionary values to strings for publicKeyJwk
        var publicKeyJwk: [String: String] = [:]
        for (key, value) in jwkDict {
            publicKeyJwk[key] = "\(value)"
        }

        let keyId = "\(did)#0"
        var vm = VerificationMethod(
            id: keyId, type: "JsonWebKey2020", controller: did, publicKeyMultibase: ""
        )
        vm.publicKeyJwk = publicKeyJwk
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
