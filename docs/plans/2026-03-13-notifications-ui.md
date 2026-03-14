# Notifications UI Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add an in-app notifications screen with unread badge to the SSDID Wallet on both Android and iOS.

**Architecture:** New `LocalNotificationStorage` persists notifications locally (DataStore on Android, JSON file on iOS). The existing `fetchAndDemux()` flow is modified to save locally before acking. A new `NotificationsScreen` reads from local storage and a bell icon with unread badge is added to `WalletHomeScreen`.

**Tech Stack:** Jetpack Compose + DataStore + Hilt (Android), SwiftUI + FileManager + Codable (iOS)

---

### Task 1: Android — `LocalNotification` model and `LocalNotificationStorage`

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/notify/LocalNotification.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/notify/LocalNotificationStorage.kt`
- Create: `android/app/src/test/java/my/ssdid/wallet/domain/notify/LocalNotificationStorageTest.kt`

**Step 1: Write the failing test**

Create `android/app/src/test/java/my/ssdid/wallet/domain/notify/LocalNotificationStorageTest.kt`:

```kotlin
package my.ssdid.wallet.domain.notify

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocalNotificationStorageTest {

    private lateinit var storage: LocalNotificationStorage
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        storage = LocalNotificationStorage(context)
    }

    @Test
    fun `save and retrieve notifications`() = runTest {
        val notification = LocalNotification(
            id = "n1",
            mailboxId = "mbx_abc",
            identityName = "Alice",
            payload = "Hello from service",
            priority = "normal",
            receivedAt = "2026-03-13T10:00:00Z",
            isRead = false
        )
        storage.save(notification)

        val all = storage.allNotifications.first()
        assertThat(all).hasSize(1)
        assertThat(all[0].id).isEqualTo("n1")
        assertThat(all[0].identityName).isEqualTo("Alice")
    }

    @Test
    fun `unread count reflects read state`() = runTest {
        storage.save(LocalNotification("n1", "mbx_a", "Alice", "msg1", "normal", "2026-03-13T10:00:00Z", false))
        storage.save(LocalNotification("n2", "mbx_b", "Bob", "msg2", "normal", "2026-03-13T10:01:00Z", false))

        assertThat(storage.unreadCount.first()).isEqualTo(2)

        storage.markAsRead("n1")
        assertThat(storage.unreadCount.first()).isEqualTo(1)
    }

    @Test
    fun `mark all as read`() = runTest {
        storage.save(LocalNotification("n1", "mbx_a", "Alice", "msg1", "normal", "2026-03-13T10:00:00Z", false))
        storage.save(LocalNotification("n2", "mbx_b", "Bob", "msg2", "normal", "2026-03-13T10:01:00Z", false))

        storage.markAllAsRead()
        assertThat(storage.unreadCount.first()).isEqualTo(0)
    }

    @Test
    fun `delete notification`() = runTest {
        storage.save(LocalNotification("n1", "mbx_a", "Alice", "msg1", "normal", "2026-03-13T10:00:00Z", false))
        storage.delete("n1")

        val all = storage.allNotifications.first()
        assertThat(all).isEmpty()
    }

    @Test
    fun `duplicate id replaces existing`() = runTest {
        storage.save(LocalNotification("n1", "mbx_a", "Alice", "msg1", "normal", "2026-03-13T10:00:00Z", false))
        storage.save(LocalNotification("n1", "mbx_a", "Alice", "updated", "normal", "2026-03-13T10:00:00Z", false))

        val all = storage.allNotifications.first()
        assertThat(all).hasSize(1)
        assertThat(all[0].payload).isEqualTo("updated")
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd /Users/amirrudinyahaya/Workspace/ssdid-wallet/android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.notify.LocalNotificationStorageTest"`
Expected: FAIL — class does not exist

**Step 3: Create the `LocalNotification` data class**

Create `android/app/src/main/java/my/ssdid/wallet/domain/notify/LocalNotification.kt`:

```kotlin
package my.ssdid.wallet.domain.notify

import kotlinx.serialization.Serializable

@Serializable
data class LocalNotification(
    val id: String,
    val mailboxId: String,
    val identityName: String?,
    val payload: String,
    val priority: String,
    val receivedAt: String,
    val isRead: Boolean = false
)
```

**Step 4: Implement `LocalNotificationStorage`**

Create `android/app/src/main/java/my/ssdid/wallet/domain/notify/LocalNotificationStorage.kt`:

```kotlin
package my.ssdid.wallet.domain.notify

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.localNotificationsStore: DataStore<Preferences> by preferencesDataStore(name = "ssdid_local_notifications")

@Singleton
class LocalNotificationStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val key = stringPreferencesKey("notifications_json")
    private val json = Json { ignoreUnknownKeys = true }

    val allNotifications: Flow<List<LocalNotification>> =
        context.localNotificationsStore.data.map { prefs ->
            val raw = prefs[key] ?: return@map emptyList()
            json.decodeFromString<List<LocalNotification>>(raw)
        }

    val unreadCount: Flow<Int> =
        allNotifications.map { list -> list.count { !it.isRead } }

    suspend fun save(notification: LocalNotification) {
        context.localNotificationsStore.edit { prefs ->
            val current = prefs[key]?.let { json.decodeFromString<List<LocalNotification>>(it) } ?: emptyList()
            val updated = current.filter { it.id != notification.id } + notification
            prefs[key] = json.encodeToString(updated)
        }
    }

    suspend fun markAsRead(id: String) {
        context.localNotificationsStore.edit { prefs ->
            val current = prefs[key]?.let { json.decodeFromString<List<LocalNotification>>(it) } ?: return@edit
            val updated = current.map { if (it.id == id) it.copy(isRead = true) else it }
            prefs[key] = json.encodeToString(updated)
        }
    }

    suspend fun markAllAsRead() {
        context.localNotificationsStore.edit { prefs ->
            val current = prefs[key]?.let { json.decodeFromString<List<LocalNotification>>(it) } ?: return@edit
            val updated = current.map { it.copy(isRead = true) }
            prefs[key] = json.encodeToString(updated)
        }
    }

    suspend fun delete(id: String) {
        context.localNotificationsStore.edit { prefs ->
            val current = prefs[key]?.let { json.decodeFromString<List<LocalNotification>>(it) } ?: return@edit
            val updated = current.filter { it.id != id }
            prefs[key] = json.encodeToString(updated)
        }
    }
}
```

**Step 5: Run test to verify it passes**

Run: `cd /Users/amirrudinyahaya/Workspace/ssdid-wallet/android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.notify.LocalNotificationStorageTest"`
Expected: PASS (5 tests)

**Step 6: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/notify/LocalNotification.kt \
        android/app/src/main/java/my/ssdid/wallet/domain/notify/LocalNotificationStorage.kt \
        android/app/src/test/java/my/ssdid/wallet/domain/notify/LocalNotificationStorageTest.kt
git commit -m "feat(android): add LocalNotification model and LocalNotificationStorage"
```

---

### Task 2: iOS — `LocalNotification` model and `LocalNotificationStorage`

**Files:**
- Create: `ios/SsdidWallet/Domain/Notify/LocalNotification.swift`
- Create: `ios/SsdidWallet/Domain/Notify/LocalNotificationStorage.swift`

**Step 1: Create the `LocalNotification` model**

Create `ios/SsdidWallet/Domain/Notify/LocalNotification.swift`:

```swift
import Foundation

struct LocalNotification: Codable, Identifiable, Equatable {
    let id: String
    let mailboxId: String
    let identityName: String?
    let payload: String
    let priority: String
    let receivedAt: String
    var isRead: Bool

    init(
        id: String,
        mailboxId: String,
        identityName: String?,
        payload: String,
        priority: String,
        receivedAt: String,
        isRead: Bool = false
    ) {
        self.id = id
        self.mailboxId = mailboxId
        self.identityName = identityName
        self.payload = payload
        self.priority = priority
        self.receivedAt = receivedAt
        self.isRead = isRead
    }
}
```

**Step 2: Implement `LocalNotificationStorage`**

Create `ios/SsdidWallet/Domain/Notify/LocalNotificationStorage.swift`:

```swift
import Foundation

/// Persists in-app notifications to a JSON file via FileManager.
/// Published properties drive SwiftUI reactivity.
@MainActor
final class LocalNotificationStorage: ObservableObject {

    @Published private(set) var notifications: [LocalNotification] = []

    var unreadCount: Int {
        notifications.filter { !$0.isRead }.count
    }

    private let fileURL: URL

    init() {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        self.fileURL = docs.appendingPathComponent("local_notifications.json")
        self.notifications = Self.load(from: fileURL)
    }

    func save(_ notification: LocalNotification) {
        notifications.removeAll { $0.id == notification.id }
        notifications.insert(notification, at: 0)
        persist()
    }

    func markAsRead(_ id: String) {
        guard let index = notifications.firstIndex(where: { $0.id == id }) else { return }
        notifications[index].isRead = true
        persist()
    }

    func markAllAsRead() {
        for i in notifications.indices {
            notifications[i].isRead = true
        }
        persist()
    }

    func delete(_ id: String) {
        notifications.removeAll { $0.id == id }
        persist()
    }

    // MARK: - Private

    private func persist() {
        do {
            let data = try JSONEncoder().encode(notifications)
            try data.write(to: fileURL, options: .atomic)
        } catch {
            print("LocalNotificationStorage: failed to persist — \(error)")
        }
    }

    private static func load(from url: URL) -> [LocalNotification] {
        guard let data = try? Data(contentsOf: url) else { return [] }
        return (try? JSONDecoder().decode([LocalNotification].self, from: data)) ?? []
    }
}
```

**Step 3: Run `xcodegen generate` to pick up new files**

Run: `cd /Users/amirrudinyahaya/Workspace/ssdid-wallet/ios && xcodegen generate`
Expected: "Generated project"

**Step 4: Build to verify**

Run: `cd /Users/amirrudinyahaya/Workspace/ssdid-wallet/ios && xcodebuild build -scheme SsdidWallet -destination 'platform=iOS Simulator,name=iPhone 16' CODE_SIGNING_ALLOWED=NO -quiet 2>&1 | tail -5`
Expected: BUILD SUCCEEDED

**Step 5: Commit**

```bash
git add ios/SsdidWallet/Domain/Notify/LocalNotification.swift \
        ios/SsdidWallet/Domain/Notify/LocalNotificationStorage.swift
git commit -m "feat(ios): add LocalNotification model and LocalNotificationStorage"
```

---

### Task 3: Android — Modify `NotifyManager.fetchAndDemux()` to save locally

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/notify/NotifyManager.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/di/AppModule.kt`

**Step 1: Add `LocalNotificationStorage` to `NotifyManager` constructor**

In `android/app/src/main/java/my/ssdid/wallet/domain/notify/NotifyManager.kt`, change the constructor:

```kotlin
// Before:
@Singleton
class NotifyManager @Inject constructor(
    private val notifyApi: NotifyApi,
    private val storage: NotifyStorage,
    private val dispatcher: NotifyDispatcher
) {

// After:
@Singleton
class NotifyManager @Inject constructor(
    private val notifyApi: NotifyApi,
    private val storage: NotifyStorage,
    private val dispatcher: NotifyDispatcher,
    private val localNotificationStorage: LocalNotificationStorage
) {
```

**Step 2: Modify `fetchAndDemux()` to save locally before acking**

In the same file, replace the `fetchAndDemux()` method body:

```kotlin
// Before (lines 54-76):
    suspend fun fetchAndDemux() {
        val pending = fetchPending()
        if (pending.isEmpty()) return

        val mailboxToName = knownIdentities.associate { identity ->
            mailboxIdFor(identity) to identity.name
        }

        for (notification in pending) {
            val identityName = mailboxToName[notification.mailboxId]
            try {
                dispatcher.dispatch(identityName, notification)
            } catch (_: Exception) { }
            try {
                ackPending(notification.notificationId)
            } catch (_: Exception) { }
        }
    }

// After:
    suspend fun fetchAndDemux() {
        val pending = fetchPending()
        if (pending.isEmpty()) return

        val mailboxToName = knownIdentities.associate { identity ->
            mailboxIdFor(identity) to identity.name
        }

        for (notification in pending) {
            val identityName = mailboxToName[notification.mailboxId]

            // Save locally first
            localNotificationStorage.save(
                LocalNotification(
                    id = notification.notificationId,
                    mailboxId = notification.mailboxId,
                    identityName = identityName,
                    payload = notification.payload,
                    priority = notification.priority,
                    receivedAt = notification.receivedAt ?: "",
                    isRead = false
                )
            )

            try {
                dispatcher.dispatch(identityName, notification)
            } catch (_: Exception) { }
            try {
                ackPending(notification.notificationId)
            } catch (_: Exception) { }
        }
    }
```

**Step 3: Update `AppModule` to pass `LocalNotificationStorage` to `NotifyManager`**

In `android/app/src/main/java/my/ssdid/wallet/di/AppModule.kt`, update `provideNotifyManager()`:

```kotlin
// Before:
    @Provides
    @Singleton
    fun provideNotifyManager(
        notifyApi: NotifyApi,
        storage: NotifyStorage,
        dispatcher: AndroidNotifyDispatcher
    ): NotifyManager = NotifyManager(notifyApi, storage, dispatcher)

// After:
    @Provides
    @Singleton
    fun provideNotifyManager(
        notifyApi: NotifyApi,
        storage: NotifyStorage,
        dispatcher: AndroidNotifyDispatcher,
        localNotificationStorage: LocalNotificationStorage
    ): NotifyManager = NotifyManager(notifyApi, storage, dispatcher, localNotificationStorage)
```

Add a provider for `LocalNotificationStorage`:

```kotlin
    @Provides
    @Singleton
    fun provideLocalNotificationStorage(
        @ApplicationContext context: Context
    ): LocalNotificationStorage = LocalNotificationStorage(context)
```

**Step 4: Build to verify**

Run: `cd /Users/amirrudinyahaya/Workspace/ssdid-wallet/android && ./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 5: Run existing tests to ensure no regressions**

Run: `cd /Users/amirrudinyahaya/Workspace/ssdid-wallet/android && ./gradlew :app:testDebugUnitTest`
Expected: All tests pass (some NotifyManager tests may need updating if they mock the constructor — fix any failures by adding mockk for the new parameter)

**Step 6: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/notify/NotifyManager.kt \
        android/app/src/main/java/my/ssdid/wallet/di/AppModule.kt
git commit -m "feat(android): save notifications locally before acking in fetchAndDemux"
```

---

### Task 4: iOS — Modify `NotifyManager.fetchAndDemux()` to save locally

**Files:**
- Modify: `ios/SsdidWallet/Domain/Notify/NotifyManager.swift`
- Modify: `ios/SsdidWallet/App/ServiceContainer.swift`

**Step 1: Add `LocalNotificationStorage` property to `NotifyManager`**

In `ios/SsdidWallet/Domain/Notify/NotifyManager.swift`, add property and update init:

```swift
// Add property after existing properties (around line 22):
    private let localNotificationStorage: LocalNotificationStorage

// Update init (around line 38):
    init(api: NotifyApi, keychainManager: KeychainManager, localNotificationStorage: LocalNotificationStorage) {
        self.api = api
        self.keychainManager = keychainManager
        self.localNotificationStorage = localNotificationStorage
    }
```

**Step 2: Modify `fetchAndDemux()` to save locally before acking**

In the same file, replace the body of `fetchAndDemux()` (lines 144-176):

```swift
    func fetchAndDemux() async throws {
        let pending = try await fetchPending()
        guard !pending.isEmpty else { return }

        let identities = await identityProvider?() ?? []
        var mailboxToName: [String: String] = [:]
        for identity in identities {
            let mbxId = mailboxIdForDid(identity.did)
            mailboxToName[mbxId] = identity.name
        }

        let center = UNUserNotificationCenter.current()

        for notification in pending {
            let identityName = mailboxToName[notification.mailboxId]

            // Save locally first
            localNotificationStorage.save(LocalNotification(
                id: notification.notificationId,
                mailboxId: notification.mailboxId,
                identityName: identityName,
                payload: notification.payload,
                priority: notification.priority ?? "normal",
                receivedAt: notification.receivedAt ?? "",
                isRead: false
            ))

            let content = UNMutableNotificationContent()
            content.title = identityName.map { "SSDID — \($0)" } ?? "SSDID"
            content.body = notification.payload
            content.sound = .default

            let request = UNNotificationRequest(
                identifier: notification.notificationId,
                content: content,
                trigger: nil
            )
            try? await center.add(request)

            try? await ackPending(notificationId: notification.notificationId)
        }
    }
```

**Step 3: Update `ServiceContainer` to pass `LocalNotificationStorage`**

In `ios/SsdidWallet/App/ServiceContainer.swift`, add property and update init:

```swift
// Add property (after notifyManager declaration around line 17):
    let localNotificationStorage: LocalNotificationStorage

// In init(), before creating notifyMgr (around line 58):
        let localNotifStorage = LocalNotificationStorage()
        self.localNotificationStorage = localNotifStorage

        let notifyMgr = NotifyManager(
            api: httpClient.notifyApi(baseURL: Self.notifyBaseURL),
            keychainManager: keychain,
            localNotificationStorage: localNotifStorage
        )
```

**Step 4: Run `xcodegen generate` and build**

Run: `cd /Users/amirrudinyahaya/Workspace/ssdid-wallet/ios && xcodegen generate && xcodebuild build -scheme SsdidWallet -destination 'platform=iOS Simulator,name=iPhone 16' CODE_SIGNING_ALLOWED=NO -quiet 2>&1 | tail -5`
Expected: BUILD SUCCEEDED

**Step 5: Commit**

```bash
git add ios/SsdidWallet/Domain/Notify/NotifyManager.swift \
        ios/SsdidWallet/App/ServiceContainer.swift
git commit -m "feat(ios): save notifications locally before acking in fetchAndDemux"
```

---

### Task 5: Android — `NotificationsScreen` + ViewModel + navigation route

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/feature/notifications/NotificationsScreen.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/ui/navigation/Screen.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/ui/navigation/NavGraph.kt`

**Step 1: Add `Screen.Notifications` route**

In `android/app/src/main/java/my/ssdid/wallet/ui/navigation/Screen.kt`, add after `object TxHistory`:

```kotlin
    object Notifications : Screen("notifications")
```

**Step 2: Create `NotificationsScreen` composable + ViewModel**

Create `android/app/src/main/java/my/ssdid/wallet/feature/notifications/NotificationsScreen.kt`:

```kotlin
package my.ssdid.wallet.feature.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import my.ssdid.wallet.domain.notify.LocalNotification
import my.ssdid.wallet.domain.notify.LocalNotificationStorage
import my.ssdid.wallet.ui.theme.*
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val storage: LocalNotificationStorage
) : ViewModel() {

    val notifications = storage.allNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun markAsRead(id: String) {
        viewModelScope.launch { storage.markAsRead(id) }
    }

    fun markAllAsRead() {
        viewModelScope.launch { storage.markAllAsRead() }
    }

    fun delete(id: String) {
        viewModelScope.launch { storage.delete(id) }
    }
}

