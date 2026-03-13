import Foundation

final class MultiMethodResolver: DidResolver {
    private let ssdidResolver: SsdidRegistryResolver
    private let keyResolver: DidKeyResolver
    private let jwkResolver: DidJwkResolver

    init(ssdidResolver: SsdidRegistryResolver, keyResolver: DidKeyResolver, jwkResolver: DidJwkResolver) {
        self.ssdidResolver = ssdidResolver
        self.keyResolver = keyResolver
        self.jwkResolver = jwkResolver
    }

    func resolve(did: String) async throws -> DidDocument {
        let parts = did.split(separator: ":", maxSplits: 3)
        guard parts.count >= 3, parts[0] == "did" else {
            throw DidResolutionError.invalidDid(did)
        }
        let method = String(parts[1])
        switch method {
        case "ssdid": return try await ssdidResolver.resolve(did: did)
        case "key": return try await keyResolver.resolve(did: did)
        case "jwk": return try await jwkResolver.resolve(did: did)
        default: throw DidResolutionError.unsupportedMethod(did)
        }
    }
}
