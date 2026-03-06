package my.ssdid.mobile.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import my.ssdid.mobile.domain.vault.VaultStorage
import my.ssdid.mobile.platform.storage.DataStoreVaultStorage
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @Singleton
    fun provideVaultStorage(@ApplicationContext context: Context): VaultStorage =
        DataStoreVaultStorage(context)
}