@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    viewModel: NotificationsViewModel = hiltViewModel()
) {
    val notifications by viewModel.notifications.collectAsState()
    val sorted = notifications.sortedByDescending { it.receivedAt }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .statusBarsPadding()
    ) {
        // Header
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Spacer(Modifier.width(4.dp))
            Text("Notifications", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (notifications.any { !it.isRead }) {
                TextButton(onClick = { viewModel.markAllAsRead() }) {
                    Text("Mark All Read", color = Accent, fontSize = 13.sp)
                }
            }
        }

        if (sorted.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(BgCard),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = TextSecondary
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("No notifications", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Notifications from services will appear here",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(sorted, key = { it.id }) { notification ->
                    NotificationRow(
                        notification = notification,
                        onTap = { viewModel.markAsRead(notification.id) },
                        onDelete = { viewModel.delete(notification.id) }
                    )
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

@Composable
private fun NotificationRow(
    notification: LocalNotification,
    onTap: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Danger)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text("Delete", color = BgPrimary, fontWeight = FontWeight.SemiBold)
            }
        },
        enableDismissFromStartToEnd = false
    ) {
        Card(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onTap),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = BgCard)
        ) {
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Unread dot
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (!notification.isRead) Accent else BgCard)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    if (notification.identityName != null) {
                        Text(
                            notification.identityName,
                            fontSize = 12.sp,
                            color = TextTertiary,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                    Text(
                        notification.payload,
                        fontSize = 14.sp,
                        color = TextPrimary,
                        maxLines = 3
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    relativeTime(notification.receivedAt),
                    fontSize = 12.sp,
                    color = TextTertiary
                )
            }
        }
    }
}

