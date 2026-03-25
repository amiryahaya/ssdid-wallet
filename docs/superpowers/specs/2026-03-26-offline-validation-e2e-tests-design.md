# Offline Validation Use Cases & E2E Test Design

**Date:** 2026-03-26
**Status:** Draft
**Scope:** 13 use cases with automated E2E tests (Espresso + XCUITest) and instrumented offline tests, both platforms

## Overview

Define the user-facing use cases that prove offline validation works end-to-end, then implement automated E2E tests for both Android and iOS. Tests use the real registry (`registry.ssdid.my`) for online scenarios. Offline fallback is tested at the domain layer via instrumented tests with mock verifiers.

## Constraints

- **Real registry:** All tests resolve DIDs against `registry.ssdid.my` — no mock HTTP layer
- **Offline simulation:** Offline fallback tested via instrumented unit tests injecting a failing verifier, not by toggling airplane mode
- **Platforms:** Android (Espresso + Compose Test + Hilt) and iOS (XCUITest + XCTest)
- **Coverage:** All 13 use cases, 5 test classes per platform

## Use Cases

### Holder-Side

**UC-1: Verify a held credential online**
- Actor: Wallet holder
- Precondition: User has a credential issued by a registered issuer, device is online
- Flow: User selects credential → taps "Verify" → orchestrator resolves issuer DID from registry → verifies signature online → returns VERIFIED
- Postcondition: Traffic light shows green, source shows "Online"

**UC-2: View verification result with traffic light**
- Actor: Wallet holder
- Precondition: A verification has completed (any status)
- Flow: Result screen displays → traffic light color matches status → user taps to expand → individual checks visible (signature, expiry, revocation, freshness)
- Postcondition: All check rows display with correct icons and messages

**UC-3: See freshness indicators on credential cards**
- Actor: Wallet holder
- Precondition: User has credentials with cached bundles at varying freshness
- Flow: User views credential list → fresh bundles show no badge → aging bundles (>50% TTL) show yellow "Bundle aging" → expired bundles show red "Bundle expired"
- Postcondition: Badges match actual freshness ratios

**UC-4: Configure bundle TTL in settings**
- Actor: Wallet holder
- Precondition: User navigates to Settings
- Flow: User taps "Bundle TTL" → picker shows presets (1, 7, 14, 30 days) with recommendations → user selects 14 days → setting persists
- Postcondition: TTL value saved, displayed correctly on return to settings

### Verifier-Side

**UC-5: Add issuer by DID**
- Actor: Verifier
- Precondition: User is on "Prepare for Offline" screen, device is online
- Flow: User taps Add → enters `did:ssdid:...` → app resolves DID from registry → caches bundle → issuer appears in list
- Postcondition: Bundle stored with freshness "Fresh"

**UC-6: Manage cached bundles**
- Actor: Verifier
- Precondition: User has cached bundles
- Flow: User views bundle list → taps "Refresh All" → stale bundles refresh → user swipes to delete one → bundle removed
- Postcondition: List reflects changes, freshness badges update

**UC-7: Verify credential using cached bundle (offline path)**
- Actor: Verifier
- Precondition: Bundle cached for credential's issuer, device offline
- Flow: Verify credential → online attempt fails → orchestrator falls back to OfflineVerifier → returns VERIFIED_OFFLINE with fresh bundle
- Postcondition: Traffic light shows green with "Offline" badge

### Background Sync

**UC-8: Bundles auto-refresh on foreground resume**
- Actor: System (automatic)
- Precondition: App was backgrounded, bundles nearing expiry (>80% TTL consumed)
- Flow: User opens app → lifecycle observer checks bundle freshness → triggers sync for stale bundles
- Postcondition: Bundle freshness ratios decrease (bundles refreshed)

### Offline Fallback (Domain-Level)

**UC-9: Network error → offline fallback → VERIFIED_OFFLINE**
- Precondition: Fresh bundle cached, online verifier throws IOException/URLError
- Result: status=VERIFIED_OFFLINE, checks all PASS, source=OFFLINE

**UC-10: Network error + stale bundle → DEGRADED**
- Precondition: Stale bundle cached (expired TTL), online verifier fails
- Result: status=DEGRADED, bundleFreshness=FAIL, source=OFFLINE

**UC-11: Network error + no bundle → FAILED**
- Precondition: No bundle cached for issuer, online verifier fails
- Result: status=FAILED, error message about missing bundle

**UC-12: Expired credential → FAILED**
- Precondition: Credential's expirationDate is in the past
- Result: status=FAILED, expiry check=FAIL, regardless of online/offline path

**UC-13: Revoked credential via cached status list → FAILED**
- Precondition: Bundle includes status list with credential's index marked revoked
- Result: status=FAILED, revocation check=FAIL

