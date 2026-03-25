# Offline Validation E2E Tests — iOS Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement 27 automated E2E tests covering all 13 offline validation use cases on iOS.

**Architecture:** 5 test classes — 4 XCUITest classes for UI flows against the real registry, plus 1 XCTest class for offline fallback logic with mock verifiers. Mirrors the Android test structure exactly.

**Tech Stack:** XCTest, XCUITest, CryptoKit

**Spec:** `docs/superpowers/specs/2026-03-26-offline-validation-e2e-tests-design.md`

---

## File Structure

```
ios/SsdidWalletUITests/
├── IdentityCreationUITests.swift              (EXISTS)
├── RecoveryFlowUITests.swift                  (EXISTS)
└── Offline/
    ├── OfflineTestHelper.swift                (CREATE — shared test utilities)
    ├── MockVerifier.swift                     (CREATE — mock verifier)
    ├── InMemoryBundleStore.swift              (CREATE — in-memory bundle store)
    ├── VerificationFlowUITests.swift          (CREATE — UC-1, UC-2: 6 tests)
    ├── BundleManagementUITests.swift          (CREATE — UC-5, UC-6: 6 tests)
    ├── OfflineSettingsUITests.swift           (CREATE — UC-3, UC-4: 5 tests)
    ├── BackgroundSyncUITests.swift            (CREATE — UC-8: 1 test)

ios/SsdidWalletTests/Offline/
    └── OfflineVerificationTests.swift         (CREATE — UC-7, UC-9–13: 9 unit tests)
```

---

## Task 1: Test Infrastructure — Helpers and Mocks

**Files:**
- Create: `ios/SsdidWalletTests/Offline/OfflineTestHelper.swift`
- Create: `ios/SsdidWalletTests/Offline/MockVerifier.swift`
- Create: `ios/SsdidWalletTests/Offline/InMemoryBundleStore.swift`

- [ ] **Step 1: Create MockVerifier**

```swift
import Foundation
@testable import SsdidWallet

class MockVerifier: Verifier {
    var shouldThrow: Error?
    var shouldReturn: Bool
    var verifyCallCount = 0

    init(shouldThrow: Error? = URLError(.notConnectedToInternet), shouldReturn: Bool = true) {
        self.shouldThrow = shouldThrow
        self.shouldReturn = shouldReturn
    }

    func resolveDid(did: String) async throws -> DidDocument {
        if let error = shouldThrow { throw error }
        throw NSError(domain: "MockVerifier", code: 0, userInfo: [NSLocalizedDescriptionKey: "Not implemented"])
    }

    func verifySignature(did: String, keyId: String, signature: Data, data: Data) async throws -> Bool {
        if let error = shouldThrow { throw error }
        return shouldReturn
    }

    func verifyChallengeResponse(did: String, keyId: String, challenge: String, signedChallenge: String) async throws -> Bool {
        if let error = shouldThrow { throw error }
        return shouldReturn
    }

    func verifyCredential(credential: VerifiableCredential) async throws -> Bool {
        verifyCallCount += 1
        if let error = shouldThrow { throw error }
        return shouldReturn
    }
}
```

- [ ] **Step 2: Create InMemoryBundleStore**

```swift
import Foundation
@testable import SsdidWallet

class InMemoryBundleStore: BundleStore {
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
```

- [ ] **Step 3: Create OfflineTestHelper**

