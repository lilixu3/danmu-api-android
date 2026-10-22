package com.example.danmuapiapp.ui.screen.apitest

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.danmuapiapp.data.repository.DanmuPayloadInspector
import com.example.danmuapiapp.domain.model.DanmuDownloadFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 弹幕测试页「导出」子控制器：导出请求、载荷校验与待保存文件状态。
 *
 * 对外由 [ApiTestViewModel] 转发，行为与拆分前一致。
 */
internal class ApiTestExportController(
    private val scope: CoroutineScope,
    private val executor: ApiTestRequestExecutor,
    private val emitNotice: (String, Boolean) -> Unit,
    private val nextEventId: () -> Long
) {
    var isExportingDanmu by mutableStateOf(false)
        private set

    var preparedExport by mutableStateOf<DanmuExportPayload?>(null)
        private set

    fun consumePreparedExport(id: Long) {
        if (preparedExport?.id == id) preparedExport = null
    }

    fun onExportSaved(fileName: String) {
        emitNotice("已导出 $fileName", false)
    }

    fun onExportSaveFailed(message: String) {
        emitNotice(message, true)
    }

    fun prepareDanmuExport(
        baseUrl: String,
        insight: DanmuInsight,
        format: DanmuDownloadFormat
    ) {
        if (isExportingDanmu) return
        val base = executor.resolveApiBaseUrl(baseUrl)
        val target = insight.exportTarget
        if (base == null || target == null) {
            emitNotice("当前弹幕结果缺少可导出的请求信息", true)
            return
        }
        val url = runCatching { buildDanmuExportUrl(base, target, format) }
            .getOrElse { throwable ->
                emitNotice(throwable.message ?: "无法构建导出请求", true)
                return
            }

        scope.launch {
            isExportingDanmu = true
            val startedAt = System.currentTimeMillis()
            val result = executor.executeBytesGet(url)
            val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
            result.fold(
                onSuccess = { response ->
                    val responseSnippet = executor.textSnippet(response.bytes, response.contentType)
                    executor.recordSuccess(
                        scene = "弹幕测试/导出/${format.value}",
                        method = "GET",
                        url = url,
                        statusCode = response.code,
                        durationMs = elapsed,
                        body = responseSnippet
                    )
                    if (response.code !in 200..299) {
                        emitNotice(
                            favoriteErrorMessage(responseSnippet, "导出失败：HTTP ${response.code}"),
                            true
                        )
                    } else {
                        val inspection = withContext(Dispatchers.Default) {
                            DanmuPayloadInspector.inspect(
                                payload = response.bytes,
                                format = format,
                                contentType = response.contentType
                            )
                        }
                        if (!inspection.valid) {
                            emitNotice(inspection.error, true)
                        } else {
                            val normalizedBytes = withContext(Dispatchers.Default) {
                                normalizeDanmuExportPayload(response.bytes, format)
                            }
                            preparedExport = DanmuExportPayload(
                                id = nextEventId(),
                                bytes = normalizedBytes,
                                format = format,
                                fileName = buildDanmuExportFileName(
                                    animeTitle = insight.animeTitle,
                                    episodeTitle = insight.episodeTitle,
                                    target = target,
                                    format = format
                                ),
                                contentType = response.contentType.ifBlank { format.mimeType }
                            )
                        }
                    }
                },
                onFailure = { throwable ->
                    val message = throwable.message ?: "导出请求失败"
                    emitNotice(message, true)
                    executor.recordFailure(
                        scene = "弹幕测试/导出/${format.value}",
                        method = "GET",
                        url = url,
                        durationMs = elapsed,
                        message = message
                    )
                }
            )
            isExportingDanmu = false
        }
    }
}
