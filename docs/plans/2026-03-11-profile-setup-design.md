# Profile Setup — Design Document

**Date:** 2026-03-11
**Status:** Approved

## Goal

Add a profile setup screen to the onboarding flow so users can enter their name, email, and phone before creating an identity. This profile is stored as a self-issued credential and shared during Sign In with SSDID authentication.

## Architecture

Global profile stored as a `VerifiableCredential` with fixed ID `urn:ssdid:profile` and issuer `did:ssdid:self`. Created before any identity exists, so the proof is a self-assertion placeholder. The consent flow reads claims from this global profile instead of per-identity credentials. A `ProfileManager` encapsulates create/read/update logic. The same form composable is reused for onboarding setup and settings edit.

## Onboarding Flow

**Current:** Onboarding slides → Create Identity → Biometric Setup → Home

**New:** Onboarding slides → **Profile Setup** → Create Identity → Biometric Setup → Home

## Profile Credential Format

```json
{
  "id": "urn:ssdid:profile",
  "type": ["VerifiableCredential", "ProfileCredential"],
  "issuer": "did:ssdid:self",
  "issuanceDate": "2026-03-11T00:00:00Z",
  "credentialSubject": {
    "id": "did:ssdid:self",
    "claims": {
      "name": "Amir Rudin",
      "email": "amir@example.com",
      "phone": "+60123456789"
    }
  },
  "proof": {
    "type": "SelfIssued2024",
    "created": "2026-03-11T00:00:00Z",
    "verificationMethod": "did:ssdid:self",
    "proofPurpose": "selfAssertion",
    "proofValue": ""
  }
}
```

- `id` is always `urn:ssdid:profile` (singleton)
- `issuer` and `credentialSubject.id` are both `did:ssdid:self`
- `claims` is `Map<String, String>` — extensible for future fields
- `proof` is a placeholder — no real key exists at profile creation time

## Profile Screen UI

```
┌─────────────────────────────────┐
│                                 │
│  Set Up Your Profile            │
│                                 │
│  This information can be shared │
│  when you sign in to services   │
│  using your SSDID wallet.       │
│                                 │
│  NAME *                         │
│  ┌─────────────────────────────┐│
│  │                             ││
│  └─────────────────────────────┘│
│                                 │
│  EMAIL *                        │
│  ┌─────────────────────────────┐│
│  │                             ││
│  └─────────────────────────────┘│
│                                 │
│  PHONE                          │
│  ┌─────────────────────────────┐│
│  │                             ││
│  └─────────────────────────────┘│
│                                 │
│  * Required                     │
│                                 │
│  ┌─────────────────────────────┐│
│  │       Continue               ││
│  └─────────────────────────────┘│
│                                 │
│  You can edit this later in     │
│  Settings.                      │
│                                 │
└─────────────────────────────────┘
```

### Behavior
- Validation uses existing `ClaimValidator` (name: non-empty max 100, email: RFC 5322, phone: E.164 if provided)
- Continue/Save button disabled until name and email are valid
- No back button in onboarding mode — required step
- Back button shown in edit mode (from Settings)
- Keyboard-friendly: name → email → phone field progression

## Settings Integration

Profile menu item added to Settings screen, showing current name and email preview. Taps navigate to `Screen.ProfileEdit` which reuses the same form with "Save" instead of "Continue" and a back button.

```
┌─────────────────────────────────┐
│  ← Settings                     │
│                                 │
│  ACCOUNT                        │
│  ┌─────────────────────────────┐│
│  │  Profile                    ││
│  │  Amir Rudin · amir@exam... ││
│  ├─────────────────────────────┤│
│  │  Biometric Lock              ││
│  ├─────────────────────────────┤│
│  │  Export Backup               ││
│  └─────────────────────────────┘│
```

## Consent Flow Integration

`ConsentViewModel` changes:
- `approve()` calls `vault.getProfile()` instead of `vault.getCredentialForDid(identity.did)`
- `hasAllRequiredClaims` StateFlow uses `vault.getProfile()`
- All identities share the same profile claims when authenticating

## Codebase Changes

### New files
| File | Purpose |
|------|---------|
| `feature/profile/ProfileSetupScreen.kt` | Profile form UI + ViewModel |
| `domain/profile/ProfileManager.kt` | Create/read/update profile credential |

### Modified files
| File | Change |
|------|--------|
| `ui/navigation/Screen.kt` | Add `Screen.ProfileSetup` and `Screen.ProfileEdit` |
| `ui/navigation/NavGraph.kt` | Insert ProfileSetup between Onboarding and CreateIdentity; add ProfileEdit route |
| `feature/auth/ConsentViewModel.kt` | Use `vault.getProfile()` for claims |
| `feature/settings/SettingsScreen.kt` | Add Profile menu item |
| `domain/vault/Vault.kt` | Add `getProfile()` / `saveProfile()` |
| `domain/vault/VaultImpl.kt` | Implement `getProfile()` / `saveProfile()` |

### Unchanged
| File | Reason |
|------|--------|
| `ClaimValidator.kt` | Already validates name, email, phone |
| `VaultStorage.kt` / `DataStoreVaultStorage.kt` | Existing credential storage sufficient |
| `AuthDtos.kt` | No changes needed |

## Error Handling

| Scenario | Behavior |
|----------|----------|
| Name empty or >100 chars | Field error, button disabled |
| Email invalid format | Field error, button disabled |
| Phone provided but invalid E.164 | Field error, button disabled |
| Storage write fails | Error message, retry |
| Profile missing at consent time | Error: "Profile not set up" |