private fun relativeTime(isoTimestamp: String): String {
    if (isoTimestamp.isBlank()) return ""
    return try {
        val instant = java.time.Instant.parse(isoTimestamp)
        val now = java.time.Instant.now()
        val minutes = java.time.Duration.between(instant, now).toMinutes()
        when {
            minutes < 1 -> "now"
            minutes < 60 -> "${minutes}m ago"
            minutes < 1440 -> "${minutes / 60}h ago"
            minutes < 2880 -> "Yesterday"
            else -> "${minutes / 1440}d ago"
        }
    } catch (_: Exception) {
        ""
    }
}
```

**Step 3: Add route to NavGraph**

In `android/app/src/main/java/my/ssdid/wallet/ui/navigation/NavGraph.kt`:

Add import at top:
```kotlin
import my.ssdid.wallet.feature.notifications.NotificationsScreen
```

Add composable after the `TxHistory` composable (after line 305):
```kotlin
        composable(Screen.Notifications.route) {
            NotificationsScreen(onBack = { navController.popBackStack() })
        }
```

**Step 4: Build to verify**

Run: `cd /Users/amirrudinyahaya/Workspace/ssdid-wallet/android && ./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/feature/notifications/NotificationsScreen.kt \
        android/app/src/main/java/my/ssdid/wallet/ui/navigation/Screen.kt \
        android/app/src/main/java/my/ssdid/wallet/ui/navigation/NavGraph.kt
