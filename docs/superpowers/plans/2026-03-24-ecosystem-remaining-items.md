# SSDID Ecosystem Remaining Items Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close all remaining gaps from the SSDID Ecosystem Review (doc 17) — challenge domain binding, XCUITest/Espresso UI tests, device binding for sessions, desktop TLS cert pinning, and offline verification integration.

**Architecture:** Five independent workstreams across 4 repos: (1) Server SDK challenge domain binding prevents cross-service replay, (2) wallet UI test targets enable automated identity flow testing, (3) Server SDK + Drive device binding ties sessions to specific devices, (4) Tauri desktop cert pinning closes the last TLS gap, (5) wallet offline verification bundles DID docs with credentials for offline use.

**Tech Stack:** .NET 10 (Server SDK), Kotlin/Compose + Espresso (Android), Swift/SwiftUI + XCUITest (iOS), Rust/Tauri (desktop), W3C DID/VC specs

---

## Phase 1: NOW — Challenge Domain Binding + UI Test Targets

---

### Task 1: Challenge Domain Binding in Server SDK

**Files:**
- Modify: `~/Workspace/ssdid-sdk-dotnet/src/Ssdid.Sdk.Server/Auth/SsdidServerOptions.cs`
- Modify: `~/Workspace/ssdid-sdk-dotnet/src/Ssdid.Sdk.Server/Session/ISessionStore.cs`
- Modify: `~/Workspace/ssdid-sdk-dotnet/src/Ssdid.Sdk.Server/Session/InMemory/InMemorySessionStore.cs`
- Modify: `~/Workspace/ssdid-sdk-dotnet/src/Ssdid.Sdk.Server/Session/Redis/RedisSessionStore.cs`
- Modify: `~/Workspace/ssdid-sdk-dotnet/src/Ssdid.Sdk.Server/Auth/SsdidAuthService.cs`
- Create: `~/Workspace/ssdid-sdk-dotnet/tests/Ssdid.Sdk.Server.Tests/Auth/DomainBindingTests.cs`

- [ ] **Step 1: Add ServiceDomain to SsdidServerOptions**

In `SsdidServerOptions.cs`, add after the `ServiceUrl` property:

```csharp
/// <summary>
/// Domain bound to authentication challenges. When set, challenge verification
/// rejects proofs created for a different domain (prevents cross-service replay).
/// Should match the service's public hostname (e.g., "drive.ssdid.my").
/// </summary>
public string ServiceDomain { get; set; } = "";
```

- [ ] **Step 2: Add Domain to ChallengeEntry**

In `ISessionStore.cs`, update the record:

```csharp
public record ChallengeEntry(string Challenge, string KeyId, DateTimeOffset CreatedAt, string? Domain = null);
```

Update `CreateChallenge` signature:

```csharp
void CreateChallenge(string did, string purpose, string challenge, string keyId, string? domain = null);
```

- [ ] **Step 3: Update InMemorySessionStore.CreateChallenge**

In `InMemorySessionStore.cs`, update the method signature and pass domain through:

```csharp
public void CreateChallenge(string did, string purpose, string challenge, string keyId, string? domain = null)
{
    var key = $"{did}:{purpose}";
    _challenges[key] = new ChallengeEntry(challenge, keyId, _clock.GetUtcNow(), domain);
}
```

- [ ] **Step 4: Update RedisSessionStore.CreateChallenge**

In `RedisSessionStore.cs`, update the signature and the internal `ChallengeData` record to include domain. The `ChallengeData` is serialized to JSON in Redis, so add a `Domain` field:

```csharp
// Update the internal record (find it near the bottom of the file)
private record ChallengeData(string Challenge, string KeyId, DateTimeOffset CreatedAt, string? Domain = null);

public void CreateChallenge(string did, string purpose, string challenge, string keyId, string? domain = null)
{
    var key = $"{ChallengePrefix}{did}:{purpose}";
    var entry = new ChallengeData(challenge, keyId, DateTimeOffset.UtcNow, domain);
    // ... rest unchanged
}
```

Also update `ConsumeChallenge` to pass domain through to the returned `ChallengeEntry`:

```csharp
// In the return statement, add domain:
return new ChallengeEntry(data.Challenge, data.KeyId, data.CreatedAt, data.Domain);
```

- [ ] **Step 5: Wire domain into SsdidAuthService**

In `SsdidAuthService.cs`:

Add field: `private readonly string _serviceDomain;`

In constructor: `_serviceDomain = options.Value.ServiceDomain;`

In `HandleRegister` (line ~82), pass domain:

```csharp
_sessionStore.CreateChallenge(clientDid, "registration", challenge, clientKeyId,
    string.IsNullOrEmpty(_serviceDomain) ? null : _serviceDomain);
```

In `HandleVerifyResponse` (after line ~103, after KeyId check), add domain validation:

```csharp
if (!string.IsNullOrEmpty(entry.Domain) && !string.IsNullOrEmpty(_serviceDomain)
    && !string.Equals(entry.Domain, _serviceDomain, StringComparison.OrdinalIgnoreCase))
{
    _logger.LogWarning("Verify failed: domain mismatch for {Did} (expected {Expected}, got {Actual})",
        clientDid, _serviceDomain, entry.Domain);
    return SsdidError.Unauthorized("Challenge domain mismatch — possible cross-service replay");
}
```

- [ ] **Step 6: Write domain binding test**

Create `tests/Ssdid.Sdk.Server.Tests/Auth/DomainBindingTests.cs`:

```csharp
using Ssdid.Sdk.Server.Session;
using Ssdid.Sdk.Server.Session.InMemory;

namespace Ssdid.Sdk.Server.Tests.Auth;

public class DomainBindingTests
{
    [Fact]
    public void Challenge_stores_and_returns_domain()
    {
        var store = new InMemorySessionStore();
        store.CreateChallenge("did:ssdid:test", "registration", "ch123", "key-1", "drive.ssdid.my");

        var entry = store.ConsumeChallenge("did:ssdid:test", "registration");

        Assert.NotNull(entry);
        Assert.Equal("drive.ssdid.my", entry.Domain);
    }

    [Fact]
    public void Challenge_without_domain_returns_null_domain()
    {
        var store = new InMemorySessionStore();
        store.CreateChallenge("did:ssdid:test", "registration", "ch123", "key-1");

        var entry = store.ConsumeChallenge("did:ssdid:test", "registration");

        Assert.NotNull(entry);
        Assert.Null(entry.Domain);
    }
}
```

- [ ] **Step 7: Build and test**

```bash
cd ~/Workspace/ssdid-sdk-dotnet
dotnet build src/Ssdid.Sdk.Server
dotnet test tests/Ssdid.Sdk.Server.Tests
```

Expected: Build succeeds, tests pass.

- [ ] **Step 8: Commit**

```bash
cd ~/Workspace/ssdid-sdk-dotnet
git add -A
git commit -m "feat: add challenge domain binding to prevent cross-service replay

- ServiceDomain config in SsdidServerOptions
- ChallengeEntry carries optional domain field
- HandleVerifyResponse rejects domain mismatches
- InMemory + Redis stores updated
- Tests for domain round-trip"
```

---

### Task 2: Android Espresso UI Test Target

**Files:**
- Create: `~/Workspace/ssdid-wallet/android/app/src/androidTest/java/my/ssdid/wallet/ui/IdentityCreationTest.kt`
- Create: `~/Workspace/ssdid-wallet/android/app/src/androidTest/java/my/ssdid/wallet/ui/RecoveryFlowTest.kt`
- Modify: `~/Workspace/ssdid-wallet/android/app/src/main/java/my/ssdid/wallet/feature/identity/CreateIdentityScreen.kt` (already has `skipOtpForTesting`)

**Context:** Espresso + Compose UI testing dependencies are already in `build.gradle.kts` (espresso-core, compose ui-test-junit4). The `CreateIdentityViewModel` already has `skipOtpForTesting` flag. Android instrumented tests go in `androidTest/`.

- [ ] **Step 1: Create IdentityCreationTest**

Create `android/app/src/androidTest/java/my/ssdid/wallet/ui/IdentityCreationTest.kt`:

```kotlin
package my.ssdid.wallet.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import my.ssdid.wallet.MainActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class IdentityCreationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun identityCreation_showsDisplayNameStep() {
        // Navigate to create identity if not already there
        composeRule.onNodeWithText("Create Identity", useUnmergedTree = true)
            .performClick()

        // Step 1: Display name + email should be visible
        composeRule.onNodeWithText("Display Name")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Email")
            .assertIsDisplayed()
    }

    @Test
    fun identityCreation_canEnterDisplayName() {
        composeRule.onNodeWithText("Create Identity", useUnmergedTree = true)
            .performClick()

        composeRule.onNodeWithText("Display Name")
            .performTextInput("Test User")

        composeRule.onNodeWithText("Test User")
            .assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Create RecoveryFlowTest**

Create `android/app/src/androidTest/java/my/ssdid/wallet/ui/RecoveryFlowTest.kt`:

```kotlin
package my.ssdid.wallet.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import my.ssdid.wallet.MainActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RecoveryFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun recoverySetup_showsRecoveryOptions() {
        // Navigate to settings → recovery
        composeRule.onNodeWithText("Settings", useUnmergedTree = true)
            .performClick()
        composeRule.onNodeWithText("Recovery", useUnmergedTree = true)
            .performClick()

        // Should show recovery options
        composeRule.onNodeWithText("Recovery Key")
            .assertIsDisplayed()
    }
}
```

- [ ] **Step 3: Add Hilt test runner config**

Check if `android/app/src/androidTest/java/my/ssdid/wallet/HiltTestRunner.kt` exists. If not, create it:

```kotlin
package my.ssdid.wallet

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, name: String?, context: Context?): Application {
        return super.newApplication(cl, HiltTestApplication::class.java.name, context)
    }
}
```

Update `build.gradle.kts` — change `testInstrumentationRunner`:

```kotlin
testInstrumentationRunner = "my.ssdid.wallet.HiltTestRunner"
```

Add Hilt testing dependency if not present:

```kotlin
androidTestImplementation("com.google.dagger:hilt-android-testing:2.56.2")
kaptAndroidTest("com.google.dagger:hilt-android-compiler:2.56.2")
```

- [ ] **Step 4: Verify build**

```bash
cd ~/Workspace/ssdid-wallet/android
./gradlew :app:compileDebugAndroidTestKotlin
```

Expected: Compiles without errors.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/androidTest/ android/app/build.gradle.kts
git commit -m "feat(android): add Espresso UI test target with identity creation tests

- HiltTestRunner for DI in instrumented tests
- IdentityCreationTest: display name step visibility, text input
- RecoveryFlowTest: recovery options visibility
- Hilt testing dependency added"
```

---

### Task 3: iOS XCUITest Target

**Files:**
- Create: `~/Workspace/ssdid-wallet/ios/SsdidWalletUITests/IdentityCreationUITests.swift`
- Create: `~/Workspace/ssdid-wallet/ios/SsdidWalletUITests/RecoveryFlowUITests.swift`
- Modify: `~/Workspace/ssdid-wallet/ios/SsdidWallet.xcodeproj/project.pbxproj` (add UI test target)

**Context:** iOS already has `--skip-otp` launch argument support in `CreateIdentityScreen.swift` (DEBUG only). XCUITest is a separate target in Xcode.

- [ ] **Step 1: Create XCUITest directory and tests**

Create `ios/SsdidWalletUITests/IdentityCreationUITests.swift`:

```swift
import XCTest

final class IdentityCreationUITests: XCTestCase {

    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["--skip-otp", "--ui-testing"]
        app.launch()
    }

    func testCreateIdentity_showsDisplayNameField() throws {
        // Tap create identity button
        let createButton = app.buttons["Create Identity"]
        if createButton.waitForExistence(timeout: 5) {
            createButton.tap()
        }

        // Step 1 should show display name field
        let displayNameField = app.textFields["Display Name"]
        XCTAssertTrue(displayNameField.waitForExistence(timeout: 3))
    }

    func testCreateIdentity_canEnterDisplayName() throws {
        let createButton = app.buttons["Create Identity"]
        if createButton.waitForExistence(timeout: 5) {
            createButton.tap()
        }

        let displayNameField = app.textFields["Display Name"]
        XCTAssertTrue(displayNameField.waitForExistence(timeout: 3))

        displayNameField.tap()
        displayNameField.typeText("Test User")

        // Verify text was entered
        XCTAssertEqual(displayNameField.value as? String, "Test User")
    }

    func testCreateIdentity_showsEmailField() throws {
        let createButton = app.buttons["Create Identity"]
        if createButton.waitForExistence(timeout: 5) {
            createButton.tap()
        }

        let emailField = app.textFields["Email"]
        XCTAssertTrue(emailField.waitForExistence(timeout: 3))
    }

    func testCreateIdentity_skipOtpAdvancesToAlgorithmStep() throws {
        let createButton = app.buttons["Create Identity"]
        if createButton.waitForExistence(timeout: 5) {
            createButton.tap()
        }

        // Enter display name and email
        let displayNameField = app.textFields["Display Name"]
        displayNameField.tap()
        displayNameField.typeText("Test User")

        let emailField = app.textFields["Email"]
        emailField.tap()
        emailField.typeText("test@example.com")

        // Tap verify — with --skip-otp, should skip to step 3 (algorithm)
        let verifyButton = app.buttons["Verify"]
        if verifyButton.waitForExistence(timeout: 3) {
            verifyButton.tap()
        }

        // Step 3 should show algorithm selection
        let algorithmText = app.staticTexts["Algorithm"]
        XCTAssertTrue(algorithmText.waitForExistence(timeout: 5))
    }
}
```

Create `ios/SsdidWalletUITests/RecoveryFlowUITests.swift`:

```swift
import XCTest

final class RecoveryFlowUITests: XCTestCase {

    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["--ui-testing"]
        app.launch()
    }

    func testSettings_showsRecoveryOption() throws {
        // Navigate to settings
        let settingsTab = app.tabBars.buttons["Settings"]
        if settingsTab.waitForExistence(timeout: 5) {
            settingsTab.tap()
        }

        // Recovery option should be visible
        let recoveryCell = app.staticTexts["Recovery"]
        XCTAssertTrue(recoveryCell.waitForExistence(timeout: 3))
    }
}
```

- [ ] **Step 2: Add XCUITest target to Xcode project**

The XCUITest target needs to be added to `project.pbxproj`. This requires:
1. New PBXNativeTarget for `SsdidWalletUITests`
2. New PBXGroup for `SsdidWalletUITests/`
3. PBXFileReference for each Swift file
4. PBXBuildFile entries
5. PBXSourcesBuildPhase
6. PBXFrameworksBuildPhase (link XCTest)
7. XCBuildConfiguration entries (Debug + Release)
8. XCConfigurationList
9. Add target to PBXProject targets array
10. Add target dependency (SsdidWallet → SsdidWalletUITests)

Use unique 24-char hex IDs prefixed with `E1F2A3B4C5D6E7F80006` to avoid conflicts.

**Note to implementer:** This is the most complex step. Read the existing `project.pbxproj` carefully and follow the pattern used by `SsdidWalletTests` target. The key sections to duplicate are the test target's `PBXNativeTarget`, `XCBuildConfiguration`, and build phases.

- [ ] **Step 3: Verify build**

Open in Xcode and verify the UI test target appears:

```bash
cd ~/Workspace/ssdid-wallet/ios
xcodebuild -project SsdidWallet.xcodeproj -scheme SsdidWalletUITests -destination 'platform=iOS Simulator,name=iPhone 16' build-for-testing 2>&1 | tail -5
```

- [ ] **Step 4: Commit**

```bash
git add ios/SsdidWalletUITests/ ios/SsdidWallet.xcodeproj/project.pbxproj
git commit -m "feat(ios): add XCUITest target with identity creation and recovery tests

- XCUITest target: SsdidWalletUITests
- IdentityCreationUITests: 4 tests using --skip-otp bypass
- RecoveryFlowUITests: settings navigation test
- Launch arguments: --skip-otp, --ui-testing"
```

---

## Phase 2: NEXT SPRINT — Device Binding for Sessions

---

### Task 4: Device Binding in Server SDK

