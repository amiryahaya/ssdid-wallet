# Sign In with SSDID — Design Document

**Date:** 2026-03-10
**Status:** Approved

## Goal

Enable users to sign in to third-party native apps and websites using their SSDID wallet, with selective profile claim sharing, MFA proof, and mutual authentication.

## Architecture

Extends the existing `authenticate` deep link/QR action with claim request parameters, a consent screen, and algorithm filtering. Two flows: native apps use deep link callbacks, web apps use QR + server-side polling. No new infrastructure — builds on existing `ServerApi`, `Vault`, and deep link handling.

## Authentication Request Format

Services initiate auth via deep link or QR code:

```json
{
  "action": "authenticate",
  "server_url": "https://app.example.com",
  "callback_url": "myapp://auth-callback",
  "session_id": "abc-123",
  "requested_claims": [
    { "key": "name", "required": true },
    { "key": "email", "required": true },
    { "key": "phone", "required": false }
  ],
  "accepted_algorithms": ["ED25519", "KAZ_SIGN_192"]
}
```

| Field | Required | Description |
|-------|----------|-------------|
| `action` | Yes | Must be `"authenticate"` |
| `server_url` | Yes | Service backend URL (HTTPS) |
| `callback_url` | No | Native app return URL. Omit for web flow. |
| `session_id` | No | Web flow only — ties QR to browser session |
| `requested_claims` | No | Claims to share. Omit for identity-only auth. |
| `accepted_algorithms` | No | Algorithm filter. Omit to accept all. |

## Well-Known Claims

| Key | Validation | Required by default |
|-----|-----------|---------------------|
| `name` | Non-empty, max 100 chars | Yes |
| `email` | RFC 5322 email format | Yes |
| `phone` | E.164 format (`+60123456789`) | No |

Photo claim reserved for future implementation (requires hosting strategy).

## Protocol — Native App Flow

```
Third-party app                    SSDID Wallet                    Service Backend
     |                                  |                               |
     |-- 1. Deep link ----------------->|                               |
     |   ssdid://authenticate?          |                               |
     |   server_url=...&                |                               |
     |   callback_url=myapp://cb&       |                               |
     |   requested_claims=[...]         |                               |
     |                                  |                               |
     |                                  |-- 2. GET /api/auth/challenge ->|
     |                                  |<-- { challenge, server_name } -|
     |                                  |                               |
     |                                  |-- 3. Show consent screen      |
     |                                  |   "AppName wants to sign in"  |
     |                                  |   [x] Name (required)         |
     |                                  |   [x] Email (required)        |
     |                                  |   [ ] Phone (optional)        |
     |                                  |   [Approve] [Decline]         |
     |                                  |                               |
     |                                  |-- 4. Biometric gate           |
     |                                  |                               |
     |                                  |-- 5. Sign challenge           |
     |                                  |   payload = challenge bytes   |
     |                                  |   signature = vault.sign()    |
     |                                  |                               |
     |                                  |-- 6. POST /api/auth/verify -->|
     |                                  |   { did, key_id,              |
     |                                  |     signed_challenge,         |
     |                                  |     shared_claims,            |
     |                                  |     amr }                     |
     |                                  |                               |
     |                                  |<-- { session_token,      ----|
     |                                  |      server_signature }       |
     |                                  |                               |
     |                                  |-- 7. Verify server sig        |
     |                                  |                               |
     |<-- 8. callback_url?session_token=|                               |
     |       &did=...                   |                               |
```

1. Third-party app opens SSDID wallet via deep link with auth parameters
2. Wallet fetches fresh challenge + service display name from backend
3. Consent screen shows service name, requested claims with values, required/optional flags
4. Biometric authentication (fingerprint/face)
5. Wallet signs challenge with selected identity's private key
6. Wallet POSTs signed challenge, selected claims, and AMR to service
7. Wallet verifies server's response signature (mutual auth)
8. Wallet redirects to callback URL with session_token and DID

## Protocol — Web App Flow

