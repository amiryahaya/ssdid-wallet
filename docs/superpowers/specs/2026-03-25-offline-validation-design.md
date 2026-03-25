# Offline Validation Design

**Date:** 2026-03-25
**Status:** Draft
**Scope:** Full-stack offline verification for both holder and verifier roles, platform-neutral

## Overview

Integrate the existing offline verification infrastructure (`OfflineVerifier`, `BundleFetcher`, `BundleManager`, `DataStoreBundleStore`) into the full app experience. Add DI wiring, background sync, user-configurable TTL, bundle management UI, and a unified verification result surface.

Both Android and iOS implement the same domain contracts. Platform-specific details are confined to DI, background scheduling, and connectivity monitoring.

## Requirements

- **Holder-side:** Auto-refresh bundles for held credentials when online (opportunistic)
- **Verifier-side:** Explicit pre-fetch via scan or manual DID entry
- **TTL:** User-configurable with recommended defaults per credential category
- **Result display:** Traffic light (green/yellow/red) with expandable detail breakdown
- **Offline badge:** Always indicates when verification used the offline path
- **Graceful degradation:** Stale bundles produce degraded results, not failures

## Section 1: Verification Orchestrator

A `VerificationOrchestrator` composes the existing online (`VerifierImpl` / equivalent) and `OfflineVerifier` into a single entry point.

### Flow

```
UI -> VerificationOrchestrator -> tries online verifier
                                    | (network failure)
                                    v
                                  falls back to OfflineVerifier (cached bundle)
                                    |
                                    v
                                  returns UnifiedVerificationResult
```

### UnifiedVerificationResult

```
UnifiedVerificationResult
  status:     VERIFIED | VERIFIED_OFFLINE | FAILED | DEGRADED
  checks:     List<VerificationCheck>   # individual check results
  source:     ONLINE | OFFLINE
  bundleAge:  Duration?                 # age of cached bundle, if used
```

Each `VerificationCheck`:

```
VerificationCheck
  type:    SIGNATURE | EXPIRY | REVOCATION | BUNDLE_FRESHNESS
  status:  PASS | FAIL | UNKNOWN
  message: String                       # one-line human-readable explanation
```

### Behavior

- Online verification succeeds: return `VERIFIED` with `source=ONLINE`
- Online fails with network error: fall back to `OfflineVerifier`
  - Fresh bundle + all checks pass: `VERIFIED_OFFLINE`
  - Stale bundle or missing revocation data: `DEGRADED`
  - Signature invalid or credential expired: `FAILED`
- Online fails with verification error (not network): return `FAILED` immediately, no fallback

The orchestrator is a singleton. No changes to `VerifierImpl` or `OfflineVerifier` — it composes them.

## Section 2: Background Bundle Sync (Holder Side)

For credentials the user already holds, bundles auto-refresh opportunistically.

### Trigger Conditions

- Device comes online after being offline
- Periodic interval (configurable, default every 12 hours)
- App foreground resume (if any bundle is within 20% of its TTL)

### Sync Logic

1. Enumerate all held credentials, extract unique issuer DIDs
2. For each issuer, check if cached bundle is within refresh threshold
3. Fetch fresh DID document + status list for stale bundles
4. Update `BundleStore`, log last sync timestamp

### Failure Handling

- Network errors are silent — stale bundle is better than no bundle
- Partial success is acceptable (refresh what you can, skip what fails)
- No user notification unless all bundles expire without refresh (then a subtle warning badge on the credential card)

### Freshness Indicator on Credential Cards

- No indicator when bundle is fresh (clean UI by default)
- Subtle icon when bundle is aging (>50% of TTL)
- Warning when bundle is expired (offline verification will be degraded)

## Section 3: Verifier-Side Bundle Management

A "Prepare for Offline" screen accessible from settings or a dedicated verifier section.

### Adding Issuers to Cache

- **Scan credential:** Scan a QR code or receive a credential, extract the issuer DID, fetch and cache the bundle. Reuses existing credential parsing.
- **Manual DID entry:** Text field accepting a `did:ssdid:*` value. Validates format, resolves against registry, caches bundle.

### Bundle Management UI

- List of cached issuer bundles showing: issuer DID (truncated), friendly name if available from DID document, bundle age, TTL status
- Swipe to delete individual bundles
- "Refresh All" button for manual bulk refresh
- Pull-to-refresh on the list

### Pre-verification Check

When entering a verification flow while offline, the app checks if it has a valid bundle for the credential's issuer. If not, it shows a clear message: "No cached data for this issuer. Connect to the internet or add this issuer's bundle while online."

