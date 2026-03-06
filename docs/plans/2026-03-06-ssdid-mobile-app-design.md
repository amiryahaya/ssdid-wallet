# SSDID Mobile App Design

Date: 2026-03-06

## Overview

Native mobile app for SSDID (Self-Sovereign Distributed Identity) — an identity wallet with service interaction capabilities. Targets Android, iOS, and HarmonyOS (NEXT + legacy).

## Decisions

### Platform Strategy

Pure native. 4 codebases:

| Platform | Language | UI | Min Version |
|----------|----------|-----|-------------|
| Android | Kotlin | Jetpack Compose | API 24 |
| iOS | Swift | SwiftUI | iOS 14+ |
| HarmonyOS NEXT | ArkTS | ArkUI | API 12 |
| HarmonyOS 4.x | — | — | Android APK via compatibility layer |

### Cryptography

Classical + Post-Quantum. KAZ-Sign is mandatory.

**Classical (platform-native):**
- Ed25519 (default)
- ECDSA P-256
- ECDSA P-384

**Post-Quantum (C library via FFI):**
- KAZ-Sign with configurable security levels (128/192/256)
- C implementation at `/Users/amirrudinyahaya/Workspace/PQC-KAZ/SIGN/`
- Existing bindings: Kotlin (JNI), Swift (C interop)
- HarmonyOS NEXT: new N-API/C FFI binding needed

**Crypto Provider abstraction** mirrors backend dual provider dispatch:

```
CryptoProvider (protocol/interface)
├── ClassicalProvider (platform-native keystore)
└── PQCProvider (KAZ-Sign C lib via FFI)
```

### Key Storage

Hardware-backed native keystores as primary storage.

| Key Type | Storage | At Rest |
|----------|---------|---------|
| Ed25519 / ECDSA | Hardware keystore directly | Hardware-protected |
| KAZ-Sign | Device filesystem | Encrypted by hardware-backed AES-256 wrapping key |
| AES wrapping key | Hardware keystore | Hardware-protected, non-exportable |

Platform keystores:
- Android: Android Keystore (TEE/StrongBox)
- iOS: Secure Enclave / Keychain
- HarmonyOS: HUKS (Huawei Universal Keystore Service)

### Vault Unlock

Biometric + password fallback.

- Primary: Biometric (Face ID / fingerprint / HarmonyOS 3D face) unlocks hardware keystore
- Fallback: OS-level credential prompt (device PIN/password) unlocks the same hardware keystore key
- If hardware keystore unavailable: PBKDF2-HMAC-SHA256 as software fallback
- No Argon2id on mobile (too resource-heavy)

### App Scope

Identity wallet + service interaction:
- Manage multiple identities with different algorithms
- Store and present Verifiable Credentials
- Register with services via QR code / deep links
- Authenticate with services
- Sign transactions with challenge-response + TX binding
- Activity history

### Network

Online only. No offline capability.

### Communication

QR code + deep links + HTTP.

- QR codes contain: `{server_url, server_did, action, [challenge]}`
- Deep links / universal links for app-to-app SSDID flows
- All protocol communication over HTTP REST

### Localization

English (primary), Malay, Chinese. i18n architecture from day one.

### Design

Enterprise/corporate. Clean, minimal, trust-focused. Dark theme primary.

See `mockup/index.html` for full interactive mockup (13 screens).

## Architecture

```
┌─────────────────────────────────┐
│  UI Layer (platform-native)     │
│  Compose / SwiftUI / ArkUI      │
├─────────────────────────────────┤
│  Feature Layer                  │
│  Identity, Registration, Auth,  │
│  Transaction, Credentials, Scan │
├─────────────────────────────────┤
│  Domain Layer                   │
│  Vault, Verifier, Transport,    │
│  DID/VC Models, Crypto Provider │
├─────────────────────────────────┤
│  Platform Layer                 │
│  Keystore, Biometrics, Camera,  │
│  Deep Links, i18n, Secure Store │
└─────────────────────────────────┘
```

## SSDID Flows

### Flow 1: Identity Creation

