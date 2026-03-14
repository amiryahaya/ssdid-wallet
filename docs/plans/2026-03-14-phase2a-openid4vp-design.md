# Phase 2a: OpenID4VP + Presentation Exchange Design

## Goal

Add OpenID for Verifiable Presentations (OpenID4VP) 1.0 support to the SSDID Wallet, replacing the custom `ssdid://authenticate` flow with the standard protocol for credential presentation. Supports both Presentation Exchange 2.0 and DCQL query languages. SSDID-network services become OpenID4VP verifiers alongside external verifiers.

## Protocol Migration Strategy

**Hybrid approach:** Migrate authentication/verification to OpenID4VP. Keep custom protocol for operations with no standard equivalent.

| Flow | Protocol | Rationale |
|------|----------|-----------|
| Credential presentation/verification | **`openid4vp://`** (NEW) | Standard protocol, replaces `ssdid://authenticate` |
| DID registration with registry | `ssdid://register` (KEEP) | No standard exists |
| Key rotation | `ssdid://` (KEEP) | No standard exists |
| Device pairing | `ssdid://` (KEEP) | No standard exists |
| Invitation acceptance | `ssdid://invite` (KEEP) | No standard exists; DIDComm v2 deferred to Phase 3 |
| Transaction signing | `ssdid://sign` (KEEP) | No standard exists |
| Credential issuance | `ssdid://credential-offer` → **`openid-credential-offer://`** (Phase 2b) | OpenID4VCI standard, deferred |

**Migration path for `ssdid://authenticate`:**
1. Wallet adds OpenID4VP support (this phase)
2. SSDID-network backend services updated to send OpenID4VP Authorization Requests (parallel backend work)
3. `ssdid://authenticate` deprecated but kept for backward compatibility during transition
4. Eventually removed once all services migrated

## Context

### What Phase 1 Delivered
- SD-JWT VC: parse, create, selective disclosure, verify, key binding
- Multi-method DID resolution (did:ssdid, did:key, did:jwk)
- Verifiable Presentation model
- Credential store (VC + SD-JWT VC)
- Security hardening (biometric gating, cert pinning)
- VCDM 2.0 context migration

### What Phase 2a Adds
- OpenID4VP 1.0 protocol (same-device + cross-device flows)
- Presentation Exchange 2.0 + DCQL credential matching
- VP Token building with KB-JWT
- Extended consent screen with PE/DCQL-driven claim toggles
- `openid4vp://` deep link scheme (coexists with `ssdid://`)

### What's Deferred to Phase 2b
- OpenID4VCI 1.0 (credential issuance)
- Pairwise DIDs (per-verifier did:key derivation)
- Trust registry (issuer allowlist/denylist)
- Consent audit trail
- `client_id_scheme` beyond HTTPS URL and DID

## Architecture

### Layered Protocol Engine

```
┌─────────────────────────────────────────────┐
│  UI Layer                                    │
│  ConsentScreen (extended) + OpenID4VP route  │
├─────────────────────────────────────────────┤
│  Protocol Layer                              │
│  OpenId4VpHandler — orchestrates the flow    │
├──────────────┬──────────────────────────────┤
│  Query Layer │  Credential Matcher           │
│  PE 2.0 +   │  Matches stored VCs +         │
│  DCQL parse  │  identity claims against      │
│              │  input_descriptors / DCQL     │
├──────────────┴──────────────────────────────┤
│  Transport Layer                             │
│  Fetch request_uri, POST response to         │
│  response_uri, validate client_id            │
└─────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility |
|-----------|---------------|
| `OpenId4VpTransport` | Fetch request object from `request_uri`, POST VP Token to `response_uri` |
| `AuthorizationRequestParser` | Parse + validate Authorization Request (client_id, response_type, nonce, presentation_definition or dcql_query) |
| `PresentationDefinitionMatcher` | Parse PE 2.0 `input_descriptors`, match against stored credentials + identity claims |
| `DcqlMatcher` | Parse DCQL query, match credentials + identity claims |
| `CredentialQuery` | Normalized query model bridging PE 2.0 and DCQL |
| `VpTokenBuilder` | Build VP Token: select disclosures, create KB-JWT, assemble SD-JWT presentation |
| `PresentationSubmission` | Build `presentation_submission` response for PE 2.0 |
| `OpenId4VpHandler` | Orchestrator: transport → parse → match → (UI consent) → build → submit |
| `ConsentScreen` (extended) | Show matched claims from PE/DCQL, claim toggles, verifier identity |

### File Structure (both platforms)

```
domain/oid4vp/
  ├── AuthorizationRequest.kt/.swift
  ├── OpenId4VpHandler.kt/.swift
  ├── OpenId4VpTransport.kt/.swift
  ├── PresentationDefinitionMatcher.kt/.swift
  ├── DcqlMatcher.kt/.swift
  ├── CredentialQuery.kt/.swift
  ├── VpTokenBuilder.kt/.swift
  └── PresentationSubmission.kt/.swift