## Section 4: User-Configurable TTL with Recommendations

Accessible from Settings > Offline Verification.

### TTL Configuration

- Preset options: 1 day, 7 days, 14 days, 30 days, custom
- Default: **7 days**

### Recommended Defaults (Displayed Inline as Guidance)

| Credential Category | Recommended TTL | Rationale |
|---|---|---|
| Financial / payment | 1-3 days | Higher risk, fresher data needed |
| Government ID / age verification | 7-14 days | Changes infrequently |
| Membership / loyalty cards | 14-30 days | Low risk, rarely revoked |

Recommendations are guidance text, not enforced. The user's chosen TTL applies globally.

### Staleness Thresholds (Relative to User's TTL)

- **Fresh:** < 50% of TTL
- **Aging:** 50-100% of TTL
- **Expired:** > TTL (triggers degraded verification result)

## Section 5: Verification Result UI

### Traffic Light (Top-Level)

- **Green** — `VERIFIED` or `VERIFIED_OFFLINE` with fresh bundle. Message: "Credential verified" or "Credential verified offline."
- **Yellow** — `DEGRADED`. Stale bundle or missing revocation data. Message: "Verified with limitations — tap for details."
- **Red** — `FAILED`. Signature invalid, credential expired, or revoked. Message: "Verification failed — tap for details."

### Expandable Detail Breakdown (Tap to Reveal)

| Check | Possible States |
|---|---|
| Signature | Valid / Invalid / Unable to verify (no key) |
| Expiry | Valid / Expired / No expiry set |
| Revocation | Not revoked / Revoked / Unknown (no status list cached) |
| Bundle freshness | Fresh (age shown) / Stale (age shown) / No bundle |
| Verification source | Online / Offline |

Each check shows a small status icon (checkmark, warning, cross) and a one-line explanation.

### Offline Badge

When verification used the offline path, a small "Offline" chip appears next to the traffic light so the user always knows the source.

## Section 6: DI Wiring & Platform Notes

### Domain Interfaces (Platform-Neutral)

- `VerificationOrchestrator` — composes online + offline verifiers
- `BundleStore` — persistence abstraction (already exists)
- `BundleSyncScheduler` — interface for scheduling background refresh
- `ConnectivityMonitor` — interface for detecting online/offline state

### Android Implementation

- **DI:** Hilt — provision `BundleFetcher`, `BundleManager`, `OfflineVerifier`, `VerificationOrchestrator` as `@Singleton`
- **Background sync:** `WorkManager` implements `BundleSyncScheduler` — periodic + connectivity-triggered work
- **Connectivity:** `ConnectivityManager` callback implements `ConnectivityMonitor`
- **UI:** New Compose screens — `VerificationResultScreen`, `BundleManagementScreen`, TTL settings section

### iOS Implementation

- **DI:** Protocol-based injection (or Swift package-level injection)
- **Background sync:** `BGAppRefreshTask` / `BGProcessingTask` implements `BundleSyncScheduler`
- **Connectivity:** `NWPathMonitor` implements `ConnectivityMonitor`
- **UI:** SwiftUI views for the same screens

### Shared Contract

Both platforms implement identical domain interfaces and produce the same `UnifiedVerificationResult` model. The verification logic, fallback behavior, and TTL rules are the same — only the platform plumbing differs.

## Existing Code Leveraged

| Component | Location | Status |
|---|---|---|
| `OfflineVerifier` | `domain/verifier/offline/OfflineVerifier` | Complete, tested |
| `BundleFetcher` | `domain/verifier/offline/BundleFetcher` | Complete, tested |
| `BundleManager` | `domain/verifier/offline/BundleManager` | Complete, tested |
| `DataStoreBundleStore` | `domain/verifier/offline/DataStoreBundleStore` | Complete, tested |
| `VerificationBundle` | `domain/verifier/offline/VerificationBundle` | Complete |
| `VerifierImpl` | `domain/verifier/VerifierImpl` | Complete, tested |
| `RevocationManager` | `domain/revocation/RevocationManager` | Complete, tested |
| `StatusListFetcher` | `domain/revocation/HttpStatusListFetcher` | Complete, tested |

## Registry Impact

None. Uses existing `GET /api/did/{did}` endpoint. Status list URLs are embedded in credentials and fetched directly.

## Out of Scope

- Issuer directory / registry browsing
- Per-credential TTL overrides (global TTL only)
- Peer-to-peer bundle sharing between devices
- iOS-specific code in this design (architecture only; implementation per platform plan)
