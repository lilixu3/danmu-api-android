package com.example.danmuapiapp.data.repository

import android.util.JsonReader
import android.util.Log
import com.example.danmuapiapp.domain.model.DanmuDownloadFormat
import com.example.danmuapiapp.domain.model.DanmuFilePreview
import com.example.danmuapiapp.domain.model.DanmuPreviewItem
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.xml.sax.EntityResolver
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory
import kotlin.math.roundToLong

object DanmuFilePreviewParser {

    private const val TAG = "DanmuFilePreview"

    /** 流式 JSON 解析的保护上限（超出后回退到小预览），防止 OOM。 */
    private const val MAX_JSON_SAFE_BYTES = 10L * 1024L * 1024L // 10 MB

    fun parse(
        input: InputStream,
        format: DanmuDownloadFormat,
        fileName: String,
        relativePath: String,
        bytes: Long,
        previewLimit: Int = 500
    ): DanmuFilePreview {
        val safeLimit = previewLimit.coerceIn(1, 100_000)
        return when (format) {
            DanmuDownloadFormat.Xml -> parseXml(input, fileName, relativePath, bytes, safeLimit)
            DanmuDownloadFormat.Json -> parseJson(input, fileName, relativePath, bytes, safeLimit)
        }
    }

    fun count(payload: ByteArray, format: DanmuDownloadFormat): Int? {
        if (payload.isEmpty()) return 0
        return runCatching {
            payload.inputStream().use { input ->
                parse(
                    input = input,
                    format = format,
                    fileName = "",
                    relativePath = "",
                    bytes = payload.size.toLong(),
                    previewLimit = 1
                ).count
            }
        }.getOrNull()
    }

    // ─── XML ──────────────────────────────────────────────

    private fun parseXml(
        input: InputStream,
        fileName: String,
        relativePath: String,
        bytes: Long,
        previewLimit: Int
    ): DanmuFilePreview {
        val items = mutableListOf<DanmuPreviewItem>()
        var count = 0
        var parseError: String? = null

        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = false
        }
        // 安全特性：API 23/24 兼容 — disallow-doctype-decl 仅在 API 24+ 可用
        runCatching {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        runCatching {
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        }
        runCatching {
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }

        val handler = object : DefaultHandler() {
            private var inDanmu = false
            private var currentP = ""
            private val textBuilder = StringBuilder()

            override fun startElement(
                uri: String?,
                localName: String?,
                qName: String?,
                attributes: org.xml.sax.Attributes?
            ) {
                if (qName == "d" || localName == "d") {
                    inDanmu = true
                    currentP = attributes?.getValue("p").orEmpty()
                    textBuilder.setLength(0)
                    count++
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inDanmu) {
                    textBuilder.append(ch, start, length)
                }
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                if (qName == "d" || localName == "d") {
                    if (items.size < previewLimit) {
                        items += buildPreviewItem(
                            index = count,
                            p = currentP,
                            text = textBuilder.toString().trim()
                        )
                    }
                    inDanmu = false
                    currentP = ""
                    textBuilder.setLength(0)
                }
            }
        }

        try {
            val parser = factory.newSAXParser()
            // EntityResolver 阻止所有外部实体 — 对 API 23 (不支持 disallow-doctype-decl) 尤其重要
            parser.xmlReader.entityResolver = SecureEntityResolver
            parser.parse(input, handler)
        } catch (e: SAXParseException) {
            parseError = "XML 解析失败（第 ${e.lineNumber} 行）: ${e.message}"
            Log.w(TAG, parseError, e)
        } catch (e: Exception) {
            parseError = "读取 XML 文件失败: ${e.message}"
            Log.w(TAG, parseError, e)
        }

        return DanmuFilePreview(
            format = DanmuDownloadFormat.Xml,
            fileName = fileName,
            relativePath = relativePath,
            bytes = bytes,
            count = count,
            previewLimit = previewLimit,
            truncated = count > items.size,
            items = items,
            parseError = parseError
        )
    }

    // ─── JSON (streaming) ─────────────────────────────────

    private fun parseJson(
        input: InputStream,
        fileName: String,
        relativePath: String,
        bytes: Long,
        previewLimit: Int
    ): DanmuFilePreview {
        // 小文件走快速路径，大文件走流式路径
        if (bytes in 1..MAX_JSON_SAFE_BYTES) {
            return parseJsonSmall(input, fileName, relativePath, bytes, previewLimit)
        }
        return parseJsonStreaming(input, fileName, relativePath, bytes, previewLimit)
    }