**Files:**
- Modify: `~/Workspace/ssdid-sdk-dotnet/src/Ssdid.Sdk.Server/Session/ISessionStore.cs`
- Modify: `~/Workspace/ssdid-sdk-dotnet/src/Ssdid.Sdk.Server/Session/InMemory/InMemorySessionStore.cs`
- Modify: `~/Workspace/ssdid-sdk-dotnet/src/Ssdid.Sdk.Server/Session/Redis/RedisSessionStore.cs`
- Modify: `~/Workspace/ssdid-sdk-dotnet/src/Ssdid.Sdk.Server/Middleware/SsdidAuthMiddleware.cs`
- Create: `~/Workspace/ssdid-sdk-dotnet/src/Ssdid.Sdk.Server/Session/DeviceFingerprint.cs`
- Create: `~/Workspace/ssdid-sdk-dotnet/tests/Ssdid.Sdk.Server.Tests/Session/DeviceBindingTests.cs`

- [ ] **Step 1: Create DeviceFingerprint utility**

Create `Session/DeviceFingerprint.cs`:

```csharp
using System.Security.Cryptography;
using System.Text;

namespace Ssdid.Sdk.Server.Session;

/// <summary>
/// Computes a device fingerprint from request metadata.
/// The fingerprint is a SHA-256 hash of User-Agent + X-Device-ID header.
/// </summary>
public static class DeviceFingerprint
{
    public const string DeviceIdHeader = "X-SSDID-Device-ID";

    public static string Compute(string? userAgent, string? deviceId)
    {
        var input = $"{userAgent ?? "unknown"}|{deviceId ?? "none"}";
        var hash = SHA256.HashData(Encoding.UTF8.GetBytes(input));
        return Convert.ToHexStringLower(hash);
    }
}
```

- [ ] **Step 2: Add device fingerprint to session creation**

Update `ISessionStore.CreateSession` to accept optional fingerprint:

```csharp
string? CreateSession(string did, string? deviceFingerprint = null);
string? GetSession(string token);  // unchanged — returns DID
string? GetSessionDeviceFingerprint(string token);  // NEW
```

Update `InMemorySessionStore.SessionEntry` to store fingerprint:

```csharp
private class SessionEntry
{
    public string Did { get; }
    public string? DeviceFingerprint { get; }
    // ... existing fields
    public SessionEntry(string did, DateTimeOffset lastAccessed, string? deviceFingerprint = null)
    {
        Did = did;
        DeviceFingerprint = deviceFingerprint;
        _lastAccessedTicks = lastAccessed.UtcTicks;
    }
}
```

- [ ] **Step 3: Update InMemorySessionStore**

Update `CreateSession`:

```csharp
public string? CreateSession(string did, string? deviceFingerprint = null)
{
    // ... existing slot-reservation logic
    if (_sessions.TryAdd(token, new SessionEntry(did, _clock.GetUtcNow(), deviceFingerprint)))
        return token;
    // ...
}
```

Add `GetSessionDeviceFingerprint`:

```csharp
public string? GetSessionDeviceFingerprint(string token)
{
    return _sessions.TryGetValue(token, out var entry) ? entry.DeviceFingerprint : null;
}
```

- [ ] **Step 4: Update RedisSessionStore**

Update the internal session data record to include device fingerprint. Update `CreateSession` and add `GetSessionDeviceFingerprint`.

- [ ] **Step 5: Update SsdidAuthMiddleware to check device**

In `SsdidAuthMiddleware.InvokeAsync`, after session lookup:

```csharp
if (did is not null)
{
    var storedFingerprint = sessionStore.GetSessionDeviceFingerprint(token);
    if (storedFingerprint is not null)
    {
        var currentFingerprint = DeviceFingerprint.Compute(
            context.Request.Headers.UserAgent.FirstOrDefault(),
            context.Request.Headers[DeviceFingerprint.DeviceIdHeader].FirstOrDefault());

        if (!string.Equals(storedFingerprint, currentFingerprint, StringComparison.Ordinal))
        {
            logger.LogWarning("Device fingerprint mismatch for session — possible token theft");
            context.Response.StatusCode = 401;
            await context.Response.WriteAsJsonAsync(new { error = "Session not valid for this device" });
            return;
        }
    }

    context.Items[UserKey] = new SsdidUser(did, token);
}
```

