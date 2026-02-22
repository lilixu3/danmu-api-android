package com.example.danmuapiapp.domain.repository

import com.example.danmuapiapp.domain.model.*
import kotlinx.coroutines.flow.StateFlow

interface RuntimeRepository {
    val runtimeState: StateFlow<RuntimeState>
    val logs: StateFlow<List<LogEntry>>
    fun startService()
    fun stopService()
    fun restartService()
    fun refreshLogs()
    fun applyServiceConfig(port: Int, token: String, restartIfRunning: Boolean = true)
    fun updatePort(port: Int)
    fun updateToken(token: String)
    fun updateVariant(variant: ApiVariant)
    fun updateRunMode(mode: RunMode)
    fun clearLogs()
    fun addLog(level: LogLevel, message: String)
}

interface CoreRepository {
    val coreInfoList: StateFlow<List<CoreInfo>>
    val isCoreInfoLoading: StateFlow<Boolean>
    val downloadProgress: StateFlow<CoreDownloadProgress>
    fun isCoreInstalled(variant: ApiVariant): Boolean
    fun refreshCoreInfo()
    suspend fun checkUpdate(variant: ApiVariant): GithubRelease?
    suspend fun checkAndMarkUpdate(variant: ApiVariant)
    suspend fun checkAllUpdates()
    suspend fun installCore(variant: ApiVariant): Result<Unit>
    suspend fun updateCore(variant: ApiVariant): Result<Unit>
    suspend fun deleteCore(variant: ApiVariant): Result<Unit>
    suspend fun rollbackCore(variant: ApiVariant, release: GithubRelease): Result<Unit>
    suspend fun fetchReleaseHistory(variant: ApiVariant): List<GithubRelease>
}

interface SettingsRepository {
    val githubProxy: StateFlow<String>
    val githubToken: StateFlow<String>
    val autoStart: StateFlow<Boolean>
    val keepAlive: StateFlow<Boolean>
    val nightMode: StateFlow<NightModePreference>
    val appDpiOverride: StateFlow<Int>
    val hideFromRecents: StateFlow<Boolean>
    val customRepo: StateFlow<String>
    val tokenVisible: StateFlow<Boolean>
    val fileLogEnabled: StateFlow<Boolean>
    fun setGithubProxy(proxy: String)
    fun setGithubToken(token: String)
    fun setAutoStart(enabled: Boolean)
    fun setKeepAlive(enabled: Boolean)
    fun setNightMode(mode: NightModePreference)
    fun setAppDpiOverride(dpi: Int)
    fun setHideFromRecents(enabled: Boolean)
    fun setCustomRepo(repo: String)
    fun setTokenVisible(visible: Boolean)
    fun setFileLogEnabled(enabled: Boolean)
    fun getIgnoredUpdateVersion(variant: ApiVariant): String?
    fun setIgnoredUpdateVersion(variant: ApiVariant, version: String?)
}

interface RequestRecordRepository {
    val records: StateFlow<List<RequestRecord>>
    suspend fun refreshFromService()
    fun addRecord(record: RequestRecord)
    fun clearRecords()
}

interface AccessControlRepository {
    suspend fun fetchSnapshot(): Result<DeviceAccessSnapshot>
    suspend fun saveConfig(
        config: DeviceAccessConfig,
        clearDevices: Boolean = false,
        clearRules: Boolean = false
    ): Result<DeviceAccessSnapshot>
}

interface AdminSessionRepository {
    val sessionState: StateFlow<AdminSessionState>
    fun refresh()
    suspend fun login(inputToken: String): Result<Unit>
    suspend fun logout()
    suspend fun setAdminTokenAndLogin(token: String): Result<Unit>
    fun currentAdminTokenOrNull(): String
}
