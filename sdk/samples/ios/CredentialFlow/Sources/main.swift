import Foundation
import SsdidCore

/// Credential Flow sample — demonstrates identity creation, credential issuance
/// (OID4VCI), credential listing, and presentation (OID4VP) API usage patterns.
///
/// Note: This sample uses simulated URIs. Real issuance/presentation requires
/// a live issuer/verifier. The sample demonstrates the API call pattern.
///
/// Usage: swift run CredentialFlow

@MainActor
func run() async {
    print("=== SSDID SDK — Credential Flow Sample ===\n")

    // Step 1: Initialize the SDK
    let sdk = SsdidSdk.Builder()
        .registryUrl("https://registry.ssdid.my")
        .build()

    print("[1] SDK initialized")

    // Step 2: Create an identity
    do {
        let identity = try await sdk.vault.createIdentity(name: "Credential User", algorithm: .ED25519)
        print("[2] Identity created: \(identity.did)")

        // Step 3: Simulate OID4VCI credential offer
        print("\n[3] Processing OID4VCI credential offer (simulated)...")
        let offerUri = "openid-credential-offer://?credential_offer_uri=https://issuer.example.com/offers/abc123"
        print("    URI: \(offerUri)")
        print("    In a real app, this URI comes from scanning a QR code or deep link.")
        print("    The SDK would call:")
        print("      let review = try sdk.issuance.processOffer(uri)")
        print("      let result = try await sdk.issuance.acceptOffer(...)")
        print("    (Skipping actual call — no real issuer available)")

        // Step 4: List stored credentials
        let credentials = await sdk.vault.listCredentials()
        print("\n[4] Stored credentials: \(credentials.count)")
        if credentials.isEmpty {
            print("    No credentials yet. Complete an OID4VCI flow with a real issuer to store one.")
        } else {
            for vc in credentials {
                print("    - \(vc.id): \(vc.type.joined(separator: ", "))")
            }
        }

        // Step 5: Simulate OID4VP presentation request
        print("\n[5] Processing OID4VP presentation request (simulated)...")
        let requestUri = "openid4vp://?request_uri=https://verifier.example.com/requests/xyz789"
        print("    URI: \(requestUri)")
        print("    In a real app, this URI comes from a verifier's QR code.")
        print("    The SDK would call:")
        print("      let review = try await sdk.presentation.processRequest(uri)")
        print("      try await sdk.presentation.submitPresentation(...)")
        print("    (Skipping actual call — no real verifier available)")

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