```swift
import Foundation
import CryptoKit
@testable import SsdidWallet

enum OfflineTestHelper {

    private static let classicalProvider = ClassicalProvider()

    static func createKeyPair() throws -> (publicKeyMultibase: String, privateKey: Data) {
        let keyPair = try classicalProvider.generateKeyPair(algorithm: .ED25519)
        return (keyPair.publicKeyMultibase, keyPair.privateKey)
    }

    static func createTestCredential(
        issuerDid: String,
        keyId: String,
        privateKey: Data,
        expirationDate: String? = nil,
        credentialStatus: CredentialStatus? = nil
    ) throws -> VerifiableCredential {
        let now = ISO8601DateFormatter().string(from: Date())
        let subject = CredentialSubject(id: "did:ssdid:holder123")

        var credWithoutProof = VerifiableCredential(
            id: "urn:uuid:\(UUID().uuidString)",
            type: ["VerifiableCredential"],
            issuer: issuerDid,
            issuanceDate: now,
            expirationDate: expirationDate,
            credentialSubject: subject,
            credentialStatus: credentialStatus,
            proof: Proof(
                type: "Ed25519Signature2020",
                created: now,
                verificationMethod: keyId,
                proofPurpose: "assertionMethod",
                proofValue: ""
            )
        )

        let canonical = try canonicalizeWithoutProof(credWithoutProof)
        let signature = try classicalProvider.sign(algorithm: .ED25519, privateKey: privateKey, data: canonical)
        let proofValue = "z" + signature.base64URLEncodedString()

        credWithoutProof = VerifiableCredential(
            id: credWithoutProof.id,
            type: credWithoutProof.type,
            issuer: credWithoutProof.issuer,
            issuanceDate: credWithoutProof.issuanceDate,
            expirationDate: credWithoutProof.expirationDate,
            credentialSubject: credWithoutProof.credentialSubject,
            credentialStatus: credWithoutProof.credentialStatus,
            proof: Proof(
                type: credWithoutProof.proof.type,
                created: credWithoutProof.proof.created,
                verificationMethod: credWithoutProof.proof.verificationMethod,
                proofPurpose: credWithoutProof.proof.proofPurpose,
                proofValue: proofValue
            )
        )
        return credWithoutProof
    }

    static func createBundle(
        issuerDid: String,
        didDocument: DidDocument,
        freshnessRatio: Double = 0.1,
        ttlDays: Int = 7,
        statusList: StatusListCredential? = nil
    ) -> VerificationBundle {
        let ttlSeconds = TimeInterval(ttlDays) * 86400
        let ageSeconds = ttlSeconds * freshnessRatio
        let fetchedAt = Date().addingTimeInterval(-ageSeconds)
        let expiresAt = fetchedAt.addingTimeInterval(ttlSeconds)
        let formatter = ISO8601DateFormatter()
        return VerificationBundle(
            issuerDid: issuerDid,
            didDocument: didDocument,
            statusList: statusList,
            fetchedAt: formatter.string(from: fetchedAt),
            expiresAt: formatter.string(from: expiresAt)
        )
    }

    static func createDidDocument(did: String, keyId: String, publicKeyMultibase: String) -> DidDocument {
        DidDocument(
            context: ["https://www.w3.org/ns/did/v1"],
            id: did,
            controller: did,
            verificationMethod: [
                VerificationMethod(
                    id: keyId,
                    type: "Ed25519VerificationKey2020",
                    controller: did,
                    publicKeyMultibase: publicKeyMultibase
                )
            ],
            authentication: [keyId],
            assertionMethod: [keyId],
            capabilityInvocation: [keyId]
        )
    }

    private static func canonicalizeWithoutProof(_ credential: VerifiableCredential) throws -> Data {
        let encoder = JSONEncoder()
        encoder.outputFormatting = .sortedKeys
        let data = try encoder.encode(credential)
        var dict = try JSONSerialization.jsonObject(with: data) as! [String: Any]
        dict.removeValue(forKey: "proof")
        let sorted = try JSONSerialization.data(withJSONObject: dict, options: [.sortedKeys])
        return sorted
    }
}

extension Data {
    func base64URLEncodedString() -> String {
        base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
}
```

- [ ] **Step 4: Verify compilation**

```bash
cd ios && xcodebuild -scheme SsdidWallet -destination 'platform=iOS Simulator,name=iPhone 16' build-for-testing 2>&1 | tail -10
```

- [ ] **Step 5: Commit**

```bash
git add ios/SsdidWalletTests/Offline/ ios/SsdidWalletUITests/Offline/
git commit -m "test(ios): add E2E test infrastructure — helpers, mocks, in-memory store"
```

---

## Task 2: OfflineVerificationTests — XCTest (9 tests)

**Files:**
- Create: `ios/SsdidWalletTests/Offline/OfflineVerificationTests.swift`

- [ ] **Step 1: Create the test class with all 9 tests**

