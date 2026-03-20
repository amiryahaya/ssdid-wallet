# SSDID Wallet — Use Cases & E2E Test Cases

**Date:** 2026-03-20
**Platforms:** Android (Kotlin/Compose), iOS (Swift/SwiftUI)
**Status Legend:** ⬜ Not tested | 🟡 Partial | ✅ Passed | ❌ Failed

---

## UC-01: First-Time Onboarding

**Actor:** New user installing the app for the first time
**Precondition:** App freshly installed, no identities exist

| # | Test Case | Steps | Expected Result | Android | iOS |
|---|-----------|-------|-----------------|:---:|:---:|
| 1.1 | Complete onboarding carousel | Open app → swipe through 3 pages → tap "Get Started" | Navigate to Create Identity wizard | ⬜ | ⬜ |
| 1.2 | Skip to create identity | Open app → tap "Skip" on any carousel page | Navigate to Create Identity wizard | ⬜ | ⬜ |
| 1.3 | Restore from backup on first launch | Onboarding → tap "Restore" → select backup file → enter passphrase | Identity restored, navigate to Home | ⬜ | ⬜ |

---

## UC-02: Create Identity (3-Step Wizard)

**Actor:** User creating a new identity
**Precondition:** User is on the Create Identity screen

| # | Test Case | Steps | Expected Result | Android | iOS |
|---|-----------|-------|-----------------|:---:|:---:|
| 2.1 | Create identity with Ed25519 | Step 1: enter name + email → "Verify Email" → Step 2: enter OTP → "Verify" → Step 3: enter identity name, select Ed25519 → "Create" | Identity created, DID registered on registry, navigate to Biometric Setup | ⬜ | ⬜ |
| 2.2 | Create identity with KAZ-Sign-192 (PQC) | Same as 2.1 but select KAZ-Sign-192 in step 3 | Identity created with PQC algorithm | ⬜ | ⬜ |
| 2.3 | Email verification with wrong code | Step 2: enter wrong 6-digit code → "Verify" | Error: "Invalid verification code" | ⬜ | ⬜ |
| 2.4 | Email resend with progressive cooldown | Step 2: tap "Resend" 3 times | Cooldown: 60s → 120s → 300s | ⬜ | ⬜ |
| 2.5 | Back navigation between wizard steps | Step 2: tap back → Step 1 fields preserved | Email and name fields retain values | ⬜ | ⬜ |
| 2.6 | Email change resets verification | Step 2: go back → change email → Step 2 again | Must re-verify with new code | ⬜ | ⬜ |
| 2.7 | Invalid email format rejected | Step 1: enter "notanemail" → "Verify Email" | Button disabled or validation error | ⬜ | ⬜ |
| 2.8 | Empty display name rejected | Step 1: leave name empty → "Verify Email" | Button disabled | ⬜ | ⬜ |

---

## UC-03: Wallet Home

**Actor:** User with at least one identity
**Precondition:** Identity exists, user is on Home screen

| # | Test Case | Steps | Expected Result | Android | iOS |
|---|-----------|-------|-----------------|:---:|:---:|
| 3.1 | View identity card | Navigate to Home | Identity card shows: name, DID (truncated), email, algorithm badge, "Active" status | ⬜ | ⬜ |
| 3.2 | Connected services count | Identity has 1+ credentials | Card shows "N services connected" | ⬜ | ⬜ |
| 3.3 | Create additional identity | Tap "+ New" | Navigate to Create Identity wizard | ⬜ | ⬜ |
| 3.4 | Navigate to identity detail | Tap identity card | Navigate to Identity Detail screen | ⬜ | ⬜ |
| 3.5 | Pull to refresh | Pull down on identity list | Identities and credential counts reload | ⬜ | ⬜ |
| 3.6 | Empty state (no identities) | Delete all identities | Shows "Create your first identity" card | ⬜ | ⬜ |

---

## UC-04: Identity Detail & Connected Services

**Actor:** User viewing identity details
**Precondition:** Identity exists with at least one connected service