git commit -m "feat(android): add NotificationsScreen with ViewModel and navigation route"
```

---

### Task 6: iOS — `NotificationsScreen` + navigation route

**Files:**
- Create: `ios/SsdidWallet/Feature/Notifications/NotificationsScreen.swift`
- Modify: `ios/SsdidWallet/UI/Navigation/AppRouter.swift`
- Modify: `ios/SsdidWallet/UI/Navigation/ContentView.swift`

**Step 1: Add `.notifications` route**

In `ios/SsdidWallet/UI/Navigation/AppRouter.swift`, add to the `Route` enum (after `case txHistory`):

```swift
    case notifications
```

**Step 2: Add route destination in ContentView**

In `ios/SsdidWallet/UI/Navigation/ContentView.swift`, add a case to `routeDestination(for:)` (after the `.txHistory` case around line 127):

```swift
        case .notifications:
            NotificationsScreen()
```

**Step 3: Create `NotificationsScreen`**

Create `ios/SsdidWallet/Feature/Notifications/NotificationsScreen.swift`:

```swift
import SwiftUI

struct NotificationsScreen: View {
    @Environment(AppRouter.self) private var router
    @EnvironmentObject private var services: ServiceContainer

    var body: some View {
        let storage = services.localNotificationStorage
        let sorted = storage.notifications.sorted { $0.receivedAt > $1.receivedAt }

        VStack(spacing: 0) {
            // Header
            HStack {
                Button { router.pop() } label: {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(Color.textPrimary)
                }
                Spacer().frame(width: 12)
                Text("Notifications")
                    .font(.ssdidTitle)
                    .foregroundStyle(Color.textPrimary)
                Spacer()
                if storage.unreadCount > 0 {
                    Button {
                        storage.markAllAsRead()
                    } label: {
                        Text("Mark All Read")
                            .font(.system(size: 13))
                            .foregroundStyle(Color.ssdidAccent)
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 12)

            if sorted.isEmpty {
                Spacer()
                VStack(spacing: 0) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 20)
                            .fill(Color.bgCard)
                            .frame(width: 72, height: 72)
                        Image(systemName: "bell")
                            .font(.system(size: 32))
                            .foregroundStyle(Color.textSecondary)
                    }
                    Spacer().frame(height: 16)
                    Text("No notifications")
                        .font(.ssdidHeadline)
                        .foregroundStyle(Color.textPrimary)
                    Spacer().frame(height: 4)
                    Text("Notifications from services will appear here")
                        .font(.system(size: 14))
                        .foregroundStyle(Color.textSecondary)
                        .multilineTextAlignment(.center)
                }
                Spacer()
            } else {
                ScrollView {
                    LazyVStack(spacing: 6) {
                        ForEach(sorted) { notification in
                            notificationRow(notification, storage: storage)
                                .transition(.opacity)
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.bottom, 20)
                }
            }
        }
        .background(Color.bgPrimary)
    }

