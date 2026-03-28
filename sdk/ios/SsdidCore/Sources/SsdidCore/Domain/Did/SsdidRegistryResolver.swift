import Foundation

public final class SsdidRegistryResolver: DidResolver {
    private let registryApi: RegistryApi

    public     init(registryApi: RegistryApi) {
        self.registryApi = registryApi
    }

    public     func resolve(did: String) async throws -> DidDocument {
        return try await registryApi.resolveDid(did: did)
    }
}