| # | Test Case | Steps | Expected Result | Android | iOS |
|---|-----------|-------|-----------------|:---:|:---:|
| 4.1 | View identity details | Open Identity Detail | Shows DID, Key ID, Algorithm, W3C Type, Created date, Key Storage, Public Key | ⬜ | ⬜ |
| 4.2 | Copy DID to clipboard | Tap "Copy" next to DID | DID copied, "Copied" label shown for 2s | ⬜ | ⬜ |
| 4.3 | View profile section | Identity has profileName and email set | Shows Name and Email in PROFILE section | ⬜ | ⬜ |
| 4.4 | Connected service shows service name | Credential from ssdid-drive with `serviceName` field | Shows human-readable name (e.g., "SSDID Drive") | ⬜ | ⬜ |
| 4.5 | Connected service shows status dot | Credential with expiration date | Green dot (active), yellow (expiring <30 days), red (expired) | ⬜ | ⬜ |
| 4.6 | Connected service revocation status | Revoked credential | Red dot with "Revoked" label | ⬜ | ⬜ |
| 4.7 | Empty connected services | No credentials for identity | Shows "No services connected" empty state | ⬜ | ⬜ |
| 4.8 | Navigate to Recovery Setup | Tap "Recovery" action | Navigate to Recovery Setup screen | ⬜ | ⬜ |
| 4.9 | Navigate to Key Rotation | Tap "Rotate Key" action | Navigate to Key Rotation screen | ⬜ | ⬜ |
| 4.10 | Navigate to Device Management | Tap "Devices" action | Navigate to Device Management screen | ⬜ | ⬜ |

---

## UC-05: Deactivate Identity

**Actor:** User permanently deactivating an identity
**Precondition:** Identity exists

| # | Test Case | Steps | Expected Result | Android | iOS |
|---|-----------|-------|-----------------|:---:|:---:|
| 5.1 | Deactivate with no connected services | Identity Detail → "Deactivate" → confirm | Generic warning shown, DID deactivated on registry, navigate to Home | ⬜ | ⬜ |
| 5.2 | Deactivate with connected services | Identity has 2 credentials → "Deactivate" → confirm | Warning lists service names: "Connected to 2 services (SSDID Drive, ...)" | ⬜ | ⬜ |
| 5.3 | Cancel deactivation | "Deactivate" → "Cancel" in dialog | No action, stays on Identity Detail | ⬜ | ⬜ |
| 5.4 | Verify DID is gone from registry | After deactivation → resolve DID | Registry returns 404/410 | ⬜ | ⬜ |

---

## UC-06: QR Code Scanning & Authentication

**Actor:** User scanning a QR code from a service
**Precondition:** At least one identity exists

| # | Test Case | Steps | Expected Result | Android | iOS |
|---|-----------|-------|-----------------|:---:|:---:|
| 6.1 | Scan registration QR | Scan QR with `ssdid://register?...` | Navigate to Registration flow | ⬜ | ⬜ |
| 6.2 | Scan authentication QR | Scan QR with `ssdid://authenticate?...` | Navigate to Consent/Auth flow | ⬜ | ⬜ |
| 6.3 | Scan login QR (Drive) | Scan QR with `ssdid://login?...` | Navigate to DriveLogin screen with service info | ⬜ | ⬜ |
| 6.4 | Scan transaction QR | Scan QR with `ssdid://sign?...` | Navigate to TxSigning screen | ⬜ | ⬜ |
| 6.5 | Scan credential offer QR | Scan QR with `openid-credential-offer://...` | Navigate to Credential Offer screen | ⬜ | ⬜ |
| 6.6 | Scan VP request QR | Scan QR with `openid4vp://...` | Navigate to Presentation Request screen | ⬜ | ⬜ |
| 6.7 | Invalid QR code | Scan non-SSDID QR | Error toast or ignored | ⬜ | ⬜ |

---

## UC-07: Service Authentication (Drive Login / Consent)

**Actor:** User authenticating with a third-party service
**Precondition:** Identity exists, deep link or QR scan initiated

| # | Test Case | Steps | Expected Result | Android | iOS |
|---|-----------|-------|-----------------|:---:|:---:|
| 7.1 | First-time registration + authentication | Scan login QR → select identity → approve | Register DID with service → receive VC → authenticate → success | ⬜ | ⬜ |
| 7.2 | Returning authentication (VC exists) | Scan login QR with existing credential | Skip registration → authenticate with stored VC → success | ⬜ | ⬜ |
| 7.3 | Select different identity | Multiple identities → select non-default | Selected identity's claims shared, correct DID used | ⬜ | ⬜ |
| 7.4 | Toggle optional claims | Requested claims: name (required), email (optional) → uncheck email | Only name shared with service | ⬜ | ⬜ |
| 7.5 | Decline authentication | Tap "Decline" | Callback URL receives `error=user_declined`, navigate back | ⬜ | ⬜ |
| 7.6 | Callback includes state parameter | Service sends `state=abc123` in deep link | Callback URL includes `state=abc123` (CSRF protection) | ⬜ | ⬜ |
| 7.7 | Expired credential → re-register | Credential expired (401 from service) | Auto-delete expired VC, show "Credential expired. Please try again." | ⬜ | ⬜ |
| 7.8 | Invalid server URL rejected | Deep link with `server_url=javascript:alert(1)` | Rejected by URL validation | ⬜ | ⬜ |

