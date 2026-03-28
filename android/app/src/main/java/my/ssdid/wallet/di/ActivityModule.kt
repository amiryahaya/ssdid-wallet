package my.ssdid.wallet.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import my.ssdid.sdk.domain.history.ActivityRepository
import my.ssdid.sdk.platform.storage.ActivityRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ActivityModule {

    @Provides
    @Singleton
    fun provideActivityRepository(@ApplicationContext context: Context): ActivityRepository =
        ActivityRepositoryImpl(context)
}
