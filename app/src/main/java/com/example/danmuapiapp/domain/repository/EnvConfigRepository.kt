package com.example.danmuapiapp.domain.repository

import com.example.danmuapiapp.domain.model.EnvVarDef
import kotlinx.coroutines.flow.StateFlow

interface EnvConfigRepository {
    val envVars: StateFlow<Map<String, String>>
    val catalog: StateFlow<List<EnvVarDef>>
    val isCatalogLoading: StateFlow<Boolean>
    val rawContent: StateFlow<String>
    fun reload()

    /**
     * 以下读写均涉及磁盘 IO；Root 模式下还会执行 Root Shell 命令（单次可达数秒），
     * 因此全部为挂起函数，实现内部保证切换到 IO 调度器，禁止在主线程同步调用。
     */
    suspend fun readCurrentRawContent(): Result<String>
    suspend fun setValue(key: String, value: String)
    suspend fun deleteKey(key: String)
    suspend fun saveRawContent(content: String): Result<String>
    fun getEnvFilePath(): String
}