---

## UC-08: Invite Acceptance

**Actor:** User accepting an organization invitation
**Precondition:** Identity exists with email configured

| # | Test Case | Steps | Expected Result | Android | iOS |
|---|-----------|-------|-----------------|:---:|:---:|
| 8.1 | Accept invitation (email matches) | Open invite deep link → email matches | Green checkmark, "Accept" enabled | ⬜ | ⬜ |
| 8.2 | Reject invitation (email mismatch) | Open invite → identity email ≠ invitation email | Error: "Email mismatch" (no server email exposed) | ⬜ | ⬜ |
| 8.3 | Decline invitation | Tap "Decline" | Callback with `status=cancelled` | ⬜ | ⬜ |

---

## UC-09: Transaction Signing

**Actor:** User signing a transaction
**Precondition:** Active session with a service

| # | Test Case | Steps | Expected Result | Android | iOS |
|---|-----------|-------|-----------------|:---:|:---:|
| 9.1 | Sign transaction successfully | Review details → biometric → "Sign" | Transaction submitted, success confirmation | ⬜ | ⬜ |
| 9.2 | Transaction countdown expires | Wait 120 seconds without signing | "Challenge expired. Please scan QR again." | ⬜ | ⬜ |
| 9.3 | Cancel biometric | Biometric prompt → cancel | Stays on signing screen, can retry | ⬜ | ⬜ |

---

## UC-10: Credentials Management

**Actor:** User managing verifiable credentials
**Precondition:** At least one credential stored

| # | Test Case | Steps | Expected Result | Android | iOS |
|---|-----------|-------|-----------------|:---:|:---:|
| 10.1 | View credentials list | Navigate to Credentials | List of all credentials with issuer, type, expiration status | ⬜ | ⬜ |
| 10.2 | View credential details | Tap credential | Full details: subject, claims, proof, expiration, revocation status | ⬜ | ⬜ |
| 10.3 | Delete credential | Credential Detail → Delete → confirm | Credential removed from vault | ⬜ | ⬜ |
| 10.4 | Accept credential offer (OID4VCI) | Receive credential offer → review → accept | Credential stored in vault | ⬜ | ⬜ |
| 10.5 | Present credential (OID4VP) | Receive presentation request → select credential → submit | VP token sent to verifier | ⬜ | ⬜ |

---

## UC-11: Key Recovery

**Actor:** User setting up or using identity recovery
**Precondition:** Identity exists

| # | Test Case | Steps | Expected Result | Android | iOS |
|---|-----------|-------|-----------------|:---:|:---:|
| 11.1 | Generate offline recovery key (Tier 1) | Recovery Setup → "Generate Recovery Key" | Base64 key displayed, copy button works | ⬜ | ⬜ |
| 11.2 | Restore with recovery key | Recovery Restore → enter DID, key, name, algorithm → "Restore" | New identity created with same DID, registered on registry | ⬜ | ⬜ |
| 11.3 | Restore with wrong recovery key | Enter incorrect key → "Restore" | Error: verification failed | ⬜ | ⬜ |
| 11.4 | Setup social recovery (Tier 2) | Recovery Setup → Social → add 3 guardians, threshold 2 → "Create Shares" | 3 shares generated, one per guardian | ⬜ | ⬜ |
| 11.5 | Restore via social recovery | Social Restore → enter 2 shares → "Recover" | Identity restored via Shamir reconstruction | ⬜ | ⬜ |
| 11.6 | Social recovery with insufficient shares | Enter only 1 share (threshold is 2) → "Recover" | Error: "Need at least 2 shares" | ⬜ | ⬜ |
| 11.7 | Enroll institutional recovery (Tier 3) | Recovery Setup → Institutional → enter org DID, name, encrypted key → "Enroll" | Organization enrolled, configuration saved | ⬜ | ⬜ |

