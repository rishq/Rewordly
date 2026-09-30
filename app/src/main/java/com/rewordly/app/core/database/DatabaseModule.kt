package com.rewordly.app.core.database

import android.content.Context
import androidx.room.Room
import com.rewordly.app.core.database.dao.WordDao
import com.rewordly.app.core.database.dao.WordProgressDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RewordlyDatabase =
        Room.databaseBuilder(context, RewordlyDatabase::class.java, RewordlyDatabase.NAME).build()

    @Provides
    fun provideWordDao(db: RewordlyDatabase): WordDao = db.wordDao()

    @Provides
    fun provideWordProgressDao(db: RewordlyDatabase): WordProgressDao = db.wordProgressDao()
}
