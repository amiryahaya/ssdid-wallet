# SSDID Wallet Production Readiness Plan

**Last updated:** 2026-03-23
**Source:** SSDID Ecosystem Review (doc 17), filtered for wallet-specific items.

## Status: All Critical and High Items Complete ✅

### Phase A: Key Recovery — ✅ DONE

| Tier | What | Status |
|------|------|--------|
| Tier 1 | Offline recovery key — generate, export, restore | ✅ `RecoveryManager` on both platforms, wired to UI |
| Tier 2 | Shamir's Secret Sharing (3-of-5 social recovery) | ✅ `ShamirSecretSharing` (GF(256)) + `SocialRecoveryManager`, iOS ported from Android |
| Tier 3 | Institutional guardian key in DID document | ✅ `InstitutionalRecoveryManager` on both platforms |

### Phase B: Multi-Device Enrollment — ✅ DONE

| What | Status |
|------|--------|
| QR pairing protocol | ✅ `DeviceManager` on both platforms — initiate, join, approve, revoke |
| Device management | ✅ UI wired to real manager calls, polling for status |

### Phase C: DID Validation + Tests — ✅ DONE

| What | Status |
|------|--------|
| `DID.validate()` | ✅ Both platforms — 22-char min, 128 max, ASCII base64url, wired to 14 call sites |
| Fix broken iOS tests | ✅ 12 test files fixed, CI test step re-enabled, Keychain probe for skips |
| Concurrent vault tests | ✅ 3 Android tests for concurrent operations |

### Phase D: Key Rotation — ✅ DONE

| What | Status |
|------|--------|
| Registry `nextKeyHash` support | ✅ Registry updated with KERI pre-rotation, cooldown, grace period |
| Wallet `KeyRotationManager` | ✅ Both platforms — `prepareRotation` + `executeRotation` |
| Wrapping key re-wrap during rotation | ✅ Fixed: decrypt prerot → re-encrypt with identity wrap alias |
| `KeyRotationScreen` UI | ✅ Wired to real manager calls |

### Phase E: Credential Revocation — ✅ DONE (wallet side)

| What | Status |
|------|--------|
| `RevocationManager` in auth flow | ✅ Wired into `SsdidClient.authenticate()` on both platforms |
| Status dots (Connected Services) | ✅ Green/yellow/red/revoked with revocation check |
| Status list service | ✅ Separate `ssdid_status_list` service built (registry side) |

### Phase F: Security Hardening — ✅ DONE

| What | Status |
|------|--------|
| Biometric / Auto-lock | ✅ `LockOverlay` (iOS) / `LockScreen` (Android), configurable timeout |
| Secure Enclave key wrapping (iOS) | ✅ ECDH P-256 → HKDF → AES-256-GCM, lazy migration |
| Certificate pinning | ✅ Real SPKI SHA-256 pins, EC header fix for iOS |
| CSRF / state parameter | ✅ Deep link `state` param echoed in callbacks |
| Protocol version | ✅ `protocol_version` in `ChallengeResponse` DTOs |
| DID collision retry | ✅ Auto-retry with new DID on 409 Conflict |

---

## Remaining Items (Medium Priority — GA Hardening)

| Item | Priority | Status |
|------|----------|--------|
| Offline credential verification | Medium | ❌ Not started (L effort, needs DID doc bundling) |
| Wallet discovery endpoint | Low | ❌ Not started (questionable value) |
| VaultImpl actor migration (iOS) | Medium | ❌ Deferred (needs Sendable dependencies) |
| Fix iOS test assertions (key sizes, JWT) | Medium | ⚠️ Skipped on CI, pending device testing |

---

## CI/CD & Distribution

| Item | Status |
|------|--------|
| Android CI (compile + test + lint) | ✅ Green |
| iOS CI (compile + test) | ✅ Green (device-only tests skipped via Keychain probe) |
| Android publish → Play Store Internal Testing | ✅ Workflow: tag `android/v*` |
| iOS publish → App Store Connect / TestFlight | ✅ Workflow: tag `ios/v*` |
| Beta signup API (TestFlight invitations) | ✅ Podman container, App Store Connect API |
| Landing page beta signup form | ✅ "Join iOS Beta" section |
| App name: "SSDID Wallet" | ✅ Both platforms, all locales |

---

## E2E Testing

| Tool | Tests | Status |
|------|:---:|--------|
| Maestro (mobile) | 11 flows | UC-01 verified on iOS simulator, others need Maestro Studio tuning |
| Playwright (desktop) | 22 tests | 19 passed, 3 skipped against live registry |
| Integration tests (wallet↔registry) | 8 Android + 10 iOS | Passed against https://registry.ssdid.my |
| Use case document | 108 test cases | Tracking at `docs/use-cases-and-e2e-tests.md` |
