# OpenID4VCI 1.0 Implementation Design (Phase 2b)

## Goal

Enable the SSDID wallet to receive verifiable credentials from standard OpenID4VCI-compliant issuers, supporting both pre-authorized code and authorization code flows.

## Context

Phase 2a added OpenID4VP (presentation). Phase 2b adds the issuance counterpart. The wallet acts as an OAuth 2.0 client receiving credentials, versus Phase 2a where it responded to verifier requests.

## Architecture

### Approach: Mirror OpenID4VP Package Structure

New `domain/oid4vci/` package parallel to `domain/oid4vp/`, using the same handler → transport → builder pattern. Reuses existing crypto, vault, SD-JWT, and DID resolution.

### Flow

```
openid-credential-offer:// deep link
    → CredentialOfferParser (parse URI, fetch by-reference)
    → IssuerMetadataResolver (fetch .well-known, cache)
    → CredentialOfferScreen (user reviews what's being offered)
    → TokenClient (pre-auth code or auth code + PKCE → access_token + c_nonce)
    → ProofJwtBuilder (build openid4vci-proof+jwt with DID key)
    → OpenId4VciTransport (POST /credential endpoint)
    → Vault.storeStoredSdJwtVc() (persist received credential)
```

### Two Issuance Flows

#### Pre-Authorized Code Flow (simpler, in-person)
1. Issuer authenticates user out-of-band
2. Credential Offer contains pre-authorized code (+ optional TX code/PIN)
3. Wallet exchanges code directly at token endpoint (no browser)
4. Wallet requests credential with proof of possession

#### Authorization Code Flow (online, browser-based)
1. Credential Offer contains `issuer_state`
2. Wallet launches browser to authorization endpoint (PKCE required)
3. User authenticates at issuer
4. Redirect back with auth code
5. Wallet exchanges code + PKCE verifier at token endpoint
6. Wallet requests credential with proof of possession

## New Files

### Android (`domain/oid4vci/`)

| File | Purpose |
|---|---|
| `CredentialOffer.kt` | Data class: issuer URL, credential config IDs, grants |
| `CredentialOfferParser.kt` | Parse `openid-credential-offer://` URI (by-value and by-reference) |
| `IssuerMetadata.kt` | Data class: endpoints, supported credential configs, display |
| `IssuerMetadataResolver.kt` | Fetch + cache `.well-known/openid-credential-issuer` and OAuth AS metadata |
| `TokenClient.kt` | Token endpoint: pre-authorized code and authorization code + PKCE |
| `NonceManager.kt` | `c_nonce` lifecycle: store, check expiry, refresh on `invalid_proof` |
| `ProofJwtBuilder.kt` | Build `openid4vci-proof+jwt` signed with wallet's DID key |
| `CredentialRequestBuilder.kt` | Build credential endpoint request body |
| `OpenId4VciTransport.kt` | HTTP client: metadata, token, credential, deferred, nonce endpoints |
| `OpenId4VciHandler.kt` | Orchestrator: processOffer() and acceptOffer() |
| `DeferredCredentialPoller.kt` | Background polling with exponential backoff |

### Android UI (`feature/credentials/`)

| File | Purpose |
|---|---|
| `CredentialOfferScreen.kt` | Review offer: issuer info, credential types, accept/decline |
| `CredentialOfferViewModel.kt` | State machine for issuance flow |

### iOS (`Domain/OpenId4Vci/`)

Mirrors Android: `CredentialOffer.swift`, `CredentialOfferParser.swift`, `IssuerMetadata.swift`, `IssuerMetadataResolver.swift`, `TokenClient.swift`, `NonceManager.swift`, `ProofJwtBuilder.swift`, `CredentialRequestBuilder.swift`, `OpenId4VciTransport.swift`, `OpenId4VciHandler.swift`, `DeferredCredentialPoller.swift`

## Component Design

### CredentialOfferParser

Two transmission modes:
- By value: `openid-credential-offer://?credential_offer=<URL-encoded JSON>`
- By reference: `openid-credential-offer://?credential_offer_uri=<HTTPS URL>`

Validation:
- Issuer URL must be HTTPS
- At least one `credential_configuration_id`
- At least one grant type present
- `credential_offer_uri` must be HTTPS (SSRF protection)

### Credential Offer Structure (per spec)

```json
{
  "credential_issuer": "https://issuer.example.com",
  "credential_configuration_ids": ["UniversityDegree_SD_JWT"],
  "grants": {
    "urn:ietf:params:oauth:grant-type:pre-authorized_code": {
      "pre-authorized_code": "SplxlOBeZQQYbYS6WxSbIA",
      "tx_code": {
        "input_mode": "numeric",
        "length": 6,
        "description": "Enter the PIN sent to your email"
      }
    },
    "authorization_code": {
      "issuer_state": "eyJhbGciOiJ..."
    }
  }
}
```

### IssuerMetadataResolver

Two-stage fetch:
1. `GET {issuer}/.well-known/openid-credential-issuer` → credential configs, endpoints
2. `GET {auth_server}/.well-known/oauth-authorization-server` → token + authorize endpoints

In-memory cache (per-session, not persistent).

### TokenClient