    /** 小文件：全量读入后 JSONObject 解析（兼容性好，性能高）。 */
    private fun parseJsonSmall(
        input: InputStream,
        fileName: String,
        relativePath: String,
        bytes: Long,
        previewLimit: Int
    ): DanmuFilePreview {
        val raw = input.readBytes().toString(Charsets.UTF_8).removePrefix("\uFEFF").trim()
        if (raw.isBlank()) {
            return emptyJsonResult(fileName, relativePath, bytes, previewLimit)
        }
        val root = JSONTokener(raw).nextValue()
        val comments = extractCommentsArray(root)
        val explicitCount = (root as? JSONObject)
            ?.takeIf { it.has("count") }
            ?.optInt("count", -1)
            ?.takeIf { it >= 0 }
        val count = explicitCount ?: comments.length()
        val items = mutableListOf<DanmuPreviewItem>()
        val itemCount = comments.length()
        val limit = minOf(previewLimit, itemCount)
        for (i in 0 until limit) {
            val item = comments.opt(i) ?: continue
            items += jsonPreviewItem(index = i + 1, item = item)
        }
        return DanmuFilePreview(
            format = DanmuDownloadFormat.Json,
            fileName = fileName,
            relativePath = relativePath,
            bytes = bytes,
            count = count,
            previewLimit = previewLimit,
            truncated = itemCount > items.size || count > items.size,
            items = items
        )
    }

    /** 大文件：流式 JsonReader，安全不 OOM。 */
    private fun parseJsonStreaming(
        input: InputStream,
        fileName: String,
        relativePath: String,
        bytes: Long,
        previewLimit: Int
    ): DanmuFilePreview {
        val reader = JsonReader(InputStreamReader(input, Charsets.UTF_8))
        reader.isLenient = true

        return try {
            reader.use { r ->
                val token = r.peek()
                when (token) {
                    android.util.JsonToken.BEGIN_ARRAY -> {
                        streamJsonArray(r, fileName, relativePath, bytes, previewLimit)
                    }

                    android.util.JsonToken.BEGIN_OBJECT -> {
                        streamJsonObject(r, fileName, relativePath, bytes, previewLimit)
                    }

                    else -> emptyJsonResult(fileName, relativePath, bytes, previewLimit)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "流式 JSON 解析失败: ${e.message}", e)
            DanmuFilePreview(
                format = DanmuDownloadFormat.Json,
                fileName = fileName,
                relativePath = relativePath,
                bytes = bytes,
                count = 0,
                previewLimit = previewLimit,
                truncated = false,
                items = emptyList(),
                parseError = "JSON 解析失败: ${e.message}"
            )
        }
    }

    private fun streamJsonArray(
        reader: JsonReader,
        fileName: String,
        relativePath: String,
        bytes: Long,
        previewLimit: Int
    ): DanmuFilePreview {
        val items = mutableListOf<DanmuPreviewItem>()
        var count = 0
        reader.beginArray()
        while (reader.hasNext()) {
            val item = streamJsonItem(reader, count + 1)
            count++
            if (items.size < previewLimit) {
                items += item
            }
        }
        reader.endArray()
        return DanmuFilePreview(
            format = DanmuDownloadFormat.Json,
            fileName = fileName,
            relativePath = relativePath,
            bytes = bytes,
            count = maxOf(count, items.size),
            previewLimit = previewLimit,
            truncated = count > items.size,
            items = items
        )
    }

    private fun streamJsonObject(
        reader: JsonReader,
        fileName: String,
        relativePath: String,
        bytes: Long,
        previewLimit: Int
    ): DanmuFilePreview {
        var explicitCount: Int? = null
        var commentsArray: JsonReader? = null

        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            when {
                name == "count" -> {
                    try {
                        explicitCount = reader.nextInt()
                    } catch (_: Exception) {
                        reader.skipValue()
                    }
                }

                isCommentsKey(name) && commentsArray == null && reader.peek() == android.util.JsonToken.BEGIN_ARRAY -> {
                    // 找到 comments 数组，延迟处理（先读完 count）
                    commentsArray = reader
                    // 不在此处 beginArray，先跳过其他字段
                    reader.skipValue()
                }

                name == "data" && reader.peek() == android.util.JsonToken.BEGIN_OBJECT -> {
                    // 处理 {"data": {"comments": [...]}} 嵌套
                    val nested = streamJsonDataObject(reader, previewLimit)
                    if (nested.items.isNotEmpty() || nested.count > 0) {
                        return DanmuFilePreview(
                            format = DanmuDownloadFormat.Json,
                            fileName = fileName,
                            relativePath = relativePath,
                            bytes = bytes,
                            count = nested.count,
                            previewLimit = previewLimit,
                            truncated = nested.count > nested.items.size,
                            items = nested.items
                        )
                    }
                    // 没找到 comments，继续
                }

                else -> reader.skipValue()
            }
        }
        reader.endObject()

        // 如果有缓存的 comments 数组引用，流式读取
        if (commentsArray != null) {
            return streamJsonArray(commentsArray, fileName, relativePath, bytes, previewLimit)
                .copy(count = explicitCount ?: 0)
        }

        return emptyJsonResult(fileName, relativePath, bytes, previewLimit)
    }