```

## Protocol Flows

### Same-Device Flow

1. User taps link in web app → `openid4vp://` deep link fires
2. Wallet receives Authorization Request (by value or `request_uri` reference)
3. If `request_uri` present, wallet fetches request object via HTTPS GET
4. Wallet parses Presentation Definition or DCQL query
5. Wallet matches stored credentials + identity claims against requested claims
6. Consent screen shows matched claims with required/optional toggles
7. User approves → Wallet builds VP Token (SD-JWT VC + selected disclosures + KB-JWT)
8. Wallet POSTs response to `response_uri` (direct_post mode)
9. Wallet redirects user back via `redirect_uri` (if provided)

### Cross-Device Flow

1. User scans QR code containing `openid4vp://` URI (existing QR scanner)
2. Steps 2–8 same as above
3. No redirect — wallet shows "Presentation shared" confirmation

## Authorization Request

### By Value

```
openid4vp://?response_type=vp_token
  &client_id=https://verifier.example.com
  &nonce=abc123
  &response_mode=direct_post
  &response_uri=https://verifier.example.com/response
  &presentation_definition={...}
```

### By Reference

```
openid4vp://?client_id=https://verifier.example.com
  &request_uri=https://verifier.example.com/request/xyz
```

### Validation Rules

1. `response_type` MUST be `vp_token`
2. `client_id` MUST be present — validated as HTTPS URL or DID
3. `nonce` MUST be present — bound into KB-JWT
4. `response_mode` MUST be `direct_post` (only mode supported initially)
5. `response_uri` MUST be present and HTTPS
6. Either `presentation_definition` or `dcql_query` MUST be present (not both)
7. If `request_uri` present, fetch and validate the returned request object

## Query Languages

### Presentation Exchange 2.0

```json
{
  "id": "example-request",
  "input_descriptors": [
    {
      "id": "employee-credential",
      "format": { "vc+sd-jwt": { "alg": ["EdDSA", "ES256"] } },
      "constraints": {
        "fields": [
          { "path": ["$.vct"], "filter": { "const": "VerifiedEmployee" } },
          { "path": ["$.name"], "optional": false },
          { "path": ["$.department"], "optional": true }
        ]
      }
    }
  ]
}
```

### DCQL

```json
{
  "credentials": [
    {
      "id": "employee-credential",
      "format": "vc+sd-jwt",
      "meta": { "vct_values": ["VerifiedEmployee"] },
      "claims": [
        { "path": ["name"] },
        { "path": ["department"], "optional": true }
      ]
    }
  ]
}
```

### Credential Matching

1. Parse query (PE or DCQL) into normalized `CredentialQuery` model
2. Load stored credentials (VC + SD-JWT VC) from vault AND identity attributes
3. For each descriptor/credential entry:
   - Match by format (`vc+sd-jwt`)
   - Match by type (`vct` for SD-JWT VC, `type` for VC)
   - Match field constraints against available claims
4. Identity attributes (name, email, etc.) are searched as a virtual credential source
5. Return `MatchResult`: matched credentials with available/required/optional claims
6. If no match → POST `error=no_credentials_available` to `response_uri`

### Consent Screen Mapping

- Each matched credential becomes a selectable card
- Required fields: locked toggles (always shared)
- Optional fields: toggleable checkboxes (user choice)
- Claims grouped by source (SD-JWT VC vs identity attributes)
- Verifier identity (`client_id`) shown prominently

## VP Token Building

### Construction (SD-JWT VC)

1. Take stored SD-JWT VC (issuer JWT + all disclosures)
2. Filter disclosures to user-approved claims only
3. Create KB-JWT:
   - `aud` = `client_id` from Authorization Request
   - `nonce` = `nonce` from Authorization Request
   - `iat` = current timestamp
   - `sd_hash` = SHA-256 of SD-JWT + selected disclosures
