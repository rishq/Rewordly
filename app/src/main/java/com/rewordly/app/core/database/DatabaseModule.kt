package com.rewordly.app.core.database

import android.content.Context
import androidx.room.Room
import com.rewordly.app.core.database.dao.DailyActivityDao
import com.rewordly.app.core.database.dao.DataTransferDao
import com.rewordly.app.core.database.dao.GenerationHistoryDao
import com.rewordly.app.core.database.dao.ReviewLogDao
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
        Room.databaseBuilder(context, RewordlyDatabase::class.java, RewordlyDatabase.NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()

    @Provides
    fun provideWordDao(db: RewordlyDatabase): WordDao = db.wordDao()

    @Provides
    fun provideWordProgressDao(db: RewordlyDatabase): WordProgressDao = db.wordProgressDao()

    @Provides
    fun provideDailyActivityDao(db: RewordlyDatabase): DailyActivityDao = db.dailyActivityDao()

    @Provides
    fun provideReviewLogDao(db: RewordlyDatabase): ReviewLogDao = db.reviewLogDao()

    @Provides
    fun provideGenerationHistoryDao(db: RewordlyDatabase): GenerationHistoryDao = db.generationHistoryDao()

    @Provides
    fun provideDataTransferDao(db: RewordlyDatabase): DataTransferDao = db.dataTransferDao()
}
