package my.ssdid.wallet.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import my.ssdid.wallet.domain.recovery.institutional.InstitutionalRecoveryStorage
import my.ssdid.wallet.domain.recovery.social.SocialRecoveryStorage
import my.ssdid.wallet.domain.vault.VaultStorage
import my.ssdid.wallet.platform.storage.DataStoreInstitutionalRecoveryStorage
import my.ssdid.wallet.platform.storage.DataStoreSocialRecoveryStorage
import my.ssdid.wallet.platform.storage.DataStoreVaultStorage
import my.ssdid.wallet.platform.storage.OnboardingStorage
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @Singleton
    fun provideDataStoreVaultStorage(@ApplicationContext context: Context): DataStoreVaultStorage =
        DataStoreVaultStorage(context)

    @Provides
    @Singleton
    fun provideVaultStorage(dataStore: DataStoreVaultStorage): VaultStorage = dataStore

    @Provides
    @Singleton
    fun provideOnboardingStorage(dataStore: DataStoreVaultStorage): OnboardingStorage = dataStore

    @Provides
    @Singleton
    fun provideSocialRecoveryStorage(@ApplicationContext context: Context): SocialRecoveryStorage =
        DataStoreSocialRecoveryStorage(context)

    @Provides
    @Singleton
    fun provideInstitutionalRecoveryStorage(@ApplicationContext context: Context): InstitutionalRecoveryStorage =
        DataStoreInstitutionalRecoveryStorage(context)
}
