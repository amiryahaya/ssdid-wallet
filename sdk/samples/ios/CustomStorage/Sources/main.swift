import Foundation
import SsdidCore

/// Custom Storage sample — demonstrates how to implement a custom in-memory
/// VaultStorage backend for the SSDID SDK.
///
/// The SSDID SDK uses a VaultStorage protocol for persisting identities,
/// credentials, and key material. By default, the iOS SDK uses FileVaultStorage
/// (UserDefaults + file-based key storage). You can provide your own implementation
/// backed by Core Data, SQLite, encrypted containers, etc.
///
/// Note: The current iOS SDK builder uses FileVaultStorage internally. This sample
/// demonstrates the VaultStorage pattern and shows how identity operations work
/// with the default storage. In a production app, you would extend the Builder
/// to accept a custom VaultStorage, similar to the Android SDK's .vaultStorage() method.
///
/// Usage: swift run CustomStorage

// MARK: - In-Memory VaultStorage Concept
//
// Below is a conceptual in-memory VaultStorage implementation.
// In the Android SDK, this can be passed directly via:
//
//   SsdidSdk.builder(context)
//       .vaultStorage(SimpleVaultStorage())
//       .build()
//
// The equivalent pattern for iOS would be:
//
//   class InMemoryVaultStorage: VaultStorage {
//       private var identities: [String: Identity] = [:]
//       private var privateKeys: [String: Data] = [:]
//       private var credentials: [String: VerifiableCredential] = [:]
//
//       func saveIdentity(_ identity: Identity, encryptedPrivateKey: Data) async throws {
//           identities[identity.keyId] = identity
//           privateKeys[identity.keyId] = encryptedPrivateKey
//       }
//
//       func getIdentity(keyId: String) async -> Identity? {
//           identities[keyId]
//       }
//
//       func listIdentities() async -> [Identity] {
//           Array(identities.values)
//       }
//
//       // ... remaining protocol methods ...
//   }

@MainActor
func run() async {
    print("=== SSDID SDK — Custom Storage Sample ===\n")

    // Initialize SDK (uses default FileVaultStorage on iOS)
    let sdk = SsdidSdk.Builder()
        .registryUrl("https://registry.ssdid.my")
        .build()

    print("[1] SDK initialized with default storage")
    print("    iOS default: FileVaultStorage (UserDefaults + file-based keys)")
    print("    Android equivalent: DataStoreVaultStorage")

    // Demonstrate identity operations that go through VaultStorage
    do {
        // Create an identity — this calls VaultStorage.saveIdentity() internally
        let identity = try await sdk.vault.createIdentity(name: "Storage Demo", algorithm: .ED25519)
        print("\n[2] Identity created (stored via VaultStorage):")
        print("    DID:    \(identity.did)")
        print("    Key ID: \(identity.keyId)")

        // List identities — this calls VaultStorage.listIdentities() internally
        let identities = await sdk.vault.listIdentities()
        print("\n[3] Listed identities from storage: \(identities.count)")
        for id in identities {
            print("    - \(id.name): \(id.did)")
        }

        // Sign data — retrieves private key from VaultStorage
        let data = "Custom storage test".data(using: .utf8)!
        let signature = try await sdk.vault.sign(keyId: identity.keyId, data: data)
        let hexPrefix = signature.prefix(16).map { String(format: "%02x", $0) }.joined()
        print("\n[4] Signed data using key from storage:")
        print("    Signature (first 16 bytes): \(hexPrefix)...")

        // Delete identity — calls VaultStorage.deleteIdentity()
        try await sdk.vault.deleteIdentity(keyId: identity.keyId)
        let remaining = await sdk.vault.listIdentities()
        print("\n[5] Deleted identity. Remaining: \(remaining.count)")

        print("\n--- Custom Storage Pattern ---")
        print("To use a custom storage backend:")
        print("  1. Implement the VaultStorage protocol")
        print("  2. Pass it to the SDK builder (Android: .vaultStorage(myStorage))")
        print("  3. All vault operations will use your storage automatically")
        print("  4. Useful for: Core Data, SQLCipher, Realm, cloud sync, etc.")

    } catch {
        print("Error: \(error.localizedDescription)")
    }

    print("\n=== Done ===")
}

Task { @MainActor in
    await run()
    exit(0)
}

RunLoop.main.run()
