package my.ssdid.wallet.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import my.ssdid.wallet.domain.recovery.social.SocialRecoveryStorage
import my.ssdid.wallet.domain.vault.VaultStorage
import my.ssdid.wallet.platform.storage.DataStoreSocialRecoveryStorage
import my.ssdid.wallet.platform.storage.DataStoreVaultStorage
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @Singleton
    fun provideVaultStorage(@ApplicationContext context: Context): VaultStorage =
        DataStoreVaultStorage(context)

    @Provides
    @Singleton
    fun provideSocialRecoveryStorage(@ApplicationContext context: Context): SocialRecoveryStorage =
        DataStoreSocialRecoveryStorage(context)
}