```kotlin
// Pre-authorized code
fun exchangePreAuthorizedCode(
    tokenEndpoint: String,
    preAuthorizedCode: String,
    txCode: String?
): TokenResponse

// Authorization code + PKCE
fun exchangeAuthorizationCode(
    tokenEndpoint: String,
    code: String,
    codeVerifier: String,
    redirectUri: String
): TokenResponse
```

Token response includes `access_token`, `c_nonce`, `c_nonce_expires_in`.

### NonceManager

```kotlin
class NonceManager {
    fun update(nonce: String, expiresIn: Int)
    fun current(): String?
    fun isExpired(): Boolean
}
```

Updated from token response and credential response. On `invalid_proof` error, extract fresh nonce from error body, update, retry once.

### ProofJwtBuilder

Creates `openid4vci-proof+jwt`:
- Header: `typ=openid4vci-proof+jwt`, `alg` (mapped from wallet algorithm), `kid` (DID URL)
- Payload: `iss` (wallet DID), `aud` (issuer URL), `iat`, `nonce` (from c_nonce)
- Signed with wallet's private key via existing `CryptoProvider`

### OpenId4VciHandler (Orchestrator)

```kotlin
class OpenId4VciHandler(
    private val offerParser: CredentialOfferParser,
    private val metadataResolver: IssuerMetadataResolver,
    private val tokenClient: TokenClient,
    private val nonceManager: NonceManager,
    private val proofBuilder: ProofJwtBuilder,
    private val transport: OpenId4VciTransport,
    private val vault: Vault
) {
    suspend fun processOffer(uri: String): OfferReviewResult
    suspend fun acceptOffer(
        offer: CredentialOffer,
        metadata: IssuerMetadata,
        selectedConfigIds: List<String>,
        txCode: String?
    ): IssuanceResult
}
```

### DeferredCredentialPoller

- Exponential backoff: 5s, 10s, 20s, 40s, max 5 minutes between polls
- Max attempts: 12 (~1 hour coverage)
- Returns credential when ready, or `IssuanceFailed` after max attempts

### Authorization Code Flow (Browser)

1. Generate PKCE `code_verifier` (43-128 chars, URL-safe random) + `code_challenge` (S256)
2. Launch Chrome Custom Tab (Android) / SFSafariViewController (iOS) to authorization endpoint
3. Capture redirect via registered callback URL scheme
4. Exchange code + verifier at token endpoint

## State Machine (ViewModel)

```
Idle
  → Parsing
  → FetchingMetadata
  → ReviewingOffer
  → (user accepts)
  → Authenticating (auth code flow only — browser launch)
  → ExchangingToken
  → RequestingCredential
  → (invalid_proof → refresh nonce → retry once)
  → RequestingCredential
  → Success (credential stored in vault)
  → Deferred (polling background) → Success | Failed
  → Error (with retry option)
```

## Modifications to Existing Files

| File | Change |
|---|---|
| `AndroidManifest.xml` | Add `openid-credential-offer://` intent filter |
| `DeepLinkHandler.kt` | Route `openid-credential-offer://` scheme to CredentialOfferScreen |
| `Screen.kt` | Add `CredentialOffer` screen route |
| `NavGraph.kt` | Add navigation entry for CredentialOfferScreen |
| `AppModule.kt` | Provide `OpenId4VciHandler` and dependencies via Hilt |
| `StoredSdJwtVc.kt` | Add `issuanceSource: IssuanceSource` field |
| iOS `Info.plist` | Add `openid-credential-offer` URL scheme |

## Credential Storage

Received SD-JWT VCs stored using existing `StoredSdJwtVc`. Add `issuanceSource` to track provenance:

```kotlin
enum class IssuanceSource { Manual, OpenId4Vci, Ssdid }
```

No other vault changes needed — `storeStoredSdJwtVc()` already exists.

## Error Handling

| Error | Action |
|---|---|
| `invalid_proof` | Extract `c_nonce` from error body, rebuild proof, retry once |
| `issuance_pending` | Start deferred poller |
| `invalid_grant` | Pre-auth code expired — inform user, offer restart |
| Network errors | Show retry button, preserve state |
| HTTPS validation failure | Reject (same pattern as OpenID4VP) |

## Security

- HTTPS required on all issuer URLs
- `credential_offer_uri` must be HTTPS (SSRF protection)
- PKCE required for authorization code flow (S256 only)
- Proof nonce prevents replay
- Browser auth uses system browser (no WebView — prevents credential phishing)

## What's NOT in Phase 2b

- mdoc/mDL credential format (Phase 3)
- Batch credential endpoint
- Digital Credentials API (Phase 3)
- DIDComm v2 (Phase 3)
- Notification endpoint (low priority, can add later)

## Reused Components

| Component | From |
|---|---|
| `CryptoProvider` (signing) | `domain/crypto/` |
| `StoredSdJwtVc`, `SdJwtParser` | `domain/sdjwt/` |
| `Vault`, `VaultImpl` | `domain/vault/` |
| `DidDocument`, `Did` | `domain/model/` |
| `SsdidHttpClient`, OkHttp stack | `domain/transport/` |
| Deep link routing pattern | `platform/deeplink/` |
| Handler/transport/builder patterns | `domain/oid4vp/` |
