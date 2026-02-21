package com.example.danmuapiapp.domain.repository

import com.example.danmuapiapp.domain.model.EnvVarDef
import kotlinx.coroutines.flow.StateFlow

interface EnvConfigRepository {
    val envVars: StateFlow<Map<String, String>>
    val catalog: StateFlow<List<EnvVarDef>>
    val isCatalogLoading: StateFlow<Boolean>
    val rawContent: StateFlow<String>
    fun reload()
    fun setValue(key: String, value: String)
    fun deleteKey(key: String)
    fun saveRawContent(content: String)
    fun getEnvFilePath(): String
}