---

## UC-12: Key Rotation

**Actor:** User rotating their signing key
**Precondition:** Identity exists and registered on registry

| # | Test Case | Steps | Expected Result | Android | iOS |
|---|-----------|-------|-----------------|:---:|:---:|
| 12.1 | Prepare pre-commitment | Key Rotation → "Prepare Pre-Commitment" | nextKeyHash published to registry, pre-rotation status shows ✓ | ⬜ | ⬜ |
| 12.2 | Execute rotation | After prepare → "Rotate Now" | New key promoted, old key in grace period, registry updated | ⬜ | ⬜ |
| 12.3 | Verify old key still works (grace period) | Immediately after rotation → authenticate with service | Authentication succeeds (old key in grace period) | ⬜ | ⬜ |
| 12.4 | Verify new key works | After rotation → authenticate | Authentication succeeds with new key | ⬜ | ⬜ |
| 12.5 | Rotation without pre-commitment | Attempt "Rotate Now" without prepare | Error: "No pre-committed key" | ⬜ | ⬜ |

---

## UC-13: Multi-Device Enrollment

**Actor:** User adding a second device to their identity
**Precondition:** Identity exists on primary device

| # | Test Case | Steps | Expected Result | Android | iOS |
|---|-----------|-------|-----------------|:---:|:---:|
| 13.1 | Initiate pairing (primary) | Device Management → "Enroll Device" → "Start Pairing" | Pairing ID + challenge displayed | ⬜ | ⬜ |
| 13.2 | Join pairing (secondary) | Enter pairing ID + challenge on secondary device → "Join" | Status: "Waiting for approval" | ⬜ | ⬜ |
| 13.3 | Approve pairing (primary) | Primary sees join request → "Approve" | DID document updated with new device key | ⬜ | ⬜ |
| 13.4 | Revoke secondary device | Device Management → secondary device → "Revoke" | Device key removed from DID document | ⬜ | ⬜ |
| 13.5 | Cannot revoke primary device | Attempt to revoke primary device | Error: "Cannot revoke primary device key" | ⬜ | ⬜ |

---

## UC-14: Backup & Restore

**Actor:** User backing up and restoring wallet data
**Precondition:** At least one identity exists

| # | Test Case | Steps | Expected Result | Android | iOS |
|---|-----------|-------|-----------------|:---:|:---:|
| 14.1 | Export encrypted backup | Backup → enter passphrase → "Export" | `.enc` file saved with all identities | ⬜ | ⬜ |
| 14.2 | Import backup on new device | Import → select file → enter passphrase | All identities restored with new device-local wrapping keys | ⬜ | ⬜ |
| 14.3 | Wrong passphrase rejected | Import → enter wrong passphrase | Error: "Invalid passphrase" or HMAC verification failed | ⬜ | ⬜ |
| 14.4 | Tampered backup rejected | Modify backup file bytes → import | Error: HMAC verification failed | ⬜ | ⬜ |
| 14.5 | Backup strength indicator | Enter short passphrase (<8 chars) | Shows "Weak" indicator | ⬜ | ⬜ |

---

## UC-15: Auto-Lock & Biometric

**Actor:** User with biometric enabled
**Precondition:** Biometric enabled in Settings, auto-lock = 5 minutes

| # | Test Case | Steps | Expected Result | Android | iOS |
|---|-----------|-------|-----------------|:---:|:---:|
| 15.1 | Auto-lock after timeout | Background app for >5 minutes → resume | Lock overlay shown, biometric prompt | ⬜ | ⬜ |
| 15.2 | No lock within timeout | Background for <5 minutes → resume | App resumes normally, no lock | ⬜ | ⬜ |
| 15.3 | Unlock with biometric | Lock screen → Face ID / fingerprint | App unlocked | ⬜ | ⬜ |
| 15.4 | Biometric failed → retry | Lock screen → cancel biometric → tap "Unlock" | Re-prompts biometric | ⬜ | ⬜ |
| 15.5 | Disable biometric in settings | Settings → toggle off biometric | No auto-lock on next resume | ⬜ | ⬜ |
| 15.6 | Change auto-lock duration | Settings → change to 10 minutes | Lock only after 10 minutes in background | ⬜ | ⬜ |

---

## UC-16: Settings

