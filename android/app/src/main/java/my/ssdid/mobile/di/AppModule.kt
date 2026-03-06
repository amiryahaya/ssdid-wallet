package my.ssdid.mobile.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import my.ssdid.mobile.domain.SsdidClient
import my.ssdid.mobile.domain.crypto.ClassicalProvider
import my.ssdid.mobile.domain.crypto.CryptoProvider
import my.ssdid.mobile.domain.transport.SsdidHttpClient
import my.ssdid.mobile.domain.vault.Vault
import my.ssdid.mobile.domain.vault.VaultImpl
import my.ssdid.mobile.domain.vault.VaultStorage
import my.ssdid.mobile.domain.verifier.Verifier
import my.ssdid.mobile.domain.verifier.VerifierImpl
import my.ssdid.mobile.platform.keystore.AndroidKeystoreManager
import my.ssdid.mobile.platform.keystore.KeystoreManager
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

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
    fun providePqcProvider(): CryptoProvider {
        // PqcProvider requires native library; provide ClassicalProvider as stub until Task 4
        // TODO: Replace with PqcProvider() after KAZ-Sign JNI integration
        return ClassicalProvider()
    }

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
}