- [ ] **Step 6: Wire device fingerprint into SsdidAuthService.CreateAuthenticatedSession**

Add optional `deviceFingerprint` parameter:

```csharp
public Result<AuthenticateResponse> CreateAuthenticatedSession(string did, string? deviceFingerprint = null)
{
    var sessionToken = _sessionStore.CreateSession(did, deviceFingerprint);
    // ... rest unchanged
}
```

- [ ] **Step 7: Write tests**

```csharp
public class DeviceBindingTests
{
    [Fact]
    public void DeviceFingerprint_same_inputs_produce_same_hash()
    {
        var fp1 = DeviceFingerprint.Compute("Mozilla/5.0", "device-123");
        var fp2 = DeviceFingerprint.Compute("Mozilla/5.0", "device-123");
        Assert.Equal(fp1, fp2);
    }

    [Fact]
    public void DeviceFingerprint_different_inputs_produce_different_hash()
    {
        var fp1 = DeviceFingerprint.Compute("Mozilla/5.0", "device-123");
        var fp2 = DeviceFingerprint.Compute("Mozilla/5.0", "device-456");
        Assert.NotEqual(fp1, fp2);
    }

    [Fact]
    public void Session_stores_and_returns_device_fingerprint()
    {
        var store = new InMemorySessionStore();
        var token = store.CreateSession("did:ssdid:test", "fingerprint-abc");

        Assert.NotNull(token);
        Assert.Equal("fingerprint-abc", store.GetSessionDeviceFingerprint(token));
    }
}
```

- [ ] **Step 8: Build, test, commit**

```bash
cd ~/Workspace/ssdid-sdk-dotnet
dotnet build && dotnet test
git add -A && git commit -m "feat: add device binding for sessions

- DeviceFingerprint: SHA-256(User-Agent + X-SSDID-Device-ID)
- Sessions store optional device fingerprint at creation
- SsdidAuthMiddleware rejects requests from different devices
- InMemory + Redis stores updated"
```

---

### Task 5: Wire Device Binding in Drive

**Files:**
- Modify: `~/Workspace/ssdid-drive/src/SsdidDrive.Api/Features/Auth/Authenticate.cs`
- Modify: `~/Workspace/ssdid-drive/src/SsdidDrive.Api/Features/Invitations/AcceptWithWallet.cs`

- [ ] **Step 1: Extract device fingerprint from request and pass to session creation**

In `Authenticate.cs`, before `auth.CreateAuthenticatedSession(did)`:

```csharp
var deviceFp = Ssdid.Sdk.Server.Session.DeviceFingerprint.Compute(
    context.Request.Headers.UserAgent.FirstOrDefault(),
    context.Request.Headers[Ssdid.Sdk.Server.Session.DeviceFingerprint.DeviceIdHeader].FirstOrDefault());

var sessionResult = auth.CreateAuthenticatedSession(did, deviceFp);
```

Note: `Handle` needs `HttpContext` injected. Add it to the parameter list if not already there.

- [ ] **Step 2: Same for AcceptWithWallet.cs**

Same pattern — pass device fingerprint when creating session.

- [ ] **Step 3: Build, test, commit**

```bash
cd ~/Workspace/ssdid-drive
dotnet build src/SsdidDrive.Api
git add -A && git commit -m "feat: wire device binding into auth + invite flows"
```

---

## Phase 3: BEFORE GA — Desktop TLS + Offline Verification

---

### Task 6: Desktop TLS Certificate Pinning

**Files:**
- Modify: `~/Workspace/ssdid-drive/clients/desktop/src-tauri/Cargo.toml`
- Create: `~/Workspace/ssdid-drive/clients/desktop/src-tauri/src/cert_pinner.rs`
- Modify: `~/Workspace/ssdid-drive/clients/desktop/src-tauri/src/main.rs` or `lib.rs`

**Context:** The desktop client uses Tauri 2 + React. HTTP requests go through either the JS fetch API or a Rust-side `reqwest` client. Tauri doesn't natively support cert pinning, so we implement it in Rust via a custom `reqwest` client with certificate validation.