```
User taps "Create Identity"
  → Select algorithm + level
  → Biometric prompt
  → Generate keypair via CryptoProvider
  → Store key (hardware or wrapped)
  → Generate DID (did:ssdid:<Base64url(128-bit random)>)
  → Build DID Document (W3C 1.1)
  → Create proof of possession
  → POST /api/did → Registry
  → Show identity card in wallet
```

### Flow 2: Service Registration (QR + Mutual Auth)

```
Scan QR → extract {server_url, server_did, action: "register"}
  → POST /api/register {did, key_id}
  ← {challenge, server_did, server_key_id, server_signature}
  → Resolve server DID from Registry
  → Verify server_signature (mutual auth)
  → Biometric prompt
  → Sign challenge
  → POST /api/register/verify {did, key_id, signed_challenge}
  ← Verifiable Credential
  → Store VC locally
```

### Flow 3: Authentication (Deep Link or QR)

```
Deep link / QR → {server_url, action: "authenticate"}
  → Select matching VC
  → POST /api/authenticate {credential}
  ← {session_token, server_did, server_key_id, server_signature}
  → Verify session_token signature (mutual auth)
  → Session active
```

### Flow 4: Transaction Signing

```
Service presents transaction
  → User reviews details
  → POST /api/transaction/challenge {session_token}
  ← {challenge}
  → Compute SHA3-256(transaction_body)
  → Biometric prompt
  → Sign: challenge || Base64url(tx_hash)
  → POST /api/transaction/submit {session_token, did, key_id, signed_challenge, transaction}
  ← {transaction_id, status: "confirmed"}
```

## HTTP Endpoints (from backend spec)

**Registry** (DID Document lifecycle):

| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | /api/did | Register new DID |
| GET | /api/did/{did} | Resolve DID |
| PUT | /api/did/{did} | Update DID Document |
| DELETE | /api/did/{did} | Deactivate DID |
| POST | /api/did/{did}/challenge | Get single-use challenge |

**Server** (Client-server interactions):

| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | /api/register | Start registration |
| POST | /api/register/verify | Complete registration |
| POST | /api/authenticate | Login with VC |
| POST | /api/transaction/challenge | Get TX challenge |
| POST | /api/transaction/submit | Submit signed TX |

## Data Structures

**DID format:** `did:ssdid:<Base64url(16 random bytes)>`

**Key ID:** `did:ssdid:<id>#key-1`

**W3C types:** Ed25519VerificationKey2020, EcdsaSecp256r1VerificationKey2019, EcdsaSecp384VerificationKey2019, KazSignVerificationKey2024

**Encoding:** Multibase (u-prefix for Base64url)

**Proof types:** Ed25519Signature2020, EcdsaSecp256r1Signature2019, KazSignSignature2024

## KAZ-Sign Integration

C library at `/Users/amirrudinyahaya/Workspace/PQC-KAZ/SIGN/`.

Runtime API (configurable security level):
```c
int kaz_sign_keypair_ex(kaz_sign_level_t level, unsigned char *pk, unsigned char *sk);
int kaz_sign_signature_ex(kaz_sign_level_t level, unsigned char *sig, unsigned long long *siglen, const unsigned char *msg, unsigned long long msglen, const unsigned char *sk);
int kaz_sign_verify_ex(kaz_sign_level_t level, unsigned char *msg, unsigned long long *msglen, const unsigned char *sig, unsigned long long siglen, const unsigned char *pk);
```

Depends on OpenSSL 3.x. Existing bindings: Kotlin (Android), Swift (iOS). New binding needed: ArkTS (HarmonyOS NEXT) via N-API.

## Screens

13 screens designed (see mockup/):
1. Onboarding (3 slides)
2. Create Identity (algorithm + level selection)
3. Biometric Setup
4. Wallet Home (identities, quick actions, activity)
5. Identity Detail (DID info, credentials, DID Document)
6. Scan QR
7. Service Registration (4-step flow, mutual auth, identity select)
8. Authentication (service request, biometric prompt)
9. Transaction Signing (TX details, security info, challenge timer)
10. Credentials (card list)
11. Credential Detail (VC metadata, raw JSON)
12. Settings (security, network, preferences)
13. Activity History (chronological, grouped by date)
