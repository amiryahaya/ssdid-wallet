# SSDID System Flows — Unified Reference

## Document Information

| Field | Value |
|-------|-------|
| Version | 1.0 |
| Date | 2026-03-09 |
| Status | Draft |
| Sources | SSDID/docs/05.SSDID-Flows.md, ssdid-drive wallet-login-flow.md, ssdid-drive integration plan |

---

## 1. System Overview

The SSDID ecosystem has three components that interact:

| Component | Role | Repository |
|-----------|------|------------|
| **SSDID Wallet** | Identity holder — generates DIDs, stores keys, signs challenges | `ssdid-wallet` |
| **SSDID Registry** | DID resolver — stores and serves DID Documents | `SSDID/src/ssdid_registry` |
| **SSDID Drive** | Service provider — file storage app that authenticates via wallet | `ssdid-drive` |

### 1.1 Interaction Pairs

| Pair | Flows |
|------|-------|
| **Wallet → Registry** | Identity initialization, DID update, DID deactivation |
| **Wallet → Drive** | Registration with service, authentication, transaction signing |
| **Drive → Registry** | DID resolution during auth verification |

### 1.2 Flow Summary

| Flow | Description |
|------|-------------|
| **Identity Initialization** | Wallet creates keypairs, generates DID, publishes DID Document to Registry |
| **Registration with Service** | Wallet registers DID with Drive via mutual authentication |
| **Authentication** | Wallet logs in to Drive using Verifiable Credential |
| **QR/Deep Link Login** | Desktop/mobile initiates login, wallet completes auth, session delivered via SSE |
| **Transaction Signing** | Wallet signs transactions with challenge-response and tx hash binding |
| **DID Update** | Wallet adds/removes keys from DID Document at Registry |
| **DID Deactivation** | Wallet deactivates DID at Registry |

---

## 2. Unified Cross-System Flow

This is the complete end-to-end flow showing all three components interacting during a QR-based login. This is the primary user-facing flow for SSDID Drive.

