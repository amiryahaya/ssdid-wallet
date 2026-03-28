# Migration Guide

This guide helps wallet app developers migrate from direct domain class usage to the SDK's public API.

## Overview

Before the SDK, wallet apps wired domain classes manually through Hilt modules (`AppModule`, `StorageModule`). The SDK replaces this with a single builder that wires everything internally.

## Before vs. After

### Initialization

**Before (Hilt AppModule):**

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun provideVaultStorage(@ApplicationContext ctx: Context): VaultStorage =
        DataStoreVaultStorage(ctx)

    @Provides @Singleton
    fun provideKeystoreManager(): KeystoreManager = AndroidKeystoreManager()

    @Provides @Singleton @Named("classical")
    fun provideClassical(): CryptoProvider = ClassicalProvider()

    @Provides @Singleton @Named("pqc")
    fun providePqc(): CryptoProvider = PqcProvider()

    @Provides @Singleton
    fun provideVault(
        @Named("classical") classical: CryptoProvider,
        @Named("pqc") pqc: CryptoProvider,
        ks: KeystoreManager,
        vs: VaultStorage
    ): Vault = VaultImpl(classical, pqc, ks, vs)

    // ... 20+ more @Provides methods
}
```

**After (SDK):**

```kotlin
val sdk = SsdidSdk.builder(applicationContext)
    .registryUrl("https://registry.ssdid.my")
    .addCryptoProvider(PqcProvider())
    .build()
```

### Identity Creation

**Before:**

```kotlin
@Inject lateinit var client: SsdidClient

val identity = client.initIdentity("Alice", Algorithm.ED25519).getOrThrow()
```

**After:**

```kotlin
val identity = sdk.identity.create("Alice", Algorithm.ED25519).getOrThrow()
```

### Credential Verification

**Before:**

```kotlin
@Inject lateinit var verifier: Verifier

val valid = verifier.verifyCredential(credential).getOrThrow()
```

**After:**

```kotlin
val valid = sdk.verifier.verifyCredential(credential).getOrThrow()
```

### Backup

**Before:**

```kotlin
@Inject lateinit var backupManager: BackupManager

val data = backupManager.createBackup(passphrase).getOrThrow()
```

**After:**

```kotlin
val data = sdk.backup.create(passphrase).getOrThrow()
```

## Gradual Migration Strategy

You do not need to migrate everything at once. The SDK exposes `internal*` properties for backward compatibility during the transition. These are annotated with `@InternalSsdidApi` -- a `@RequiresOptIn` annotation that produces a compile error if used without `@OptIn(InternalSsdidApi::class)`. This ensures you consciously acknowledge the instability of these APIs.

### Step 1: Replace Hilt Wiring with SDK

Replace your `AppModule` with a single SDK provider:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object SdkModule {
    @Provides @Singleton
    fun provideSdk(@ApplicationContext ctx: Context): SsdidSdk =
        SsdidSdk.builder(ctx)
            .registryUrl("https://registry.ssdid.my")
            .addCryptoProvider(PqcProvider())
            .build()
}
```

### Step 2: Bridge Internal Dependencies

If existing ViewModels inject domain classes directly, bridge them through the SDK's internal properties. These properties are annotated with `@InternalSsdidApi`, an opt-in annotation that signals they may change without notice. You must opt in explicitly:

```kotlin
@OptIn(InternalSsdidApi::class)
@Module
@InstallIn(SingletonComponent::class)
object BridgeModule {
    @Provides fun provideVault(sdk: SsdidSdk): Vault = sdk.internalVault
    @Provides fun provideVerifier(sdk: SsdidSdk): Verifier = sdk.internalVerifier
    @Provides fun provideClient(sdk: SsdidSdk): SsdidClient = sdk.internalClient
    @Provides fun provideHttpClient(sdk: SsdidSdk): SsdidHttpClient = sdk.internalHttpClient
    // Add more as needed
}
```

### Step 3: Migrate ViewModels One at a Time

Replace `@Inject` domain class dependencies with the SDK's typed API sub-objects:

```kotlin
// Before
class IdentityViewModel @Inject constructor(
    private val client: SsdidClient,
    private val vault: Vault
) : ViewModel() { /* ... */ }

// After
class IdentityViewModel @Inject constructor(
    private val sdk: SsdidSdk
) : ViewModel() {
    fun createIdentity(name: String) = viewModelScope.launch {
        sdk.identity.create(name, Algorithm.ED25519)
    }
}
```

### Step 4: Remove Bridge Module

Once all ViewModels use the SDK directly, delete the `BridgeModule` and the old `AppModule`.

## API Mapping Reference

| Old (Domain) | New (SDK API) |
|---|---|
| `SsdidClient.initIdentity()` | `sdk.identity.create()` |
| `Vault.listIdentities()` | `sdk.identity.list()` |
| `Vault.sign()` | `sdk.vault.sign()` |
| `Verifier.verifyCredential()` | `sdk.verifier.verifyCredential()` |
| `BackupManager.createBackup()` | `sdk.backup.create()` |
| `BackupManager.restoreBackup()` | `sdk.backup.restore()` |
| `RecoveryManager.generateRecoveryKey()` | `sdk.recovery.generateRecoveryKey()` |
| `KeyRotationManager.prepareRotation()` | `sdk.rotation.prepare()` |
| `KeyRotationManager.executeRotation()` | `sdk.rotation.execute()` |
| `NotifyManager.fetchAndDemux()` | `sdk.notifications.fetchAndDemux()` |
| `DeviceManager.initiatePairing()` | `sdk.device.initiatePairing()` |
| `OpenId4VciHandler.processOffer()` | `sdk.issuance.processOffer()` |
| `OpenId4VpHandler.processRequest()` | `sdk.presentation.processRequest()` |

## See Also

- [Getting Started](getting-started.md) -- fresh setup guide
- [Configuration](configuration.md) -- all builder options
- [Custom Implementations](custom-implementations.md) -- overriding defaults
