# Configuration

The SDK is configured through the `SsdidSdk.Builder`. Only `registryUrl()` is required; all other options have sensible defaults.

## Builder Options

### registryUrl (required)

The SSDID Registry endpoint for DID registration and resolution.

```kotlin
// Kotlin
SsdidSdk.builder(context)
    .registryUrl("https://registry.ssdid.my")
    .build()
```

```swift
// Swift
SsdidSdk.Builder()
    .registryUrl("https://registry.ssdid.my")
    .build()
```

### notifyUrl

URL for the push notification service. Defaults to the registry URL on Android.

```kotlin
// Kotlin
.notifyUrl("https://notify.ssdid.my")
```

```swift
// Swift
.notifyUrl("https://notify.ssdid.my")
```

### certificatePinning (Android only)

Enable TLS certificate pinning for specific hosts. Pins use the OkHttp format (`sha256/...`).

```kotlin
.certificatePinning(
    enabled = true,
    pins = listOf(
        "registry.ssdid.my" to listOf("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
    )
)
```

### keystoreManager

Replace the default `AndroidKeystoreManager` with a custom `KeystoreManager` implementation. Useful for testing or custom HSM integrations.

```kotlin
// Kotlin
.keystoreManager(MyCustomKeystoreManager())
```

### vaultStorage

Replace the default DataStore-backed vault persistence with a custom `VaultStorage` implementation.

```kotlin
// Kotlin
.vaultStorage(MyRoomVaultStorage(database))
```

### addCryptoProvider (Android)

Register an additional `CryptoProvider` for post-quantum algorithm support. The SDK auto-detects PQC providers by checking `supportsAlgorithm()` against PQC algorithm entries.

```kotlin
// Kotlin — add KAZ-Sign PQC support
import my.ssdid.sdk.pqc.PqcProvider

SsdidSdk.builder(context)
    .registryUrl("https://registry.ssdid.my")
    .addCryptoProvider(PqcProvider())
    .build()
```

On iOS, use `pqcProvider()` instead:

```swift
// Swift — add PQC support
import SsdidPqc

SsdidSdk.Builder()
    .registryUrl("https://registry.ssdid.my")
    .pqcProvider(PqcProvider())
    .build()
```

### logger

Provide a custom `SsdidLogger` implementation for debug logging. The default is `NoOpLogger`.

```kotlin
// Kotlin
.logger(object : SsdidLogger {
    override fun debug(tag: String, message: String) = Log.d(tag, message)
    override fun error(tag: String, message: String, throwable: Throwable?) =
        Log.e(tag, message, throwable)
})
```

```swift
// Swift
.logger(MyLogger())
```

### activityRepository

Replace the default activity/audit log storage with a custom `ActivityRepository`.

```kotlin
// Kotlin
.activityRepository(MyActivityRepository())
```

### settingsRepository

Replace the default `DataStoreSettingsRepository` with a custom implementation.

```kotlin
// Kotlin
.settingsRepository(MySettingsRepository())
```

### notifyStorage / notifyDispatcher

Override notification persistence and dispatch behavior.

```kotlin
// Kotlin
.notifyStorage(MyNotifyStorage())
.notifyDispatcher(MyNotifyDispatcher())
```

### credentialRepository / bundleStore

Override offline verification storage backends.

```kotlin
// Kotlin
.credentialRepository(MyCredentialRepository())
.bundleStore(MyBundleStore())
```

### socialRecoveryStorage (Android)

Replace the default DataStore-backed social recovery share storage with a custom `SocialRecoveryStorage` implementation.

```kotlin
// Kotlin
.socialRecoveryStorage(MySocialRecoveryStorage())
```

### institutionalRecoveryStorage (Android)

Replace the default DataStore-backed institutional recovery storage with a custom `InstitutionalRecoveryStorage` implementation.

```kotlin
// Kotlin
.institutionalRecoveryStorage(MyInstitutionalRecoveryStorage())
```

### localNotificationStore (Android)

Replace the default `LocalNotificationStorage` with a custom `LocalNotificationStore` implementation. The `LocalNotificationStore` interface exposes `allNotifications` and `unreadCount` as reactive `Flow` properties.

```kotlin
// Kotlin
.localNotificationStore(MyLocalNotificationStore())
```

### requireBiometric (iOS only)

Configure whether vault keys require biometric authentication. Defaults to `false` in DEBUG builds and `true` in release builds.

```swift
// Swift
SsdidSdk.Builder()
    .registryUrl("https://registry.ssdid.my")
    .requireBiometric(true)
    .build()
```

## Full Example

### Kotlin

```kotlin
val sdk = SsdidSdk.builder(applicationContext)
    .registryUrl("https://registry.ssdid.my")
    .notifyUrl("https://notify.ssdid.my")
    .certificatePinning(enabled = true, pins = listOf(
        "registry.ssdid.my" to listOf("sha256/abc123...")
    ))
    .addCryptoProvider(PqcProvider())
    .socialRecoveryStorage(MySocialRecoveryStorage())
    .institutionalRecoveryStorage(MyInstitutionalRecoveryStorage())
    .logger(MyLogger())
    .build()
```

### Swift

```swift
let sdk = SsdidSdk.Builder()
    .registryUrl("https://registry.ssdid.my")
    .notifyUrl("https://notify.ssdid.my")
    .pqcProvider(PqcProvider())
    .requireBiometric(true)
    .logger(MyLogger())
    .build()
```
