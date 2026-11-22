package com.ivy.smsimport.di

import android.content.Context
import androidx.room.Room
import com.ivy.smsimport.data.SmsImportDao
import com.ivy.smsimport.data.SmsImportDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SmsImportModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SmsImportDatabase =
        Room.databaseBuilder(context, SmsImportDatabase::class.java, SmsImportDatabase.NAME)
            .addMigrations(SmsImportDatabase.MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideDao(database: SmsImportDatabase): SmsImportDao = database.dao()
}
