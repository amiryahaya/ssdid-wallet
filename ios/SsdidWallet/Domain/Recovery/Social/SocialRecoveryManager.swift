import Foundation

// MARK: - Models

struct Guardian: Codable, Equatable {
    let id: String
    let name: String
    let did: String
    let shareIndex: Int
    let enrolledAt: String
}

struct SocialRecoveryConfig: Codable, Equatable {
    let did: String
    let threshold: Int
    let totalShares: Int
    let guardians: [Guardian]
    let createdAt: String
}

// MARK: - SocialRecoveryManager

/// Manages social recovery using Shamir's Secret Sharing.
/// Splits the recovery private key into shares distributed to trusted guardians.
/// Any K-of-N guardians can reconstruct the recovery key for restoration.
final class SocialRecoveryManager {

    private let recoveryManager: RecoveryManager
    private let vault: Vault
    private let defaults: UserDefaults

    init(
        recoveryManager: RecoveryManager,
        vault: Vault,
        defaults: UserDefaults = .standard
    ) {
        self.recoveryManager = recoveryManager
        self.vault = vault
        self.defaults = defaults
    }

    // MARK: - Public API

    /// Set up social recovery for an identity.
    /// Generates a recovery key, splits it into shares, and returns
    /// the shares for distribution to guardians.
    ///
    /// - Parameters:
    ///   - identity: The identity to protect
    ///   - guardianNames: Names and DIDs of guardians [(name, did)]
    ///   - threshold: Minimum shares needed to recover (K)
    /// - Returns: Map of guardian ID to their Base64URL-encoded share
    func setupSocialRecovery(
        identity: Identity,
        guardianNames: [(name: String, did: String)],
        threshold: Int
    ) async throws -> [String: String] {
        let totalShares = guardianNames.count

        guard threshold >= 2 else {
            throw SocialRecoveryError.thresholdTooLow
        }
        guard totalShares >= threshold else {
            throw SocialRecoveryError.insufficientGuardians(needed: threshold, got: totalShares)
        }
        guard totalShares <= 255 else {
            throw SocialRecoveryError.tooManyGuardians
        }

        // Generate recovery key (stores public key in vault)
        let recoveryPrivateKey = try await recoveryManager.generateRecoveryKey(identity: identity)

        // Split recovery key into shares
        let shares = try ShamirSecretSharing.split(
            secret: recoveryPrivateKey,
            threshold: threshold,
            shares: totalShares
        )

        // Zero out recovery private key from memory
        // (Data is value type, so the local copy is the only reference at this point)

        let now = ISO8601DateFormatter().string(from: Date())

        let guardians = guardianNames.enumerated().map { idx, guardian in
            Guardian(
                id: UUID().uuidString,
                name: guardian.name,
                did: guardian.did,
                shareIndex: shares[idx].index,
                enrolledAt: now
            )
        }

        // Store social recovery configuration
        let config = SocialRecoveryConfig(
            did: identity.did,
            threshold: threshold,
            totalShares: totalShares,
            guardians: guardians,
            createdAt: now
        )
        saveConfig(config, did: identity.did)

        // Return guardian ID -> Base64URL-encoded share
        var result: [String: String] = [:]
        for (guardian, share) in zip(guardians, shares) {
            result[guardian.id] = share.data.base64URLEncodedString()
        }
        return result
    }

    /// Recover identity using shares collected from guardians.
    ///
    /// - Parameters:
    ///   - did: The DID to recover
    ///   - collectedShares: Map of share index to Base64URL-encoded share data
    ///   - name: Name for the restored identity
    ///   - algorithm: Algorithm to use for new key generation
    func recoverWithShares(
        did: String,
        collectedShares: [Int: String],
        name: String,
        algorithm: Algorithm
    ) async throws -> Identity {
        guard let config = getConfig(did: did) else {
            throw SocialRecoveryError.notConfigured(did)
        }

        guard collectedShares.count >= config.threshold else {
            throw SocialRecoveryError.insufficientShares(
                needed: config.threshold,
                got: collectedShares.count
            )
        }

        // Reconstruct shares
        let shares = try collectedShares.map { index, data in
            guard let shareData = Data(base64URLEncoded: data) else {
                throw SocialRecoveryError.invalidShareData
            }
            return ShamirSecretSharing.Share(index: index, data: shareData)
        }

        // Reconstruct recovery private key
        let recoveryPrivateKey = try ShamirSecretSharing.combine(shares: shares)
        let recoveryKeyBase64 = recoveryPrivateKey.base64EncodedString()

        // Delegate to existing recovery manager
        return try await recoveryManager.restoreWithRecoveryKey(
            did: did,
            recoveryPrivateKeyBase64: recoveryKeyBase64,
            name: name,
            algorithm: algorithm
        )
    }

    /// Get social recovery configuration for a DID.
    func getConfig(did: String) -> SocialRecoveryConfig? {
        let key = Self.storageKey(for: did)
        guard let data = defaults.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(SocialRecoveryConfig.self, from: data)
    }

    /// Check if social recovery is configured for a DID.
    func hasSocialRecovery(did: String) -> Bool {
        return getConfig(did: did) != nil
    }

    // MARK: - Private Helpers

    private func saveConfig(_ config: SocialRecoveryConfig, did: String) {
        let key = Self.storageKey(for: did)
        if let data = try? JSONEncoder().encode(config) {
            defaults.set(data, forKey: key)
        }
    }

    private static func storageKey(for did: String) -> String {
        "ssdid_social_recovery_\(did)"
    }
}

// MARK: - Errors

enum SocialRecoveryError: Error, LocalizedError {
    case thresholdTooLow
    case insufficientGuardians(needed: Int, got: Int)
    case tooManyGuardians
    case notConfigured(String)
    case insufficientShares(needed: Int, got: Int)
    case invalidShareData

    var errorDescription: String? {
        switch self {
        case .thresholdTooLow:
            return "Threshold must be at least 2"
        case .insufficientGuardians(let needed, let got):
            return "Need at least \(needed) guardians, got \(got)"
        case .tooManyGuardians:
            return "Maximum 255 guardians"
        case .notConfigured(let did):
            return "No social recovery configured for \(did)"
        case .insufficientShares(let needed, let got):
            return "Need at least \(needed) shares, got \(got)"
        case .invalidShareData:
            return "Invalid Base64URL share data"
        }
    }
}

// MARK: - Base64URL Helpers

private extension Data {
    func base64URLEncodedString() -> String {
        base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    init?(base64URLEncoded string: String) {
        var base64 = string
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        // Pad to multiple of 4
        let remainder = base64.count % 4
        if remainder > 0 {
            base64 += String(repeating: "=", count: 4 - remainder)
        }
        self.init(base64Encoded: base64)
    }
}
