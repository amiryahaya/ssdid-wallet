# SSDID SDK Design

## Goal

Standalone Rust SDK library that enables any app to request credentials from ssdid-wallet, authenticate users via DID, and issue credentials — delivered via deeplink (same device) or QR code (cross device). ssdid-drive is the first consumer; the SDK is reusable by any third-party app.

## Architecture

```
┌─────────────────────────────────────────────┐
│              ssdid-sdk (Rust)                │
├─────────────────────────────────────────────┤
│  CredentialRequest  — request claims         │
│  AuthRequest        — DID authentication     │
│  CredentialOffer    — issue credentials       │
│  Delivery           — deeplink / QR encode   │
│  ResponseParser     — parse VP token / result│
│  CallbackParser     — parse callback URI     │
└──────┬──────────┬──────────┬────────────────┘
    UniFFI      UniFFI     Native
       │          │          │
   Android     iOS       Tauri
   (Kotlin)  (Swift)    (Rust)
```

**The SDK does NOT:**
- Store credentials (ssdid-wallet's job)
- Handle crypto/signing (ssdid-wallet's job)
- Verify VP tokens (app backend's job)

## Key Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Language | Rust | Tauri is already Rust; UniFFI generates Kotlin/Swift; single codebase |
| Claim request format | DCQL | Simpler than Presentation Definition; wallet already supports it |
| Credential formats | SD-JWT VC + mdoc | Both supported by wallet; low cost on SDK side |
| Response delivery | direct_post to backend + deeplink callback with reference ID | Avoids URI size limits; backend is source of truth |
| Repository | Standalone `ssdid-sdk/` | Decoupled from wallet and consumers |
| Platform targets (v1) | Android + iOS + Tauri | All three ssdid-drive clients active |
| Scope (v1) | Auth + claim sharing + credential issuance | Full suite |

## Public API

### Credential Request (claim sharing)

```rust
let request = SsdidCredentialRequest::builder()
    .credential("IdentityCredential", CredentialFormat::SdJwt)
        .claim("name", ClaimPriority::Required)
        .claim("email", ClaimPriority::Optional)
        .done()
    .response_url("https://myapp.com/api/ssdid/response")
    .callback_scheme("myapp://ssdid/callback")
    .build()?;

let deeplink = request.to_deeplink();
let qr_string = request.to_qr_string();
let qr_png = request.to_qr_image(300)?;
```

### Authentication (DID challenge-response)

```rust
let auth = SsdidAuthRequest::builder()
    .challenge("random-challenge-from-server")
    .accepted_algorithms(&["Ed25519", "EcdsaP256"])
    .response_url("https://myapp.com/api/ssdid/auth/response")
    .callback_scheme("myapp://ssdid/auth/callback")
    .build()?;

let deeplink = auth.to_deeplink();
```

### Credential Issuance

```rust
let offer = SsdidCredentialOffer::builder()
    .issuer_url("https://myapp.com")
    .credential_type("EmployeeBadge", CredentialFormat::SdJwt)
    .pre_authorized_code("code-from-backend")
    .callback_scheme("myapp://ssdid/issuance/callback")
    .build()?;

let deeplink = offer.to_deeplink();
```

### Response Parsing (backend side)

```rust
let response = SsdidResponse::parse_vp_response(form_body)?;
let claims = response.disclosed_claims();
let holder_did = response.holder_did()?;
```

### Callback Parsing (client side)

```rust
let callback = SsdidCallback::parse(callback_uri)?;
match callback {
    SsdidCallback::Success { response_id } => { /* fetch claims from backend */ }
    SsdidCallback::Denied => { /* user declined */ }
    SsdidCallback::Error { code } => { /* handle error */ }
}
```

### Custom Extensions

Apps can add arbitrary parameters and use the DCQL builder directly:

```rust
// Extra params on the deeplink
let request = SsdidCredentialRequest::builder()
    .credential("IdentityCredential", CredentialFormat::SdJwt)
        .claim("name", ClaimPriority::Required)
        .done()
    .response_url("https://myapp.com/api/ssdid/response")
    .callback_scheme("myapp://ssdid/callback")
    .extra("tenant_id", "org-123")
    .extra("purpose", "kyc_verification")
    .build()?;

// Or raw DCQL builder for full control
let dcql = DcqlBuilder()
    .credential("IdentityCredential", CredentialFormat::SdJwt)
        .claim("name", ClaimPriority::Required)
        .done()
    .to_json();
```

## Data Flow

### Same-Device Credential Request

```
App (SDK)                    Backend                     ssdid-wallet
    │                            │                            │
    │ 1. Build request           │                            │
    │ 2. Open deeplink ──────────────────────────────────────>│
    │    ssdid://authorize?      │                            │ 3. Parse request
    │    dcql_query=...          │                            │ 4. Show consent
    │    &response_url=...       │                            │    ☑ name
    │    &callback_scheme=...    │                            │    ☐ email
    │                            │                            │
    │                            │<───── 5. direct_post ──────│ 5. Build VP token
    │                            │   vp_token=...             │
    │                            │                            │
    │                            │ 6. Store, return           │
    │                            │    response_id             │
    │                            │                            │
    │<───── 7. deeplink callback ─────────────────────────────│ 7. Redirect
    │    myapp://ssdid/callback? │                            │
    │    response_id=abc123      │                            │
    │    &status=success         │                            │
    │                            │                            │
    │ 8. GET /response/abc123 ──>│                            │
    │<── { name: "Ahmad" } ──────│                            │
```

### Cross-Device Credential Request

```
App (desktop)                Backend                     ssdid-wallet (phone)
    │                            │                            │
    │ 1. Build request           │                            │
    │ 2. Show QR ·····················································│ 3. Scan QR
    │                            │                            │ 4. Consent
    │                            │<───── 5. direct_post ──────│ 5. VP token
    │                            │ 6. Store response          │
    │ 6. SSE/poll <──────────────│                            │
    │ 7. GET /response/ ────────>│                            │
    │<── { name: "Ahmad" } ──────│                            │
```

## Deeplink Contract

### SDK → Wallet

| Flow | Scheme | Format |
|------|--------|--------|
| Credential Request | `ssdid://` | `ssdid://authorize?dcql_query={json}&response_url={url}&callback_scheme={scheme}&nonce={nonce}` |
| Authentication | `ssdid://` | `ssdid://authenticate?challenge={challenge}&accepted_algorithms={json_array}&response_url={url}&callback_scheme={scheme}` |
| Credential Offer | `openid-credential-offer://` | `openid-credential-offer://?credential_offer_uri={url}` |

### Wallet → App (callback)

| Status | Format |
|--------|--------|
| Success | `{callback_scheme}?response_id={id}&status=success` |
| Denied | `{callback_scheme}?status=denied` |
| Error | `{callback_scheme}?status=error&error={code}` |
| Issuance OK | `{callback_scheme}?status=success&credential_id={id}` |

### Wallet → Backend (direct_post)

```http
POST {response_url}
Content-Type: application/x-www-form-urlencoded

vp_token={vp_token}&presentation_submission={json}&nonce={nonce}
```

Backend returns `{"response_id": "abc123"}` → wallet redirects to callback.

### QR Code

Same URI as deeplink. QR encodes the full `ssdid://authorize?...` string.

## Crate Structure

```
ssdid-sdk/
├── Cargo.toml                    # workspace
├── crates/
│   ├── ssdid-sdk-core/           # pure Rust, no platform deps
│   │   ├── credential_request.rs
│   │   ├── auth_request.rs
│   │   ├── credential_offer.rs
│   │   ├── response_parser.rs
│   │   ├── delivery.rs
│   │   ├── dcql.rs
│   │   ├── callback.rs
│   │   └── error.rs
│   │
│   ├── ssdid-sdk-qr/            # QR image generation (optional)
│   │
│   └── ssdid-sdk-ffi/           # UniFFI bindings layer
│       ├── ssdid_sdk.udl
│       └── lib.rs
│
├── bindings/
│   ├── kotlin/                   # generated (UniFFI)
│   ├── swift/                    # generated (UniFFI)
│   └── wasm/                     # future
│
└── examples/
    ├── android/
    ├── ios/
    └── tauri/
```

### Dependencies

| Crate | Purpose |
|-------|---------|
| `serde` + `serde_json` | DCQL JSON serialization |
| `url` | URI encoding/validation |
| `qrcode` | QR code generation |
| `image` | QR to PNG bytes |
| `uniffi` | Kotlin/Swift binding generation |
| `base64` | Base64url encoding |

## Wallet Changes Required

Small changes in ssdid-wallet to support the SDK flow:

1. **New deeplink action**: `ssdid://authorize` with `dcql_query`, `response_url`, `callback_scheme` params
2. **Decline posts error**: `PresentationRequestViewModel.decline()` must call `transport.postError()` and redirect to `callback_scheme?status=denied`
3. **Success redirects**: After direct_post, read `response_id` from backend response and redirect to `callback_scheme?response_id={id}&status=success`
4. **Existing flows work as-is**: `ssdid://authenticate` and `openid-credential-offer://` already handled

## Error Handling

### SDK errors (build time)

| Error | When |
|-------|------|
| `InvalidConfig` | Missing required field |
| `InvalidUrl` | response_url not HTTPS, malformed callback scheme |
| `InvalidFormat` | Unrecognized credential format |
| `QrGenerationFailed` | Image encoding failure |
| `ResponseParseError` | Malformed VP token or JSON |

### Wallet callback errors (runtime)

| Status | Meaning |
|--------|---------|
| `no_matching_credentials` | Wallet has no matching credential |
| `invalid_request` | Malformed deeplink |
| `wallet_unavailable` | Internal wallet error |

## Testing Strategy

### Rust core (unit + integration)

- DCQL JSON generation matches spec
- Deeplink URI encoding correctness
- Callback parsing for all status variants
- Builder validation (missing fields → errors)
- Round-trip: build request → parse as wallet would → verify match

### Platform bindings

- Builder API → deeplink string → verify URI params (Kotlin + Swift)
- Tauri uses core directly (no FFI)

### End-to-end (example apps)

- Same-device: app → deeplink → wallet → callback → verify claims
- Cross-device: app → QR → scan → verify backend receives VP token
- Denial and error paths