## Test Architecture

### Approach

Feature-scoped test classes, 5 per platform:

| Test Class | Use Cases | Type | Description |
|---|---|---|---|
| `VerificationFlowTest` | UC-1, UC-2 | UI (Espresso/XCUITest) | Online verification + traffic light result |
| `BundleManagementTest` | UC-5, UC-6 | UI (Espresso/XCUITest) | Add/refresh/delete bundles |
| `OfflineSettingsTest` | UC-3, UC-4 | UI (Espresso/XCUITest) | TTL config + freshness badges |
| `BackgroundSyncTest` | UC-8 | UI (Espresso/XCUITest) | Foreground resume sync |
| `OfflineVerificationTest` | UC-7, UC-9–13 | Instrumented unit test | Offline fallback logic |

### Why This Split

- UI tests (classes 1–4) exercise real screens against the real registry
- Instrumented tests (class 5) test offline fallback without needing to disable the network — inject a mock verifier that throws network errors

## Android Test Classes (Espresso + Compose)

### VerificationFlowTest (UC-1, UC-2)

**Setup:** Hilt + ComposeTestRule. Create identity via real registry, store a test credential.

**Test 1: verify credential online shows green traffic light**
- Navigate to credential detail
- Tap "Verify"
- Assert: green checkmark icon visible
- Assert: "Credential verified" text displayed
- Assert: "Online" source in detail section

**Test 2: verification result shows expandable check details**
- After verification completes
- Tap the traffic light card
- Assert: "Signature" row with checkmark
- Assert: "Expiry" row with checkmark
- Assert: "Revocation" row visible
- Assert: "Bundle Freshness" row visible

**Test 3: yellow traffic light for DEGRADED result**
- Setup: Hilt test module replaces `Verifier` with `FakeOnlineVerifier(shouldThrow: IOException)`. Pre-cache a stale bundle (fetchedAt beyond TTL) via `OfflineTestHelper.cacheBundleForIssuer(did, freshnessRatio: 1.5)` using Hilt's `@BindValue` to inject a pre-seeded `BundleStore`.
- Navigate to credential detail → tap "Verify"
- Assert: yellow warning icon visible
- Assert: "Verified with limitations" text displayed

**Test 4: red traffic light for FAILED result**
- Setup: Same Hilt injection, but use expired credential (expirationDate in the past)
- Assert: red X icon visible
- Assert: "Verification failed" text displayed

**Test 5: offline badge appears when source is offline**
- Setup: Same Hilt injection as Test 3 but with fresh bundle
- Assert: "Offline" chip visible next to traffic light

**Test 6: pre-verification check shows message when no bundle cached (offline)**
- Setup: Hilt injection with `FakeOnlineVerifier(shouldThrow: IOException)` and empty `BundleStore`
- Navigate to credential detail → tap "Verify"
- Assert: "No cached data for this issuer" message visible (or equivalent FAILED state with clear guidance)

**Note on UI test dependency injection:** Tests 3-6 use Hilt's `@UninstallModules(OfflineModule::class)` + `@BindValue` to replace the real `Verifier` with `FakeOnlineVerifier` and pre-seed `BundleStore` with test bundles. This allows UI tests to exercise the full screen rendering for all traffic light states without network manipulation.

### BundleManagementTest (UC-5, UC-6)

**Setup:** Navigate to Settings → Prepare for Offline.

**Test 1: add issuer by valid DID**
- Tap Add button → enter known registered DID → tap "Add"
- Assert: issuer appears in bundle list with "Fresh" badge

**Test 2: reject invalid DID format**
- Tap Add → enter "not-a-did" → tap "Add"
- Assert: error message containing "Invalid"

**Test 3: refresh all bundles updates freshness**
- With cached bundle showing "Bundle aging" badge → tap Refresh button
- Assert: progress indicator appears then disappears
- Assert: bundle's freshness badge changes (aging → fresh/no badge) or timestamp text updates

**Test 4: delete bundle via swipe**
- Swipe bundle card → tap delete
- Assert: bundle removed from list

**Test 5: empty state shown when no bundles**
- With no cached bundles
- Assert: empty state message visible

**Test 6: scan credential to add issuer (QR flow)**
- Tap Add → tap "Scan Credential QR"
- Assert: QR scanner screen opens
- Note: Full QR scan simulation requires injecting a mock camera/barcode result. If not feasible in the test environment, this test verifies the navigation to the scan screen only. The actual scan-to-cache flow is covered by the existing ScanQrScreen → deep link → extract issuer DID path.

### OfflineSettingsTest (UC-3, UC-4)

**Setup:** Navigate to Settings.

