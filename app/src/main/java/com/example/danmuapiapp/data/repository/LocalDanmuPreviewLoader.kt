package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.DanmuDownloadFormat
import com.example.danmuapiapp.domain.model.DanmuFilePreview
import com.example.danmuapiapp.domain.model.DanmuPreviewItem
import com.example.danmuapiapp.domain.model.LocalDanmuSourceRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads a preview back from the original import source. This avoids asking the core for a
 * potentially huge comment array and lets the local danmaku detail page reuse the download
 * page's preview panel.
 */
@Singleton
class LocalDanmuPreviewLoader @Inject constructor(
    private val importManager: LocalDanmuImportManager,
    private val sourceStore: LocalDanmuSourceStore
) {

    suspend fun load(resourceKey: String, previewLimit: Int = 500): Result<DanmuFilePreview> =
        withContext(Dispatchers.IO) {
            runLocalDanmuRequest {
                val ref = sourceStore.get(resourceKey)
                    ?: error("该资源没有可用的导入源，重新导入一次即可预览")
                importManager.withSourceStream(
                    uriText = ref.uri,
                    displayName = ref.displayName.ifBlank { "弹幕文件" }
                ) { input ->
                    parsePreview(ref, input, previewLimit)
                }.getOrThrow()
            }
        }

    private fun parsePreview(
        ref: LocalDanmuSourceRef,
        input: InputStream,
        previewLimit: Int
    ): DanmuFilePreview {
        val limit = previewLimit.coerceIn(1, 100_000)
        val formatHint = ref.format.lowercase(Locale.ROOT).ifBlank {
            ref.displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        }
        return when (formatHint) {
            "xml", "bili.xml" -> DanmuFilePreviewParser.parse(
                input = input,
                format = DanmuDownloadFormat.BiliXml,
                fileName = ref.displayName,
                relativePath = ref.displayName,
                bytes = ref.sizeBytes,
                previewLimit = limit
            )
            "json", "artplayer.json", "baha.json", "danuni.json", "ddplay.json", "dplayer.json", "vod.json" ->
                DanmuFilePreviewParser.parse(
                    input = input,
                    format = DanmuDownloadFormat.Json,
                    fileName = ref.displayName,
                    relativePath = ref.displayName,
                    bytes = ref.sizeBytes,
                    previewLimit = limit
                )
            else -> parseTextPreview(ref, input, limit, formatHint)
        }
    }

    private fun parseTextPreview(
        ref: LocalDanmuSourceRef,
        input: InputStream,
        previewLimit: Int,
        formatHint: String
    ): DanmuFilePreview {
        val text = readText(input)
        val lines = text.split(Regex("\\r?\\n"))
        val items = mutableListOf<DanmuPreviewItem>()
        var count = 0
        when (formatHint) {
            "ass", "ssa" -> {
                for (line in lines) {
                    val trimmed = line.trim()
                    if (!trimmed.startsWith("Dialogue:", ignoreCase = true)) continue
                    count++
                    if (items.size >= previewLimit) continue
                    val payload = trimmed.substringAfter(':').trim()
                    val parts = payload.split(',')
                    if (parts.size < 10) continue
                    val start = parseAssTime(parts[1])
                    val textValue = parts.drop(9).joinToString(",")
                        .replace(Regex("\\{[^}]*}"), "")
                        .replace("\\N", " ")
                        .trim()
                    if (textValue.isBlank()) continue
                    items += DanmuPreviewItem(
                        index = count,
                        timeSeconds = start,
                        mode = "1",
                        color = extractAssColor(payload),
                        text = textValue
                    )
                }
            }
            else -> {
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.isBlank() || trimmed.startsWith("#")) continue
                    count++
                    if (items.size >= previewLimit) continue
                    val delimiter = when {
                        trimmed.contains('\t') -> "\t"
                        trimmed.contains('|') -> "|"
                        else -> ","
                    }
                    val parts = trimmed.split(delimiter).map { it.trim() }
                    if (parts.isEmpty()) continue
                    val time = parseSimpleTime(parts.first())
                    val textValue = when {
                        parts.size >= 4 && parts[1].toIntOrNull() != null &&
                            parts[2].toIntOrNull() != null -> parts.drop(3).joinToString(" ").trim()
                        parts.size >= 2 -> parts.drop(1).joinToString(" ").trim()
                        else -> ""
                    }
                    if (textValue.isBlank()) continue
                    items += DanmuPreviewItem(
                        index = count,
                        timeSeconds = time,
                        mode = parts.getOrNull(1)?.takeIf { it.toIntOrNull() != null }.orEmpty().ifBlank { "1" },
                        color = parts.getOrNull(2)?.takeIf { it.toIntOrNull() != null }.orEmpty(),
                        text = textValue
                    )
                }
            }
        }
        val label = when (formatHint) {
            "ass" -> "ASS"
            "ssa" -> "SSA"
            "csv" -> "CSV"
            "txt" -> "TXT"
            else -> formatHint.uppercase(Locale.ROOT).ifBlank { "文本" }
        }
        return DanmuFilePreview(
            format = DanmuDownloadFormat.Json,
            formatLabelOverride = label,
            fileName = ref.displayName,
            relativePath = ref.displayName,
            bytes = ref.sizeBytes,
            count = count,
            previewLimit = previewLimit,
            truncated = count > items.size,
            items = items
        )
    }

    private fun readText(input: InputStream): String {
        val bytes = input.readBytes()
        return when {
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16LE)
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
                bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16BE)
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
                bytes.copyOfRange(3, bytes.size).toString(Charsets.UTF_8)
            else -> bytes.toString(Charsets.UTF_8)
        }.removePrefix("\uFEFF")
    }

    private fun parseAssTime(raw: String): Double? {
        val match = Regex("^(\\d+):(\\d{1,2}):(\\d{1,2})[.,](\\d{1,3})$").find(raw.trim())
            ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        val second = match.groupValues[3].toIntOrNull() ?: return null
        val fraction = match.groupValues[4].padEnd(3, '0').take(3).toIntOrNull() ?: 0
        return hour * 3600.0 + minute * 60.0 + second + fraction / 1000.0
    }

    private fun parseSimpleTime(raw: String): Double? {
        val value = raw.trim()
        value.toDoubleOrNull()?.let { return it }
        val parts = value.split(':')
        if (parts.size !in 2..3) return null
        val numbers = parts.map { it.toDoubleOrNull() ?: return null }
        return when (numbers.size) {
            2 -> numbers[0] * 60.0 + numbers[1]
            3 -> numbers[0] * 3600.0 + numbers[1] * 60.0 + numbers[2]
            else -> null
        }
    }

    private fun extractAssColor(payload: String): String {
        val match = Regex("\\\\c&H([0-9A-Fa-f]{6})&").find(payload) ?: return ""
        val bgr = match.groupValues[1]
        val rgb = bgr.substring(4, 6) + bgr.substring(2, 4) + bgr.substring(0, 2)
        return rgb.toIntOrNull(16)?.toString().orEmpty()
    }
}
