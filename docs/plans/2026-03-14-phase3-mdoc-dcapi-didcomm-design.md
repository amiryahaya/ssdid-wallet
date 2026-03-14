# Phase 3: mdoc, Digital Credentials API & DIDComm v2 Design

## Goal

Add mdoc/mDL (ISO 18013-5) credential support, register the wallet as a system credential provider via Digital Credentials API, and add DIDComm v2 peer-to-peer messaging.

## Context

Phase 2 added OpenID4VP (presentation) and OpenID4VCI (issuance) for SD-JWT VC credentials. Phase 3 extends the wallet to handle mdoc credentials (CBOR-based, digest-selective-disclosure), integrates with the OS credential management system, and adds encrypted messaging via DIDComm v2.

## Sequencing

```
3a: mdoc core (CBOR, MDoc model, MSO, storage)
  ↓
3b: mdoc via OpenID4VP/VCI (extend existing handlers)
  ↓
3c: Digital Credentials API (Android CredentialManager + iOS ASAuthorizationController)
  ↓
3d: DIDComm v2 (X25519, DID Document extensions, message pack/unpack)
```

Each phase is an independent PR. 3b depends on 3a. 3c depends on 3a+3b. 3d is independent but comes last per priority.

---

## Phase 3a: mdoc/mDL Core

### Architecture

New `domain/mdoc/` package on both platforms, parallel to `domain/sdjwt/`. The mdoc format (ISO 18013-5) uses CBOR encoding — a fundamentally different credential type from SD-JWT VC. Selective disclosure is digest-based: the Mobile Security Object (MSO) contains hashes of each data element, and the wallet reveals individual elements by including them alongside their digests.

### New Components

| Component | Purpose |
|-----------|---------|
| `CborCodec` | CBOR encode/decode using cbor-java (Android) / SwiftCBOR (iOS) |
| `MDoc` | Top-level credential model: `docType`, `issuerSigned`, `deviceSigned` |
| `IssuerSigned` | `nameSpaces` (data elements + digests) + `issuerAuth` (COSE_Sign1 wrapping MSO) |
| `MobileSecurityObject` | MSO: digest algorithm, value digests per namespace, device key, validity info |
| `DeviceAuth` | Device signature (`DeviceSignature` via COSE_Sign1) or MAC |
| `StoredMDoc` | Vault storage model: `id`, `docType`, `issuerSigned` (CBOR bytes), `deviceKeyId`, `issuedAt`, `expiresAt` |
| `MDocPresenter` | Selective disclosure: given requested elements, build `DeviceSigned` with only those elements |
| `MsoVerifier` | Verify issuer signature on MSO, check digest matches, check validity period |

### Dependencies

- **Android:** `com.upokecenter:cbor` (CBOR codec)
- **iOS:** `SwiftCBOR` (SPM) or Foundation built-in CBOR support

### Credential Storage

Extend `VaultStorage` with:
- `saveMDoc(mdoc: StoredMDoc)`
- `listMDocs(): List<StoredMDoc>`
- `getMDoc(id: String): StoredMDoc?`
- `deleteMDoc(id: String)`

Store CBOR bytes directly (not JSON-serialized), since mdoc is inherently binary.

### What's NOT in 3a

- No OpenID4VP integration (3b)
- No Digital Credentials API (3c)
- No BLE/NFC proximity presentation (out of scope — online only per ISO 18013-7)

---

## Phase 3b: mdoc via OpenID4VP/VCI

### Architecture

Extend existing `domain/oid4vp/` and `domain/oid4vci/` packages to handle mdoc alongside SD-JWT VC. No new packages — new format branches in existing components.

### OpenID4VP Changes (Presentation)

Per ISO 18013-7 Annex C, mdoc presentation via OpenID4VP uses `vp_token` containing a CBOR-encoded `DeviceResponse`.

| Component | Change |
|-----------|--------|
| `PresentationDefinitionMatcher` | Add mdoc format: `"mso_mdoc": {}`, match by `docType` |
| `DcqlMatcher` | Add mdoc format: `"mso_mdoc"`, match `doctype_value` |
| `MDocVpTokenBuilder` | New — builds `DeviceResponse` CBOR with selective disclosure + `DeviceAuth` signature |
| `OpenId4VpHandler` | Branch on credential type: SD-JWT → `VpTokenBuilder`, mdoc → `MDocVpTokenBuilder` |
| `SessionTranscript` | New — CBOR array `[clientId, responseUri, nonce]` per ISO 18013-7 §9.1 |

### OpenID4VCI Changes (Issuance)

| Component | Change |
|-----------|--------|
| `CredentialRequestBuilder` | Support `format: "mso_mdoc"` + `doctype` field |
| `OpenId4VciHandler` | Branch on response: CBOR content-type → parse as mdoc, store via `saveMDoc()` |

### MatchResult Refactor

```kotlin
sealed class CredentialRef {
    data class SdJwt(val credential: StoredSdJwtVc) : CredentialRef()
    data class MDoc(val credential: StoredMDoc) : CredentialRef()
}
```

`MatchResult.credential` becomes `MatchResult.credentialRef: CredentialRef`.

---

## Phase 3c: Digital Credentials API

### Architecture

Register the SSDID wallet as a system-level credential provider so the OS can broker credential requests from apps/browsers without deep links.

