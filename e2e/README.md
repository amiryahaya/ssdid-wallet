# SSDID Wallet E2E Test Suite

End-to-end tests for the SSDID Wallet across mobile (Maestro) and desktop/web (Playwright).

## Structure

```
e2e/
├── maestro/                    # Mobile E2E tests (Android + iOS)
│   ├── config/
│   │   ├── android.yaml        # Android app config
│   │   └── ios.yaml            # iOS app config
│   ├── flows/
│   │   ├── uc01-onboarding.yaml
│   │   ├── uc02-create-identity.yaml
│   │   ├── uc03-wallet-home.yaml
│   │   ├── uc04-identity-detail.yaml
│   │   ├── uc05-deactivate-identity.yaml
│   │   ├── uc07-authentication.yaml
│   │   ├── uc11-recovery.yaml
│   │   ├── uc12-key-rotation.yaml
│   │   ├── uc14-backup.yaml
│   │   ├── uc15-autolock.yaml
│   │   └── uc16-settings.yaml
│   ├── results/                # Test run results (gitignored)
│   └── run-all.sh              # Run all Maestro flows
│
├── playwright/                 # Desktop/Web E2E tests
│   ├── tests/
│   │   ├── registry-api.spec.ts        # Registry API validation
│   │   ├── deeplink-validation.spec.ts # Deep link format tests
│   │   └── drive-integration.spec.ts   # ssdid-drive integration
│   ├── playwright.config.ts
│   └── package.json
│
└── README.md                   # This file
```

## Maestro (Mobile)

### Prerequisites

```bash
# Install Maestro
brew install maestro

# Android: Start emulator or connect device
adb devices

# iOS: Start simulator
xcrun simctl boot "iPhone 16"
```

### Run Tests

```bash
# Run all flows
cd e2e/maestro && ./run-all.sh android
cd e2e/maestro && ./run-all.sh ios

# Run a single flow
maestro test flows/uc02-create-identity.yaml

# Run with video recording
maestro test flows/uc02-create-identity.yaml --record

# Maestro Studio (interactive)
maestro studio
```

### Deep Link Testing (Android)

```bash
# Trigger registration deep link
adb shell am start -a android.intent.action.VIEW \
  -d "ssdid://register?server_url=https://drive.ssdid.my&server_did=did:ssdid:test"

# Trigger login deep link
adb shell am start -a android.intent.action.VIEW \
  -d "ssdid://login?service_url=https://drive.ssdid.my&service_name=SSDID+Drive&callback_url=ssdid://callback&state=test123"

# Trigger invite deep link
adb shell am start -a android.intent.action.VIEW \
  -d "ssdid://invite?server_url=https://drive.ssdid.my&token=test-token&callback_url=ssdid://callback"
```

### Deep Link Testing (iOS)

```bash
# Trigger deep links via xcrun
xcrun simctl openurl booted "ssdid://login?service_url=https://drive.ssdid.my&service_name=SSDID+Drive"
```

## Playwright (Desktop/Web)

### Prerequisites

```bash
cd e2e/playwright
npm install
npx playwright install
```

### Run Tests

```bash
# Run all tests
npx playwright test

# Run specific project
npx playwright test --project=registry-api
npx playwright test --project=drive-integration
npx playwright test --project=deeplink

# Run with UI
npx playwright test --ui

# Run against local registry
REGISTRY_URL=http://localhost:4000 npx playwright test --project=registry-api

# Run against local Drive
DRIVE_URL=http://localhost:5000 npx playwright test --project=drive-integration
```

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `REGISTRY_URL` | `https://registry.ssdid.my` | SSDID Registry base URL |
| `DRIVE_URL` | `https://drive.ssdid.my` | SSDID Drive base URL |

## Test Coverage Map

| Use Case | Maestro (Mobile) | Playwright (Desktop) |
|----------|:-:|:-:|
| UC-01: Onboarding | ✅ | — |
| UC-02: Create Identity | ✅ | — |
| UC-03: Wallet Home | ✅ | — |
| UC-04: Identity Detail | ✅ | — |
| UC-05: Deactivate Identity | ✅ | — |
| UC-06: QR Scanning | — | ✅ (deep link format) |
| UC-07: Authentication | ✅ | ✅ (Drive login flow) |
| UC-08: Invite Acceptance | — | ✅ (Drive invitation) |
| UC-09: Transaction Signing | — | ✅ (Drive challenge) |
| UC-11: Recovery | ✅ | — |
| UC-12: Key Rotation | ✅ | — |
| UC-14: Backup | ✅ | — |
| UC-15: Auto-Lock | ✅ | — |
| UC-16: Settings | ✅ | — |
| UC-17: Deep Links | — | ✅ (URL validation) |
| UC-19: Error Handling | — | ✅ (RFC 7807, rate limiting) |
| Cross-service compat | — | ✅ (registry+drive) |

## Notes

- **Email OTP in E2E**: The 3-step identity wizard requires email verification. For automated testing, either:
  - Use a debug bypass (e.g., code `000000` accepted in debug builds)
  - Use a test email API to fetch the OTP code
  - Skip Step 2 by pre-seeding an identity
- **Biometric in E2E**: Maestro cannot simulate biometric. Tests that require biometric auth need either:
  - A debug bypass flag
  - Manual testing on real devices
- **Rate Limiting**: Tests against the live registry may hit rate limits (10 creates/hour). Use a local registry (`http://localhost:4000`) for heavy testing.
- **Destructive Tests**: Tests like UC-05 (deactivate) permanently destroy identities on the registry. Mark them clearly and run last.