**Test 1: TTL picker shows presets with recommendations**
- Tap "Bundle TTL" row
- Assert: dialog shows 1, 7, 14, 30 day options
- Assert: recommendation text visible for each preset

**Test 2: selecting TTL persists value**
- Select "14 days" → navigate away → return to settings
- Assert: Bundle TTL displays "14 days"

**Test 3: freshness badge on credential card (aging)**
- Setup: Use Hilt `@BindValue` to inject a pre-seeded `BundleStore` with a bundle whose `fetchedAt` is at 60% of TTL. The `OfflineTestHelper.cacheBundleForIssuer(did, freshnessRatio: 0.6)` computes `fetchedAt = now - (0.6 * ttl)`.
- Navigate to credentials list
- Assert: "Bundle aging" badge visible on credential card

**Test 4: freshness badge on credential card (expired)**
- Setup: Same injection, `freshnessRatio: 1.5` (fetchedAt beyond TTL)
- Assert: "Bundle expired" badge visible

**Test 5: no badge when bundle is fresh**
- Setup: Same injection, `freshnessRatio: 0.1`
- Assert: no freshness badge visible on credential card

### BackgroundSyncTest (UC-8)

**Test 1: foreground resume triggers bundle refresh**
- Setup: Use Hilt `@BindValue` to inject `BundleStore` with a bundle at freshnessRatio > 0.8 (i.e., >80% of TTL consumed, meaning <20% remaining)
- Simulate app background → foreground via ActivityScenario
- Wait briefly for async sync
- Assert: UI-observable change — navigate to bundle management and verify the bundle's freshness badge has changed from "aging" to no badge (fresh), or the timestamp text has updated

### OfflineVerificationTest — Instrumented (UC-7, UC-9–13)

**Setup:** Hilt instrumented test with real ClassicalProvider, InMemoryBundleStore, mock online Verifier that throws IOException.

**Test 1: network error with fresh bundle → VERIFIED_OFFLINE (UC-9)**
- Create real key pair, sign credential, cache bundle with DID doc
- Mock online verifier throws IOException
- Call orchestrator.verify(credential)
- Assert: status == VERIFIED_OFFLINE, source == OFFLINE, signature check == PASS, bundleAge != null

**Test 2: network error with stale bundle → DEGRADED (UC-10)**
- Same as test 1 but fetchedAt set beyond TTL
- Assert: status == DEGRADED, bundleFreshness check == FAIL

**Test 3: network error with no cached bundle → FAILED (UC-11)**
- Empty bundle store, online verifier throws IOException
- Assert: status == FAILED, error about missing bundle

**Test 4: expired credential → FAILED (UC-12)**
- Credential with expirationDate in the past, fresh bundle cached
- Assert: status == FAILED, expiry check == FAIL

**Test 5: revoked credential via cached status list → FAILED (UC-13)**
- Credential with credentialStatus pointing to index, bundle includes status list with that index revoked
- Assert: status == FAILED, revocation check == FAIL

**Test 6: offline happy path — all checks pass (UC-7)**
- Fresh bundle, valid credential, all checks pass
- Assert: status == VERIFIED_OFFLINE, all 4 checks == PASS, source == OFFLINE

**Test 7: verification error (not network) does NOT trigger offline fallback**
- Fresh bundle cached for issuer
- Mock online verifier throws `SecurityException("invalid signature")` (not IOException)
- Call orchestrator.verify(credential)
- Assert: status == FAILED, source == ONLINE
- Assert: OfflineVerifier was NOT consulted (verify mock call count == 0)

**Test 8: network error + fresh bundle + unknown revocation → DEGRADED**
- Fresh bundle cached but with no status list (statusList == null)
- Mock online verifier throws IOException
- Assert: status == DEGRADED, revocation check == UNKNOWN, bundleFreshness check == PASS

**Test 9: credential with no expiry date → VERIFIED_OFFLINE**
- Fresh bundle, valid credential with `expirationDate = null`
- Mock online verifier throws IOException
- Assert: status == VERIFIED_OFFLINE (not FAILED)
- Assert: expiry check == PASS with informational message

## iOS Test Classes (XCUITest + XCTest)

Mirror Android exactly, adapted for iOS APIs:

### VerificationFlowUITests (UC-1, UC-2)

**Setup:** XCUIApplication with `["--skip-otp", "--ui-testing"]` launch arguments.

**Tests 1–3:** Same scenarios as Android.
- Use `staticTexts["Credential verified"].waitForExistence(timeout: 10)`
- Assert SF Symbol icons via `images["checkmark.circle.fill"]`
- Assert "Offline" chip via `staticTexts["Offline"]`

### BundleManagementUITests (UC-5, UC-6)

