import Foundation
import SsdidCore

/// Basic Identity sample — demonstrates SDK initialization, identity creation,
/// signing data, and building a DID document.
///
/// Usage: swift run BasicIdentity

@MainActor
func run() async {
    print("=== SSDID SDK — Basic Identity Sample ===\n")

    // Step 1: Initialize the SDK
    let sdk = SsdidSdk.Builder()
        .registryUrl("https://registry.ssdid.my")
        .build()

    print("[1] SDK initialized with registry: https://registry.ssdid.my")

    // Step 2: Create an identity using Ed25519
    do {
        let identity = try await sdk.vault.createIdentity(name: "Alice", algorithm: .ED25519)
        print("[2] Identity created!")
        print("    DID:       \(identity.did)")
        print("    Key ID:    \(identity.keyId)")
        print("    Algorithm: \(identity.algorithm)")

        // Step 3: List identities
        let identities = await sdk.vault.listIdentities()
        print("\n[3] Identities (\(identities.count)):")
        for id in identities {
            print("    - \(id.name): \(id.did)")
        }

        // Step 4: Sign some data
        let data = "Hello, SSDID!".data(using: .utf8)!
        let signature = try await sdk.vault.sign(keyId: identity.keyId, data: data)
        let hexPrefix = signature.prefix(16).map { String(format: "%02x", $0) }.joined()
        print("\n[4] Signed 'Hello, SSDID!'")
        print("    Signature (first 16 bytes): \(hexPrefix)...")

        // Step 5: Build DID Document
        let didDoc = try await sdk.vault.buildDidDocument(keyId: identity.keyId)
        print("\n[5] DID Document built!")
        print("    ID: \(didDoc.id)")
        print("    Verification methods: \(didDoc.verificationMethod.count)")

    } catch {
        print("Error: \(error.localizedDescription)")
    }

    print("\n=== Done ===")
}

// Entry point
Task { @MainActor in
    await run()
    exit(0)
}

RunLoop.main.run()
