import Foundation
import CryptoKit
import Security

/// File-based BundleStore that persists verification bundles as JSON files protected with
/// HMAC-SHA256 integrity verification. The HMAC key is stored in the Keychain so it survives
/// app restarts and is never written to disk in plaintext.
///
/// Files are written with `.completeFileProtection` so they are encrypted by iOS at rest
/// when the device is locked. The HMAC additionally detects any tampering with the files.
public final class FileBundleStore: BundleStore {
    private let directory: URL
    private let macKey: SymmetricKey

    public     init() {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        directory = docs.appendingPathComponent("verification_bundles", isDirectory: true)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        macKey = FileBundleStore.loadOrCreateMacKey()
    }

    // MARK: - Keychain MAC key management

    private static let macKeyService = "my.ssdid.wallet"
    private static let macKeyAccount = "bundle_mac_key"

    private static func loadOrCreateMacKey() -> SymmetricKey {
        // Try to load from Keychain
        let query: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: macKeyService,
            kSecAttrAccount: macKeyAccount,
            kSecReturnData: true,
            kSecMatchLimit: kSecMatchLimitOne
        ]
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        if status == errSecSuccess, let data = result as? Data {
            return SymmetricKey(data: data)
        }

        // Generate a new 256-bit key and store in Keychain
        let newKey = SymmetricKey(size: .bits256)
        let keyData = newKey.withUnsafeBytes { Data(Array($0)) }
        let addQuery: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: macKeyService,
            kSecAttrAccount: macKeyAccount,
            kSecValueData: keyData,
            kSecAttrAccessible: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        ]
        SecItemAdd(addQuery as CFDictionary, nil)
        return newKey
    }

    // MARK: - File naming

    private func fileURL(for issuerDid: String) -> URL {
        let hash = SHA256.hash(data: Data(issuerDid.utf8))
            .prefix(8)
            .map { String(format: "%02x", $0) }
            .joined()
        let safe = issuerDid.replacingOccurrences(of: "[^a-zA-Z0-9_-]", with: "_", options: .regularExpression)
        let truncated = String(safe.prefix(48))
        return directory.appendingPathComponent("\(truncated)_\(hash).json")
    }

    private func macURL(for bundleURL: URL) -> URL {
        return bundleURL.appendingPathExtension("mac")
    }

    // MARK: - HMAC helpers

    private func computeHmac(for data: Data) -> Data {
        let code = HMAC<SHA256>.authenticationCode(for: data, using: macKey)
        return Data(code)
    }

    private func verifyHmac(data: Data, mac: Data) -> Bool {
        return HMAC<SHA256>.isValidAuthenticationCode(mac, authenticating: data, using: macKey)
    }

    // MARK: - BundleStore implementation

    public     func saveBundle(_ bundle: VerificationBundle) async throws {
        let data = try JSONEncoder().encode(bundle)
        let mac = computeHmac(for: data)
        let url = fileURL(for: bundle.issuerDid)
        let macUrl = macURL(for: url)

        try data.write(to: url, options: [.atomic, .completeFileProtection])
        try mac.write(to: macUrl, options: [.atomic, .completeFileProtection])
    }

    public     func getBundle(issuerDid: String) async -> VerificationBundle? {
        let url = fileURL(for: issuerDid)
        let macUrl = macURL(for: url)
        return await withCheckedContinuation { continuation in
            DispatchQueue.global(qos: .utility).async { [weak self] in
                guard let self else { continuation.resume(returning: nil); return }
                guard let data = try? Data(contentsOf: url),
                      let storedMac = try? Data(contentsOf: macUrl) else {
                    continuation.resume(returning: nil)
                    return
                }
                guard self.verifyHmac(data: data, mac: storedMac) else {
                    // File has been tampered — reject silently
                    continuation.resume(returning: nil)
                    return
                }
                guard let bundle = try? JSONDecoder().decode(VerificationBundle.self, from: data) else {
                    continuation.resume(returning: nil)
                    return
                }
                continuation.resume(returning: bundle)
            }
        }
    }

    public     func deleteBundle(issuerDid: String) async throws {
        let url = fileURL(for: issuerDid)
        try? FileManager.default.removeItem(at: url)
        try? FileManager.default.removeItem(at: macURL(for: url))
    }

    public     func listBundles() async throws -> [VerificationBundle] {
        let dir = directory
        return try await withCheckedThrowingContinuation { continuation in
            DispatchQueue.global(qos: .utility).async { [weak self] in
                guard let self else { continuation.resume(returning: []); return }
                do {
                    let files = try FileManager.default.contentsOfDirectory(
                        at: dir, includingPropertiesForKeys: nil
                    ).filter { $0.pathExtension == "json" }

                    let bundles = files.compactMap { url -> VerificationBundle? in
                        guard let data = try? Data(contentsOf: url),
                              let storedMac = try? Data(contentsOf: self.macURL(for: url)),
                              self.verifyHmac(data: data, mac: storedMac) else { return nil }
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
