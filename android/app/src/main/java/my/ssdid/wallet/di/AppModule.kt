package my.ssdid.wallet.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import my.ssdid.wallet.domain.SsdidClient
import my.ssdid.wallet.domain.backup.BackupManager
import my.ssdid.wallet.domain.crypto.ClassicalProvider
import my.ssdid.wallet.domain.crypto.CryptoProvider
import my.ssdid.wallet.domain.crypto.PqcProvider
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
import my.ssdid.wallet.platform.keystore.KeystoreManager
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
        httpClient: SsdidHttpClient
    ): SsdidClient = SsdidClient(vault, verifier, httpClient)

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
        keystoreManager: KeystoreManager
    ): KeyRotationManager = KeyRotationManager(storage, classical, pqc, keystoreManager)

    @Provides
    @Singleton
    fun provideBackupManager(
        vault: Vault,
        storage: VaultStorage,
        keystoreManager: KeystoreManager
    ): BackupManager = BackupManager(vault, storage, keystoreManager)
}