```
Browser                    Service Backend              SSDID Wallet
  |                              |                           |
  |-- 1. Click "Sign in" ------>|                           |
  |                              |                           |
  |<-- 2. QR code with ---------|                           |
  |   { action: "authenticate", |                           |
  |     server_url, session_id, |                           |
  |     requested_claims }      |                           |
  |                              |                           |
  |-- 3. Poll GET ------------->|                           |
  |   /api/auth/status/{id}     |                           |
  |<-- { status: "pending" } ---|                           |
  |                              |                           |
  |                              |<-- 4. Wallet scans QR ---|
  |                              |                           |
  |                              |<-- 5. GET /api/auth/ ----|
  |                              |       challenge           |
  |                              |--- { challenge,      --->|
  |                              |      server_name }        |
  |                              |                           |
  |                              |   6. Consent + biometric  |
  |                              |                           |
  |                              |<-- 7. POST /api/auth/ ---|
  |                              |       verify              |
  |                              |   { did, key_id,          |
  |                              |     signed_challenge,     |
  |                              |     shared_claims,        |
  |                              |     amr, session_id }     |
  |                              |                           |
  |                              |--- { server_signature }-->|
  |                              |                           |
  |                              |   8. Wallet verifies,     |
  |                              |      shows success        |
  |                              |                           |
  |-- 9. Poll GET ------------->|                           |
  |   /api/auth/status/{id}     |                           |
  |<-- { status: "completed",---|                           |
  |      session_token, did }   |                           |
  |                              |                           |
  |-- 10. Browser logged in     |                           |
```

Key differences from native flow:
- No callback_url — browser polls instead
- session_id links QR to browser session
- Wallet includes session_id in verify request so backend updates polling status
- Browser polls every 2-3 seconds, times out after 2 minutes

## API Contracts

### GET /api/auth/challenge

```json
{
  "challenge": "random-base64url-string",
  "server_name": "MyApp",
  "server_did": "did:ssdid:server123",
  "server_key_id": "did:ssdid:server123#key-1"
}
```

### POST /api/auth/verify

Request:
```json
{
  "did": "did:ssdid:user123",
  "key_id": "did:ssdid:user123#key-1",
  "signed_challenge": "uBase64Signature",
  "shared_claims": {
    "name": "Amir Rudin",
    "email": "amir@example.com"
  },
  "amr": ["hwk", "bio"],
  "session_id": "abc-123"
}
```

Response:
```json
{
  "session_token": "jwt-or-opaque-token",
  "server_did": "did:ssdid:server123",
  "server_key_id": "did:ssdid:server123#key-1",
  "server_signature": "uServerSig"
}
```

### GET /api/auth/status/{session_id}

```json
{ "status": "pending" }
{ "status": "completed", "session_token": "...", "did": "did:ssdid:user123" }
{ "status": "expired" }
```

## Consent Screen

```
┌─────────────────────────────────┐
│  ← Sign In Request             │
│                                 │
│  ┌─────────────────────────────┐│
│  │  MyApp                      ││
│  │  https://app.example.com    ││
│  └─────────────────────────────┘│
│                                 │
│  wants to verify your identity  │
│  and access the following:      │
│                                 │
│  IDENTITY                       │
│  ┌─────────────────────────────┐│
│  │  Personal                   ││
│  │  did:ssdid:abc...xyz   ▼   ││
│  └─────────────────────────────┘│
│                                 │
│  REQUESTED INFORMATION          │
│  ┌─────────────────────────────┐│
│  │  ☑ Name        Required    ││
│  │    Amir Rudin              ││
│  ├─────────────────────────────┤│
│  │  ☑ Email       Required    ││
│  │    amir@example.com        ││
│  ├─────────────────────────────┤│
│  │  ☐ Phone       Optional    ││
│  │    +60123456789            ││
│  └─────────────────────────────┘│
│                                 │
│  AUTHENTICATION                 │
│  Biometric + Hardware Key       │
│                                 │
│  ┌─────────────────────────────┐│
│  │       Approve               ││
│  └─────────────────────────────┘│
│  ┌─────────────────────────────┐│
│  │       Decline               ││
│  └─────────────────────────────┘│
└─────────────────────────────────┘
```

Identity selector behavior:
- Dropdown if multiple identities exist
- Filtered by `accepted_algorithms` if specified
- If no matching identity: show message with "Create New Identity" button
- "Create New" link available even when matches exist

