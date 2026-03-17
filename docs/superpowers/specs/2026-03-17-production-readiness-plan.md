# SSDID Wallet Production Readiness Plan

Based on the SSDID Ecosystem Review (doc 17), filtered for wallet-specific items.

## Unblocked Now (no registry changes needed)

### Phase A: Key Recovery (Critical)

| Tier | What | Status | Work Needed |
|------|------|--------|-------------|
| Tier 1 | Offline recovery key — generate, QR export, restore | Partial — `RecoveryManager` exists | Complete UI flow, test end-to-end |
| Tier 2 | Shamir's Secret Sharing (3-of-5 social recovery) | Not started | SSS algorithm, share distribution UI, reconstruction flow |
| Tier 3 | Institutional guardian key in DID document | Not started | Add guardian key via DID doc UPDATE, custodian approval flow |

### Phase B: Multi-Device Enrollment (High)

| What | Status | Work Needed |
|------|--------|-------------|
| QR pairing protocol | Shell UI exists | Implement: primary generates QR → secondary scans → keypair generation → challenge signing → primary approval → DID doc UPDATE |
| Device management | Shell UI exists | Wire up: list devices, revoke device key |

### Phase C: DID Validation + Test Fixes (High/Medium)

| What | Status | Work Needed |
|------|--------|-------------|
| `DID.validate()` / `DID.parse()` | `Did.kt`/`Did.swift` exist with generate/keyId | Add format validation: prefix, base64url ID, min entropy |
| Fix broken iOS tests | Multiple files broken | Fix OpenId4Vp handler tests, IssuerMetadata tests, re-enable CI test step |
| Concurrent vault access tests | None | Add tests for concurrent sign/createIdentity |

---

## Blocked by Registry

### Needs Registry Spec: Key Rotation Ceremony (Critical)

**Registry changes needed:**
1. Add `nextKeyHash` field to DID document schema (optional string, SHA3-256 hash of pre-rotated public key)
2. Validate `nextKeyHash` on UPDATE: if current doc has `nextKeyHash`, the new primary key's hash must match it
3. Add rotation grace period: both old and new keys valid for configurable window (default 1 hour)

**Wallet work (after registry):**
- Add `nextKeyHash` to `DidDocument.kt` / `DidDocument.swift`
- Implement rotation ceremony in `KeyRotationScreen`: commit hash → wait → execute rotation
- Pre-generate rotation key pair in `VaultImpl` during identity creation

### Needs Registry Spec: Credential Revocation (High)

**Registry changes needed:**
1. Add `/api/status/{list_id}` endpoint serving W3C Bitstring Status List v1.0
2. Issuers (like ssdid-drive) publish status lists to registry
3. Status list format: compressed bitstring, signed by issuer

**Wallet work (after registry):**
- Wire `RevocationManager` into consent/auth flows
- Cache status lists locally with TTL (e.g., 24 hours)
- Show credential status in Connected Services section (already has status dots)

### Needs Registry: Rate Limiting (Critical, registry-only)

No wallet changes needed. Registry must add per-IP and per-DID throttling.

---

## Implementation Order

```
NOW (wallet only, no registry dependency):
  1. Key Recovery Tier 1 (complete existing flow)
  2. Key Recovery Tier 2 (Shamir SSS)
  3. Key Recovery Tier 3 (institutional guardian)
  4. Multi-Device Enrollment
  5. DID Validation utility
  6. Fix iOS test suite + re-enable CI tests

AFTER REGISTRY UPDATES:
  7. Key Rotation Ceremony
  8. Credential Revocation Checking
```