    /** 处理 {\"data\": {\"comments\": [...]}} 嵌套。 */
    private fun streamJsonDataObject(
        reader: JsonReader,
        previewLimit: Int
    ): DanmuFilePreview {
        val items = mutableListOf<DanmuPreviewItem>()
        var count = 0
        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            if (isCommentsKey(name) && reader.peek() == android.util.JsonToken.BEGIN_ARRAY) {
                reader.beginArray()
                while (reader.hasNext()) {
                    val item = streamJsonItem(reader, count + 1)
                    count++
                    if (items.size < previewLimit) {
                        items += item
                    }
                }
                reader.endArray()
            } else {
                reader.skipValue()
            }
        }
        reader.endObject()
        return DanmuFilePreview(
            format = DanmuDownloadFormat.Json,
            fileName = "",
            relativePath = "",
            bytes = 0,
            count = maxOf(count, items.size),
            previewLimit = previewLimit,
            truncated = count > items.size,
            items = items
        )
    }

    private fun streamJsonItem(reader: JsonReader, index: Int): DanmuPreviewItem {
        if (reader.peek() == android.util.JsonToken.STRING) {
            return DanmuPreviewItem(index = index, text = reader.nextString())
        }
        var p = ""
        var text = ""
        var timeSeconds: Double? = null
        var mode = ""
        var color = ""
        var source = ""

        reader.beginObject()
        while (reader.hasNext()) {
            when (val name = reader.nextName()) {
                "p" -> p = reader.nextStringOrSkip()
                "m", "content", "text", "message" -> {
                    val v = reader.nextStringOrSkip()
                    if (v.isNotBlank() && text.isEmpty()) text = v
                }
                "timepoint" -> timeSeconds = reader.nextDoubleOrSkip()?.takeIf(Double::isFinite)
                "time" -> timeSeconds = reader.nextDoubleOrSkip()?.takeIf(Double::isFinite)
                "progress" -> timeSeconds = reader.nextDoubleOrSkip()
                    ?.takeIf(Double::isFinite)
                    ?.div(1000.0)
                "mode", "ct" -> {
                    val v = reader.nextStringOrSkip()
                    if (v.isNotBlank() && mode.isEmpty()) mode = v
                }
                "color" -> color = reader.nextStringOrSkip().trim()
                "source", "platform", "type" -> {
                    val v = reader.nextStringOrSkip()
                    if (v.isNotBlank() && source.isEmpty()) source = v
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return if (p.isNotBlank()) {
            buildPreviewItem(index = index, p = p, text = text)
        } else {
            DanmuPreviewItem(
                index = index,
                timeSeconds = timeSeconds,
                mode = mode,
                color = color,
                source = source,
                text = text
            )
        }
    }

    // ─── helpers ──────────────────────────────────────────

    private fun isCommentsKey(name: String): Boolean {
        return name == "comments" || name == "danmus" || name == "danmaku" ||
            name == "danmuku" || name == "d" || name == "danmu"
    }

    private fun JsonReader.nextStringOrSkip(): String {
        return try {
            nextString()
        } catch (_: Exception) {
            ""
        }
    }

    private fun JsonReader.nextDoubleOrSkip(): Double? {
        return try {
            val v = nextDouble()
            if (v.isFinite()) v else null
        } catch (_: Exception) {
            null
        }
    }

    private fun emptyJsonResult(
        fileName: String,
        relativePath: String,
        bytes: Long,
        previewLimit: Int
    ): DanmuFilePreview {
        return DanmuFilePreview(
            format = DanmuDownloadFormat.Json,
            fileName = fileName,
            relativePath = relativePath,
            bytes = bytes,
            count = 0,
            previewLimit = previewLimit,
            truncated = false,
            items = emptyList()
        )
    }

    /** 阻止所有外部实体的 EntityResolver — XXE 防护。 */
    private object SecureEntityResolver : EntityResolver {
        override fun resolveEntity(publicId: String?, systemId: String?): InputSource {
            return InputSource(StringReader(""))
        }
    }

    // ─── shared parsing ───────────────────────────────────

    private fun extractCommentsArray(root: Any?): JSONArray {
        return when (root) {
            is JSONArray -> root
            is JSONObject -> {
                root.optJSONArray("comments")
                    ?: root.optJSONArray("danmus")
                    ?: root.optJSONArray("danmaku")
                    ?: root.optJSONArray("danmuku")
                    ?: root.optJSONArray("d")
                    ?: root.optJSONArray("danmu")
                    ?: root.optJSONArray("data")
                    // data 可能是 {\"comments\": [...]} 嵌套
                    ?: root.optJSONObject("data")?.optJSONArray("comments")
                    ?: root.optJSONObject("data")?.optJSONArray("danmus")
                    ?: JSONArray()
            }
            else -> JSONArray()
        }
    }

    private fun jsonPreviewItem(index: Int, item: Any): DanmuPreviewItem {
        if (item !is JSONObject) {
            return DanmuPreviewItem(index = index, text = item.toString())
        }
        val p = item.optString("p").trim()
        val text = firstNonBlank(
            item.optString("m"),
            item.optString("content"),
            item.optString("text"),
            item.optString("message")
        )
        if (p.isNotBlank()) {
            return buildPreviewItem(index = index, p = p, text = text)
        }
        val timeSeconds = when {
            item.has("timepoint") -> item.optDouble("timepoint", Double.NaN).takeIf(Double::isFinite)
            item.has("time") -> item.optDouble("time", Double.NaN).takeIf(Double::isFinite)
            item.has("progress") -> item.optDouble("progress", Double.NaN)
                .takeIf(Double::isFinite)
                ?.div(1000.0)
            else -> null
        }
        return DanmuPreviewItem(
            index = index,
            timeSeconds = timeSeconds,
            mode = firstNonBlank(item.optString("mode"), item.optString("ct")),
            color = item.optString("color").trim(),
            source = firstNonBlank(
                item.optString("source"),
                item.optString("platform"),
                item.optString("type")
            ),
            text = text
        )
    }

    private fun buildPreviewItem(index: Int, p: String, text: String): DanmuPreviewItem {
        val parts = p.split(',')
        val timeSeconds = parts.getOrNull(0)?.toDoubleOrNull()
        val mode = parts.getOrNull(1).orEmpty()
        val color = when {
            parts.size == 4 -> parts.getOrNull(2).orEmpty()
            parts.size >= 8 -> parts.getOrNull(3).orEmpty()
            else -> parts.getOrNull(3) ?: parts.getOrNull(2).orEmpty()
        }
        val source = parts.lastOrNull()
            ?.trim()
            ?.removeSurrounding("[", "]")
            .orEmpty()
        return DanmuPreviewItem(
            index = index,
            timeSeconds = timeSeconds,
            mode = mode,
            color = color,
            source = source,
            text = text
        )
    }

    fun formatTime(seconds: Double?): String {
        if (seconds == null || !seconds.isFinite()) return "--:--.--"
        val totalCentis = (seconds.coerceAtLeast(0.0) * 100.0).roundToLong()
        val minutes = totalCentis / 6000
        val sec = (totalCentis % 6000) / 100
        val centis = totalCentis % 100
        return "%02d:%02d.%02d".format(minutes, sec, centis)
    }

    private fun firstNonBlank(vararg values: String): String {
        return values.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    }
}