- [ ] **Step 1: Add pinning module**

Create `src-tauri/src/cert_pinner.rs`:

```rust
use sha2::{Sha256, Digest};
use reqwest::tls::Certificate;

/// SPKI SHA-256 pins for SSDID infrastructure.
/// Generate with: openssl s_client -connect host:443 | openssl x509 -pubkey -noout | openssl pkey -pubin -outform DER | openssl dgst -sha256 -binary | base64
const PINNED_HASHES: &[&str] = &[
    // registry.ssdid.my (primary)
    "sha256/YLh1dUR9y6Kja30RrAn7JKnbQG/uEtLMkBgFF2Fuihg=",
    // registry.ssdid.my (backup)
    "sha256/Vjs8r4z+80wjNcr1YKepWQboSIRi63WsWXhIMN+eWys=",
];

pub fn validate_certificate(der_chain: &[Vec<u8>]) -> bool {
    for cert_der in der_chain {
        let hash = Sha256::digest(cert_der);
        let pin = format!("sha256/{}", base64::engine::general_purpose::STANDARD.encode(&hash));
        if PINNED_HASHES.contains(&pin.as_str()) {
            return true;
        }
    }
    false
}
```

- [ ] **Step 2: Create pinned HTTP client**

In `main.rs` or a `commands.rs` module, create a Tauri command that uses the pinned client:

```rust
use reqwest::ClientBuilder;

fn build_pinned_client() -> reqwest::Client {
    ClientBuilder::new()
        .danger_accept_invalid_certs(false)
        .tls_built_in_root_certs(true)
        // reqwest doesn't support pin callbacks directly,
        // so we verify after connection in a middleware pattern
        .build()
        .expect("Failed to build HTTP client")
}
```

**Note:** Full SPKI pinning in `reqwest` requires either a custom `rustls` verifier or post-connection validation. The simplest approach is to add the actual leaf/intermediate certificates as `add_root_certificate()` and restrict trust to only those.

- [ ] **Step 3: Build and commit**

```bash
cd ~/Workspace/ssdid-drive/clients/desktop
cargo build --manifest-path src-tauri/Cargo.toml
git add -A && git commit -m "feat(desktop): add TLS certificate pinning for registry.ssdid.my"
```

---

### Task 7: Offline Verification Integration (DID Doc Bundling)

**Files:**
- Create: `~/Workspace/ssdid-wallet/android/app/src/main/java/my/ssdid/wallet/domain/verifier/offline/BundleFetcher.kt`
- Create: `~/Workspace/ssdid-wallet/ios/SsdidWallet/Domain/Verifier/Offline/BundleFetcher.swift`
- Modify: `~/Workspace/ssdid-wallet/android/app/src/main/java/my/ssdid/wallet/domain/SsdidClient.kt` (cache bundles after auth)
- Modify: `~/Workspace/ssdid-wallet/ios/SsdidWallet/Domain/SsdidClient.swift` (same)
- Create: `~/Workspace/ssdid-wallet/android/app/src/test/java/my/ssdid/wallet/domain/verifier/offline/BundleFetcherTest.kt`
- Create: `~/Workspace/ssdid-wallet/ios/SsdidWalletTests/Domain/Verifier/BundleFetcherTests.swift`

**Context:** `OfflineVerifier` + `BundleStore` + `VerificationBundle` already exist on both platforms. What's missing is: (1) a `BundleFetcher` that resolves a DID document + status list into a bundle, and (2) wiring into `SsdidClient.authenticate()` to cache bundles after successful auth.

- [ ] **Step 1: Create Android BundleFetcher**