```
┌──────────────┐       ┌──────────────┐       ┌──────────────┐       ┌──────────────┐
│ Drive Client │       │  Drive API   │       │   Registry   │       │ SSDID Wallet │
│ (Desktop/Web)│       │  (Backend)   │       │              │       │  (Mobile)    │
└──────┬───────┘       └──────┬───────┘       └──────┬───────┘       └──────┬───────┘
       │                      │                      │                      │
       │ 1. POST /login/      │                      │                      │
       │    initiate           │                      │                      │
       │─────────────────────→│                      │                      │
       │                      │                      │                      │
       │                      │ Generate challengeId │                      │
       │                      │ + challenge           │                      │
       │                      │ Sign with server key  │                      │
       │                      │                      │                      │
       │ { challengeId,       │                      │                      │
       │   subscriber_secret, │                      │                      │
       │   qr_payload }       │                      │                      │
       │←─────────────────────│                      │                      │
       │                      │                      │                      │
       │ 2. GET /events?      │                      │                      │
       │    challenge_id&     │                      │                      │
       │    subscriber_secret │                      │                      │
       │─────────────────────→│ (SSE connection,     │                      │
       │                      │  waiting...)         │                      │
       │                      │                      │                      │
       │ 3. Display QR code   │                      │                      │
       │    containing        │                      │                      │
       │    qr_payload        │                      │                      │
       │                      │                      │                      │
       │ ·····················│······ QR scan ·······│······················→│
       │                      │                      │                      │
       │                      │                      │                      │ 4. Parse QR payload
       │                      │                      │                      │    Extract server_did,
       │                      │                      │                      │    challenge, service_url
       │                      │                      │                      │
       │                      │                      │ 5. GET /api/did/     │
       │                      │                      │    {server_did}      │
       │                      │                      │←─────────────────────│
       │                      │                      │                      │
       │                      │                      │ Server DID Document  │
       │                      │                      │─────────────────────→│
       │                      │                      │                      │
       │                      │                      │                      │ 6. Verify server's
       │                      │                      │                      │    challenge signature
       │                      │                      │                      │    using server's
       │                      │                      │                      │    public key from
       │                      │                      │                      │    DID Document
       │                      │                      │                      │
       │                      │ 7. POST /register    │                      │
       │                      │    { did, key_id }   │                      │
       │                      │←─────────────────────│──────────────────────│
       │                      │                      │                      │
       │                      │ 8. GET /api/did/     │                      │
       │                      │    {wallet_did}      │                      │
       │                      │─────────────────────→│                      │
       │                      │                      │                      │
       │                      │ Wallet DID Document  │                      │
       │                      │←─────────────────────│                      │
       │                      │                      │                      │
       │                      │ Verify wallet DID,   │                      │
       │                      │ generate challenge,  │                      │
       │                      │ sign with server key │                      │
       │                      │                      │                      │
       │                      │ { challenge }        │                      │
       │                      │─────────────────────────────────────────────→│
       │                      │                      │                      │
       │                      │                      │                      │ 9. Sign challenge
       │                      │                      │                      │    with wallet key
       │                      │                      │                      │
       │                      │ 10. POST /register/  │                      │
       │                      │     verify           │                      │
       │                      │     { did, key_id,   │                      │
       │                      │       signed_        │                      │
       │                      │       challenge }    │                      │
       │                      │←─────────────────────│──────────────────────│
       │                      │                      │                      │
       │                      │ 11. Resolve wallet   │                      │
       │                      │     DID, verify      │                      │
       │                      │     signature,       │                      │
       │                      │     issue VC         │                      │
       │                      │                      │                      │
       │                      │ { credential (VC) }  │                      │
       │                      │─────────────────────────────────────────────→│
       │                      │                      │                      │
       │                      │                      │                      │ 12. Store VC
       │                      │                      │                      │     in vault
       │                      │                      │                      │
       │                      │ 13. POST             │                      │
       │                      │     /authenticate    │                      │
       │                      │     { credential,    │                      │
       │                      │       challenge_id } │                      │
       │                      │←─────────────────────│──────────────────────│
       │                      │                      │                      │
       │                      │ 14. Verify VC        │                      │
       │                      │     issuer = self,   │                      │
       │                      │     generate session │                      │
       │                      │     token            │                      │
       │                      │                      │                      │
       │ 15. SSE event:       │                      │                      │
       │     "authenticated"  │                      │                      │
       │     { session_token }│                      │                      │
       │←─────────────────────│                      │                      │
       │                      │                      │                      │
       │ 16. Store session    │                      │                      │
       │     token, navigate  │                      │                      │
       │     to files         │                      │                      │
```

**Notes:**
- Steps 7-12 (registration) are skipped if the wallet is already registered with Drive.
- For returning users, the wallet goes directly from step 6 to step 13 (authenticate with stored VC).
- Mobile Drive clients use deep links (`ssdid://login?payload=...`) instead of QR codes.

---

## 3. Wallet → Registry Flows

### 3.1 Identity Initialization

```
   Wallet (Entity)                   Vault                         Registry
     │                               │                               │
     │  Initialize(                  │                               │
     │    key_name,                  │                               │
     │    password,                  │                               │
     │    options)                   │                               │
     │ ─────────────────────────────▶│                               │
     │                               │                               │
     │              ┌────────────────┤                               │
     │              │ 1. Generate    │                               │
     │              │    user        │                               │
     │              │    keypair     │                               │
     │              │                │                               │
     │              │ 2. Generate    │                               │
     │              │    DID (random │                               │
     │              │    bytes)      │                               │
     │              │                │                               │
     │              │ 3. Build DID   │                               │
     │              │    Document    │                               │
     │              │                │                               │
     │              │ 4. Create      │                               │
     │              │    proof of    │                               │
     │              │    possession  │                               │
     │              └────────────────┤                               │
     │                               │                               │
     │                               │  POST /api/did                │
     │                               │  {signed DID Document}        │
     │                               │ ─────────────────────────────▶│
     │                               │                               │
     │                               │            ┌──────────────────┤
     │                               │            │ Validate &       │
     │                               │            │ Store            │
     │                               │            └──────────────────┤
     │                               │                               │
     │                               │  201 Created                  │
     │                               │◀─────────────────────────────│
     │                               │                               │
     │  Identity Initialized         │                               │
     │  {                            │                               │
     │    key_id: "did:ssdid:...#..",│                               │
     │    did: "did:ssdid:...",      │                               │
     │    public_key: <<...>>,       │                               │
     │    did_document: %{...},      │                               │
     │    proof: %{...}              │                               │
     │  }                            │                               │
     │◀─────────────────────────────│                               │
```

