package com.a2d.app.di

import android.content.Context
import com.a2d.app.data.local.A2DDatabase
import com.a2d.app.data.local.dao.TaskDao
import com.a2d.app.data.local.dao.SyncDao
import com.a2d.app.data.network.ApiClient
import com.a2d.app.data.network.TokenStore
import com.a2d.app.data.sync.SyncEngine
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

    @Provides
    fun provideSyncDao(database: A2DDatabase): SyncDao {
        return database.syncDao()
    }

    @Provides
    @Singleton
    fun provideApiClient(@ApplicationContext context: Context): ApiClient {
        return ApiClient(context)
    }

    @Provides
    @Singleton
    fun provideTokenStore(@ApplicationContext context: Context): TokenStore {
        return TokenStore(context)
    }

    @Provides
    @Singleton
    fun provideSyncEngine(
        @ApplicationContext context: Context,
        taskDao: TaskDao,
        syncDao: SyncDao,
        apiClient: ApiClient,
        tokenStore: TokenStore
    ): SyncEngine {
        return SyncEngine(
            context = context,
            taskDao = taskDao,
            syncDao = syncDao,
            apiClient = apiClient,
            tokenStore = tokenStore
        )
    }
}