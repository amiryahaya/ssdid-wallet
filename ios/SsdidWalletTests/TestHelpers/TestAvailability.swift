@testable import SsdidCore
import Foundation
import Security

/// Probes whether Keychain operations work in the current test environment.
/// Returns false on CI simulators where Keychain entitlements are missing (-34018).
enum KeychainAvailability {
    static let isAvailable: Bool = {
        let tag = "ssdid.test.keychain.probe.\(UUID().uuidString)"
        let addQuery: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: tag,
            kSecAttrAccount as String: "probe",
            kSecValueData as String: Data([1, 2, 3]),
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        ]
        let status = SecItemAdd(addQuery as CFDictionary, nil)
        if status == errSecSuccess {
            // Clean up
            let deleteQuery: [String: Any] = [
                kSecClass as String: kSecClassGenericPassword,
                kSecAttrService as String: tag,
                kSecAttrAccount as String: "probe"
            ]
            SecItemDelete(deleteQuery as CFDictionary)
            return true
        }
        // -34018 = errSecMissingEntitlement — Keychain not available
        return false
    }()
}

/// Probes whether the SSDID registry is reachable.
enum RegistryAvailability {
    static let isReachable: Bool = {
        let semaphore = DispatchSemaphore(value: 0)
        var reachable = false
        guard let url = URL(string: "https://registry.ssdid.my/health/ready") else { return false }
        var request = URLRequest(url: url, timeoutInterval: 3)
        request.httpMethod = "GET"
        URLSession.shared.dataTask(with: request) { _, response, _ in
            if let http = response as? HTTPURLResponse, http.statusCode == 200 {
                reachable = true
            }
            semaphore.signal()
        }.resume()
        _ = semaphore.wait(timeout: .now() + 5)
        return reachable
    }()
}