**DID format:** `did:ssdid:<Base64url(128-bit random)>`

**Supported algorithms:**
- Classical: Ed25519, ECDSA P-256, ECDSA P-384
- Post-quantum: KAZ-Sign (128/192/256-bit)

### 3.2 Identity Initialization with DSA Compliance (Planned)

> **Note:** DSA compliance is a planned feature. Not yet implemented.

For legally-binding signatures, an additional DSA keypair and X.509 certificate are required:

```
   Entity                 Vault              Registry           Licensed CA
     │                      │                    │                    │
     │  ... Basic init ...  │                    │                    │
     │◀────────────────────▶│                    │                    │
     │                      │                    │                    │
     │  Initialize DSA      │                    │                    │
     │  keypair             │                    │                    │
     │ ────────────────────▶│                    │                    │
     │                      │                    │                    │
     │         ┌────────────┤                    │                    │
     │         │ Generate   │                    │                    │
     │         │ DSA keypair│                    │                    │
     │         │ + recovery │                    │                    │
     │         │ + DID Doc  │                    │                    │
     │         └────────────┤                    │                    │
     │                      │                    │                    │
     │                      │  Publish DID Doc   │                    │
     │                      │ ──────────────────▶│                    │
     │                      │◀──────────────────│                    │
     │                      │                    │                    │
     │  DSA ID initialized  │                    │                    │
     │◀────────────────────│                    │                    │
     │                      │                    │                    │
     │  Generate CSR        │                    │                    │
     │  (DSA key, name,     │                    │                    │
     │   national ID)       │                    │                    │
     │ ────────────────────▶│                    │                    │
     │                      │                    │                    │
     │         ┌────────────┤                    │                    │
     │         │ Activate   │                    │                    │
     │         │ DSA key    │                    │                    │
     │         │ Generate   │                    │                    │
     │         │ CSR        │                    │                    │
     │         └────────────┤                    │                    │
     │                      │                    │                    │
     │  CSR                 │                    │                    │
     │◀────────────────────│                    │                    │
     │                      │                    │                    │
     │  Apply for cert (CSR)│                    │                    │
     │ ─────────────────────────────────────────────────────────────▶│
     │                      │                    │                    │
     │                      │                    │       ┌────────────┤
     │                      │                    │       │ Verify CSR │
     │                      │                    │       │ Issue cert │
     │                      │                    │       └────────────┤
     │                      │                    │                    │
     │  X.509 Certificate   │                    │                    │
     │◀─────────────────────────────────────────────────────────────│
     │                      │                    │                    │
     │  Bind certificate    │                    │                    │
     │  (DSA key, cert)     │                    │                    │
     │ ────────────────────▶│                    │                    │
     │                      │                    │                    │
     │         ┌────────────┤                    │                    │
     │         │ Associate  │                    │                    │
     │         │ cert with  │                    │                    │
     │         │ DSA key ID │                    │                    │
     │         └────────────┤                    │                    │
     │                      │                    │                    │
     │  Certificate bound   │                    │                    │
     │◀────────────────────│                    │                    │
```

### 3.3 DID Update (Add Key)

