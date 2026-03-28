import Foundation
import CryptoKit

/// File-based CredentialRepository that persists held verifiable credentials as JSON files.
/// Each credential gets its own file in the app's documents directory under `held_credentials/`.
/// The filename is derived from a sanitised credential ID and a SHA-256 hash prefix,
/// following the same convention used on Android.
public final class FileCredentialRepository: CredentialRepository {
    private let directory: URL
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    public     init() {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        directory = docs.appendingPathComponent("held_credentials", isDirectory: true)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    }

    public     func saveCredential(_ credential: VerifiableCredential) async throws {
        let data = try encoder.encode(credential)
        let url = fileUrl(for: credential.id)
        try data.write(to: url, options: [.atomic, .completeFileProtection])
    }

    public     func getHeldCredentials() async -> [VerifiableCredential] {
        let dir = directory
        let dec = decoder
        return await withCheckedContinuation { continuation in
            DispatchQueue.global(qos: .utility).async {
                guard let files = try? FileManager.default.contentsOfDirectory(
                    at: dir, includingPropertiesForKeys: nil
                ) else {
                    continuation.resume(returning: [])
                    return
                }
                let credentials = files.compactMap { url -> VerifiableCredential? in
                    guard let data = try? Data(contentsOf: url) else { return nil }
                    return try? dec.decode(VerifiableCredential.self, from: data)
                }
                continuation.resume(returning: credentials)
            }
        }
    }

    public     func getUniqueIssuerDids() async -> [String] {
        let credentials = await getHeldCredentials()
        return Array(Set(credentials.map { $0.issuer }))
    }

    public     func deleteCredential(credentialId: String) async throws {
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
