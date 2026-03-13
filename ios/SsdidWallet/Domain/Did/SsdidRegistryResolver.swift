import Foundation

final class SsdidRegistryResolver: DidResolver {
    private let registryApi: RegistryApi

    init(registryApi: RegistryApi) {
        self.registryApi = registryApi
    }

    func resolve(did: String) async throws -> DidDocument {
        return try await registryApi.resolveDid(did: did)
    }
}