```
   Wallet                            Vault                         Registry
     │                               │                               │
     │  Generate new keypair         │                               │
     │  (e.g., ECDSA P-256)          │                               │
     │ ─────────────────────────────▶│                               │
     │                               │                               │
     │              ┌────────────────┤                               │
     │              │ Generate new   │                               │
     │              │ keypair        │                               │
     │              └────────────────┤                               │
     │                               │                               │
     │  New key_id                   │                               │
     │◀─────────────────────────────│                               │
     │                               │                               │
     │  Build updated DID Doc        │                               │
     │  (add new key)                │                               │
     │ ─────────────────────────────▶│                               │
     │                               │                               │
     │              ┌────────────────┤                               │
     │              │ Add key to     │                               │
     │              │ verificationMe │                               │
     │              │ thod           │                               │
     │              │                │                               │
     │              │ Sign with      │                               │
     │              │ EXISTING       │                               │
     │              │ capability     │                               │
     │              │ Invocation key │                               │
     │              └────────────────┤                               │
     │                               │                               │
     │                               │  PUT /api/did/{did}           │
     │                               │  {updated doc + proof}        │
     │                               │ ─────────────────────────────▶│
     │                               │                               │
     │                               │            ┌──────────────────┤
     │                               │            │ Verify proof key │
     │                               │            │ is in CURRENT    │
     │                               │            │ capabilityInvo   │
     │                               │            │ cation           │
     │                               │            │                  │
     │                               │            │ Update document  │
     │                               │            └──────────────────┤
     │                               │                               │
     │                               │  200 OK                       │
     │                               │◀─────────────────────────────│
     │                               │                               │
     │  Key added                    │                               │
     │◀─────────────────────────────│                               │
```

### 3.4 DID Deactivation

```
   Wallet                            Vault                         Registry
     │                               │                               │
     │  Deactivate DID               │                               │
     │ ─────────────────────────────▶│                               │
     │                               │                               │
     │              ┌────────────────┤                               │
     │              │ Create         │                               │
     │              │ deactivation   │                               │
     │              │ proof          │                               │
     │              │ (sign with     │                               │
     │              │ capabilityInvo │                               │
     │              │ cation key)    │                               │
     │              └────────────────┤                               │
     │                               │                               │
     │                               │  DELETE /api/did/{did}        │
     │                               │  {proof}                      │
     │                               │ ─────────────────────────────▶│
     │                               │                               │
     │                               │            ┌──────────────────┤
     │                               │            │ Verify proof     │
     │                               │            │ Mark as          │
     │                               │            │ deactivated      │
     │                               │            └──────────────────┤
     │                               │                               │
     │                               │  200 OK                       │
     │                               │  {status: deactivated}        │
     │                               │◀─────────────────────────────│
     │                               │                               │
     │  DID deactivated              │                               │
     │◀─────────────────────────────│                               │
```

---

## 4. Wallet → Drive Flows

### 4.1 Registration with Service (Mutual Authentication)

