@testable import SsdidCore
import Foundation
@testable import SsdidWallet

final class InMemoryBundleStore: BundleStore, @unchecked Sendable {
    private var bundles: [String: VerificationBundle] = [:]

    func saveBundle(_ bundle: VerificationBundle) async throws {
        bundles[bundle.issuerDid] = bundle
    }

    func getBundle(issuerDid: String) async -> VerificationBundle? {
        bundles[issuerDid]
    }

    func deleteBundle(issuerDid: String) async throws {
        bundles.removeValue(forKey: issuerDid)
    }

    func listBundles() async throws -> [VerificationBundle] {
        Array(bundles.values)
    }

    func clear() { bundles.removeAll() }
}