**Actor:** User configuring app preferences
**Precondition:** App initialized

| # | Test Case | Steps | Expected Result | Android | iOS |
|---|-----------|-------|-----------------|:---:|:---:|
| 16.1 | Change language | Settings → Language → "Bahasa Melayu" | UI language changes to Malay | ⬜ | ⬜ |
| 16.2 | View registry URL | Settings → Network section | Shows "registry.ssdid.my" (read-only) | ⬜ | ⬜ |
| 16.3 | Navigate to backup | Settings → "Backup & Export" | Navigate to Backup screen | ⬜ | ⬜ |

---

## UC-17: Deep Link Handling

**Actor:** External app sending deep link to wallet
**Precondition:** App installed

| # | Test Case | Steps | Expected Result | Android | iOS |
|---|-----------|-------|-----------------|:---:|:---:|
| 17.1 | Registration deep link | Open `ssdid://register?server_url=...&server_did=...` | Navigate to Registration screen | ⬜ | ⬜ |
| 17.2 | Login deep link | Open `ssdid://login?service_url=...&callback_url=...` | Navigate to Drive Login screen | ⬜ | ⬜ |
| 17.3 | Invite deep link | Open `ssdid://invite?server_url=...&token=...` | Navigate to Invite Accept screen | ⬜ | ⬜ |
| 17.4 | Invalid scheme rejected | Open `javascript://...` or `file://...` | Rejected, no navigation | ⬜ | ⬜ |
| 17.5 | DID validation on deep link | Deep link with malformed `server_did` | Sanitized or rejected | ⬜ | ⬜ |
| 17.6 | State parameter echoed | Deep link includes `state=xyz` | Callback URL includes `state=xyz` | ⬜ | ⬜ |

---

## UC-18: Notifications

**Actor:** User viewing wallet notifications
**Precondition:** Notifications exist (identity created, rotation completed, etc.)

| # | Test Case | Steps | Expected Result | Android | iOS |
|---|-----------|-------|-----------------|:---:|:---:|
| 18.1 | View notification list | Home → tap bell icon | List of notifications with timestamps | ⬜ | ⬜ |
| 18.2 | Unread badge | New notification arrives | Bell icon shows count badge | ⬜ | ⬜ |
| 18.3 | Mark as read | View notification | Unread count decrements | ⬜ | ⬜ |

---

## UC-19: Error Handling

**Actor:** User encountering errors
**Precondition:** Various

| # | Test Case | Steps | Expected Result | Android | iOS |
|---|-----------|-------|-----------------|:---:|:---:|
| 19.1 | Network error during registration | Airplane mode → create identity | Error: "Network error" or "Connection timed out" | ⬜ | ⬜ |
| 19.2 | Registry unavailable | Registry down → attempt any registry operation | Graceful error, no crash | ⬜ | ⬜ |
| 19.3 | Rate limited by registry | Rapid operations → 429 response | User-friendly message: "Too many requests. Try again in X seconds." | ⬜ | ⬜ |
| 19.4 | Invalid DID from server | Server returns malformed DID | Rejected by Did.validate(), error shown | ⬜ | ⬜ |
| 19.5 | Biometric hardware unavailable | Device without biometric → attempt biometric operation | Graceful fallback or skip | ⬜ | ⬜ |

---

## Summary

| Use Case | Test Cases | Priority |
|----------|:---:|----------|
| UC-01: Onboarding | 3 | High |
| UC-02: Create Identity | 8 | Critical |
| UC-03: Wallet Home | 6 | High |
| UC-04: Identity Detail | 10 | High |
| UC-05: Deactivate Identity | 4 | Critical |
| UC-06: QR Scanning | 7 | High |
| UC-07: Service Authentication | 8 | Critical |
| UC-08: Invite Acceptance | 3 | High |
| UC-09: Transaction Signing | 3 | High |
| UC-10: Credentials | 5 | High |
| UC-11: Key Recovery | 7 | Critical |
| UC-12: Key Rotation | 5 | Critical |
| UC-13: Multi-Device | 5 | High |
| UC-14: Backup & Restore | 5 | Critical |
| UC-15: Auto-Lock & Biometric | 6 | High |
| UC-16: Settings | 3 | Medium |
| UC-17: Deep Link Handling | 6 | High |
| UC-18: Notifications | 3 | Medium |
| UC-19: Error Handling | 5 | High |
| **Total** | **107** | |