```
   Wallet        Vault       Verifier      Registry       Drive       Verifier
  (Client)     (Client)     (Client)                   (Server)     (Server)
     │            │            │             │            │            │
     │  Register(DID)          │             │            │            │
     │ ───────────────────────────────────────────────────▶            │
     │            │            │             │            │            │
     │            │            │             │            │  Verify    │
     │            │            │             │            │  DID       │
     │            │            │             │            │ ──────────▶│
     │            │            │             │            │            │
     │            │            │             │  GET DID   │            │
     │            │            │             │◀───────────────────────│
     │            │            │             │            │            │
     │            │            │             │  DID Doc   │            │
     │            │            │             │ ───────────────────────▶│
     │            │            │             │            │            │
     │            │            │             │            │  ┌─────────┤
     │            │            │             │            │  │ Verify  │
     │            │            │             │            │  │ DID Doc │
     │            │            │             │            │  └─────────┤
     │            │            │             │            │            │
     │            │            │             │            │  Valid     │
     │            │            │             │            │◀──────────│
     │            │            │             │            │            │
     │            │            │             │  ┌─────────┤            │
     │            │            │             │  │Generate │            │
     │            │            │             │  │challenge│            │
     │            │            │             │  │Sign with│            │
     │            │            │             │  │server   │            │
     │            │            │             │  │key      │            │
     │            │            │             │  └─────────┤            │
     │            │            │             │            │            │
     │  Server DID + Signed Challenge        │            │            │
     │◀──────────────────────────────────────────────────│            │
     │            │            │             │            │            │
     │  Verify server         │             │            │            │
     │ ──────────────────────▶│             │            │            │
     │            │            │             │            │            │
     │            │            │  GET DID    │            │            │
     │            │            │  (server)   │            │            │
     │            │            │ ───────────▶│            │            │
     │            │            │             │            │            │
     │            │            │  Server Doc │            │            │
     │            │            │◀───────────│            │            │
     │            │            │             │            │            │
     │            │  ┌─────────┤             │            │            │
     │            │  │Verify   │             │            │            │
     │            │  │Server   │             │            │            │
     │            │  │DID +    │             │            │            │
     │            │  │Challenge│             │            │            │
     │            │  └─────────┤             │            │            │
     │            │            │             │            │            │
     │  Server verified       │             │            │            │
     │◀──────────────────────│             │            │            │
     │            │            │             │            │            │
     │  Activate + Sign       │             │            │            │
     │ ──────────▶│            │             │            │            │
     │            │            │             │            │            │
     │  ┌─────────┤            │             │            │            │
     │  │Activate │            │             │            │            │
     │  │key      │            │             │            │            │
     │  │Sign     │            │             │            │            │
     │  │challenge│            │             │            │            │
     │  └─────────┤            │             │            │            │
     │            │            │             │            │            │
     │  Signature │            │             │            │            │
     │◀──────────│            │             │            │            │
     │            │            │             │            │            │
     │  Response(DID, signature)             │            │            │
     │ ───────────────────────────────────────────────────▶            │
     │            │            │             │            │            │
     │            │            │             │            │  Verify    │
     │            │            │             │            │  response  │
     │            │            │             │            │ ──────────▶│
     │            │            │             │            │            │
     │            │            │             │  GET DID   │            │
     │            │            │             │◀───────────────────────│
     │            │            │             │  DID Doc   │            │
     │            │            │             │ ───────────────────────▶│
     │            │            │             │            │            │
     │            │            │             │            │  ┌─────────┤
     │            │            │             │            │  │Verify   │
     │            │            │             │            │  │signature│
     │            │            │             │            │  └─────────┤
     │            │            │             │            │            │
     │            │            │             │            │  Verified  │
     │            │            │             │            │◀──────────│
     │            │            │             │            │            │
     │            │            │             │  ┌─────────┤            │
     │            │            │             │  │Issue VC │            │
     │            │            │             │  │(no PII!)│            │
     │            │            │             │  │Sign VC  │            │
     │            │            │             │  └─────────┤            │
     │            │            │             │            │            │
     │  Account Registered (VC)              │            │            │
     │◀──────────────────────────────────────────────────│            │
     │            │            │             │            │            │
     │  Store VC  │            │             │            │            │
     │ ──────────▶│            │             │            │            │
     │            │            │             │            │            │
     │  ┌─────────┤            │             │            │            │
     │  │Store VC │            │             │            │            │
     │  │with key │            │             │            │            │
     │  │ID       │            │             │            │            │
     │  └─────────┤            │             │            │            │
```

**Key points:**
- Both sides resolve each other's DID from Registry (mutual authentication)
- Server generates challenge, signs with server key
- Wallet verifies server, then signs challenge with wallet key
- Server verifies wallet signature, issues Verifiable Credential (no PII)
- Wallet stores VC for future authentication

### 4.2 Authentication (Login with VC)

