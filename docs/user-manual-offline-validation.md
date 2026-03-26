# SSDID Wallet — Offline Validation User Manual

**Version:** 1.0
**Last Updated:** 2026-03-26
**Platforms:** Android (Kotlin/Compose) | iOS (Swift/SwiftUI)

---

## Table of Contents

1. [Overview](#overview)
2. [Onboarding](#onboarding)
3. [Getting Started](#getting-started)
4. [Verifying a Credential](#verifying-a-credential)
5. [Understanding Verification Results](#understanding-verification-results)
6. [Preparing for Offline Use](#preparing-for-offline-use)
7. [Managing Cached Bundles](#managing-cached-bundles)
8. [Configuring Bundle TTL](#configuring-bundle-ttl)
9. [Freshness Indicators](#freshness-indicators)
10. [Offline Verification](#offline-verification)
11. [Background Sync](#background-sync)
12. [Troubleshooting](#troubleshooting)
13. [Security Considerations](#security-considerations)

---

## 1. Overview

SSDID Wallet supports **offline credential verification** — allowing you to verify the authenticity of verifiable credentials even without an internet connection. The wallet automatically caches issuer information (called "verification bundles") so that signature checks, expiry validation, and revocation lookups can happen locally.

### Key Concepts

| Term | Description |
|------|-------------|
| **Verification Bundle** | A cached copy of an issuer's DID Document and (optionally) their revocation status list. Used for offline verification. |
| **TTL (Time-To-Live)** | How long a cached bundle is considered trustworthy. Default: 7 days. Configurable in Settings. |
| **Traffic Light** | The color-coded verification result: Green (verified), Yellow (verified with limitations), Red (failed). |
| **Freshness** | How recently a bundle was fetched. Fresh bundles produce confident results; stale bundles produce degraded results. |

### Two Roles

- **Holder** — You own credentials and want to verify they're still valid
- **Verifier** — You check credentials presented by others and want to prepare for offline scenarios

---

## 2. Onboarding

When you first open SSDID Wallet, you'll be guided through a setup process to create your decentralized identity. This must be completed before you can receive or verify credentials.

### Step 1: Welcome Screen

The onboarding screen introduces SSDID Wallet and its core capabilities: self-sovereign identity, post-quantum security, and offline verification.

Tap **Get Started** to begin, or **Restore** if you're recovering an existing identity from backup.

> [Screenshot: Android — Onboarding welcome screen with "Get Started" and "Restore" buttons]

> [Screenshot: iOS — Onboarding welcome screen with "Get Started" and "Restore" buttons]

### Step 2: Create Your Identity — Display Name

Enter a display name for your identity. This is how services will identify you (e.g., "Amir's Personal ID"). You can have multiple identities later.

> [Screenshot: Android — Create Identity screen, Step 1: Display Name field]

> [Screenshot: iOS — Create Identity screen, Step 1: Display Name field]

### Step 3: Email Verification (Optional)

If you'd like to associate an email with your identity, enter it here. A verification code will be sent to confirm ownership. You can skip this step.

> [Screenshot: Android — Email verification screen with code entry field]

> [Screenshot: iOS — Email verification screen with code entry field]

### Step 4: Choose a Signing Algorithm

Select the cryptographic algorithm for your identity's signing key. The wallet supports both classical and post-quantum algorithms:

| Algorithm Group | Options | Best For |
|----------------|---------|----------|
| **Classical** | Ed25519, ECDSA P-256, ECDSA P-384 | Broad compatibility with existing systems |
| **KAZ-Sign** (Post-Quantum) | KAZ-Sign 128, 192, 256 | Future-proof against quantum computing |
| **ML-DSA** (Post-Quantum) | ML-DSA-44, 65, 87 | NIST-standardized post-quantum |
| **SLH-DSA** (Post-Quantum) | SHA2 and SHAKE variants | Stateless hash-based, conservative security |

The default is **KAZ-Sign 192** — a good balance of security and performance.

> [Screenshot: Android — Algorithm selection screen showing Classical and PQC groups]

> [Screenshot: iOS — Algorithm selection screen showing Classical and PQC groups]

### Step 5: Set Up Biometric Authentication (Optional)

Enable Face ID / Touch ID (iOS) or fingerprint / face unlock (Android) to protect access to your wallet. You can skip this and set it up later in Settings.

> [Screenshot: Android — Biometric setup screen with "Enable" and "Skip" buttons]

> [Screenshot: iOS — Biometric setup screen with Face ID prompt]

### Step 6: Wallet Home

After setup, you'll see the Wallet Home screen. Your identity is created and registered with the SSDID registry. You're ready to:
- **Receive credentials** from issuers via QR code or deep link
- **Verify credentials** you hold
- **Present credentials** to services that request them
- **Prepare for offline** verification

> [Screenshot: Android — Wallet Home screen showing the newly created identity]

> [Screenshot: iOS — Wallet Home screen showing the newly created identity]

### Receiving Your First Credential

Before you can verify anything offline, you need at least one credential. Credentials are received from issuers (e.g., a university, government agency, or employer):

1. The issuer sends you a **credential offer** via QR code, deep link, or notification
2. Tap **Scan QR** on the Wallet Home screen (or open the deep link)
3. Review the credential details — issuer name, credential type, claims
4. Tap **Accept** to store the credential in your wallet

> [Screenshot: Android — Scan QR screen]

> [Screenshot: Android — Credential offer review screen with "Accept" button]

> [Screenshot: iOS — Credential offer review screen with "Accept" button]

Once you have a credential, the wallet automatically caches the issuer's verification bundle in the background. You're now ready for both online and offline verification.

---

## 3. Getting Started

Offline validation works automatically once you have credentials in your wallet. No setup is required for basic use.

For advanced offline preparation (e.g., verifying credentials in areas without connectivity), see [Preparing for Offline Use](#preparing-for-offline-use).

### Prerequisites

- SSDID Wallet installed (Android 8.0+ or iOS 16.0+)
- At least one identity created in the wallet
- At least one verifiable credential received

---

## 4. Verifying a Credential

### Step 1: Open Credential Detail

From the Wallet Home screen, tap **Credentials** to see your credential list. Tap any credential to open its detail view.

> [Screenshot: Wallet Home screen showing the Credentials button]

> [Screenshot: Credential list showing one or more credentials with issuer name and type]

### Step 2: Tap "Verify Credential"

On the credential detail screen, tap the **Verify Credential** button. The wallet will attempt to verify the credential's signature, expiry, and revocation status.

> [Screenshot: Credential detail screen with the "Verify Credential" button highlighted]

### Step 3: View the Result

The verification result appears as a **traffic light** indicator:

> [Screenshot: Green verification result — "Credential verified"]

- **Online verification:** The wallet contacted the issuer's registry directly. Results are fully up-to-date.
- **Offline verification:** The wallet used a cached bundle. An "Offline" badge appears next to the result.

> [Screenshot: Green verification result with "Offline" badge]

### Step 4: View Details (Optional)

Tap the traffic light card to expand the **detail breakdown**. Each check is shown individually:

> [Screenshot: Expanded verification details showing Signature, Expiry, Revocation, and Bundle Freshness checks]

| Check | What It Means |
|-------|---------------|
| **Signature** | Whether the credential's cryptographic signature is valid |
| **Expiry** | Whether the credential is within its validity period |
| **Revocation** | Whether the credential has been revoked by the issuer |
| **Bundle Freshness** | How recently the verification data was fetched (offline only) |

Each check shows a status icon:
- Checkmark = Pass
- Warning = Unknown (e.g., no cached revocation data)
- Cross = Fail

---

## 5. Understanding Verification Results

### Green — Verified

> [Screenshot: Green traffic light with checkmark icon]

**"Credential verified"** or **"Credential verified offline"**

All checks passed. The credential is authentic, not expired, and not revoked. If verified offline, the cached bundle was fresh.

### Yellow — Verified with Limitations

> [Screenshot: Yellow traffic light with warning icon]

**"Verified with limitations — tap for details"**

The credential's signature is valid, but there are caveats:
- **Stale bundle:** The cached verification data is older than your configured TTL. The credential was probably valid when the bundle was fetched, but the issuer may have revoked it since.
- **Missing revocation data:** No cached revocation status list was available. The signature is valid, but revocation status is unknown.

**What to do:** Connect to the internet and verify again for a fully confident result, or refresh your bundles in Settings > Prepare for Offline.

### Red — Verification Failed

> [Screenshot: Red traffic light with X icon]

**"Verification failed — tap for details"**

One or more critical checks failed:
- **Invalid signature:** The credential was tampered with or the issuer's key doesn't match
- **Expired:** The credential's validity period has passed
- **Revoked:** The issuer has explicitly revoked this credential

**What to do:** Tap to expand details and identify which check failed. An expired credential cannot be renewed through the wallet — contact the issuer.

---

## 6. Preparing for Offline Use

If you know you'll be in an area without internet (e.g., remote sites, underground facilities, international travel), you can pre-cache verification bundles for specific issuers.

### Accessing the Prepare for Offline Screen

1. Open **Settings** from the Wallet Home screen
2. Scroll to the **Offline Verification** section
3. Tap **Prepare for Offline**

> [Screenshot: Settings screen showing the "Offline Verification" section with "Bundle TTL" and "Prepare for Offline" items]

> [Screenshot: "Prepare for Offline" screen showing the bundle list (or empty state)]

### Adding an Issuer by DID

1. Tap the **Add** (+) button in the top-right corner
2. Enter the issuer's DID (e.g., `did:ssdid:abc123...`)
3. Tap **Add**
4. The wallet fetches the issuer's DID Document and status list from the registry
5. The bundle appears in the list with a "Fresh" status

> [Screenshot: Add Issuer dialog with DID text field]

> [Screenshot: Bundle list showing a newly added issuer with "Fresh" badge]

### Adding an Issuer by Scanning

1. Tap the **Add** (+) button
2. Tap **Scan Credential QR**
3. Scan a QR code containing a verifiable credential
4. The wallet extracts the issuer's DID and caches their bundle

> [Screenshot: Add Issuer dialog showing the "Scan Credential QR" button]

---

## 7. Managing Cached Bundles

### Viewing Bundles

The "Prepare for Offline" screen shows all cached bundles with:
- **Issuer DID** (truncated for readability)
- **Freshness badge** — indicates how fresh the cached data is
- **Fetch timestamp** — when the bundle was last updated

> [Screenshot: Bundle list with multiple issuers showing different freshness states]

### Refreshing Bundles

Tap the **Refresh** button in the top-right to update all stale bundles. A progress indicator appears during the refresh.

> [Screenshot: Bundle list with progress indicator during refresh]

### Deleting a Bundle

Swipe left on a bundle and tap **Delete** to remove it. This frees storage but means you won't be able to verify credentials from that issuer while offline.

> [Screenshot: Bundle card with swipe-to-delete action revealed]

### Empty State

If no bundles are cached, the screen shows a message:

> [Screenshot: Empty bundle list with "No cached bundles" message]

---

## 8. Configuring Bundle TTL

The **TTL (Time-To-Live)** controls how long a cached bundle is considered fresh. After the TTL expires, the bundle is considered "stale" and offline verification produces a **Degraded** (yellow) result instead of a fully confident green.

### Changing the TTL

1. Open **Settings**
2. Tap **Bundle TTL** in the Offline Verification section
3. Select a preset or enter a custom value

> [Screenshot: TTL picker dialog showing 1, 7, 14, 30 day presets with recommendations]

### Recommended TTL by Credential Type

| Credential Type | Recommended TTL | Why |
|----------------|-----------------|-----|
| Financial / payment credentials | **1-3 days** | High risk — revocation must be detected quickly |
| Government ID / age verification | **7-14 days** | Rarely changes; moderate risk |
| Membership / loyalty cards | **14-30 days** | Low risk; revocations are uncommon |

These recommendations appear as guidance text in the TTL picker. The setting applies globally to all bundles.

### How TTL Affects Verification

| Bundle Age vs TTL | Freshness | Verification Result |
|-------------------|-----------|-------------------|
| < 50% of TTL | **Fresh** (no badge) | Green — fully confident |
| 50% - 100% of TTL | **Aging** (yellow badge) | Green — still within TTL |
| > 100% of TTL | **Expired** (red badge) | Yellow — degraded confidence |

---

## 9. Freshness Indicators

Freshness badges appear on your **credential cards** to give you an at-a-glance sense of how current your offline verification data is.

### Badge States

| Badge | Meaning | Action Needed |
|-------|---------|---------------|
| *(no badge)* | Bundle is fresh (< 50% of TTL) | None |
| **Bundle aging** (yellow) | Bundle is aging (50-100% of TTL) | Consider refreshing when online |
| **Bundle expired** (red) | Bundle has exceeded TTL | Refresh required for confident offline verification |

> [Screenshot: Credential list showing cards with no badge, "Bundle aging" badge, and "Bundle expired" badge]

### Where Badges Appear

- **Credential list** — next to each credential card
- **Credential detail** — near the credential header
- **Bundle management** — in the "Prepare for Offline" screen

---

## 10. Offline Verification

When your device has no internet connection, the wallet automatically falls back to offline verification using cached bundles.

### How It Works

```
You tap "Verify" on a credential
        |
        v
Wallet tries online verification (registry)
        |
        | Network error or timeout
        v
Wallet falls back to cached bundle
        |
        v
Checks signature against cached DID Document
Checks expiry against credential dates
Checks revocation against cached status list
        |
        v
Returns result with "Offline" badge
```

### What You'll See

**If a fresh bundle is cached:**

> [Screenshot: Green result with "Credential verified offline" and "Offline" badge]

The result is green with an "Offline" badge. All checks passed using the cached data.

**If the bundle is stale:**

> [Screenshot: Yellow result with "Verified with limitations" and detail showing "Bundle is stale"]

The result is yellow. The signature is valid, but the bundle has exceeded its TTL, so there's reduced confidence that the credential hasn't been revoked since the last sync.

**If no bundle is cached:**

> [Screenshot: Red result with "Verification failed" and detail showing "No cached data for this issuer"]

The result is red. Without a cached bundle, offline verification cannot proceed. The message suggests connecting to the internet or adding the issuer's bundle while online.

### Best Practices for Offline Use

1. **Pre-cache before going offline** — Visit Settings > Prepare for Offline and add issuers you expect to need
2. **Set appropriate TTL** — Shorter for high-risk credentials, longer for low-risk
3. **Check freshness badges** — Before going offline, ensure your bundles are not already expired
4. **Refresh when you reconnect** — The wallet auto-refreshes stale bundles when it detects connectivity, but you can also manually refresh from the bundle management screen

---

## 11. Background Sync

The wallet automatically keeps your verification bundles up-to-date through background sync. You don't need to take any action — this happens silently.

### When Sync Occurs

| Trigger | Description |
|---------|-------------|
| **Periodic** | Every 12 hours, the wallet refreshes stale bundles in the background |
| **Connectivity restore** | When your device reconnects to the internet after being offline, a sync is triggered |
| **App foreground** | When you open the app, if any bundle has consumed more than 80% of its TTL, a sync runs |

### What Gets Synced

The wallet identifies all unique issuers from your held credentials and refreshes their bundles if they're stale. This includes:
- Updating the issuer's DID Document (in case of key rotation)
- Fetching the latest revocation status list

### Sync Failures

Background sync is **best-effort**. If a network error occurs during sync:
- Existing stale bundles are preserved (stale data is better than no data)
- The wallet retries on the next trigger
- No notification is shown to the user

---

## 12. Troubleshooting

### "Verification failed" for a credential I know is valid

**Possible causes:**
- **Expired credential:** Check the expiry date in the credential detail. Tap "Verify" and expand details to confirm.
- **Issuer key rotation:** The issuer may have rotated their signing key after the credential was issued. Try refreshing bundles (Settings > Prepare for Offline > Refresh) and verify again.
- **No cached bundle while offline:** If you're offline and haven't cached the issuer's bundle, verification will fail. Connect to the internet and try again.

### "Verified with limitations" when I'm online

This shouldn't happen when online — the wallet should use the registry directly. Possible causes:
- **Registry is temporarily down (HTTP 5xx):** The wallet fell back to offline verification because the registry returned a server error. Try again later.
- **Intermittent connectivity:** Your connection may have dropped during verification. Check your network and retry.

### Freshness badge shows "Bundle expired" but I'm online

The badge reflects the cached bundle's age, not your connectivity status. The bundle was last fetched more than your configured TTL ago. To fix:
1. Go to Settings > Prepare for Offline
2. Tap Refresh to update all bundles
3. The badge will disappear once the bundle is refreshed

### Background sync doesn't seem to work

**Android:** Background sync uses WorkManager, which respects battery optimization settings. Ensure the SSDID Wallet is not restricted in your device's battery settings.

**iOS:** Background sync uses BGAppRefreshTask, which iOS schedules at its discretion based on usage patterns. If the app is not used frequently, iOS may delay background tasks. Open the app periodically to trigger foreground sync.

### How do I know if verification used online or offline?

The verification result always shows the **source**:
- Expand the detail breakdown by tapping the traffic light card
- The last row shows "Source: Online" or "Source: Offline"
- If offline, an "Offline" chip badge also appears on the result card

---

## 13. Security Considerations

### Trust Model

Offline verification trusts the **cached bundle** as a snapshot of the issuer's state at the time it was fetched. Key security properties:

| Property | Online | Offline |
|----------|--------|---------|
| Signature verification | Real-time against registry | Against cached DID Document |
| Expiry check | Real-time | Real-time (uses credential dates, not network) |
| Revocation check | Real-time against status list | Against cached status list (may be stale) |
| Key rotation detection | Immediate | Delayed until next bundle refresh |

### Maximum Offline Trust Window

A revoked credential could appear valid offline for up to **your configured TTL** (default: 7 days). This is the fundamental trade-off of offline verification — convenience vs. freshness.

**For high-security scenarios:**
- Set TTL to 1 day
- Refresh bundles immediately before going offline
- Use online verification whenever possible

### Data Protection

| Platform | Bundle Storage | Credential Storage |
|----------|---------------|-------------------|
| Android | AES-256-GCM encrypted + HMAC integrity (Android Keystore) | AES-256-GCM encrypted + HMAC integrity (Android Keystore) |
| iOS | HMAC-SHA256 integrity (Keychain-backed key) + `.completeFileProtection` | `.completeFileProtection` (encrypted when device locked) |

Bundles are verified for integrity before use — any tampering is detected and the bundle is rejected.

### Certificate Pinning

All network requests to the SSDID registry (`registry.ssdid.my`) use certificate pinning. This prevents man-in-the-middle attacks when fetching DID Documents and status lists, even on untrusted networks.

### Status List Verification

Cached status lists are only trusted if they include a valid cryptographic proof from the issuer. If the proof is missing or invalid, the revocation status is reported as "Unknown" (producing a yellow/degraded result).

---

## Appendix: Quick Reference

### Verification Status Summary

| Status | Color | When | User Action |
|--------|-------|------|-------------|
| Verified | Green | Online check passed | None needed |
| Verified Offline | Green + "Offline" badge | Offline check with fresh bundle passed | None needed |
| Degraded | Yellow | Offline check passed but bundle stale or revocation unknown | Refresh bundle when online |
| Failed | Red | Signature invalid, credential expired, or revoked | Contact issuer or discard credential |

### Settings at a Glance

| Setting | Location | Default | Options |
|---------|----------|---------|---------|
| Bundle TTL | Settings > Offline Verification > Bundle TTL | 7 days | 1, 7, 14, 30 days |
| Prepare for Offline | Settings > Offline Verification > Prepare for Offline | — | Add/refresh/delete bundles |

### Navigation Paths

| Destination | Path |
|-------------|------|
| Verify a credential | Wallet Home > Credentials > [credential] > Verify Credential |
| View verification details | [verification result] > tap traffic light card |
| Manage offline bundles | Wallet Home > Settings > Prepare for Offline |
| Change TTL | Wallet Home > Settings > Bundle TTL |
