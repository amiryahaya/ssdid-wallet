# VC Service Metadata Enrichment

## Problem

When ssdid-drive (or any third-party service) issues a Verifiable Credential to the wallet during registration, the VC's `credentialSubject` contains a `service` field with an internal identifier (e.g., `"drive"`). The wallet has no human-readable display name for the service and must either hardcode a mapping or show the raw identifier.

## Solution

Services should include a `serviceName` field in the VC's `credentialSubject` with their human-readable display name. The wallet reads this field for display with a fallback chain.

## VC credentialSubject Format

### Current (incomplete)

```json
{
  "credentialSubject": {
    "id": "did:ssdid:abc...",
    "service": "drive",
    "registeredAt": "2026-03-17T00:00:00Z"
  }
}
```

### Proposed (enriched)

```json
{
  "credentialSubject": {
    "id": "did:ssdid:abc...",
    "service": "drive",
    "serviceName": "SSDID Drive",
    "serviceUrl": "https://drive.ssdid.my",
    "registeredAt": "2026-03-17T00:00:00Z"
  }
}
```

## New Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `serviceName` | string | Recommended | Human-readable display name (e.g., "SSDID Drive", "Gov Portal") |
| `serviceUrl` | string | Optional | Service's base URL for display |

The existing `service` field (internal identifier) is kept for backward compatibility and programmatic use.

## Wallet Fallback Chain

The wallet resolves the display name in this order:

1. `credentialSubject.serviceName` — human-readable name from the service
2. `credentialSubject.service` — internal identifier, capitalized
3. `vc.issuer` — DID of the issuer, truncated

No wallet-side mapping table. The service controls its own display name.

## Server-Side Change (ssdid-drive)

In `RegisterVerify` (or equivalent endpoint that issues the VC), add `serviceName` and `serviceUrl` to the credential subject:

```csharp
// Example: C# / ssdid-drive
var credential = new VerifiableCredential {
    CredentialSubject = new {
        id = request.Did,
        service = "drive",
        serviceName = "SSDID Drive",           // NEW
        serviceUrl = configuration.BaseUrl,     // NEW
        registeredAt = DateTime.UtcNow
    },
    // ... rest of VC
};
```

For third-party services integrating with SSDID, document that `serviceName` should be included in the issued VC for proper wallet display.

## Backward Compatibility

- Existing VCs without `serviceName` continue to work — wallet falls back to `service` then issuer DID
- The wallet already preserves all unknown `additionalProperties` in `CredentialSubject` via custom serializer, so no wallet storage changes needed
- No migration required — the enrichment only affects newly issued VCs

## Impact

- **Wallet:** Already updated (this commit). Reads `serviceName` → `service` → issuer DID.
- **ssdid-drive:** Needs to add `serviceName` and `serviceUrl` to issued VCs.
- **Third-party services:** Should include `serviceName` in their VCs for proper display.
