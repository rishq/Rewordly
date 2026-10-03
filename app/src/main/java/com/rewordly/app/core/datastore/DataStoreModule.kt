package com.rewordly.app.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.rewordly.app.domain.repository.NotificationLedger
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.userPreferences: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.userPreferences
}

/**
 * The notification ledger shares the app's preference store: it holds two small bookkeeping values and
 * does not deserve a file of its own.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationLedgerModule {
    @Binds
    abstract fun bindNotificationLedger(impl: DataStoreNotificationLedger): NotificationLedger
}
