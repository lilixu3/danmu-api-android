package com.example.danmuapiapp.ui.screen.push

import com.example.danmuapiapp.data.util.DotEnvCodec
import com.example.danmuapiapp.domain.model.EnvVarDef
import com.example.danmuapiapp.domain.repository.EnvConfigRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PushTargetPersistenceTest {
    private val target = "https://tv.example:9978/action?do=refresh&type=danmaku&path=&token=a%2Fb"

    @Test
    fun `只有回读到写入值才能报告保存成功`() = runBlocking {
        val repository = MemoryConfigRepository()
        persistDefaultPushTarget(repository, target)
        assertEquals(target, DotEnvCodec.parse(repository.content)[PUSH_TARGET_CONFIG_KEY])
        assertEquals("keep", DotEnvCodec.parse(repository.content)["OTHER_KEY"])
    }

    @Test
    fun `底层吞掉写入错误时仍会报告保存失败`() = runBlocking {
        val repository = MemoryConfigRepository(ignoreWrites = true)
        assertTrue(runCatching { persistDefaultPushTarget(repository, target) }.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `不能回读文件时不能报告保存成功`() = runBlocking {
        val repository = MemoryConfigRepository(failReads = true)
        assertTrue(runCatching { persistDefaultPushTarget(repository, target) }.isFailure)
        assertEquals(0, repository.writeCount)
    }

    @Test
    fun `保存只更新目标项并清理重复项且保留其他配置和注释`() = runBlocking {
        val repository = MemoryConfigRepository().apply {
            content = "# keep comment\nOTHER_KEY=keep\nDANMU_PUSH_URL=old\nDANMU_PUSH_URL=older\n"
        }
        persistDefaultPushTarget(repository, target)
        assertTrue(repository.content.startsWith("# keep comment\nOTHER_KEY=keep\n"))
        assertEquals(1, repository.content.lineSequence().count { it.startsWith("DANMU_PUSH_URL=") })
    }

    private class MemoryConfigRepository(
        private val ignoreWrites: Boolean = false,
        private val failReads: Boolean = false
    ) : EnvConfigRepository {
        var content = "OTHER_KEY=keep\n"
        var writeCount = 0
        override val envVars = MutableStateFlow(emptyMap<String, String>())
        override val loadedEnvVars = MutableStateFlow<Map<String, String>?>(null)
        override val catalog = MutableStateFlow(emptyList<EnvVarDef>())
        override val isCatalogLoading = MutableStateFlow(false)
        override val rawContent = MutableStateFlow("")
        override fun reload() = Unit
        override suspend fun readCurrentRawContent(): Result<String> =
            if (failReads) Result.failure(IllegalStateException("read failed")) else Result.success(content)
        override suspend fun setValue(key: String, value: String) = error("Use the checked save API")
        override suspend fun deleteKey(key: String) = Unit
        override suspend fun saveRawContent(content: String): Result<String> {
            writeCount++
            if (!ignoreWrites) this.content = content
            return Result.success(content)
        }
        override fun getEnvFilePath(): String = "memory.env"
    }
}
