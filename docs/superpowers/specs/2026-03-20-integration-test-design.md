# Wallet ↔ Registry Integration Test Design

**Date:** 2026-03-20
**Target:** End-to-end integration tests between ssdid-wallet and the live SSDID Registry at https://registry.ssdid.my
**Goal:** Verify that all hardening, enhancements, and production-readiness work actually works across the wire.

## Test Registry

Tests run against: `https://registry.ssdid.my`
- Health: `/health/ready`
- Protocol version: `1.0`
- 19 supported algorithms
- Rate limiting active (10 creates/hour, 60 challenges/min)

## Test Architecture

```
┌──────────────────────────┐         ┌──────────────────────────┐
│   Integration Test       │         │   Live Registry          │
│   (Android/iOS)          │         │   registry.ssdid.my      │
│                          │  HTTPS  │                          │
│   Real CryptoProvider    ├────────▶│   Real Mnesia storage    │
│   Real Vault             │         │   Real challenge system  │
│   Real SsdidClient       │         │   Real rate limiting     │
│   Real KeyRotationMgr    │         │   Real proof verification│
│                          │         │                          │
└──────────────────────────┘         └──────────────────────────┘
```

Tests use REAL crypto (not mocks) and REAL HTTP calls. This verifies:
- Signing payload format matches registry expectations
- Multibase encoding is compatible
- DID document schema is accepted
- Proof verification passes end-to-end
- Challenge lifecycle works (create → sign → verify → expire)

## Test Flows

### Flow 1: Identity Lifecycle (Critical)

```
1. Create identity with Ed25519
2. Register DID with registry → assert 201
3. Resolve DID document → assert matches local document
4. Verify public key in resolved document matches local key
5. Update DID document (add nextKeyHash) → assert 200
6. Resolve again → assert nextKeyHash present
7. Deactivate DID → assert 200
8. Resolve deactivated DID → assert 410 Gone or empty
```

**Algorithms to test:** Ed25519, ECDSA P-256, KAZ-Sign-128 (one classical, one standard, one PQC)

### Flow 2: Key Rotation Ceremony (Critical)

```
1. Create identity and register DID
2. prepareRotation() → generates nextKeyHash, publishes to registry
3. Resolve DID → assert nextKeyHash present in verification method
4. executeRotation() → promotes pre-committed key, publishes new document
5. Resolve DID → assert new public key matches pre-committed hash
6. Sign data with NEW key → verify signature against resolved DID document
7. Attempt rotation again immediately → assert 429 (rate limit, max 1/hour)
```

### Flow 3: Challenge Lifecycle (High)

```
1. Create and register identity
2. Request challenge → assert challenge string + domain + expires_at
3. Sign challenge with identity key
4. Use signed challenge in a DID document UPDATE → assert accepted
5. Reuse SAME challenge → assert rejected (replay protection)
6. Request new challenge, wait > proof_max_age (300s) → assert expired
   (Note: this test takes 5+ minutes — mark as slow/optional)
```

### Flow 4: Multi-Algorithm Compatibility (High)

```
For each algorithm in [Ed25519, ECDSA_P256, ECDSA_P384, KAZ_SIGN_128, KAZ_SIGN_192, ML_DSA_44]:
  1. Generate keypair
  2. Build DID document with algorithm-specific verification method
  3. Sign and register with registry → assert accepted
  4. Resolve → assert algorithm type matches
  5. Deactivate (cleanup)
```

### Flow 5: Rate Limiting Verification (High)

```
1. Create and register 10 identities rapidly (within 1 minute)
2. 11th creation → assert 429 Too Many Requests
3. Assert response includes Retry-After header
4. Assert RFC 7807 error format: { type, title, status, detail }
```

### Flow 6: DID Validation End-to-End (Medium)

```
1. Attempt to register DID with malformed format → assert 422
2. Attempt to register DID with too-short ID → assert 422
3. Attempt to register with unsupported algorithm type → assert 422
4. Attempt to update someone else's DID (wrong key) → assert 401
5. Attempt to update with expired challenge → assert 401
```

