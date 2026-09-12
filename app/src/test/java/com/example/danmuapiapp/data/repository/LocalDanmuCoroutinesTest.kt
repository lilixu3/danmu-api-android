package com.example.danmuapiapp.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDanmuCoroutinesTest {

    @Test
    fun `业务异常被收进 Result 而不是抛出`() = runBlocking {
        val result = runLocalDanmuRequest<String> { error("文件不存在") }

        assertTrue(result.isFailure)
        assertEquals("文件不存在", result.exceptionOrNull()?.message)
    }

    @Test
    fun `协程取消继续向上抛，不会被当成失败`() = runBlocking {
        var thrown = false
        try {
            // 复现"离开页面"场景：runCatching 会把它变成 Result.failure，
            // 于是界面显示 "Job was cancelled"；这里必须原样抛出。
            runLocalDanmuRequest<String> { throw CancellationException("Job was cancelled") }
        } catch (cancelled: CancellationException) {
            thrown = true
        }

        assertTrue("取消异常必须继续向上抛", thrown)
    }
}