    @ViewBuilder
    private func notificationRow(_ notification: LocalNotification, storage: LocalNotificationStorage) -> some View {
        HStack(alignment: .top, spacing: 12) {
            // Unread dot
            Circle()
                .fill(notification.isRead ? Color.clear : Color.ssdidAccent)
                .frame(width: 8, height: 8)
                .padding(.top, 6)

            VStack(alignment: .leading, spacing: 2) {
                if let name = notification.identityName {
                    Text(name)
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(Color.textTertiary)
                }
                Text(notification.payload)
                    .font(.system(size: 14))
                    .foregroundStyle(Color.textPrimary)
                    .lineLimit(3)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            Text(relativeTime(notification.receivedAt))
                .font(.system(size: 12))
                .foregroundStyle(Color.textTertiary)
        }
        .padding(14)
        .background(Color.bgCard)
        .cornerRadius(12)
        .onTapGesture {
            storage.markAsRead(notification.id)
        }
        .swipeActions(edge: .trailing) {
            Button(role: .destructive) {
                storage.delete(notification.id)
            } label: {
                Label("Delete", systemImage: "trash")
            }
        }
    }

    private func relativeTime(_ isoTimestamp: String) -> String {
        guard !isoTimestamp.isEmpty else { return "" }
        let formatter = ISO8601DateFormatter()
        guard let date = formatter.date(from: isoTimestamp) else { return "" }
        let minutes = Int(Date().timeIntervalSince(date) / 60)
        switch minutes {
        case ..<1: return "now"
        case ..<60: return "\(minutes)m ago"
        case ..<1440: return "\(minutes / 60)h ago"
        case ..<2880: return "Yesterday"
        default: return "\(minutes / 1440)d ago"
        }
    }
}
```

**Step 4: Run `xcodegen generate` and build**

Run: `cd /Users/amirrudinyahaya/Workspace/ssdid-wallet/ios && xcodegen generate && xcodebuild build -scheme SsdidWallet -destination 'platform=iOS Simulator,name=iPhone 16' CODE_SIGNING_ALLOWED=NO -quiet 2>&1 | tail -5`
Expected: BUILD SUCCEEDED

**Step 5: Commit**

```bash
git add ios/SsdidWallet/Feature/Notifications/NotificationsScreen.swift \
        ios/SsdidWallet/UI/Navigation/AppRouter.swift \
        ios/SsdidWallet/UI/Navigation/ContentView.swift
git commit -m "feat(ios): add NotificationsScreen with navigation route"
```

---

### Task 7: Android — Bell icon with unread badge on WalletHomeScreen

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/feature/identity/WalletHomeScreen.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/ui/navigation/NavGraph.kt`

**Step 1: Add `onNotifications` callback to `WalletHomeScreen`**

In `android/app/src/main/java/my/ssdid/wallet/feature/identity/WalletHomeScreen.kt`:

Add imports:
```kotlin
import my.ssdid.wallet.domain.notify.LocalNotificationStorage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.foundation.shape.CircleShape
```

Add `onNotifications` parameter to the composable signature (after `onSettings`):
```kotlin
@Composable
fun WalletHomeScreen(
    onCreateIdentity: () -> Unit,
    onIdentityClick: (String) -> Unit,
    onScanQr: () -> Unit,
    onCredentials: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onNotifications: () -> Unit,
    viewModel: WalletHomeViewModel = hiltViewModel()
) {
```

**Step 2: Inject `LocalNotificationStorage` into ViewModel**

In the same file, update `WalletHomeViewModel`:

```kotlin
@HiltViewModel
class WalletHomeViewModel @Inject constructor(
    private val vault: Vault,
    private val localNotificationStorage: LocalNotificationStorage
) : ViewModel() {
    private val _identities = MutableStateFlow<List<Identity>>(emptyList())
    val identities = _identities.asStateFlow()

    val unreadNotificationCount = localNotificationStorage.unreadCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init { refresh() }

    fun refresh() {
        viewModelScope.launch { _identities.value = vault.listIdentities() }
    }
}
```

Add import:
```kotlin
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
```

**Step 3: Add bell icon with badge to the header**

Replace the header Row (lines 71-86) with:

```kotlin
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("IDENTITY WALLET", style = MaterialTheme.typography.labelMedium)
                Text("Self-Sovereign Digital ID", style = MaterialTheme.typography.headlineLarge)
            }
            val unreadCount by viewModel.unreadNotificationCount.collectAsState()
            // Notifications bell
            Box {
                IconButton(
                    onClick = onNotifications,
                    modifier = Modifier.semantics { contentDescription = "Notifications" }
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = TextSecondary)
                }
                if (unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-4).dp, y = 4.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Danger),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (unreadCount > 99) "99+" else unreadCount.toString(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = BgPrimary
                        )
                    }
                }
            }
            // Settings gear
            IconButton(
                onClick = onSettings,
                modifier = Modifier.semantics { contentDescription = "Settings" }
            ) {
                Text("\u2699", fontSize = 22.sp, color = TextSecondary)
            }
        }
```

**Step 4: Wire `onNotifications` in NavGraph**

In `android/app/src/main/java/my/ssdid/wallet/ui/navigation/NavGraph.kt`, update the WalletHome composable (lines 97-106):

```kotlin
        composable(Screen.WalletHome.route) {
            WalletHomeScreen(
                onCreateIdentity = { navController.navigate(Screen.CreateIdentity.createRoute()) },
                onIdentityClick = { keyId -> navController.navigate(Screen.IdentityDetail.createRoute(keyId)) },
                onScanQr = { navController.navigate(Screen.ScanQr.route) },
                onCredentials = { navController.navigate(Screen.Credentials.route) },
                onHistory = { navController.navigate(Screen.TxHistory.route) },
                onSettings = { navController.navigate(Screen.Settings.route) },
                onNotifications = { navController.navigate(Screen.Notifications.route) }
            )
        }
```

**Step 5: Build to verify**

Run: `cd /Users/amirrudinyahaya/Workspace/ssdid-wallet/android && ./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 6: Run tests**

Run: `cd /Users/amirrudinyahaya/Workspace/ssdid-wallet/android && ./gradlew :app:testDebugUnitTest`
Expected: All tests pass

**Step 7: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/feature/identity/WalletHomeScreen.kt \
        android/app/src/main/java/my/ssdid/wallet/ui/navigation/NavGraph.kt
git commit -m "feat(android): add bell icon with unread badge to WalletHome header"
```

---

### Task 8: iOS — Bell icon with unread badge on WalletHomeScreen

**Files:**
- Modify: `ios/SsdidWallet/Feature/Identity/WalletHomeScreen.swift`

**Step 1: Add bell icon with badge to the header**

In `ios/SsdidWallet/Feature/Identity/WalletHomeScreen.swift`, replace the header HStack (lines 12-29) with:

```swift
            // Header
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("IDENTITY WALLET")
                        .font(.ssdidCaption)
                        .foregroundStyle(Color.textSecondary)
                    Text("SSDID")
                        .font(.ssdidTitle)
                        .foregroundStyle(Color.textPrimary)
                }

                Spacer()

                // Notifications bell with badge
                Button { router.push(.notifications) } label: {
                    ZStack(alignment: .topTrailing) {
                        Image(systemName: "bell")
                            .font(.system(size: 22))
                            .foregroundStyle(Color.textSecondary)
                        if services.localNotificationStorage.unreadCount > 0 {
                            Text(services.localNotificationStorage.unreadCount > 99
                                 ? "99+"
                                 : "\(services.localNotificationStorage.unreadCount)")
                                .font(.system(size: 9, weight: .bold))
                                .foregroundStyle(Color.bgPrimary)
                                .frame(minWidth: 16, minHeight: 16)
                                .background(Color.danger)
                                .clipShape(Circle())
                                .offset(x: 6, y: -6)
                        }
                    }
                }

                Button { router.push(.settings) } label: {
                    Image(systemName: "gearshape")
                        .font(.system(size: 22))
                        .foregroundStyle(Color.textSecondary)
                }
            }
            .padding(20)
```

**Step 2: Run `xcodegen generate` and build**

Run: `cd /Users/amirrudinyahaya/Workspace/ssdid-wallet/ios && xcodegen generate && xcodebuild build -scheme SsdidWallet -destination 'platform=iOS Simulator,name=iPhone 16' CODE_SIGNING_ALLOWED=NO -quiet 2>&1 | tail -5`
Expected: BUILD SUCCEEDED

**Step 3: Commit**

```bash
git add ios/SsdidWallet/Feature/Identity/WalletHomeScreen.swift
git commit -m "feat(ios): add bell icon with unread badge to WalletHome header"
```

---

## Summary

| Task | Platform | Description |
|------|----------|-------------|
| 1 | Android | `LocalNotification` model + `LocalNotificationStorage` (DataStore) + tests |
| 2 | iOS | `LocalNotification` model + `LocalNotificationStorage` (JSON file) |
| 3 | Android | Modify `fetchAndDemux()` to save locally before ack |
| 4 | iOS | Modify `fetchAndDemux()` to save locally before ack |
| 5 | Android | `NotificationsScreen` + ViewModel + navigation route |
| 6 | iOS | `NotificationsScreen` + navigation route |
| 7 | Android | Bell icon with unread badge on WalletHome |
| 8 | iOS | Bell icon with unread badge on WalletHome |