```
   Wallet        Vault       Verifier      Registry       Drive       Verifier
  (Client)     (Client)     (Client)                   (Server)     (Server)
     │            │            │             │            │            │
     │  Select VC │            │             │            │            │
     │ ──────────▶│            │             │            │            │
     │            │            │             │            │            │
     │  VC        │            │             │            │            │
     │◀──────────│            │             │            │            │
     │            │            │             │            │            │
     │  Login(VC) │            │             │            │            │
     │ ───────────────────────────────────────────────────▶            │
     │            │            │             │            │            │
     │            │            │             │            │  Verify VC │
     │            │            │             │            │ ──────────▶│
     │            │            │             │            │            │
     │            │            │             │  GET DID   │            │
     │            │            │             │  (issuer)  │            │
     │            │            │             │◀───────────────────────│
     │            │            │             │            │            │
     │            │            │             │  Issuer Doc│            │
     │            │            │             │ ───────────────────────▶│
     │            │            │             │            │            │
     │            │            │             │            │  ┌─────────┤
     │            │            │             │            │  │1. Verify│
     │            │            │             │            │  │   issuer│
     │            │            │             │            │  │2. Verify│
     │            │            │             │            │  │   VC sig│
     │            │            │             │            │  │3. Check │
     │            │            │             │            │  │   status│
     │            │            │             │            │  └─────────┤
     │            │            │             │            │            │
     │            │            │             │            │  VC valid  │
     │            │            │             │            │◀──────────│
     │            │            │             │            │            │
     │            │            │             │  ┌─────────┤            │
     │            │            │             │  │Generate │            │
     │            │            │             │  │session  │            │
     │            │            │             │  │token    │            │
     │            │            │             │  └─────────┤            │
     │            │            │             │            │            │
     │  Session Token          │             │            │            │
     │◀──────────────────────────────────────────────────│            │
     │            │            │             │            │            │
     │  Continue with session token          │            │            │
     │ ───────────────────────────────────────────────────▶            │
```

### 4.3 Transaction Signing

```
   Wallet        Vault                        Drive       Verifier
  (Client)     (Client)                    (Server)     (Server)
     │            │                           │            │
     │  Step 1: Request challenge             │            │
     │  (session_token)                       │            │
     │ ───────────────────────────────────────▶            │
     │            │                           │            │
     │            │              ┌─────────────┤            │
     │            │              │ Verify      │            │
     │            │              │ session     │            │
     │            │              │ Generate    │            │
     │            │              │ challenge   │            │
     │            │              └─────────────┤            │
     │            │                           │            │
     │  Challenge │                           │            │
     │◀───────────────────────────────────────│            │
     │            │                           │            │
     │  Step 2: Sign challenge + tx hash      │            │
     │  (binds tx body to signature)          │            │
     │ ──────────▶│                           │            │
     │            │                           │            │
     │  ┌─────────┤                           │            │
     │  │Hash tx  │                           │            │
     │  │body     │                           │            │
     │  │(SHA3-256│                           │            │
     │  │Sign:    │                           │            │
     │  │challenge│                           │            │
     │  │+ tx_hash│                           │            │
     │  └─────────┤                           │            │
     │            │                           │            │
     │  Signature │                           │            │
     │◀──────────│                           │            │
     │            │                           │            │
     │  Step 3: Submit signed transaction     │            │
     │  (session, did, key_id, sig, tx)       │            │
     │ ───────────────────────────────────────▶            │
     │            │                           │            │
     │            │              ┌─────────────┤            │
     │            │              │ 1. Verify   │            │
     │            │              │    session  │            │
     │            │              │ 2. Consume  │            │
     │            │              │    challenge│            │
     │            │              │ 3. Hash tx  │            │
     │            │              │    body     │            │
     │            │              └─────────────┤            │
     │            │                           │            │
     │            │                           │  Verify    │
     │            │                           │  sig over  │
     │            │                           │  challenge │
     │            │                           │  + tx_hash │
     │            │                           │ ──────────▶│
     │            │                           │            │
     │            │                           │  ┌─────────┤
     │            │                           │  │Resolve  │
     │            │                           │  │DID +    │
     │            │                           │  │verify   │
     │            │                           │  │signature│
     │            │                           │  └─────────┤
     │            │                           │            │
     │            │                           │  Verified  │
     │            │                           │◀──────────│
     │            │                           │            │
     │            │              ┌─────────────┤            │
     │            │              │ Store txn   │            │
     │            │              │ + generate  │            │
     │            │              │ tx_id       │            │
     │            │              └─────────────┤            │
     │            │                           │            │
     │  Transaction confirmed                │            │
     │◀───────────────────────────────────────│            │
```

