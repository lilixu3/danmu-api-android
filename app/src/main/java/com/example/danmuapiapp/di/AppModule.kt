package com.example.danmuapiapp.di

import com.example.danmuapiapp.data.repository.*
import com.example.danmuapiapp.domain.repository.*
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.example.danmuapiapp.data.network.GithubOutboundNetwork
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

    @Binds @Singleton
    abstract fun bindCacheRepository(impl: CacheRepositoryImpl): CacheRepository

    @Binds @Singleton
    abstract fun bindDanmuDownloadRepository(impl: DanmuDownloadRepositoryImpl): DanmuDownloadRepository

    @Binds @Singleton
    abstract fun bindLocalDanmuRepository(impl: LocalDanmuRepositoryImpl): LocalDanmuRepository
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton
    fun provideGithubApiQuota(@ApplicationContext context: Context): com.example.danmuapiapp.data.service.GithubApiQuota =
        com.example.danmuapiapp.data.service.GithubApiQuota.get(context)

    @Provides @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context, quota: com.example.danmuapiapp.data.service.GithubApiQuota): OkHttpClient {
        return GithubOutboundNetwork.createClient(context).newBuilder()
            .addInterceptor(com.example.danmuapiapp.data.network.GithubQuotaInterceptor(quota)).build()
    }
}
