package com.a2d.app.di

import android.content.Context
import com.a2d.app.data.local.A2DDatabase
import com.a2d.app.data.local.dao.TaskDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): A2DDatabase {
        return A2DDatabase.getDatabase(context)
    }

    @Provides
    fun provideTaskDao(database: A2DDatabase): TaskDao {
        return database.taskDao()
    }
}
