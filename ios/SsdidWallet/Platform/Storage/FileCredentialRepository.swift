import Foundation
import CryptoKit

/// File-based CredentialRepository that persists held verifiable credentials as JSON files.
/// Each credential gets its own file in the app's documents directory under `held_credentials/`.
/// The filename is derived from a sanitised credential ID and a SHA-256 hash prefix,
/// following the same convention used on Android.
final class FileCredentialRepository: CredentialRepository {
    private let directory: URL
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    init() {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        directory = docs.appendingPathComponent("held_credentials", isDirectory: true)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    }

    func saveCredential(_ credential: VerifiableCredential) async throws {
        let data = try encoder.encode(credential)
        let url = fileUrl(for: credential.id)
        try data.write(to: url, options: [.atomic, .completeFileProtection])
    }

    func getHeldCredentials() async -> [VerifiableCredential] {
        guard let files = try? FileManager.default.contentsOfDirectory(
            at: directory, includingPropertiesForKeys: nil
        ) else { return [] }
        return files.compactMap { url in
            guard let data = try? Data(contentsOf: url) else { return nil }
            return try? decoder.decode(VerifiableCredential.self, from: data)
        }
    }

    func getUniqueIssuerDids() async -> [String] {
        let credentials = await getHeldCredentials()
        return Array(Set(credentials.map { $0.issuer }))
    }

    func deleteCredential(credentialId: String) async throws {
        try FileManager.default.removeItem(at: fileUrl(for: credentialId))
    }

    // MARK: - Private

    private func fileUrl(for id: String) -> URL {
        // 8-byte (16 hex char) SHA-256 hash prefix for uniqueness, matching Android behaviour.
        let hash = SHA256.hash(data: Data(id.utf8))
            .prefix(8)
            .map { String(format: "%02x", $0) }
            .joined()
        let safe = id.replacingOccurrences(
            of: "[^a-zA-Z0-9_-]", with: "_", options: .regularExpression
        )
        let truncated = String(safe.prefix(48))
        return directory.appendingPathComponent("\(truncated)_\(hash).json")
    }
}
