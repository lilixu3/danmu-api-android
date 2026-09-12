package com.example.danmuapiapp.data.repository

import android.content.Context
import com.example.danmuapiapp.data.service.RuntimePaths
import com.example.danmuapiapp.domain.model.LocalDanmuResource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * 直接读核心的本地弹幕缓存目录，拿到每个资源的元数据。
 *
 * 核心的列表接口在旧版本里会把每个文件（含全部评论）解析一遍，资源一多就要数秒，
 * 甚至把核心进程 OOM 掉；而元数据其实集中在每个文件的开头（评论数组在它后面），
 * 所以这里只读每个文件的头部 + 尾部若干字节，几十个文件也只是毫秒级。
 *
 * 读不到（Redis 存储模式、目录不存在、格式不符）时由调用方回退到接口方式。
 */
@Singleton
class LocalDanmuCoreCacheReader @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val HEAD_BYTES = 8 * 1024
        private const val TAIL_BYTES = 512
        private const val INDEX_FILE_NAME = "index.json"
        /** 共享存储上每个文件都有 FUSE 开销，少量并发读能明显缩短总耗时。 */
        private const val READ_CONCURRENCY = 6
    }

    fun cacheDir(): File = File(RuntimePaths.normalProjectDir(context), ".cache/local-danmu")

    suspend fun readResources(): Result<List<LocalDanmuResource>> = withContext(Dispatchers.IO) {
        runLocalDanmuRequest {
            val dir = cacheDir()
            if (!dir.isDirectory) error("核心本地弹幕缓存目录不存在")
            val files = dir.listFiles { file ->
                file.isFile && file.name.endsWith(".json") && file.name != INDEX_FILE_NAME
            }.orEmpty()
            if (files.isEmpty()) error("核心本地弹幕缓存目录为空")
            val resources = ArrayList<LocalDanmuResource>(files.size)
            val semaphore = Semaphore(READ_CONCURRENCY)
            val parsed = coroutineScope {
                files.map { file ->
                    async { semaphore.withPermit { readOne(file) } }
                }.awaitAll()
            }
            parsed.forEach { resource ->
                resource?.let(resources::add)
            }
            if (resources.isEmpty()) error("无法从核心缓存解析本地弹幕元数据")
            resources
        }
    }

    private fun readOne(file: File): LocalDanmuResource? = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            val length = raf.length()
            if (length <= 0L) return@use null
            val headBytes = readAt(raf, 0L, minOf(HEAD_BYTES.toLong(), length).toInt())
            val tailBytes = readAt(
                raf,
                (length - minOf(TAIL_BYTES.toLong(), length)).coerceAtLeast(0L),
                minOf(TAIL_BYTES.toLong(), length).toInt()
            )
            LocalDanmuCacheMetaParser.parse(
                head = headBytes.toString(Charsets.UTF_8),
                tail = tailBytes.toString(Charsets.UTF_8)
            )
        }
    }.getOrNull()

    private fun readAt(raf: RandomAccessFile, offset: Long, size: Int): ByteArray {
        val buffer = ByteArray(size)
        raf.seek(offset)
        raf.readFully(buffer)
        return buffer
    }
}