4. Assemble: `<issuer-jwt>~<disc1>~<disc2>~<kb-jwt>`

### Response (PE 2.0)

```
POST /response HTTP/1.1
Content-Type: application/x-www-form-urlencoded

vp_token=<sd-jwt-presentation>
&presentation_submission={"id":"...","definition_id":"...","descriptor_map":[...]}
&state=<state-from-request>
```

### Response (DCQL)

```
POST /response HTTP/1.1
Content-Type: application/x-www-form-urlencoded

vp_token=<sd-jwt-presentation>
&state=<state-from-request>
```

### Error Handling

- Network failure on POST → retry once, then show error with "Try Again"
- Verifier returns error → display verifier's error message
- User declines → POST `error=access_denied` to `response_uri`

## Platform Integration

### Deep Link Registration (Coexistence)

- Android `AndroidManifest.xml`: Add `<intent-filter>` for `openid4vp://` scheme alongside existing `ssdid://`
- iOS `Info.plist`: Add `openid4vp` to `CFBundleURLSchemes` alongside existing `ssdid`
- Both `DeepLinkHandler` classes: Add `openid4vp` parsing, route to OpenID4VP flow
- Existing `ssdid://` flow completely untouched

### QR Code Scanning

- Existing `ScanQrScreen` already decodes arbitrary URLs — no changes needed
- QR content containing `openid4vp://...` routes through `DeepLinkHandler`

### Backward Compatibility & Migration

- `ssdid://register`, `ssdid://sign`, `ssdid://invite` — unchanged, kept permanently
- `ssdid://authenticate` — deprecated but kept during transition; wallet supports both paths
- `ConsentScreen` operates in two modes:
  1. Legacy mode: `requestedClaims` string from `ssdid://` (existing behavior, deprecated)
  2. OpenID4VP mode: `CredentialQuery` from PE/DCQL matching (new standard path)
- SSDID-network services will migrate to sending `openid4vp://` requests (parallel backend work, not part of this wallet plan)

## Testing Strategy

### Shared Test Vectors

```
test-vectors/oid4vp/
  ├── authorization-request/     — Parse + validate request URIs
  ├── presentation-exchange/     — PE 2.0 matching scenarios
  ├── dcql/                      — DCQL matching scenarios
  └── vp-token/                  — VP Token construction + KB-JWT
```

### Unit Tests Per Layer

- `AuthorizationRequestParser`: valid/invalid URIs, by-value vs by-reference, missing fields
- `PresentationDefinitionMatcher`: format matching, type matching, field constraints, optional fields
- `DcqlMatcher`: credential matching, claim filtering, optional claims
- `VpTokenBuilder`: disclosure selection, KB-JWT nonce/aud binding, sd_hash
- `OpenId4VpTransport`: mock HTTP for request_uri fetch and response POST

### Conformance

- Run OpenID Foundation conformance test suite during development
- Target self-certification for OpenID4VP 1.0

## Key Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Response mode | `direct_post` only | Simplest, most common, conformance-sufficient |
| Query languages | PE 2.0 + DCQL | Both required for conformance |
| client_id validation | HTTPS URL + DID | Covers common cases; X.509 deferred |
| Credential sources | SD-JWT VC + identity claims | Preserves existing user claims alongside new format |
| ConsentScreen | Extend existing, dual-mode | No breaking changes, code reuse |
| Deep link | Coexist ssdid:// + openid4vp:// | No breaking changes |
| Auth migration | ssdid://authenticate deprecated, OpenID4VP replaces | One verification protocol to maintain and certify |
| Invitation flow | Keep ssdid://invite | No standard exists; DIDComm v2 deferred |

## References

- [OpenID4VP 1.0 Specification](https://openid.net/specs/openid-4-verifiable-presentations-1_0.html)
- [OpenID Foundation Self-Certification (Feb 2026)](https://openid.net/openid-for-verifiable-credential-self-certification-to-launch-feb-2026/)
- [DIF Presentation Exchange 2.0](https://identity.foundation/presentation-exchange/)
- [HAIP 1.0 Editor's Draft](https://openid.github.io/OpenID4VC-HAIP/openid4vc-high-assurance-interoperability-profile-wg-draft.html)
