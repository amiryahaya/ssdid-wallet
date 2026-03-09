package my.ssdid.wallet.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import my.ssdid.wallet.domain.SsdidClient
import my.ssdid.wallet.domain.backup.BackupManager
import my.ssdid.wallet.domain.credential.CredentialIssuanceManager
import my.ssdid.wallet.domain.device.DeviceInfoProvider
import my.ssdid.wallet.domain.device.DeviceManager
import my.ssdid.wallet.domain.crypto.ClassicalProvider
import my.ssdid.wallet.domain.crypto.CryptoProvider
import my.ssdid.wallet.domain.crypto.PqcProvider
import my.ssdid.wallet.domain.history.ActivityRepository
import my.ssdid.wallet.domain.recovery.RecoveryManager
import my.ssdid.wallet.domain.rotation.KeyRotationManager
import my.ssdid.wallet.domain.transport.SsdidHttpClient
import my.ssdid.wallet.domain.vault.Vault
import my.ssdid.wallet.domain.vault.VaultImpl
import my.ssdid.wallet.domain.vault.VaultStorage
import my.ssdid.wallet.domain.verifier.Verifier
import my.ssdid.wallet.domain.verifier.VerifierImpl
import my.ssdid.wallet.platform.biometric.BiometricAuthenticator
import my.ssdid.wallet.platform.keystore.AndroidKeystoreManager
import my.ssdid.wallet.platform.device.AndroidDeviceInfoProvider
import my.ssdid.wallet.domain.vault.KeystoreManager
import my.ssdid.wallet.domain.settings.SettingsRepository
import my.ssdid.wallet.platform.storage.DataStoreSettingsRepository
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideBiometricAuthenticator(): BiometricAuthenticator = BiometricAuthenticator()

    @Provides
    @Singleton
    fun provideKeystoreManager(): KeystoreManager = AndroidKeystoreManager()

    @Provides
    @Singleton
    @Named("classical")
    fun provideClassicalProvider(): CryptoProvider = ClassicalProvider()

    @Provides
    @Singleton
    @Named("pqc")
    fun providePqcProvider(): CryptoProvider = PqcProvider()

    @Provides
    @Singleton
    fun provideHttpClient(): SsdidHttpClient {
        return SsdidHttpClient(registryUrl = "https://registry.ssdid.my")
    }

    @Provides
    @Singleton
    fun provideVault(
        @Named("classical") classical: CryptoProvider,
        @Named("pqc") pqc: CryptoProvider,
        keystoreManager: KeystoreManager,
        storage: VaultStorage
    ): Vault = VaultImpl(classical, pqc, keystoreManager, storage)

    @Provides
    @Singleton
    fun provideVerifier(
        httpClient: SsdidHttpClient,
        @Named("classical") classical: CryptoProvider,
        @Named("pqc") pqc: CryptoProvider
    ): Verifier = VerifierImpl(httpClient.registry, classical, pqc)

    @Provides
    @Singleton
    fun provideSsdidClient(
        vault: Vault,
        verifier: Verifier,
        httpClient: SsdidHttpClient,
        activityRepo: ActivityRepository
    ): SsdidClient = SsdidClient(vault, verifier, httpClient, activityRepo)

    @Provides
    @Singleton
    fun provideRecoveryManager(
        vault: Vault,
        storage: VaultStorage,
        @Named("classical") classical: CryptoProvider,
        @Named("pqc") pqc: CryptoProvider,
        keystoreManager: KeystoreManager
    ): RecoveryManager = RecoveryManager(vault, storage, classical, pqc, keystoreManager)

    @Provides
    @Singleton
    fun provideKeyRotationManager(
        storage: VaultStorage,
        @Named("classical") classical: CryptoProvider,
        @Named("pqc") pqc: CryptoProvider,
        keystoreManager: KeystoreManager,
        activityRepo: ActivityRepository,
        ssdidClient: dagger.Lazy<SsdidClient>
    ): KeyRotationManager = KeyRotationManager(storage, classical, pqc, keystoreManager, activityRepo, ssdidClient)

    @Provides
    @Singleton
    fun provideBackupManager(
        vault: Vault,
        storage: VaultStorage,
        keystoreManager: KeystoreManager,
        activityRepo: ActivityRepository
    ): BackupManager = BackupManager(vault, storage, keystoreManager, activityRepo)

    @Provides
    @Singleton
    fun provideCredentialIssuanceManager(
        vault: Vault,
        httpClient: SsdidHttpClient
    ): CredentialIssuanceManager = CredentialIssuanceManager(vault, httpClient)

    @Provides
    @Singleton
    fun provideDeviceInfoProvider(): DeviceInfoProvider = AndroidDeviceInfoProvider()

    @Provides
    @Singleton
    fun provideDeviceManager(
        vault: Vault,
        httpClient: SsdidHttpClient,
        ssdidClient: dagger.Lazy<SsdidClient>,
        deviceInfoProvider: DeviceInfoProvider
    ): DeviceManager = DeviceManager(vault, httpClient, ssdidClient, deviceInfoProvider)

    @Provides
    @Singleton
    fun provideSettingsRepository(
        @ApplicationContext context: Context
    ): SettingsRepository = DataStoreSettingsRepository(context)
}
