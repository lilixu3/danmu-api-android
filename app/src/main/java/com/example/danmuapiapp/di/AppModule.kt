package com.example.danmuapiapp.di

import com.example.danmuapiapp.data.repository.*
import com.example.danmuapiapp.domain.repository.*
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton
    abstract fun bindRuntimeRepository(impl: RuntimeRepositoryImpl): RuntimeRepository

    @Binds @Singleton
    abstract fun bindCoreRepository(impl: CoreRepositoryImpl): CoreRepository

    @Binds @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds @Singleton
    abstract fun bindRequestRecordRepository(impl: RequestRecordRepositoryImpl): RequestRecordRepository

    @Binds @Singleton
    abstract fun bindAccessControlRepository(impl: AccessControlRepositoryImpl): AccessControlRepository

    @Binds @Singleton
    abstract fun bindEnvConfigRepository(impl: EnvConfigRepositoryImpl): EnvConfigRepository

    @Binds @Singleton
    abstract fun bindAdminSessionRepository(impl: AdminSessionRepositoryImpl): AdminSessionRepository
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}
