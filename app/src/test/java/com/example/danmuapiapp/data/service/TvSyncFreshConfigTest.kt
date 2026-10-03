package com.example.danmuapiapp.data.service

import com.example.danmuapiapp.domain.model.EnvVarDef
import com.example.danmuapiapp.domain.repository.EnvConfigRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TvSyncFreshConfigTest {
    private class Repository(var disk: Result<String>) : EnvConfigRepository {
        override val envVars = MutableStateFlow<Map<String, String>>(emptyMap())
        override val loadedEnvVars = MutableStateFlow<Map<String, String>?>(null)
        override val catalog = MutableStateFlow<List<EnvVarDef>>(emptyList())
        override val isCatalogLoading = MutableStateFlow(false)
        override val rawContent = MutableStateFlow("SOURCE_ORDER=stale")
        override fun reload() = Unit
        override suspend fun readCurrentRawContent() = disk
        override suspend fun setValue(key: String, value: String) = Unit
        override suspend fun deleteKey(key: String) = Unit
        override suspend fun saveRawContent(content: String) = Result.success(content)
        override fun getEnvFilePath() = "/current/.env"
    }
    @Test fun sendsDiskContentEvenWhenCachedStateIsStaleOrEmpty() = runBlocking {
        val repository = Repository(Result.success("SOURCE_ORDER=bahamut,tmdb\nTMDB_API_KEY=example\n"))
        assertEquals(repository.disk.getOrThrow(), readTvSyncEnvContent(repository))
        repository.rawContent.value = ""
        assertEquals(repository.disk.getOrThrow(), readTvSyncEnvContent(repository))
    }
    @Test fun failsInsteadOfSendingEmptyCommentOrIgnoringReadFailure() = runBlocking {
        assertTrue(runCatching { readTvSyncEnvContent(Repository(Result.success("# empty\n"))) }.isFailure)
        assertTrue(runCatching { readTvSyncEnvContent(Repository(Result.failure(Exception("read failed")))) }.isFailure)
    }
}
