# Notifications

The SDK manages DIDComm-style notification mailboxes for receiving messages from issuers, verifiers, and other parties.

## Ensure Inbox is Registered

Register the device's notification inbox with the SSDID notify service. Returns the inbox ID if successful.

### Kotlin

```kotlin
val inboxId = sdk.notifications.ensureInboxRegistered()
println("Inbox registered: $inboxId")
```

### Swift

```swift
let inboxId = try await sdk.notifyManager.ensureInboxRegistered()
print("Inbox registered: \(inboxId ?? "none")")
```

## Create a Mailbox

Create a notification mailbox for a specific identity. This allows the identity to receive targeted notifications.

### Kotlin

```kotlin
val identity = sdk.identity.get(keyId)!!
sdk.notifications.createMailbox(identity)
println("Mailbox created for ${identity.did}")
```

### Swift

```swift
let identity = try await sdk.vault.getIdentity(keyId: keyId)!
try await sdk.notifyManager.createMailbox(identity: identity)
```

## Delete a Mailbox

### Kotlin

```kotlin
sdk.notifications.deleteMailbox(identity)
```

### Swift

```swift
try await sdk.notifyManager.deleteMailbox(identity: identity)
```

## Fetch and Process Notifications

Fetch pending notifications from the server and demultiplex them to the appropriate handlers.

### Kotlin

```kotlin
sdk.notifications.fetchAndDemux()
// Notifications are dispatched via the NotifyDispatcher
```

### Swift

```swift
try await sdk.notifyManager.fetchAndDemux()
```

## Acknowledge a Notification

Mark a notification as processed so it is not fetched again.

### Kotlin

```kotlin
sdk.notifications.ackPending(notificationId)
```

### Swift

```swift
try await sdk.notifyManager.ackPending(notificationId: notificationId)
```

## Push Notification Integration

Register a device token for push notifications. Call this when your app receives a new push token from FCM (Android) or APNs (iOS).

### Kotlin

```kotlin
// In your FirebaseMessagingService
override fun onNewToken(token: String) {
    runBlocking {
        sdk.notifications.updateDeviceToken("android", token)
    }
}
```

### Swift

```swift
// In AppDelegate
func application(_ application: UIApplication,
                 didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
    let token = deviceToken.map { String(format: "%02x", $0) }.joined()
    Task {
        try await sdk.notifyManager.updateDeviceToken(platform: "ios", token: token)
    }
}
```

## Notification Flow

1. Call `ensureInboxRegistered()` at app startup
2. Call `createMailbox(identity)` for each identity that should receive notifications
3. Call `updateDeviceToken()` when you receive a new push token
4. When a push arrives, call `fetchAndDemux()` to retrieve and dispatch the full notification
5. After processing, call `ackPending()` to acknowledge

## Local Notification Store

On Android, the SDK uses the `LocalNotificationStore` interface for persisting in-app notifications. This interface exposes reactive `Flow` properties for observing notifications.

```kotlin
interface LocalNotificationStore {
    val allNotifications: Flow<List<LocalNotification>>
    val unreadCount: Flow<Int>
    suspend fun save(notification: LocalNotification)
    suspend fun markAsRead(id: String)
    suspend fun markAllAsRead()
    suspend fun delete(id: String)
}
```

The default implementation is `LocalNotificationStorage` (DataStore-backed). You can provide a custom implementation via the builder's `localNotificationStore()` method.

On iOS, `LocalNotificationStorage` is a concrete `ObservableObject` class with `@Published` properties for SwiftUI reactivity.

## See Also

- [Identity Management](identity-management.md) -- managing identities
- [Custom Implementations](custom-implementations.md) -- custom NotifyStorage, NotifyDispatcher, and LocalNotificationStore