**Transaction binding:** The wallet signs `challenge + Base64url(SHA3-256(transaction_body))`. This binds the transaction body to the challenge signature, preventing transaction body substitution attacks. The server independently computes the same hash and verifies the signature over the combined payload.

**DSA compliance (planned):** Sign with DSA key, include X.509 certificate, server verifies via CRL/OCSP.

---

## 5. Drive → Registry Flow

During authentication and registration, Drive resolves wallet DIDs from the Registry to obtain public keys for signature verification. This happens transparently as part of the flows above.

```
   Drive API                                              Registry
     │                                                       │
     │  1. Wallet sends DID + signed challenge               │
     │     (from /register or /authenticate)                 │
     │                                                       │
     │  2. GET /api/did/{wallet_did}                         │
     │─────────────────────────────────────────────────────→│
     │                                                       │
     │                                  ┌────────────────────┤
     │                                  │ Look up DID        │
     │                                  │ Return DID Document│
     │                                  │ with public keys   │
     │                                  └────────────────────┤
     │                                                       │
     │  DID Document                                         │
     │  {                                                    │
     │    "id": "did:ssdid:...",                             │
     │    "verificationMethod": [{                           │
     │      "id": "did:ssdid:...#key-1",                    │
     │      "type": "Ed25519VerificationKey2020",            │
     │      "publicKeyMultibase": "z..."                     │
     │    }],                                                │
     │    ...                                                │
     │  }                                                    │
     │←─────────────────────────────────────────────────────│
     │                                                       │
     │  3. Extract public key from                           │
     │     verificationMethod matching                       │
     │     the wallet's key_id                               │
     │                                                       │
     │  4. Verify signature using                            │
     │     extracted public key                              │
     │                                                       │
     │  5. If valid: proceed with                            │
     │     registration or session creation                  │
```

**When this happens:**
- During `POST /register` — Drive resolves the wallet's DID to verify it exists and extract the public key
- During `POST /register/verify` — Drive resolves again to verify the signed challenge
- During `POST /authenticate` — Drive resolves the VC issuer's DID (which is its own DID) to verify the VC signature

**Registry endpoint:** `GET https://registry.ssdid.my/api/did/{did}`

**Error cases:**
- DID not found → 404 → Drive returns 401 to wallet
- DID deactivated → Drive returns 401 to wallet
- Registry unreachable → Drive returns 503 to wallet

---

## 6. QR/Deep Link Login Flow (Drive-Specific)

This is the practical implementation of sections 4.1 and 4.2 for the Drive desktop/mobile clients.

### 6.1 Flow Diagram

```
Desktop/Web Client                Backend API                        SSDID Wallet
      │                               │                                   │
      │── POST /login/initiate ──────→│                                   │
      │   { }                         │  generate challengeId + challenge │
      │←── { challengeId,             │  sign challenge with server key   │
      │      subscriber_secret,       │                                   │
      │      qrPayload (JSON) }       │                                   │
      │                               │                                   │
      │── GET /events?challenge_id    │                                   │
      │   &subscriber_secret ────────→│  (SSE connection, waiting)        │
      │                               │                                   │
      │  [show QR / open deep link]   │                                   │
      │         ········QR scan / deep link ·····················→        │
      │                               │                                   │
      │                               │  IF wallet NOT registered:        │
      │                               │←── POST /register ───────────────│
      │                               │←── POST /register/verify ────────│
      │                               │                                   │
      │                               │  THEN (always):                   │
      │                               │←── POST /authenticate ──────────│
      │                               │    { credential, challengeId }    │
      │                               │                                   │
      │←── SSE: { session_token,      │                                   │
      │          did, user } ─────────│                                   │
      │                               │                                   │
      │  [logged in, store token]     │                                   │
```

