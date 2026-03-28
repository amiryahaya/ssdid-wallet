import Foundation

// MARK: - Models

public struct OrgRecoveryConfig: Codable, Equatable {
    public let userDid: String
    public let orgDid: String
    public let orgName: String
    public let encryptedRecoveryKey: String
    public let enrolledAt: String
}

// MARK: - InstitutionalRecoveryManager

/// Manages institutional (organization-assisted) recovery.
/// An organization enrolls as a recovery custodian by providing their DID.
/// During recovery, the organization signs a recovery authorization that
/// proves the user's identity claim, allowing key restoration.
public final class InstitutionalRecoveryManager: @unchecked Sendable {

    private let recoveryManager: RecoveryManager
    private let defaults: UserDefaults

    public init(
        recoveryManager: RecoveryManager,
        defaults: UserDefaults = .standard
    ) {
        self.recoveryManager = recoveryManager
        self.defaults = defaults
    }

    // MARK: - Public API

    /// Enroll an organization as a recovery custodian.
    /// The organization's DID is stored alongside a copy of the recovery key
    /// (encrypted to the organization's public key in a real deployment).
    ///
    /// - Parameters:
    ///   - identity: The identity to protect
    ///   - orgDid: The organization's DID
    ///   - orgName: Display name for the organization
    ///   - encryptedRecoveryKey: Recovery key encrypted to the org's public key
    /// - Returns: The stored organization recovery configuration
    public func enrollOrganization(
        identity: Identity,
        orgDid: String,
        orgName: String,
        encryptedRecoveryKey: Data
    ) async throws -> OrgRecoveryConfig {
        guard orgDid.hasPrefix("did:") else {
            throw InstitutionalRecoveryError.invalidOrgDid(orgDid)
        }

        // Ensure identity has a recovery key
        let hasKey = await recoveryManager.hasRecoveryKey(keyId: identity.keyId)
        guard hasKey else {
            throw InstitutionalRecoveryError.missingRecoveryKey
        }

        let encodedKey = encryptedRecoveryKey
            .base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")

        let config = OrgRecoveryConfig(
            userDid: identity.did,
            orgDid: orgDid,
            orgName: orgName,
            encryptedRecoveryKey: encodedKey,
            enrolledAt: ISO8601DateFormatter().string(from: Date())
        )

        saveConfig(config, userDid: identity.did)
        return config
    }

    /// Recover using organization-provided recovery key.
    /// The organization decrypts and provides the recovery key after
    /// verifying the user's identity through their own KYC process.
    ///
    /// - Parameters:
    ///   - did: The DID to recover
    ///   - recoveryKeyBase64: The recovery private key (provided by org after verification)
    ///   - name: Name for the restored identity
    ///   - algorithm: Algorithm for new key generation
    public func recoverWithOrgAssistance(
        did: String,
        recoveryKeyBase64: String,
        name: String,
        algorithm: Algorithm
    ) async throws -> Identity {
        guard getConfig(did: did) != nil else {
            throw InstitutionalRecoveryError.notConfigured(did)
        }

        // Delegate to standard recovery manager
        return try await recoveryManager.restoreWithRecoveryKey(
            did: did,
            recoveryPrivateKeyBase64: recoveryKeyBase64,
            name: name,
            algorithm: algorithm
        )
    }

    /// Check if institutional recovery is configured for a DID.
    public func hasOrgRecovery(did: String) -> Bool {
        return getConfig(did: did) != nil
    }

    /// Get organization recovery configuration.
    public func getConfig(did: String) -> OrgRecoveryConfig? {
        let key = Self.storageKey(for: did)
        guard let data = defaults.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(OrgRecoveryConfig.self, from: data)
    }

    // MARK: - Private Helpers

    private func saveConfig(_ config: OrgRecoveryConfig, userDid: String) {
        let key = Self.storageKey(for: userDid)
        if let data = try? JSONEncoder().encode(config) {
            defaults.set(data, forKey: key)
        }
    }

    private static func storageKey(for did: String) -> String {
        "ssdid_institutional_recovery_\(did)"
    }
}

// MARK: - Errors

public enum InstitutionalRecoveryError: Error, LocalizedError {
    case invalidOrgDid(String)
    case missingRecoveryKey
    case notConfigured(String)

    public var errorDescription: String? {
        switch self {
        case .invalidOrgDid(let did):
            return "Invalid organization DID: \(did)"
        case .missingRecoveryKey:
            return "Identity must have a recovery key before enrolling an organization"
        case .notConfigured(let did):
            return "No institutional recovery configured for \(did)"
        }
    }
}