### Flow 7: Proof Verification Cross-Check (Critical)

```
1. Create identity locally (wallet generates keypair)
2. Build DID document
3. Get challenge from registry
4. Create proof: SHA3-256(canonical_json(proof_options)) || SHA3-256(canonical_json(document))
5. Sign with private key → multibase encode
6. Submit to registry → assert 201 (proof accepted)
7. This verifies: canonical JSON, SHA3-256, signing payload, multibase encoding ALL match
```

### Flow 8: Grace Period After Rotation (Medium)

```
1. Create identity, register, prepare rotation
2. Execute rotation → old key should enter grace period (1 hour)
3. Immediately: sign with OLD key → assert still accepted (within grace)
4. Sign with NEW key → assert accepted
5. (Optional long test): wait 1 hour, sign with old key → assert rejected
```

### Flow 9: DID Document Schema Compliance (Medium)

```
1. Register DID with all required fields
2. Resolve → verify W3C DID 1.1 compliance:
   - @context includes "https://www.w3.org/ns/did/v1"
   - id matches registered DID
   - verificationMethod has required fields (id, type, controller, publicKeyMultibase)
   - authentication, assertionMethod, capabilityInvocation reference valid method IDs
3. Update with nextKeyHash → verify schema still valid
4. Update with multiple verification methods → verify all present
```

### Flow 10: Error Response Format (Medium)

```
1. Trigger various errors and verify RFC 7807 format:
   - 401: { type, title: "Unauthorized", status: 401, detail: "..." }
   - 404: { type, title: "Not Found", ... }
   - 422: { type, title: "Unprocessable Entity", ... }
   - 429: { type, title: "Too Many Requests", ... } + Retry-After header
2. Verify Content-Type: application/problem+json
```

## Test Implementation Plan

### Android

File: `android/app/src/test/java/my/ssdid/wallet/integration/WalletRegistryIntegrationTest.kt`

- Use real `ClassicalProvider`, `PqcProvider` (Robolectric for crypto)
- Use real `SsdidHttpClient` pointed at `https://registry.ssdid.my`
- Use `FakeVaultStorage` (in-memory) for local vault operations
- Mock `KeystoreManager` (Robolectric can't do hardware keystore)
- Mark all tests with `@Tag("integration")` — exclude from default CI run
- Run on demand: `./gradlew :app:testDebugUnitTest --tests "*.WalletRegistryIntegrationTest"`

### iOS

File: `ios/SsdidWalletTests/Integration/WalletRegistryIntegrationTests.swift`

- Use real `ClassicalProvider` (CryptoKit works on simulator for Ed25519, ECDSA)
- Use real `SsdidHttpClient` pointed at `https://registry.ssdid.my`
- Use `InMemoryVaultStorage` for local vault
- Skip KAZ-Sign tests if PQC provider unavailable on simulator
- Mark with `XCTSkipUnless(RegistryAvailability.isReachable)`
- Run on demand, not in CI (registry may be unreachable from GitHub runners)

### Test Isolation

Each test creates its own unique DID and cleans up (deactivates) at the end. Tests are independent and can run in any order. Rate limit tests use a dedicated test to avoid interfering with other tests.

### Expected Test Count

| Flow | Tests | Priority |
|------|:---:|----------|
| Flow 1: Identity Lifecycle | 3 (Ed25519, P-256, KAZ-Sign) | Critical |
| Flow 2: Key Rotation | 3 | Critical |
| Flow 3: Challenge Lifecycle | 3 | High |
| Flow 4: Multi-Algorithm | 6 | High |
| Flow 5: Rate Limiting | 2 | High |
| Flow 6: DID Validation | 5 | Medium |
| Flow 7: Proof Cross-Check | 3 | Critical |
| Flow 8: Grace Period | 2 | Medium |
| Flow 9: Schema Compliance | 3 | Medium |
| Flow 10: Error Format | 4 | Medium |
| **Total** | **~34** | |
