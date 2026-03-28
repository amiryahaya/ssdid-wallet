import Foundation

/// Facade for encrypted backup and restore operations.
public struct BackupApi {
    let manager: BackupManager

    public func create(passphrase: String) async throws -> Data {
        try await manager.createBackup(passphrase: passphrase)
    }

    public func restore(backupData: Data, passphrase: String) async throws -> Int {
        try await manager.restoreBackup(backupData: backupData, passphrase: passphrase)
    }
}
