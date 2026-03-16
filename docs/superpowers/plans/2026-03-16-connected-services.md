# Connected Services Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show which services each identity is registered with, directly on the IdentityDetailScreen, with status badges, credential count on home screen, and empty states.

**Architecture:** Add `getCredentialsForDid(did)` to Vault returning all VCs for a DID. Extract service name from VC's `additionalProperties["service"]` with fallback to issuer DID. Derive credential status (active/expiring/expired) from `expirationDate`. Display as "CONNECTED SERVICES" cards on IdentityDetailScreen and credential count badge on WalletHomeScreen.

**Tech Stack:** Kotlin (Android, Compose), Swift (iOS, SwiftUI)

---

## File Structure

| File | Responsibility |
|------|---------------|
| `Vault.kt` / `Vault.swift` | Add `getCredentialsForDid` interface method |
| `VaultImpl.kt` / `VaultImpl.swift` | Implement filter: all VCs where subject.id == did |
| `IdentityDetailScreen.kt` | Add CONNECTED SERVICES section + empty state (Android) |
| `IdentityDetailScreen.swift` | Add CONNECTED SERVICES section + empty state (iOS) |
| `IdentityDetailViewModel.kt` | Load credentials for identity's DID (Android) |
| `WalletHomeScreen.kt` | Add credential count badge (Android) |
| `WalletHomeScreen.swift` | Add credential count badge (iOS) |
| `VaultGetCredentialsTest.kt` | Test for new Vault method |

---

## Chunk 1: Data Layer + Android UI

### Task 1: Add getCredentialsForDid to Vault (Android)

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/vault/Vault.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/vault/VaultImpl.kt`
- Create: `android/app/src/test/java/my/ssdid/wallet/domain/vault/VaultGetCredentialsTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// File: android/app/src/test/java/my/ssdid/wallet/domain/vault/VaultGetCredentialsTest.kt
package my.ssdid.wallet.domain.vault

import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import my.ssdid.wallet.domain.model.*
import org.junit.Before
import org.junit.Test

class VaultGetCredentialsTest {

    private lateinit var storage: VaultStorage
    private lateinit var vault: VaultImpl

    @Before
    fun setup() {
        storage = mockk(relaxed = true)
        vault = VaultImpl(mockk(), mockk(), mockk(), storage)
    }

    private fun vc(id: String, subjectDid: String, issuer: String = "did:ssdid:server") = VerifiableCredential(
        id = id,
        type = listOf("VerifiableCredential"),
        issuer = issuer,
        issuanceDate = "2026-03-16T00:00:00Z",
        credentialSubject = CredentialSubject(id = subjectDid),
        proof = Proof(type = "Ed25519Signature2020", created = "2026-03-16T00:00:00Z",
            verificationMethod = "did:ssdid:server#key-1", proofPurpose = "assertionMethod", proofValue = "z...")
    )

    @Test
    fun `getCredentialsForDid returns all matching credentials`() = runTest {
        val vc1 = vc("vc-1", "did:ssdid:alice")
        val vc2 = vc("vc-2", "did:ssdid:alice", issuer = "did:ssdid:drive")
        val vc3 = vc("vc-3", "did:ssdid:bob")

        coEvery { storage.listCredentials() } returns listOf(vc1, vc2, vc3)

        val result = vault.getCredentialsForDid("did:ssdid:alice")

        assertThat(result).hasSize(2)
        assertThat(result.map { it.id }).containsExactly("vc-1", "vc-2")
    }

