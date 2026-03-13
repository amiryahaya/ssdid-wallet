# SSDID Interoperability & SDK Design

## Goal

Evolve SSDID from a closed ecosystem to an interoperable, certifiable SSI platform with a parallel SDK enabling external parties to join the SSDID network as issuers or verifiers.

## Context

### Current State
- Wallet supports W3C DID Core 1.1, VC Data Model v1.1, Data Integrity proofs
- Dual crypto: Classical (Ed25519, ECDSA P-256/384) + PQC (KAZ-Sign, ML-DSA, SLH-DSA)
- Hardware-backed key storage (TEE/StrongBox, Secure Enclave)
- KERI-inspired key rotation, multi-layer recovery, multi-device pairing
- Centralized registry at registry.ssdid.my
- Custom REST challenge-response auth protocol

### Gaps Identified
- No SD-JWT VC (the interop credential format)
- No Verifiable Presentations
- No OpenID4VP/OpenID4VCI (standard presentation/issuance protocols)
- No mdoc/mDL (ISO 18013-5)
- Only resolves did:ssdid (no did:key, did:jwk, did:web)
- No JWK key format support
- Biometric gating not wired to keystore
- No certificate pinning
- No pairwise DIDs (full correlation across services)
- No consent audit trail
- No trust registry
- No SDK for external integrators

### Target Market
- Near-term: ASEAN/Malaysia ecosystem
- Ultimate: Globally competitive, OpenID Foundation + HAIP certified

## Architecture

### Approach: Shared Core Library + Platform Shells

Three layers:

1. **`@ssdid/core` (TypeScript)** — Pure protocol engine: SD-JWT VC, DID resolution, VP, OpenID4VP, OpenID4VCI, credential verification. No I/O, no platform dependencies. Becomes the server SDK and web SDK.

2. **Wallet-native modules (Kotlin/Swift)** — Port protocol logic to native code for the wallet, using `@ssdid/core` as reference specification. Shared test vectors ensure parity.

3. **`@ssdid/sdk` (TypeScript)** — Thin wrapper over `@ssdid/core` adding I/O: HTTP client, key storage adapters, issuer/verifier convenience APIs. Published to npm as `@ssdid/sdk`.

### Package Structure

```
@ssdid/core
  ├── sd-jwt/         — SD-JWT VC create, parse, disclose, verify, key binding
  ├── did/            — Multi-method resolver (did:ssdid, did:key, did:jwk, did:web)
  ├── vp/             — Verifiable Presentation create + verify
  ├── oid4vp/         — OpenID4VP request parsing, response construction
  ├── oid4vci/        — OpenID4VCI offer handling, token exchange, credential fetch
  ├── revocation/     — Bitstring Status List decode + check
  ├── proof/          — Data Integrity proof create + verify (SHA3-256 canonical JSON)
  └── crypto/         — Algorithm registry, JWK <-> multibase conversion, multicodec

@ssdid/sdk
  ├── issuer/         — Issue SD-JWT VCs, manage status lists, publish DIDs
  ├── verifier/       — Verify presentations, resolve DIDs, check revocation
  ├── registry/       — SSDID registry client (HTTP)
  ├── storage/        — Pluggable adapters (in-memory, Redis, PostgreSQL)
  └── http/           — Fetch-based HTTP client (works in Node + browser)
```

### Shared Test Vectors

```
test-vectors/
  ├── sd-jwt/          — SD-JWT create -> disclose -> verify round-trips
  ├── did-resolution/  — did:key, did:jwk -> expected DID Documents
  ├── vp/              — VP creation -> verification
  ├── oid4vp/          — Authorization request -> response pairs
  ├── oid4vci/         — Offer -> token -> credential flows
  └── proof/           — Canonical JSON -> hash -> signature verification
```

Each vector is a JSON file with `input`, `expected_output`, and `description`. All three implementations (TypeScript, Kotlin, Swift) run the same vectors.

### Design Principles

- `@ssdid/core` is pure functions — no fetch, no fs, no crypto.subtle. Platform injects these via interfaces.
- Isomorphic — same `@ssdid/core` runs in Node.js, browser, Deno, edge workers.
- `@ssdid/sdk` is opinionated — provides default HTTP client, key storage, convenience methods.
- Wallet does NOT depend on TypeScript SDK — native implementations validated by shared test vectors.
- SDK ships incrementally — modular structure allows shipping individual features ahead of schedule based on client demand.

