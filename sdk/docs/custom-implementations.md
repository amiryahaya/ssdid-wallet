# Custom Implementations

The SDK uses interface-based design throughout. You can replace any default implementation with your own by passing it to the builder.

## VaultStorage

Controls how identity and credential data is persisted. The default uses Android DataStore (Android) or file-based storage (iOS).

### Interface (Kotlin)

```kotlin
interface VaultStorage {
    suspend fun loadEntries(): List<VaultEntry>
    suspend fun saveEntries(entries: List<VaultEntry>)
}
```

### Example: Room Database

```kotlin
class RoomVaultStorage(private val dao: VaultDao) : VaultStorage {
    override suspend fun loadEntries(): List<VaultEntry> =
        dao.getAll().map { it.toVaultEntry() }

    override suspend fun saveEntries(entries: List<VaultEntry>) =
        dao.replaceAll(entries.map { it.toEntity() })
}

// Register with builder
val sdk = SsdidSdk.builder(context)
    .registryUrl("https://registry.ssdid.my")
    .vaultStorage(RoomVaultStorage(database.vaultDao()))
    .build()
```

### iOS Equivalent

On iOS, the `VaultStorage` protocol is implemented by `FileVaultStorage` by default. You can provide a custom Core Data-backed implementation through the builder's internal configuration.

## KeystoreManager

Controls hardware-backed key wrapping. The default uses Android Keystore (TEE/StrongBox) or iOS Keychain.

### Interface (Kotlin)

```kotlin
interface KeystoreManager {
    fun generateWrappingKey(alias: String)
    fun encrypt(alias: String, plaintext: ByteArray): ByteArray
    fun decrypt(alias: String, ciphertext: ByteArray): ByteArray
    fun deleteKey(alias: String)
    fun hasKey(alias: String): Boolean
}
```

```kotlin
val sdk = SsdidSdk.builder(context)
    .registryUrl("https://registry.ssdid.my")
    .keystoreManager(MyHsmKeystoreManager())
    .build()
```

## CryptoProvider

Implements cryptographic operations for a set of algorithms. The default `ClassicalProvider` handles Ed25519 and ECDSA. Add PQC support by registering the `PqcProvider` from the `ssdid-pqc` module.

### Interface (Kotlin)

```kotlin
interface CryptoProvider {
    fun supportsAlgorithm(algorithm: Algorithm): Boolean
    fun generateKeyPair(algorithm: Algorithm): KeyPairResult
    fun sign(algorithm: Algorithm, privateKey: ByteArray, data: ByteArray): ByteArray
    fun verify(algorithm: Algorithm, publicKey: ByteArray, signature: ByteArray, data: ByteArray): Boolean
}
```

### Interface (Swift)

```swift
protocol CryptoProvider {
    func supportsAlgorithm(_ algorithm: Algorithm) -> Bool
    func generateKeyPair(algorithm: Algorithm) throws -> KeyPairResult
    func sign(algorithm: Algorithm, privateKey: Data, data: Data) throws -> Data
    func verify(algorithm: Algorithm, publicKey: Data, signature: Data, data: Data) throws -> Bool
}
```

```kotlin
// Kotlin — custom provider for a hypothetical algorithm
class MyCustomProvider : CryptoProvider {
    override fun supportsAlgorithm(algorithm: Algorithm) =
        algorithm == Algorithm.ED25519 // example

    override fun generateKeyPair(algorithm: Algorithm): KeyPairResult { /* ... */ }
    override fun sign(algorithm: Algorithm, privateKey: ByteArray, data: ByteArray): ByteArray { /* ... */ }
    override fun verify(algorithm: Algorithm, publicKey: ByteArray, signature: ByteArray, data: ByteArray): Boolean { /* ... */ }
}

val sdk = SsdidSdk.builder(context)
    .registryUrl("https://registry.ssdid.my")
    .addCryptoProvider(MyCustomProvider())
    .build()
```

## ActivityRepository

Custom audit logging for identity operations.

### Interface (Kotlin)

```kotlin
interface ActivityRepository {
    suspend fun addActivity(record: ActivityRecord)
    suspend fun listActivities(): List<ActivityRecord>
    suspend fun listActivitiesForDid(did: String): List<ActivityRecord>
    suspend fun clearAll()
}
```

```kotlin
.activityRepository(MyFirebaseActivityRepository())
```

## NotifyStorage

Controls how notification mailbox state (keys, inbox IDs) is persisted.

```kotlin
.notifyStorage(MyEncryptedNotifyStorage())
```

## NotifyDispatcher

Controls how incoming notifications are routed to the app's UI layer.

```kotlin
.notifyDispatcher(NotifyDispatcher { type, payload ->
    // Route to your app's notification handling
    when (type) {
        "credential_offer" -> showCredentialOfferUI(payload)
        "presentation_request" -> showPresentationRequestUI(payload)
    }
})
```

## LocalNotificationStore (Android)

Controls how in-app notifications are persisted and observed. The `LocalNotificationStore` interface provides reactive `Flow` properties (`allNotifications`, `unreadCount`) for UI binding.

### Interface (Kotlin)

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

The default implementation is `LocalNotificationStorage`, which uses DataStore. To provide your own:

```kotlin
val sdk = SsdidSdk.builder(context)
    .registryUrl("https://registry.ssdid.my")
    .localNotificationStore(MyLocalNotificationStore())
    .build()
```

On iOS, `LocalNotificationStorage` is a concrete `ObservableObject` class with `@Published` properties for SwiftUI reactivity. It is not currently replaceable via the iOS builder.

## CredentialRepository / BundleStore

Override offline verification storage for custom persistence backends.

```kotlin
.credentialRepository(MyCredentialRepository())
.bundleStore(MyBundleStore())
```

## SocialRecoveryStorage / InstitutionalRecoveryStorage (Android)

Override the persistence layer for social and institutional recovery data. The defaults use DataStore.

```kotlin
.socialRecoveryStorage(MySocialRecoveryStorage())
.institutionalRecoveryStorage(MyInstitutionalRecoveryStorage())
```

## See Also

- [Configuration](configuration.md) -- all builder options
- [Migration Guide](migration-guide.md) -- migrating from direct domain usage
