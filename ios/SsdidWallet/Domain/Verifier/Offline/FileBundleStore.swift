import Foundation

/// File-based BundleStore that persists verification bundles as JSON files.
/// Each issuer DID gets its own file in the app's documents directory.
final class FileBundleStore: BundleStore {
    private let directory: URL

    init() {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        directory = docs.appendingPathComponent("verification_bundles", isDirectory: true)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    }

    private func fileURL(for issuerDid: String) -> URL {
        let safe = issuerDid.replacingOccurrences(of: ":", with: "_")
            .replacingOccurrences(of: "/", with: "_")
        return directory.appendingPathComponent("\(safe).json")
    }

    func saveBundle(_ bundle: VerificationBundle) async throws {
        let data = try JSONEncoder().encode(bundle)
        try data.write(to: fileURL(for: bundle.issuerDid), options: [.atomic, .completeFileProtection])
    }

    func getBundle(issuerDid: String) async -> VerificationBundle? {
        let url = fileURL(for: issuerDid)
        return await withCheckedContinuation { continuation in
            DispatchQueue.global(qos: .utility).async {
                guard let data = try? Data(contentsOf: url),
                      let bundle = try? JSONDecoder().decode(VerificationBundle.self, from: data) else {
                    continuation.resume(returning: nil)
                    return
                }
                continuation.resume(returning: bundle)
            }
        }
    }

    func deleteBundle(issuerDid: String) async throws {
        try FileManager.default.removeItem(at: fileURL(for: issuerDid))
    }

    func listBundles() async throws -> [VerificationBundle] {
        let dir = directory
        return try await withCheckedThrowingContinuation { continuation in
            DispatchQueue.global(qos: .utility).async {
                do {
                    let files = try FileManager.default.contentsOfDirectory(
                        at: dir, includingPropertiesForKeys: nil
                    )
                    let bundles = files.compactMap { url -> VerificationBundle? in
                        guard let data = try? Data(contentsOf: url) else { return nil }
                        return try? JSONDecoder().decode(VerificationBundle.self, from: data)
                    }
                    continuation.resume(returning: bundles)
                } catch {
                    continuation.resume(throwing: error)
                }
            }
        }
    }
}