## Phases

### Phase 1 — Wallet MVP (Q3 2026, ~3 months)

**Target:** Sales-ready wallet with interoperable credential formats.

#### Wallet Changes
- **SD-JWT VC** — Parse, create, selective disclosure, verify, key binding. New `SdJwtVc` model alongside existing `VerifiableCredential`. `CredentialStore` becomes format-aware.
- **Verifiable Presentation** — New `VerifiablePresentation` model wrapping one or more VCs. For SD-JWT VC, the VP is implicit (SD-JWT + disclosures + KB-JWT).
- **Multi-method DID resolution** — New `DidResolver` interface with `MultiMethodResolver` supporting did:ssdid (registry), did:key (local), did:jwk (local). Replaces direct `RegistryApi.resolveDid()`.
- **JWK in VerificationMethod** — Add `publicKeyJwk` field alongside existing `publicKeyMultibase`.
- **Biometric gating** — Wire `BiometricPrompt.CryptoObject` into `AndroidKeystoreManager` decrypt; add `LAContext` to iOS keychain access.
- **Certificate pinning** — OkHttp `CertificatePinner` for `registry.ssdid.my` (Android), `URLSession` pinning delegate (iOS).
- **VCDM 2.0 context** — Update default `@context` from `https://www.w3.org/2018/credentials/v1` to `https://www.w3.org/ns/credentials/v2`.

#### SDK
- `@ssdid/core` v0.1 — SD-JWT VC, DID resolution, VP, proof
- `@ssdid/sdk` v0.1 — Basic issuer + verifier APIs
- Shared test vectors for SD-JWT VC and DID resolution

### Phase 2 — OpenID4VC + Certification (Q4 2026, ~3 months)

**Target:** OpenID Foundation self-certification for OpenID4VP + OpenID4VCI.

#### Wallet Changes
- **OpenID4VP 1.0** — New `OpenId4VpHandler`: parse Authorization Request, fetch request object, validate `client_id`. Extend `ConsentScreen` to map Presentation Definition `input_descriptors` to claim toggles. New `VpTokenBuilder` for SD-JWT VC presentations. Register `openid4vp://` URI scheme.
  - Same-device: deep link triggers wallet, response POSTed to `response_uri`
  - Cross-device: QR code scanned, same flow
- **OpenID4VCI 1.0** — New `OpenId4VciHandler`: fetch Credential Offer, token exchange, credential endpoint call with proof of possession. Register `openid-credential-offer://` URI scheme.
  - Pre-authorized code flow (in-person issuance)
  - Authorization code flow (online with user auth via in-app browser)
- **Presentation Exchange 2.0 / DCQL** — Parse query language for requested credentials.
- **Pairwise DIDs** — Derive `did:key` per verifier origin. Deterministic (same verifier = same DID), unlinkable across verifiers. Existing `did:ssdid` stays for SSDID-network services.
- **Consent audit trail** — Persistent log of what was shared, with whom, when.
- **Trust registry** — Issuer allowlist/denylist. UI shows issuer trust status.
- **Coexistence** — `ssdid://` keeps existing flow, `openid4vp://` adds standard flow. No breaking changes.

#### SDK
- `@ssdid/core` v0.5 — OpenID4VP, OpenID4VCI, revocation
- `@ssdid/sdk` v0.5 — Full issuer + verifier flows
- `@ssdid/mobile` v0.1 — Kotlin wrapper for Android (Maven Central)

#### Certification
- Run OpenID Foundation conformance test suite during development
- Submit self-certification for OpenID4VP 1.0 + OpenID4VCI 1.0

### Phase 3 — mdoc + HAIP (Q1-Q2 2027)

**Target:** HAIP 1.0 certification, government ID readiness.

#### Wallet Changes
- **mdoc/mDL (ISO 18013-5)** — CBOR parser/encoder, `MDoc` model (IssuerSigned, DeviceSigned, MSO), digest-based selective disclosure, credential store extended.
- **ISO 18013-7 online presentation** — mdoc via OpenID4VP (Annex C).
- **Digital Credentials API** — Register as credential provider on Android (`CredentialManager`) and iOS (`ASAuthorizationController`). Handle system-mediated presentation flow.
- **DIDComm v2** (client-demand gated) — `service` endpoints in DID Document, `keyAgreement` with X25519 keys, message packing/unpacking.
- **Service endpoints + keyAgreement** — Add to DID Document model.

