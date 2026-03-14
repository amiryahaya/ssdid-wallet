import Foundation

final class DidKeyResolver: DidResolver {
    func resolve(did: String) async throws -> DidDocument {
        guard did.hasPrefix("did:key:") else {
            throw DidResolutionError.unsupportedMethod(did)
        }
        let methodSpecificId = String(did.dropFirst("did:key:".count))
        guard methodSpecificId.hasPrefix("z") else {
            throw DidResolutionError.invalidMultibase
        }
        guard let decoded = Base58.decode(String(methodSpecificId.dropFirst())) else {
            throw DidResolutionError.invalidMultibase
        }
        let (codec, _) = try Multicodec.decode(decoded)

        let vmType: String
        switch codec {
        case Multicodec.ed25519Pub: vmType = "Ed25519VerificationKey2020"
        case Multicodec.p256Pub: vmType = "EcdsaSecp256r1VerificationKey2019"
        case Multicodec.p384Pub: vmType = "EcdsaSecp384VerificationKey2019"
        default: throw DidResolutionError.unsupportedCodec(codec)
        }

        let keyId = "\(did)#\(methodSpecificId)"
        let vm = VerificationMethod(
            id: keyId, type: vmType, controller: did, publicKeyMultibase: methodSpecificId
        )
        return DidDocument(
            id: did, controller: did, verificationMethod: [vm],
            authentication: [keyId], assertionMethod: [keyId], capabilityInvocation: [keyId]
        )
    }
}
