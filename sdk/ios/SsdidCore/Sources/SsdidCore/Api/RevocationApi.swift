import Foundation

/// Facade for credential revocation checking.
public struct RevocationApi {
    let manager: RevocationManager

    public func checkStatus(_ credential: VerifiableCredential) async -> RevocationStatus {
        await manager.checkRevocation(credential)
    }

    public func invalidateCache() async {
        await manager.invalidateCache()
    }
}
