package com.example.danmuapiapp.data.repository

import kotlinx.coroutines.CancellationException

/**
 * 本地弹幕相关异步操作的 Result 包装。
 *
 * 与 `runCatching` 的区别：**协程取消继续向上抛**，只把真正的业务/IO 异常收进 Result。
 * 否则用户离开页面导致的取消会被当成失败，界面显示 "Job was cancelled"，
 * 批量导入也可能停在"上传中"。
 */
internal suspend inline fun <T> runLocalDanmuRequest(crossinline block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }
