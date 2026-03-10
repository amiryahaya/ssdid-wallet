# SSDID Drive Deep Link Integration Design

## Goal

Integrate SSDID Wallet with SSDID Drive via deep links for two flows: authentication callback and cloud backup/restore.

## Architecture

Two features using app-to-app deep links only — no API client code in the wallet.

### Feature 1: Auth Callback

When ssdid-drive launches `ssdid://authenticate?server_url=...&callback_url=ssdiddrive://auth/callback`, the wallet authenticates the user and calls back `ssdiddrive://auth/callback?session_token=<token>`.

- Modify `AuthFlow` screen/ViewModel to detect `callback_url` parameter
- After successful authentication, launch callback URL with session token
- Validate callback URL scheme (whitelist `ssdiddrive://`)

### Feature 2: Cloud Backup/Restore

**Backup:** Wallet produces encrypted backup via `BackupManager.exportBackup()`, then launches ssdid-drive via `ACTION_SEND` intent with the file URI. ssdid-drive already handles share intents.

**Restore:** ssdid-drive sends backup file to wallet via `ACTION_SEND` intent or `ssdid://restore-backup` deep link. Wallet feeds file into `BackupManager.importBackup()`.

## Deep Link Changes

| Action | URL | Direction |
|--------|-----|-----------|
| Authenticate with callback | `ssdid://authenticate?server_url=X&callback_url=ssdiddrive://auth/callback` | drive → wallet |
| Auth callback | `ssdiddrive://auth/callback?session_token=T` | wallet → drive |
| Restore backup | `ssdid://restore-backup` (via share intent with file URI) | drive → wallet |

## Data Flow

```
Auth:
  ssdid-drive → ssdid://authenticate?server_url=X&callback_url=ssdiddrive://auth/callback
  wallet authenticates user, gets session_token
  wallet → ssdiddrive://auth/callback?session_token=T

Backup:
  wallet → BackupManager.exportBackup() → encrypted file
  wallet → ACTION_SEND intent to ssdid-drive with file URI

Restore:
  ssdid-drive → ACTION_SEND intent to wallet with backup file URI
  wallet → BackupManager.importBackup()
```

## Security

- Callback URL scheme whitelisted to `ssdiddrive://` only
- Backup file encrypted with AES-256-GCM + PBKDF2 (existing BackupManager)
- File URIs use FileProvider for secure inter-app sharing
- Biometric required before backup export (existing behavior)

## Components Modified

- `DeepLinkHandler.kt` — add `callback_url` param parsing, add `restore-backup` action
- `Screen.kt` — add `callback_url` arg to AuthFlow
- `NavGraph.kt` — wire callback_url through AuthFlow route
- `AuthFlowScreen.kt` / ViewModel — launch callback after successful auth
- `BackupScreen.kt` — add "Backup to Cloud" button using ACTION_SEND intent
- `AndroidManifest.xml` — add ACTION_SEND intent filter for receiving backup files
- `MainActivity.kt` — handle incoming share intents for restore

## Testing

- DeepLinkHandler: callback_url parsing, restore-backup action, scheme validation
- AuthFlow ViewModel: callback launching after auth success
- Backup: cloud backup intent construction, restore from share intent
