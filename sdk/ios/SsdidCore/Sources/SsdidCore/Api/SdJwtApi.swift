import Foundation

/// Facade for SD-JWT VC operations (parse, store, list).
public struct SdJwtApi {
    let storage: VaultStorage

    public func parse(compactSdJwt: String) throws -> SdJwtVc {
        try SdJwtParser.parse(compactSdJwt)
    }

    public func store(_ sdJwtVc: StoredSdJwtVc) async throws {
        try await storage.saveSdJwtVc(sdJwtVc)
    }

    public func list() async -> [StoredSdJwtVc] {
        await storage.listSdJwtVcs()
    }
}