**Tests 1–5:** Same scenarios. Use `textFields`, `buttons`, `swipeLeft()`, `waitForExistence(timeout:)`.

### OfflineSettingsUITests (UC-3, UC-4)

**Tests 1–5:** Same scenarios. Adapted for SwiftUI sheets and pickers.

### BackgroundSyncUITests (UC-8)

**Test 1:** `XCUIDevice.shared.press(.home)` → reactivate app → assert bundle refreshed.

### OfflineVerificationTests — XCTest (UC-7, UC-9–13)

**Tests 1–6:** Same scenarios as Android instrumented tests.
- Uses real `ClassicalProvider` (CryptoKit)
- In-memory `BundleStore` (dictionary-backed)
- `MockVerifier` throws `URLError(.notConnectedToInternet)` instead of `IOException`

## Test Infrastructure

### Android: `OfflineTestHelper.kt`

Shared across all test classes:
- `createTestIdentity()` — creates real identity via registry, returns Identity + key pair
- `createTestCredential(issuer, subject)` — signs a real VC with issuer's key
- `cacheBundleForIssuer(issuerDid, freshnessRatio)` — fetches DID doc from registry, stores bundle with computed fetchedAt
- `cacheBundleWithStatusList(issuerDid, revokedIndices)` — includes GZIP-compressed bitstring status list
- `navigateToSettings(rule)` — wallet home → settings
- `navigateToBundleManagement(rule)` — settings → Prepare for Offline

### Android: `FakeOnlineVerifier.kt`

For instrumented offline tests:
- Implements `Verifier` interface
- Configurable: `shouldThrow: Throwable?` and `shouldReturn: Boolean`
- Default: throws `IOException("simulated network failure")`

### iOS: `OfflineTestHelper.swift`

Mirror of Android helper:
- Same functions adapted for Swift/CryptoKit
- `ClassicalProvider` for real key generation
- Temp directory `FileBundleStore` for test isolation

### iOS: `MockVerifier.swift`

- Conforms to `Verifier` protocol
- Configurable error/return behavior
- Default: throws `URLError(.notConnectedToInternet)`

### Test Data

Both platforms share the same patterns:
- Test DID: created live against `registry.ssdid.my` per test run
- Test credential: Ed25519-signed VC with configurable expiration and status
- Status list: GZIP-compressed base64url bitstring with controllable revoked indices

### CI Configuration

- Tag UI tests: `@Tag("e2e")` (Android) / test plan filter (iOS)
- Tag instrumented tests: `@Tag("offline")`
- UI tests need running emulator/simulator
- Instrumented offline tests run without UI (faster)
- Registry-dependent tests should retry once on transient network failure

## Test Matrix Summary

| # | Use Case | Android Class | iOS Class | Type |
|---|----------|---------------|-----------|------|
| UC-1 | Verify online | VerificationFlowTest | VerificationFlowUITests | UI |
| UC-2 | Traffic light UI | VerificationFlowTest | VerificationFlowUITests | UI |
| UC-3 | Freshness badges | OfflineSettingsTest | OfflineSettingsUITests | UI |
| UC-4 | TTL config | OfflineSettingsTest | OfflineSettingsUITests | UI |
| UC-5 | Add issuer DID | BundleManagementTest | BundleManagementUITests | UI |
| UC-6 | Manage bundles | BundleManagementTest | BundleManagementUITests | UI |
| UC-7 | Offline verify | OfflineVerificationTest | OfflineVerificationTests | Instrumented |
| UC-8 | Background sync | BackgroundSyncTest | BackgroundSyncUITests | UI |
| UC-9 | Fallback → VERIFIED_OFFLINE | OfflineVerificationTest | OfflineVerificationTests | Instrumented |
| UC-10 | Fallback → DEGRADED | OfflineVerificationTest | OfflineVerificationTests | Instrumented |
| UC-11 | Fallback → FAILED (no bundle) | OfflineVerificationTest | OfflineVerificationTests | Instrumented |
| UC-12 | Expired → FAILED | OfflineVerificationTest | OfflineVerificationTests | Instrumented |
| UC-13 | Revoked → FAILED | OfflineVerificationTest | OfflineVerificationTests | Instrumented |

**Total: 27 test methods per platform, 54 total across both platforms.**

Breakdown: VerificationFlowTest (6) + BundleManagementTest (6) + OfflineSettingsTest (5) + BackgroundSyncTest (1) + OfflineVerificationTest (9) = 27.

Note: Pull-to-refresh (BundleManagementTest) shares the same code path as "Refresh All" button — covered by Test 3's assertion. QR scan test (BundleManagementTest Test 6) verifies navigation only; full scan-to-cache requires mock camera injection.
