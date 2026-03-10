# Android Release & Monitoring Setup

This guide walks through configuring GitHub repository secrets for the `android-release` CI/CD workflow, including release signing and Sentry error monitoring.

## Prerequisites

- JDK 17+ installed (for `keytool`)
- Admin access to the GitHub repository
- `base64` CLI tool (preinstalled on macOS/Linux)
- A [Sentry](https://sentry.io) account with a project created (for error monitoring)

---

## Step 1: Generate a Release Keystore

Skip this step if you already have a keystore file.

```bash
keytool -genkeypair \
  -v \
  -keystore ssdid-wallet-release.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias ssdid-wallet \
  -storepass YOUR_STORE_PASSWORD \
  -keypass YOUR_KEY_PASSWORD \
  -dname "CN=SSDID Wallet, OU=Mobile, O=SSDID, L=Kuala Lumpur, ST=WP, C=MY"
```

Replace:
- `YOUR_STORE_PASSWORD` — password for the keystore file
- `YOUR_KEY_PASSWORD` — password for the signing key
- `-dname` values — your organization details

This creates `ssdid-wallet-release.jks` in the current directory.

**IMPORTANT:** Back up this keystore file securely. If lost, you cannot update your app on Google Play — you would need to create a new app listing.

---

## Step 2: Base64-Encode the Keystore

GitHub secrets are text-only, so the binary keystore must be base64-encoded.

```bash
base64 -i ssdid-wallet-release.jks -o keystore-base64.txt
```

On Linux:
```bash
base64 ssdid-wallet-release.jks > keystore-base64.txt
```

The output file `keystore-base64.txt` contains the encoded keystore. You will paste this entire content into a GitHub secret.

---

## Step 2b: Get Sentry Credentials

### Get the DSN

1. Go to [sentry.io](https://sentry.io) and sign in
2. Create a new project (or use an existing one):
   - Platform: **Android**
   - Project name: `ssdid-wallet-android`
   - Organization: `ssdid`
3. Go to **Settings → Projects → ssdid-wallet-android → Client Keys (DSN)**
4. Copy the **DSN** value — it looks like:
   ```
   https://examplePublicKey@o0.ingest.sentry.io/0
   ```

### Generate an Auth Token

The auth token is used by the Gradle plugin to upload ProGuard mappings and source context during release builds. This enables readable stack traces in the Sentry dashboard.

1. Go to **Settings → Auth Tokens** (or visit `https://sentry.io/settings/auth-tokens/`)
2. Click **Create New Token**
3. Select scopes:
   - `project:releases`
   - `project:write`
   - `org:read`
4. Click **Create Token**
5. Copy the token — it starts with `sntrys_` and is only shown once

### Local Development (Optional)

To enable Sentry locally, add the DSN to `android/local.properties`:

```properties
sentry.dsn=https://your-key@o0.ingest.sentry.io/0
```

This file is already in `.gitignore` so your DSN won't be committed. Without a DSN, the SDK is a no-op — no data is sent and the app runs normally.

In CI, the `SENTRY_DSN` environment variable is used instead (set via GitHub secrets).

---

## Step 3: Add Secrets to GitHub

### Navigate to Repository Secrets

1. Go to your repository on GitHub: `https://github.com/amiryahaya/ssdid-wallet`
2. Click **Settings** (tab at the top)
3. In the left sidebar, expand **Secrets and variables**
4. Click **Actions**
5. Click **New repository secret**

### Add Each Secret

Add these 6 secrets one at a time:

#### Secret 1: `ANDROID_KEYSTORE_FILE`

- **Name:** `ANDROID_KEYSTORE_FILE`
- **Value:** Paste the entire content of `keystore-base64.txt`
- Click **Add secret**

#### Secret 2: `ANDROID_KEYSTORE_PASSWORD`

- **Name:** `ANDROID_KEYSTORE_PASSWORD`
- **Value:** The store password you used in Step 1 (e.g., `YOUR_STORE_PASSWORD`)
- Click **Add secret**

#### Secret 3: `ANDROID_KEY_ALIAS`

- **Name:** `ANDROID_KEY_ALIAS`
- **Value:** The alias you used in Step 1 (e.g., `ssdid-wallet`)
- Click **Add secret**

#### Secret 4: `ANDROID_KEY_PASSWORD`

- **Name:** `ANDROID_KEY_PASSWORD`
- **Value:** The key password you used in Step 1 (e.g., `YOUR_KEY_PASSWORD`)
- Click **Add secret**

#### Secret 5: `SENTRY_DSN`

- **Name:** `SENTRY_DSN`
- **Value:** Your Sentry project DSN (see [Step 2b](#step-2b-get-sentry-credentials) below)
- Click **Add secret**

#### Secret 6: `SENTRY_AUTH_TOKEN`

- **Name:** `SENTRY_AUTH_TOKEN`
- **Value:** Your Sentry auth token (see [Step 2b](#step-2b-get-sentry-credentials) below)
- Click **Add secret**

### Verify

After adding all 6 secrets, the **Actions secrets** page should show:

```
ANDROID_KEYSTORE_FILE      Updated just now
ANDROID_KEYSTORE_PASSWORD  Updated just now
ANDROID_KEY_ALIAS          Updated just now
ANDROID_KEY_PASSWORD       Updated just now
SENTRY_DSN                 Updated just now
SENTRY_AUTH_TOKEN           Updated just now
```

---

## Step 4: Configure build.gradle.kts for Signing (Required)

The release workflow passes secrets as environment variables. You need to add a `signingConfigs` block to `android/app/build.gradle.kts` that reads them.

Add this inside the `android { }` block, before `defaultConfig`:

```kotlin
signingConfigs {
    create("release") {
        val keystoreBase64 = System.getenv("KEYSTORE_FILE")
        if (keystoreBase64 != null) {
            // CI: decode base64 keystore to a temp file
            val keystoreFile = File.createTempFile("keystore", ".jks")
            keystoreFile.writeBytes(java.util.Base64.getDecoder().decode(keystoreBase64))
            keystoreFile.deleteOnExit()

            storeFile = keystoreFile
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }
}
```

Then add the signing config to the release build type. Add this inside `android { }`:

```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
        signingConfig = signingConfigs.getByName("release")
    }
}
```

---

## Step 5: Trigger a Release

Once secrets are configured and `build.gradle.kts` is updated:

```bash
# Tag the release
git tag android/v1.0.0
git push origin android/v1.0.0
```

This triggers the `android-release` workflow which:
1. Runs unit tests
2. Builds a signed release APK (with Sentry DSN baked in)
3. Uploads ProGuard mappings and source context to Sentry (if `SENTRY_AUTH_TOKEN` is set)
4. Creates a GitHub Release with the APK attached

---

## Troubleshooting

### "No key with alias 'xxx' found in keystore"

The `ANDROID_KEY_ALIAS` secret does not match the alias used when generating the keystore. Check with:

```bash
keytool -list -keystore ssdid-wallet-release.jks
```

### "Keystore was tampered with, or password was incorrect"

The `ANDROID_KEYSTORE_PASSWORD` secret is wrong, or the base64 encoding is corrupted. Re-encode and re-upload:

```bash
base64 -i ssdid-wallet-release.jks | pbcopy   # copies to clipboard on macOS
```

### "assembleRelease" succeeds but APK is unsigned

The `KEYSTORE_FILE` environment variable was empty or not set. Check the workflow logs to verify the secrets are being passed correctly. Ensure the secret names in GitHub match exactly: `ANDROID_KEYSTORE_FILE`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`.

### Release workflow not triggering

Tags must match the pattern `android/v*`. Verify:

```bash
git tag -l "android/*"
```

### Sentry: No events appearing in dashboard

1. Verify `SENTRY_DSN` is set correctly — check the workflow logs for the `Build release APK` step
2. The DSN is baked into `BuildConfig.SENTRY_DSN` at build time. If empty, Sentry is disabled (no-op)
3. Check the DSN format: must start with `https://` and contain `.ingest.sentry.io`

### Sentry: ProGuard mapping upload failed

The `SENTRY_AUTH_TOKEN` is missing or invalid. The build will still succeed — only the upload is skipped. To fix:

1. Verify the token has `project:releases` and `project:write` scopes
2. Ensure the org name (`ssdid`) and project name (`ssdid-wallet-android`) in `app/build.gradle.kts` match your Sentry project
3. Regenerate the token if it has expired

### Sentry: Obfuscated stack traces in dashboard

ProGuard mappings were not uploaded for that release. Ensure `SENTRY_AUTH_TOKEN` is set and the build completed successfully. You can manually upload mappings:

```bash
sentry-cli upload-proguard \
  --org ssdid \
  --project ssdid-wallet-android \
  app/build/outputs/mapping/release/mapping.txt
```

---

## Security Notes

- Never commit the keystore file (`.jks`) to the repository
- Add `*.jks` and `keystore-base64.txt` to `.gitignore`
- Rotate the keystore password periodically
- Consider using [Google Play App Signing](https://developer.android.com/studio/publish/app-signing#app-signing-google-play) for additional protection
- Delete `keystore-base64.txt` from your local machine after uploading to GitHub
- The Sentry DSN is embedded in the APK. It is write-only (cannot read events), but an adversary who extracts it could flood your project with fake events. Mitigate by enabling [rate limits](https://docs.sentry.io/product/accounts/quotas/) in your Sentry project settings
- The Sentry auth token (`SENTRY_AUTH_TOKEN`) is sensitive — it grants write access to your Sentry project. Keep it in GitHub secrets only
- The app scrubs PII (email, IP, name) from Sentry events, redacts DID identifiers from exception messages and transaction names, strips query strings from breadcrumb URLs, and filters breadcrumbs containing sensitive keywords (session_token, private_key, password, mnemonic, seed_phrase, did:)
- Screenshots and view hierarchy capture are disabled to prevent leaking sensitive wallet screens (mnemonics, credentials, QR codes)