### 6.2 QR / Deep Link Payload Format

```json
{
  "action": "login",
  "service_url": "https://drive.ssdid.my",
  "service_name": "ssdid-drive",
  "challenge_id": "abc123def456",
  "challenge": "BASE64URL_CHALLENGE",
  "server_did": "did:ssdid:...",
  "server_key_id": "did:ssdid:...#key-1",
  "server_signature": "uSIGNATURE...",
  "registry_url": "https://registry.ssdid.my"
}
```

- **QR code:** `ssdid://login?payload=BASE64URL(json)`
- **Deep link:** `ssdid://login?payload=BASE64URL(json)`
- Same URI scheme for both. Client chooses QR display vs deep link based on platform.

### 6.3 Drive API Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/auth/ssdid/login/initiate` | POST | Generate challenge without requiring wallet DID |
| `/api/auth/ssdid/events` | GET | SSE stream, delivers session token when wallet completes |
| `/api/auth/ssdid/register` | POST | Wallet sends DID + key_id to start registration |
| `/api/auth/ssdid/register/verify` | POST | Wallet sends signed challenge to complete registration |
| `/api/auth/ssdid/authenticate` | POST | Wallet sends VC + challengeId to authenticate |
| `/api/auth/ssdid/server-info` | GET | Returns server DID, key_id, registry URL |
| `/api/auth/ssdid/logout` | POST | Invalidate session |

### 6.4 SSE Events

| Event | Data | When |
|-------|------|------|
| `authenticated` | `{ session_token, did, user }` | Wallet completes auth |
| `timeout` | `{}` | Challenge expires (5 minutes) |

### 6.5 Security

- **Subscriber secret:** SSE URL requires `subscriber_secret` query param to prevent unauthorized subscription to a challenge
- **Challenge TTL:** 5 minutes
- **One-time use:** ChallengeId can only deliver one SSE session (uses `TryRemove`)
- **Mutual auth:** Wallet verifies server DID before signing; server verifies wallet DID before issuing VC

---

## 7. Summary

| Flow | Wallet | Drive | Registry |
|------|--------|-------|----------|
| **Initialize** | Generate keys, build DID Doc | — | Store DID Doc |
| **Register** | Sign challenge, store VC | Verify DID, issue VC | Resolve DIDs (both) |
| **Authenticate** | Present VC | Verify VC, issue session | Resolve issuer DID |
| **QR Login** | Scan QR, complete auth | Initiate challenge, deliver session via SSE | Resolve DIDs |
| **Sign Txn** | Sign challenge + tx hash | Verify signature | Resolve wallet DID |
| **Update DID** | Sign with capabilityInvocation key | — | Verify + update |
| **Deactivate** | Sign deactivation proof | — | Verify + deactivate |

---

## 8. Related Documents

- [01.SSDID-Overview](https://github.com/nicholasgasior/SSDID/blob/main/docs/01.SSDID-Overview.md)
- [02.SSDID-Vault-Specification](https://github.com/nicholasgasior/SSDID/blob/main/docs/02.SSDID-Vault-Specification.md)
- [03.SSDID-Verifier-Specification](https://github.com/nicholasgasior/SSDID/blob/main/docs/03.SSDID-Verifier-Specification.md)
- [04.SSDID-Registry-Specification](https://github.com/nicholasgasior/SSDID/blob/main/docs/04.SSDID-Registry-Specification.md)
- [09.SSDID-Crypto-Specification](https://github.com/nicholasgasior/SSDID/blob/main/docs/09.SSDID-Crypto-Specification.md)