Claim display behavior:
- Required claims: checkbox checked and disabled, labeled "Required"
- Optional claims: checkbox checked by default, user can uncheck, labeled "Optional"
- Claim values shown below each claim
- Missing required claims: disable Approve, show warning

## Algorithm Filtering

Services specify `accepted_algorithms` in the auth request.

| Scenario | Wallet Behavior |
|----------|----------------|
| User has matching identities | Show only those identities in selector |
| User has no matching identities | Show message + "Create New Identity" button (algorithm pre-filtered) |
| User wants fresh identity | "Create New" link below selector |
| `accepted_algorithms` omitted | Show all identities |

## MFA / AMR

SSDID provides two authentication factors by default:
- **`hwk`** — hardware-backed private key (always present)
- **`bio`** — biometric authentication (when biometric gate triggered)

The `amr` field is included in the signed `AuthVerifyRequest` so services can trust it. Services can enforce MFA policy by checking `amr` contents.

## Error Handling

| Scenario | Wallet Behavior |
|----------|----------------|
| Invalid/malformed QR or deep link | "Invalid authentication request" |
| `server_url` fails validation | "Untrusted service URL" |
| Challenge fetch fails (network) | Error with retry button |
| Challenge fetch fails (server error) | "Service unavailable" |
| User declines consent | Native: callback with `?error=user_declined`. Web: close screen. |
| Biometric fails | Allow retry, max 3 attempts, then cancel |
| Server signature verification fails | "Service authentication failed. Do not proceed." No retry. |
| Required claim missing from identity | Disable Approve: "Missing required: [claim]" |
| Polling expires (web, 2 min) | Browser shows: "Sign-in expired. Please try again." |
| Callback URL invalid scheme | "Cannot return to app — invalid callback" |

## Security

**Callback URL validation:**
- Custom schemes allowed (e.g., `myapp://`, `ssdiddrive://`)
- HTTPS URLs allowed
- HTTP rejected (except localhost for dev)
- No `javascript:`, `data:`, `file:` schemes
- Callback URL displayed on consent screen

**Challenge replay protection:**
- Single-use, fetched fresh per auth attempt
- Server-side TTL (2 minutes recommended)
- Wallet does not cache or reuse challenges

**Claim data protection:**
- Claims sent over HTTPS only via POST body
- Callback URL only carries session_token and DID, not claim data
- Service receives claims server-side via `/api/auth/verify`

**Mutual authentication:**
- Wallet verifies server signature before returning to caller
- Prevents phishing — fake service can't produce valid server_signature

**Web flow session binding:**
- session_id is opaque, generated server-side
- Polling returns minimal data until auth completes

**AMR integrity:**
- amr is part of the signed request — service can trust it
- Service can reject auth if amr doesn't include required factors

## Codebase Changes

### New files
| File | Purpose |
|------|---------|
| `feature/auth/ConsentScreen.kt` | Consent UI + ViewModel |
| `domain/auth/ClaimValidator.kt` | Validation rules for well-known claims |
| `domain/transport/dto/AuthDtos.kt` | AuthChallengeResponse, AuthVerifyRequest, AuthVerifyResponse |

### Modified files
| File | Change |
|------|--------|
| `platform/deeplink/DeepLinkHandler.kt` | Parse requested_claims, session_id, accepted_algorithms |
| `platform/scan/QrScanner.kt` | Parse new QR payload fields |
| `domain/transport/ServerApi.kt` | Add getAuthChallenge(), verifyAuth() |
| `feature/auth/AuthFlowScreen.kt` | Route to ConsentScreen when requested_claims present |
| `ui/navigation/NavGraph.kt` | Add ConsentScreen route |
| `ui/navigation/Screen.kt` | Add Screen.Consent |
| `feature/identity/CreateIdentityScreen.kt` | Accept optional acceptedAlgorithms pre-filter |

### Unchanged
| File | Reason |
|------|--------|
| `SsdidClient.kt` | New flow uses ServerApi directly from ViewModel |
| `Vault.kt` / `VaultImpl.kt` | Existing sign() and listIdentities() sufficient |
| `RegistryApi.kt` | Not involved in service auth |