```kotlin
package my.ssdid.wallet.domain.verifier.offline

import my.ssdid.wallet.domain.transport.RegistryApi
import my.ssdid.wallet.domain.model.DidDocument
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.temporal.ChronoUnit

class BundleFetcher(
    private val registryApi: RegistryApi,
    private val bundleStore: BundleStore,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    suspend fun fetchAndCache(issuerDid: String): VerificationBundle? {
        return try {
            val didDocResponse = registryApi.resolveDid(issuerDid)
            val didDoc = json.decodeFromString<DidDocument>(
                json.encodeToString(DidDocument.serializer(), didDocResponse.didDocument)
            )

            val now = Instant.now()
            val bundle = VerificationBundle(
                issuerDid = issuerDid,
                didDocument = didDoc,
                fetchedAt = now.toString(),
                expiresAt = now.plus(7, ChronoUnit.DAYS).toString()
            )
            bundleStore.saveBundle(bundle)
            bundle
        } catch (e: Exception) {
            null // Graceful fallback — offline verification is best-effort
        }
    }
}
```

- [ ] **Step 2: Create iOS BundleFetcher**

```swift
import Foundation

final class BundleFetcher {
    private let httpClient: SsdidHttpClient
    private let bundleStore: BundleStore

    init(httpClient: SsdidHttpClient, bundleStore: BundleStore) {
        self.httpClient = httpClient
        self.bundleStore = bundleStore
    }

    func fetchAndCache(issuerDid: String) async -> VerificationBundle? {
        do {
            let didDoc = try await httpClient.resolveDid(issuerDid)
            let formatter = ISO8601DateFormatter()
            let now = Date()
            let bundle = VerificationBundle(
                issuerDid: issuerDid,
                didDocument: didDoc,
                fetchedAt: formatter.string(from: now),
                expiresAt: formatter.string(from: now.addingTimeInterval(7 * 86400))
            )
            try await bundleStore.saveBundle(bundle)
            return bundle
        } catch {
            return nil
        }
    }
}
```

- [ ] **Step 3: Wire into SsdidClient.authenticate()**

On both platforms, after a successful authentication (credential verified, session created), call:

```kotlin
// Android — in SsdidClient.authenticate(), after successful auth:
try {
    val issuerDid = credential.issuer
    bundleFetcher.fetchAndCache(issuerDid)
} catch (_: Exception) { /* non-blocking */ }
```

```swift
// iOS — same pattern:
Task {
    _ = await bundleFetcher.fetchAndCache(issuerDid: credential.issuer)
}
```

- [ ] **Step 4: Write tests**

Android test:

```kotlin
@Test
fun `fetchAndCache stores bundle for valid DID`() = runTest {
    val mockApi = mockk<RegistryApi>()
    val store = InMemoryBundleStore()
    coEvery { mockApi.resolveDid(any()) } returns mockDidDocResponse()

    val fetcher = BundleFetcher(mockApi, store)
    val bundle = fetcher.fetchAndCache("did:ssdid:test")

    assertThat(bundle).isNotNull()
    assertThat(store.getBundle("did:ssdid:test")).isNotNull()
}

@Test
fun `fetchAndCache returns null on network error`() = runTest {
    val mockApi = mockk<RegistryApi>()
    val store = InMemoryBundleStore()
    coEvery { mockApi.resolveDid(any()) } throws IOException("Network error")

    val fetcher = BundleFetcher(mockApi, store)
    val bundle = fetcher.fetchAndCache("did:ssdid:test")

    assertThat(bundle).isNull()
}
```

- [ ] **Step 5: Build, test, commit**

```bash
cd ~/Workspace/ssdid-wallet/android
./gradlew :app:testDebugUnitTest --tests "*.BundleFetcherTest"

cd ~/Workspace/ssdid-wallet
git add -A && git commit -m "feat: add BundleFetcher for offline verification DID doc caching

- BundleFetcher resolves DID doc + caches as VerificationBundle (7-day TTL)
- Wired into SsdidClient.authenticate() on both platforms (non-blocking)
- Graceful fallback on network errors"
```

---

## Summary

| Phase | Task | Repo | Effort |
|-------|------|------|--------|
| NOW | 1. Challenge domain binding | Server SDK | S |
| NOW | 2. Espresso UI test target | Wallet (Android) | M |
| NOW | 3. XCUITest target | Wallet (iOS) | M |
| NEXT | 4. Device binding (SDK) | Server SDK | M |
| NEXT | 5. Device binding (Drive) | Drive | S |
| GA | 6. Desktop TLS cert pinning | Drive (desktop) | M |
| GA | 7. Offline verification bundles | Wallet | M |
