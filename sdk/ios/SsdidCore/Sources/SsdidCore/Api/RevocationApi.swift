import Foundation

/// Facade for credential revocation checking.
public struct RevocationApi {
    private let manager: RevocationManager

    init(manager: RevocationManager) {
        self.manager = manager
    }

    public func checkStatus(_ credential: VerifiableCredential) async -> RevocationStatus {
        await manager.checkRevocation(credential)
    }

    public func invalidateCache() async {
        await manager.invalidateCache()
    }
}