### Android — CredentialManager API

| Component | Purpose |
|-----------|---------|
| `SsdidCredentialProviderService` | Extends `CredentialProviderService`. Handles `BeginGetCredentialRequest` and `GetCredentialRequest` |
| `SsdidCredentialEntry` | Builds `CredentialEntry` for each matching credential |
| `AndroidManifest.xml` | Register provider service + `credential-provider` metadata XML |

**Flow:**
1. Browser/app calls `CredentialManager.getCredential()` with OpenID4VP request
2. Android discovers SSDID wallet as provider
3. `BeginGetCredentialRequest` → wallet returns matching credential entries
4. User picks credential → `GetCredentialRequest` → wallet builds VP token
5. Response returned to calling app via system

### iOS — ASAuthorizationController

| Component | Purpose |
|-----------|---------|
| `SsdidCredentialProvider` | Conforms to `ASAuthorizationProvider`. Handles `ASAuthorizationCredentialRequest` |
| `Info.plist` | Register credential provider entitlement |

**Note:** iOS Digital Credentials API is newer — design for it but mark as experimental.

### Shared Logic

Both platforms reuse matching + token building from `domain/oid4vp/`. The DC API is a different entry point — the OS mediates instead of deep links.

---

## Phase 3d: DIDComm v2 + DID Document Extensions

### Architecture

New `domain/didcomm/` package. Adds peer-to-peer encrypted messaging.

### DID Document Extensions

| Field | Change |
|-------|--------|
| `keyAgreement` | New — list of key IDs for encryption (X25519 or ML-KEM) |
| `service` | New — list of `Service` objects: `{id, type, serviceEndpoint}` |

Both Android and iOS `DidDocument` models updated.

### Key Agreement

| Component | Purpose |
|-----------|---------|
| `KeyAgreementProvider` | New interface: `generateKeyAgreementPair()`, `deriveSharedSecret()` |
| `X25519Provider` | X25519 ECDH via BouncyCastle (Android) / CryptoKit (iOS) |
| Algorithm enum | Add `X25519` variant (key agreement, not signing) |

### DIDComm v2 Messaging

| Component | Purpose |
|-----------|---------|
| `DIDCommMessage` | Data class: `id`, `type`, `from`, `to`, `body`, `attachments` |
| `DIDCommPacker` | Encrypt: resolve recipient DID → get `keyAgreement` key → ECDH → AES-GCM wrap |
| `DIDCommUnpacker` | Decrypt: identify recipient key → ECDH → unwrap |
| `DIDCommTransport` | HTTP POST to service endpoint |

### Scope Constraints (YAGNI)

- Authcrypt only (authenticated encryption) — no anoncrypt
- HTTP transport only — no WebSocket, no Bluetooth
- No routing/mediator — direct peer-to-peer only
- No DIDComm protocols beyond basic messaging (OpenID4VP/VCI handles credential exchange)

---

## Reused Components

| Component | From |
|-----------|------|
| `CryptoProvider` (signing) | `domain/crypto/` |
| `StoredSdJwtVc`, `SdJwtParser` | `domain/sdjwt/` |
| `Vault`, `VaultImpl`, `VaultStorage` | `domain/vault/` |
| `DidDocument`, `Did` | `domain/model/` |
| `OpenId4VpHandler`, `OpenId4VciHandler` | `domain/oid4vp/`, `domain/oid4vci/` |
| `PresentationDefinitionMatcher`, `DcqlMatcher` | `domain/oid4vp/` |
| Deep link routing pattern | `platform/deeplink/` |

## Modifications to Existing Files

| File | Change |
|------|--------|
| `DidDocument.kt/.swift` | Add `keyAgreement: List<String>`, `service: List<Service>` |
| `Algorithm.kt/.swift` | Add `X25519` variant |
| `VaultStorage` | Add mdoc CRUD methods |
| `Vault` interface | Add `saveMDoc()`, `listMDocs()`, `getMDoc()`, `deleteMDoc()` |
| `PresentationDefinitionMatcher` | Add `mso_mdoc` format matching |
| `DcqlMatcher` | Add `mso_mdoc` format matching |
| `MatchResult` | Refactor to use `CredentialRef` sealed class |
| `OpenId4VpHandler` | Branch on credential format for token building |
| `OpenId4VciHandler` | Branch on response format for mdoc parsing |
| `AndroidManifest.xml` | Register `CredentialProviderService` |
| iOS `Info.plist` | Register credential provider entitlement |
| `build.gradle.kts` | Add CBOR + CredentialManager dependencies |

## Security

- COSE_Sign1 signature verification on all MSO structures
- Digest validation: each revealed data element must hash-match MSO digest
- SessionTranscript binding prevents mdoc replay across sessions
- Device key proof prevents credential cloning
- X25519 shared secrets zeroed after use
- DIDComm authcrypt: sender authenticated, no anonymous messages

## Success Criteria

- Wallet can store, present, and receive mdoc/mDL credentials
- mdoc presentation works via OpenID4VP (ISO 18013-7 online flow)
- Wallet appears as credential provider in Android system credential picker
- DIDComm v2 messages can be sent/received between two SSDID wallets
- HAIP 1.0 certification requirements met (SD-JWT VC + mdoc + DC API)
