package my.ssdid.wallet.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import my.ssdid.sdk.domain.vault.VaultStorage
import my.ssdid.sdk.domain.storage.OnboardingStorage
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
    fun provideOnboardingStorage(vaultStorage: VaultStorage): OnboardingStorage {
        check(vaultStorage is OnboardingStorage) {
            "VaultStorage must implement OnboardingStorage. If using a custom VaultStorage via SsdidSdk.builder(), also implement OnboardingStorage."
        }
        return vaultStorage
    }
}