#### SDK
- `@ssdid/core` v1.0 + `@ssdid/sdk` v1.0 — Production-ready
- `@ssdid/mobile` v0.5 — iOS Swift Package added
- `@ssdid/web` v0.1 — Browser bundle

#### Certification
- HAIP 1.0 self-certification (requires SD-JWT VC + mdoc + DC API)

## SDK API Design

### Issuer API

```typescript
import { SsdidIssuer } from '@ssdid/sdk';

const issuer = new SsdidIssuer({
  did: 'did:ssdid:abc123',
  privateKey: loadKey(),
  registryUrl: 'https://registry.ssdid.my',
  statusListUrl: 'https://issuer.example/status/1'
});

await issuer.register();

const credential = await issuer.issue({
  subject: 'did:ssdid:holder456',
  type: 'VerifiedEmployee',
  claims: { name: 'Ahmad bin Ali', employeeId: 'EMP-1234', department: 'Engineering' },
  disclosable: ['name', 'department'],
  nonDisclosable: ['employeeId'],
  expiresIn: '365d'
});

await issuer.revoke(credential.id);
```

### Verifier API

```typescript
import { SsdidVerifier } from '@ssdid/sdk';

const verifier = new SsdidVerifier({
  clientId: 'https://myservice.com',
  registryUrl: 'https://registry.ssdid.my',
  trustedIssuers: ['did:ssdid:gov-my', 'did:ssdid:bank-abc']
});

const { requestUri, sessionId } = await verifier.createPresentationRequest({
  requestedClaims: [
    { key: 'name', required: true },
    { key: 'email', required: false }
  ],
  acceptedFormats: ['sd-jwt-vc'],
  callbackUrl: 'https://myservice.com/callback'
});

const result = await verifier.verifyPresentation(vpToken, { sessionId, nonce });
// result.verified, result.holder, result.claims, result.issuer, result.revocationStatus
```

## Key Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| SDK language | TypeScript first, then Kotlin/Swift | Covers server + web, largest ecosystem |
| SDK priority | Server -> Mobile -> Web | Matches integrator profile in ASEAN |
| Wallet <-> SDK | Independent codebases, shared test vectors | Native performance, no JS runtime in mobile |
| DID registry | Hybrid (centralized did:ssdid + resolve did:key/jwk/web) | Keep trust anchor, enable external interop |
| Credential formats | VC (existing) + SD-JWT VC (Phase 1) + mdoc (Phase 3) | Incremental, backward-compatible |
| OpenID4VP vs existing auth | Coexist — ssdid:// stays, openid4vp:// adds standard | No breaking changes |
| Pairwise DIDs | did:key derived per verifier origin | Lightweight, no registry needed |
| Certification path | OpenID4VP + OpenID4VCI first, HAIP after mdoc | Achievable milestones |
| DIDComm v2 | Client-demand gated in Phase 3 | YAGNI unless clients need it |

## Team Split (2-4 devs)

| Stream | Owner | Scope |
|--------|-------|-------|
| Wallet protocols | 1-2 devs | SD-JWT VC, VP, OpenID4VP/VCI, mdoc (Kotlin + Swift) |
| SDK + test vectors | 1-2 devs | @ssdid/core, @ssdid/sdk, shared fixtures (TypeScript) |
| Infrastructure | Shared | Registry updates, CI/CD, conformance test runner |

## Success Criteria

- **Phase 1:** Wallet can receive and present SD-JWT VCs from external issuers using did:key/did:jwk. SDK can issue and verify SD-JWT VCs on the SSDID network.
- **Phase 2:** Wallet passes OpenID Foundation conformance tests. External verifiers can request credentials via OpenID4VP. External issuers can issue via OpenID4VCI. OpenID4VP + OpenID4VCI certified.
- **Phase 3:** Wallet handles mdoc credentials. HAIP 1.0 certification obtained. SDK at v1.0 production-ready on npm + Maven Central + Swift Package Manager.