Same 9 scenarios as Android `OfflineVerificationTest`:
1. `testNetworkError_freshBundle_returnsVerifiedOffline`
2. `testNetworkError_staleBundle_returnsDegraded`
3. `testNetworkError_noBundle_returnsFailed`
4. `testExpiredCredential_returnsFailed`
5. `testRevokedCredential_returnsFailed`
6. `testOfflineHappyPath_allChecksPass`
7. `testVerificationError_doesNotFallbackToOffline`
8. `testNetworkError_unknownRevocation_returnsDegraded`
9. `testNoExpiryDate_returnsVerifiedOffline`

Each test follows the same pattern:
1. Create key pair via `OfflineTestHelper.createKeyPair()`
2. Build DID document + credential
3. Configure `MockVerifier` and `InMemoryBundleStore`
4. Create `OfflineVerifier` with real `ClassicalProvider`
5. Create `VerificationOrchestrator` with mock + real offline verifier
6. Call `orchestrator.verify(credential:)` and assert result

Uses: `XCTAssertEqual`, `XCTAssertNotNil`, `XCTAssertTrue`

- [ ] **Step 2: Verify compilation and run**

```bash
cd ios && xcodebuild test -scheme SsdidWallet -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:SsdidWalletTests/OfflineVerificationTests 2>&1 | tail -20
```

- [ ] **Step 3: Commit**

```bash
git add ios/SsdidWalletTests/Offline/OfflineVerificationTests.swift
git commit -m "test(ios): add 9 offline verification unit tests (UC-7, UC-9–13)"
```

---

## Task 3: XCUITest Classes (4 classes, 18 tests)

**Files:**
- Create: `ios/SsdidWalletUITests/Offline/VerificationFlowUITests.swift`
- Create: `ios/SsdidWalletUITests/Offline/BundleManagementUITests.swift`
- Create: `ios/SsdidWalletUITests/Offline/OfflineSettingsUITests.swift`
- Create: `ios/SsdidWalletUITests/Offline/BackgroundSyncUITests.swift`

All 4 classes follow the existing iOS E2E pattern:
- `XCUIApplication` with `launchArguments: ["--skip-otp", "--ui-testing"]`
- `waitForExistence(timeout: 10)` for async operations
- `staticTexts`, `buttons`, `textFields` for element queries
- `swipeLeft()` for delete gestures

Each test class mirrors its Android counterpart:
- `VerificationFlowUITests`: 6 tests (green/yellow/red traffic light, details, offline badge, pre-verification check)
- `BundleManagementUITests`: 6 tests (add DID, invalid DID, refresh, delete, empty state, scan navigation)
- `OfflineSettingsUITests`: 5 tests (TTL picker, persistence, aging/expired/fresh badges)
- `BackgroundSyncUITests`: 1 test (foreground resume → `XCUIDevice.shared.press(.home)` → reactivate)

- [ ] **Step 1: Create all 4 test files**

Follow the patterns from `IdentityCreationUITests.swift` for setup and assertions.

- [ ] **Step 2: Add files to Xcode project (.pbxproj)**

Add to the SsdidWalletUITests target, not the main app target.

- [ ] **Step 3: Verify compilation**

```bash
cd ios && xcodebuild -scheme SsdidWallet -destination 'platform=iOS Simulator,name=iPhone 16' build-for-testing 2>&1 | tail -10
```

- [ ] **Step 4: Commit**

```bash
git add ios/SsdidWalletUITests/Offline/ ios/SsdidWallet.xcodeproj/project.pbxproj
git commit -m "test(ios): add 18 XCUITest E2E tests for offline validation (UC-1–6, UC-8)"
```

---

## Task 4: Full Build + Run

- [ ] **Step 1: Build for testing**

```bash
cd ios && xcodebuild -scheme SsdidWallet -destination 'platform=iOS Simulator,name=iPhone 16' build-for-testing 2>&1 | tail -10
```

- [ ] **Step 2: Run the unit tests (OfflineVerificationTests)**

```bash
cd ios && xcodebuild test -scheme SsdidWallet -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:SsdidWalletTests/OfflineVerificationTests 2>&1 | tail -20
```

- [ ] **Step 3: Fix any issues and commit**

```bash
git commit -m "fix(ios): address compilation issues in E2E offline tests"
```
