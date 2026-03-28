import Foundation

public final class DidJwkResolver: DidResolver {
    public     func resolve(did: String) async throws -> DidDocument {
        guard did.hasPrefix("did:jwk:") else {
            throw DidResolutionError.unsupportedMethod(did)
        }
        let encoded = String(did.dropFirst("did:jwk:".count))
        guard let jwkData = Data(base64URLEncoded: encoded),
              let jwkDict = try? JSONSerialization.jsonObject(with: jwkData) as? [String: Any]
        else {
            throw DidResolutionError.invalidJwk
        }

        // Only extract values that are strings, skipping numeric/boolean/nested values
        var publicKeyJwk: [String: String] = [:]
        for (key, value) in jwkDict {
            if let strValue = value as? String {
                publicKeyJwk[key] = strValue
            }
            // Skip non-string values rather than mangling them
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
}
