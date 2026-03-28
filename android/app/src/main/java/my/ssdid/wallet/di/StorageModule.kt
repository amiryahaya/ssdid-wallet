package my.ssdid.wallet.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import my.ssdid.sdk.domain.recovery.institutional.InstitutionalRecoveryStorage
import my.ssdid.sdk.domain.recovery.social.SocialRecoveryStorage
import my.ssdid.sdk.domain.vault.VaultStorage
import my.ssdid.sdk.platform.storage.DataStoreInstitutionalRecoveryStorage
import my.ssdid.sdk.platform.storage.DataStoreSocialRecoveryStorage
import my.ssdid.sdk.platform.storage.OnboardingStorage
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    /**
     * OnboardingStorage is backed by VaultStorage (DataStoreVaultStorage implements both).
     * The VaultStorage instance is provided by AppModule via SsdidSdk.
     */
    @Provides
    @Singleton
    fun provideOnboardingStorage(vaultStorage: VaultStorage): OnboardingStorage =
        vaultStorage as OnboardingStorage

    @Provides
    @Singleton
    fun provideSocialRecoveryStorage(@ApplicationContext context: Context): SocialRecoveryStorage =
        DataStoreSocialRecoveryStorage(context)

    @Provides
    @Singleton
    fun provideInstitutionalRecoveryStorage(@ApplicationContext context: Context): InstitutionalRecoveryStorage =
        DataStoreInstitutionalRecoveryStorage(context)
}