    @Test
    fun `getCredentialsForDid returns empty for unknown DID`() = runTest {
        coEvery { storage.listCredentials() } returns listOf(vc("vc-1", "did:ssdid:alice"))

        val result = vault.getCredentialsForDid("did:ssdid:unknown")

        assertThat(result).isEmpty()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.vault.VaultGetCredentialsTest"`
Expected: FAIL — `getCredentialsForDid` doesn't exist.

- [ ] **Step 3: Add to Vault interface and implement**

In `Vault.kt`, add after `getCredentialForDid`:
```kotlin
suspend fun getCredentialsForDid(did: String): List<VerifiableCredential>
```

In `VaultImpl.kt`, add after the existing `getCredentialForDid`:
```kotlin
override suspend fun getCredentialsForDid(did: String): List<VerifiableCredential> {
    return storage.listCredentials().filter { it.credentialSubject.id == did }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.vault.VaultGetCredentialsTest"`
Expected: PASS

- [ ] **Step 5: Run all tests**

Run: `cd android && ./gradlew :app:testDebugUnitTest`
Expected: All PASS

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/vault/Vault.kt android/app/src/main/java/my/ssdid/wallet/domain/vault/VaultImpl.kt android/app/src/test/java/my/ssdid/wallet/domain/vault/VaultGetCredentialsTest.kt
git commit -m "feat(android): add getCredentialsForDid to Vault"
```

---

### Task 2: Add Connected Services section to Android IdentityDetailScreen

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/feature/identity/IdentityDetailScreen.kt`

- [ ] **Step 1: Add credentials state to IdentityDetailViewModel**

In `IdentityDetailViewModel`, add:

```kotlin
import my.ssdid.wallet.domain.model.VerifiableCredential

private val _credentials = MutableStateFlow<List<VerifiableCredential>>(emptyList())
val credentials = _credentials.asStateFlow()
```

Update the `init` block to also load credentials:

```kotlin
init {
    viewModelScope.launch {
        val id = vault.getIdentity(keyId)
        _identity.value = id
        if (id != null) {
            _credentials.value = vault.getCredentialsForDid(id.did)
        }
    }
}
```

- [ ] **Step 2: Add a helper function for credential status**

Add to the file (outside the ViewModel, as a top-level or companion function):

```kotlin
import java.time.Instant
import java.time.temporal.ChronoUnit

enum class CredentialStatus { ACTIVE, EXPIRING, EXPIRED }

fun credentialStatus(vc: VerifiableCredential): CredentialStatus {
    val exp = vc.expirationDate ?: return CredentialStatus.ACTIVE
    return try {
        val expInstant = Instant.parse(exp)
        val now = Instant.now()
        when {
            expInstant.isBefore(now) -> CredentialStatus.EXPIRED
            expInstant.isBefore(now.plus(30, ChronoUnit.DAYS)) -> CredentialStatus.EXPIRING
            else -> CredentialStatus.ACTIVE
        }
    } catch (_: Exception) {
        CredentialStatus.ACTIVE
    }
}

fun serviceName(vc: VerifiableCredential): String {
    // Try additionalProperties first, fall back to issuer DID
    val fromProps = vc.credentialSubject.additionalProperties["service"]
    if (fromProps != null) {
        return fromProps.toString().trim('"')
    }
    return vc.issuer.let {
        if (it.length > 30) it.take(20) + "..." + it.takeLast(8) else it
    }
}

fun serviceUrl(vc: VerifiableCredential): String? {
    val fromProps = vc.credentialSubject.additionalProperties["serviceUrl"]
    return fromProps?.toString()?.trim('"')
}
```

- [ ] **Step 3: Add the CONNECTED SERVICES section to the UI**

In the `IdentityDetailScreen` composable, add `val credentials by viewModel.credentials.collectAsState()` alongside the other collectAsState calls.

Insert a new section in the LazyColumn AFTER the PROFILE section and BEFORE the ACTIONS section (after line 229, before line 231):

```kotlin
// Connected Services
item {
    Spacer(Modifier.height(8.dp))
    Text("CONNECTED SERVICES", style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(8.dp))
}

if (credentials.isEmpty()) {
    item {
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = BgCard)
        ) {
            Column(
                Modifier.padding(24.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("🔗", fontSize = 32.sp)
                Spacer(Modifier.height(8.dp))
                Text("No services connected", fontSize = 14.sp, color = TextSecondary)
                Text("Scan a QR code to register with a service", fontSize = 12.sp, color = TextTertiary)
            }
        }
    }
} else {
    items(credentials.size) { index ->
        val vc = credentials[index]
        val status = credentialStatus(vc)
        val name = serviceName(vc)
        val url = serviceUrl(vc)
        val statusColor = when (status) {
            CredentialStatus.ACTIVE -> Success
            CredentialStatus.EXPIRING -> Warning
            CredentialStatus.EXPIRED -> Danger
        }
        val statusLabel = when (status) {
            CredentialStatus.ACTIVE -> "Active"
            CredentialStatus.EXPIRING -> "Expiring soon"
            CredentialStatus.EXPIRED -> "Expired"
        }

        Card(
            Modifier.fillMaxWidth().clickable { onCredentialClick(vc.id) },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = BgCard)
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                // Status dot
                Box(
                    Modifier.size(10.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                    if (url != null) {
                        Text(url, fontSize = 11.sp, color = TextTertiary, maxLines = 1)
                    }
                    Text("Issued: ${vc.issuanceDate.take(10)}", fontSize = 11.sp, color = TextTertiary)
                }
                Text(statusLabel, fontSize = 11.sp, color = statusColor)
            }
        }
    }
}
```

Add the missing import: `import androidx.compose.foundation.shape.CircleShape`

- [ ] **Step 4: Verify compilation**

Run: `cd android && ./gradlew compileDebugKotlin`
Expected: PASS

- [ ] **Step 5: Run all tests**

Run: `cd android && ./gradlew :app:testDebugUnitTest`
Expected: All PASS

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/feature/identity/IdentityDetailScreen.kt
git commit -m "feat(android): add Connected Services section to IdentityDetailScreen"
```

---

### Task 3: Add credential count badge to Android WalletHomeScreen

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/feature/identity/WalletHomeScreen.kt`

- [ ] **Step 1: Load credential counts in the ViewModel**

Read the WalletHomeScreen file to find the ViewModel. Add a credential count map state:

```kotlin
private val _credentialCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
val credentialCounts = _credentialCounts.asStateFlow()
```

In the identity loading logic, after identities are loaded, compute counts:

```kotlin
val counts = mutableMapOf<String, Int>()
for (id in identities) {
    counts[id.did] = vault.getCredentialsForDid(id.did).size
}
_credentialCounts.value = counts
```

- [ ] **Step 2: Display count in IdentityCard**

In the `IdentityCard` composable, after the email line (which shows `identity.email`), add the credential count:

```kotlin
val count = credentialCounts[identity.did] ?: 0
if (count > 0) {
    Text(
        "$count service${if (count != 1) "s" else ""} connected",
        fontSize = 11.sp,
        color = TextTertiary
    )
}
```

Pass `credentialCounts` into the `IdentityCard` composable or hoist from the ViewModel.

- [ ] **Step 3: Verify compilation**

Run: `cd android && ./gradlew compileDebugKotlin`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/feature/identity/WalletHomeScreen.kt
git commit -m "feat(android): add credential count badge to WalletHomeScreen"
```

---

## Chunk 2: iOS Implementation

### Task 4: Add getCredentialsForDid to iOS Vault

**Files:**
- Modify: `ios/SsdidWallet/Domain/Vault/Vault.swift`
- Modify: `ios/SsdidWallet/Domain/Vault/VaultImpl.swift`

- [ ] **Step 1: Add to Vault protocol**

In `Vault.swift`, add after `getCredentialForDid`:
```swift
/// Returns all credentials whose subject ID matches the given DID.
func getCredentialsForDid(_ did: String) async -> [VerifiableCredential]
```

- [ ] **Step 2: Implement in VaultImpl**

In `VaultImpl.swift`, add after the existing `getCredentialForDid`:
```swift
func getCredentialsForDid(_ did: String) async -> [VerifiableCredential] {
    let credentials = await storage.listCredentials()
    return credentials.filter { $0.credentialSubject.id == did }
}
```

- [ ] **Step 3: Commit**

```bash
git add ios/SsdidWallet/Domain/Vault/Vault.swift ios/SsdidWallet/Domain/Vault/VaultImpl.swift
git commit -m "feat(ios): add getCredentialsForDid to Vault"
```

---

### Task 5: Add Connected Services section to iOS IdentityDetailScreen

**Files:**
- Modify: `ios/SsdidWallet/Feature/Identity/IdentityDetailScreen.swift`

- [ ] **Step 1: Add credentials state**

Add to the `@State` properties:
```swift
@State private var credentials: [VerifiableCredential] = []
```

- [ ] **Step 2: Add helper functions**

Add these as private methods on the view:

```swift
private enum CredentialStatus {
    case active, expiring, expired

    var color: Color {
        switch self {
        case .active: return .success
        case .expiring: return .warning
        case .expired: return .danger
        }
    }

    var label: String {
        switch self {
        case .active: return "Active"
        case .expiring: return "Expiring soon"
        case .expired: return "Expired"
        }
    }
}

private func credentialStatus(_ vc: VerifiableCredential) -> CredentialStatus {
    guard let exp = vc.expirationDate else { return .active }
    let formatter = ISO8601DateFormatter()
    guard let expDate = formatter.date(from: exp) else { return .active }
    let now = Date()
    if expDate < now { return .expired }
    if expDate < Calendar.current.date(byAdding: .day, value: 30, to: now) ?? now { return .expiring }
    return .active
}

private func serviceName(_ vc: VerifiableCredential) -> String {
    if let name = vc.credentialSubject.additionalProperties["service"] {
        return "\(name)".trimmingCharacters(in: CharacterSet(charactersIn: "\""))
    }
    let issuer = vc.issuer
    if issuer.count > 30 {
        return String(issuer.prefix(20)) + "..." + String(issuer.suffix(8))
    }
    return issuer
}

private func serviceUrl(_ vc: VerifiableCredential) -> String? {
    guard let url = vc.credentialSubject.additionalProperties["serviceUrl"] else { return nil }
    return "\(url)".trimmingCharacters(in: CharacterSet(charactersIn: "\""))
}
```

- [ ] **Step 3: Load credentials in .task**

Update the `.task` block to also load credentials:

```swift
.task {
    await loadIdentity()
    if let id = identity {
        credentials = await services.vault.getCredentialsForDid(id.did)
    }
}
```

- [ ] **Step 4: Add Connected Services UI section**

Insert after the PROFILE section and before the ACTIONS section (after line 137, before line 139):

```swift
// Connected Services
Spacer().frame(height: 8)
Text("CONNECTED SERVICES")
    .font(.ssdidCaption)
    .foregroundStyle(Color.textSecondary)

if credentials.isEmpty {
    VStack(spacing: 8) {
        Text("🔗")
            .font(.system(size: 32))
        Text("No services connected")
            .font(.system(size: 14))
            .foregroundStyle(Color.textSecondary)
        Text("Scan a QR code to register with a service")
            .font(.system(size: 12))
            .foregroundStyle(Color.textTertiary)
    }
    .frame(maxWidth: .infinity)
    .padding(24)
    .background(Color.bgCard)
    .cornerRadius(12)
} else {
    ForEach(credentials, id: \.id) { vc in
        let status = credentialStatus(vc)
        let name = serviceName(vc)
        let url = serviceUrl(vc)

        HStack(spacing: 12) {
            Circle()
                .fill(status.color)
                .frame(width: 10, height: 10)
            VStack(alignment: .leading, spacing: 2) {
                Text(name)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(Color.textPrimary)
                if let url = url {
                    Text(url)
                        .font(.system(size: 11))
                        .foregroundStyle(Color.textTertiary)
                        .lineLimit(1)
                }
                Text("Issued: \(String(vc.issuanceDate.prefix(10)))")
                    .font(.system(size: 11))
                    .foregroundStyle(Color.textTertiary)
            }
            Spacer()
            Text(status.label)
                .font(.system(size: 11))
                .foregroundStyle(status.color)
        }
        .padding(14)
        .background(Color.bgCard)
        .cornerRadius(12)
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add ios/SsdidWallet/Feature/Identity/IdentityDetailScreen.swift
git commit -m "feat(ios): add Connected Services section to IdentityDetailScreen"
```

---

### Task 6: Add credential count badge to iOS WalletHomeScreen

**Files:**
- Modify: `ios/SsdidWallet/Feature/Identity/WalletHomeScreen.swift`

- [ ] **Step 1: Add credential counts state**

Add to the `@State` properties:
```swift
@State private var credentialCounts: [String: Int] = [:]
```

- [ ] **Step 2: Load counts after identities load**

In the identity loading logic, after identities are loaded, add:

```swift
var counts: [String: Int] = [:]
for identity in identities {
    let creds = await services.vault.getCredentialsForDid(identity.did)
    counts[identity.did] = creds.count
}
credentialCounts = counts
```

- [ ] **Step 3: Display count in identityCard**

In the `identityCard` function, after the email display (around line 194), add:

```swift
if let count = credentialCounts[identity.did], count > 0 {
    Text("\(count) service\(count == 1 ? "" : "s") connected")
        .font(.system(size: 11))
        .foregroundStyle(Color.textTertiary)
}
```

Pass `credentialCounts` into the function or access it from the parent scope.

- [ ] **Step 4: Commit**

```bash
git add ios/SsdidWallet/Feature/Identity/WalletHomeScreen.swift
git commit -m "feat(ios): add credential count badge to WalletHomeScreen"
```

---

### Task 7: Final verification

- [ ] **Step 1: Run full Android test suite**

Run: `cd android && ./gradlew :app:testDebugUnitTest`
Expected: All PASS

- [ ] **Step 2: Verify no compilation errors**

Run: `cd android && ./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit if cleanup needed**

```bash
git add -A && git commit -m "chore: final cleanup for connected services feature"
```
