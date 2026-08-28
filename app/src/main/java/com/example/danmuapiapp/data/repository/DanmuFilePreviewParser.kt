package com.example.danmuapiapp.data.repository

import android.util.JsonReader
import android.util.Log
import com.example.danmuapiapp.domain.model.DanmuDownloadFormat
import com.example.danmuapiapp.domain.model.DanmuPayloadKind
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
        return when (format.payloadKind) {
            DanmuPayloadKind.Xml -> parseXml(input, fileName, relativePath, bytes, safeLimit)
                .copy(format = format)
            DanmuPayloadKind.Json -> parseJson(input, fileName, relativePath, bytes, safeLimit, format)
                .copy(format = format)
            DanmuPayloadKind.Text -> parseAssText(input, fileName, relativePath, bytes, safeLimit)
                .copy(format = format)
            DanmuPayloadKind.Binary -> error("${format.label} 是二进制格式，不支持内容预览")
        }
    }

    /**
     * ASS 文本预览：逐行解析 Dialogue 事件行。
     * 行格式：Dialogue: Layer,Start,End,Style,Name,MarginL,MarginR,MarginV,Effect,Text
     */
    private fun parseAssText(
        input: InputStream,
        fileName: String,
        relativePath: String,
        bytes: Long,
        previewLimit: Int
    ): DanmuFilePreview {
        val items = mutableListOf<DanmuPreviewItem>()
        var count = 0
        var truncated = false

        val startSeconds: (String) -> Double? = { token ->
            val pieces = token.trim().split(':')
            runCatching {
                when (pieces.size) {
                    3 -> pieces[0].toInt() * 3600 + pieces[1].toInt() * 60 + pieces[2].toDouble()
                    2 -> pieces[0].toInt() * 60 + pieces[1].toDouble()
                    else -> null
                }
            }.getOrNull()
        }

        input.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (raw in lines) {
                if (!raw.startsWith("Dialogue:")) continue
                count++
                if (items.size >= previewLimit) {
                    truncated = true
                    continue
                }
                val body = raw.removePrefix("Dialogue:").trim()
                // Text 字段本身可含逗号，固定前 9 段后剩余整体为文本
                val parts = body.split(',').toMutableList()
                if (parts.size < 10) continue
                val text = parts.drop(9).joinToString(",")
                val style = parts.getOrNull(3).orEmpty()
                val timeSeconds = startSeconds(parts.getOrNull(1).orEmpty())
                items.add(
                    DanmuPreviewItem(
                        index = items.size,
                        timeSeconds = timeSeconds,
                        mode = style,
                        text = text,
                    )
                )
            }
        }

        return DanmuFilePreview(
            format = DanmuDownloadFormat.Ass,
            fileName = fileName,
            relativePath = relativePath,
            bytes = bytes,
            count = count,
            previewLimit = previewLimit,
            truncated = truncated,
            items = items,
        )
    }

    fun count(payload: ByteArray, format: DanmuDownloadFormat): Int? {
        if (!format.supportsPreview) return null
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
        previewLimit: Int,
        format: DanmuDownloadFormat
    ): DanmuFilePreview {
        // 小文件走快速路径，大文件走流式路径
        if (bytes in 1..MAX_JSON_SAFE_BYTES) {
            return parseJsonSmall(input, fileName, relativePath, bytes, previewLimit, format)
        }
        return parseJsonStreaming(input, fileName, relativePath, bytes, previewLimit, format)
    }

    /** 小文件：全量读入后 JSONObject 解析（兼容性好，性能高）。 */
    private fun parseJsonSmall(
        input: InputStream,
        fileName: String,
        relativePath: String,
        bytes: Long,
        previewLimit: Int,
        format: DanmuDownloadFormat
    ): DanmuFilePreview {
        val raw = input.readBytes().toString(Charsets.UTF_8).removePrefix("\uFEFF").trim()
        if (raw.isBlank()) {
            return emptyJsonResult(fileName, relativePath, bytes, previewLimit)
        }
        val root = JSONTokener(raw).nextValue()
        val comments = extractCommentsArray(root)
        val explicitCount = extractExplicitCount(root, format)
        val count = explicitCount ?: comments.length()
        val items = mutableListOf<DanmuPreviewItem>()
        val itemCount = comments.length()
        val limit = minOf(previewLimit, itemCount)
        for (i in 0 until limit) {
            val item = comments.opt(i) ?: continue
            items += jsonPreviewItem(index = i + 1, item = item, format = format)
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
        previewLimit: Int,
        format: DanmuDownloadFormat
    ): DanmuFilePreview {
        val reader = JsonReader(InputStreamReader(input, Charsets.UTF_8))
        reader.isLenient = true

        return try {
            reader.use { r ->
                when (r.peek()) {
                    android.util.JsonToken.BEGIN_ARRAY ->
                        streamJsonArray(r, fileName, relativePath, bytes, previewLimit, format)
                    android.util.JsonToken.BEGIN_OBJECT ->
                        streamJsonObject(r, fileName, relativePath, bytes, previewLimit, format)
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
        previewLimit: Int,
        format: DanmuDownloadFormat
    ): DanmuFilePreview {
        val streamed = readJsonArray(reader, previewLimit, format)
        return DanmuFilePreview(
            format = DanmuDownloadFormat.Json,
            fileName = fileName,
            relativePath = relativePath,
            bytes = bytes,
            count = streamed.count,
            previewLimit = previewLimit,
            truncated = streamed.count > streamed.items.size,
            items = streamed.items
        )
    }

    private fun streamJsonObject(
        reader: JsonReader,
        fileName: String,
        relativePath: String,
        bytes: Long,
        previewLimit: Int,
        format: DanmuDownloadFormat
    ): DanmuFilePreview {
        var explicitCount: Int? = null
        var streamed: StreamedJsonItems? = null

        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            when {
                isCountKey(name) -> readIntOrNull(reader)?.let { value ->
                    if (value >= 0) explicitCount = value
                }
                isCommentsKey(name) && reader.peek() == android.util.JsonToken.BEGIN_ARRAY -> {
                    if (streamed == null) {
                        streamed = readJsonArray(reader, previewLimit, format)
                    } else {
                        reader.skipValue()
                    }
                }
                name == "data" -> when (reader.peek()) {
                    android.util.JsonToken.BEGIN_ARRAY -> {
                        if (streamed == null) {
                            streamed = readJsonArray(reader, previewLimit, format)
                        } else {
                            reader.skipValue()
                        }
                    }
                    android.util.JsonToken.BEGIN_OBJECT -> {
                        val nested = streamJsonDataObject(reader, previewLimit, format)
                        if (streamed == null) streamed = nested.first
                        if (explicitCount == null) explicitCount = nested.second
                    }
                    else -> reader.skipValue()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        val result = streamed ?: StreamedJsonItems(emptyList(), 0)
        val count = explicitCount ?: result.count
        return DanmuFilePreview(
            format = DanmuDownloadFormat.Json,
            fileName = fileName,
            relativePath = relativePath,
            bytes = bytes,
            count = count,
            previewLimit = previewLimit,
            truncated = count > result.items.size || result.count > result.items.size,
            items = result.items
        )
    }

    private fun streamJsonDataObject(
        reader: JsonReader,
        previewLimit: Int,
        format: DanmuDownloadFormat
    ): Pair<StreamedJsonItems?, Int?> {
        var streamed: StreamedJsonItems? = null
        var explicitCount: Int? = null
        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            when {
                isCountKey(name) -> readIntOrNull(reader)?.let { value ->
                    if (value >= 0) explicitCount = value
                }
                isCommentsKey(name) && reader.peek() == android.util.JsonToken.BEGIN_ARRAY -> {
                    if (streamed == null) {
                        streamed = readJsonArray(reader, previewLimit, format)
                    } else {
                        reader.skipValue()
                    }
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return streamed to explicitCount
    }

    private fun readJsonArray(
        reader: JsonReader,
        previewLimit: Int,
        format: DanmuDownloadFormat
    ): StreamedJsonItems {
        val items = mutableListOf<DanmuPreviewItem>()
        var count = 0
        reader.beginArray()
        while (reader.hasNext()) {
            count++
            val item = streamJsonItem(reader, count, format)
            if (items.size < previewLimit) items += item
        }
        reader.endArray()
        return StreamedJsonItems(items = items, count = count)
    }

    private fun streamJsonItem(
        reader: JsonReader,
        index: Int,
        format: DanmuDownloadFormat
    ): DanmuPreviewItem {
        when (reader.peek()) {
            android.util.JsonToken.STRING -> return DanmuPreviewItem(index = index, text = reader.nextString())
            android.util.JsonToken.BEGIN_ARRAY -> return streamJsonTuple(reader, index, format)
            android.util.JsonToken.BEGIN_OBJECT -> Unit
            else -> {
                val value = reader.nextStringOrSkip()
                return DanmuPreviewItem(index = index, text = value)
            }
        }

        var p = ""
        var text = ""
        var timeSeconds: Double? = null
        var mode = ""
        var color = ""
        var source = ""

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "p" -> p = reader.nextStringOrSkip()
                "m", "content", "text", "message" -> {
                    val value = reader.nextStringOrSkip()
                    if (value.isNotBlank() && text.isEmpty()) text = value
                }
                "timepoint" -> timeSeconds = reader.nextDoubleOrSkip()?.takeIf(Double::isFinite)
                "time" -> timeSeconds = normalizeTime(reader.nextDoubleOrSkip(), format)
                "progress" -> timeSeconds = reader.nextDoubleOrSkip()
                    ?.takeIf(Double::isFinite)
                    ?.div(1000.0)
                "mode", "ct", "position" -> {
                    val value = reader.nextStringOrSkip()
                    if (value.isNotBlank() && mode.isEmpty()) mode = value
                }
                "color" -> color = reader.nextStringOrSkip().trim()
                "source", "platform", "type" -> {
                    val value = reader.nextStringOrSkip()
                    if (value.isNotBlank() && source.isEmpty()) source = value
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
                mode = normalizeMode(mode, format),
                color = color,
                source = source,
                text = text
            )
        }
    }

    private fun streamJsonTuple(
        reader: JsonReader,
        index: Int,
        format: DanmuDownloadFormat
    ): DanmuPreviewItem {
        val values = mutableListOf<String>()
        reader.beginArray()
        while (reader.hasNext()) {
            val value = when (reader.peek()) {
                android.util.JsonToken.STRING,
                android.util.JsonToken.NUMBER -> reader.nextString()
                android.util.JsonToken.BOOLEAN -> reader.nextBoolean().toString()
                android.util.JsonToken.NULL -> {
                    reader.nextNull()
                    ""
                }
                else -> {
                    reader.skipValue()
                    ""
                }
            }
            values += value
        }
        reader.endArray()
        return tuplePreviewItem(index, values, format)
    }

    private data class StreamedJsonItems(
        val items: List<DanmuPreviewItem>,
        val count: Int
    )

    // ─── helpers ──────────────────────────────────────────

    private fun isCommentsKey(name: String): Boolean {
        return name == "comments" || name == "danmus" || name == "danmaku" ||
            name == "danmuku" || name == "d" || name == "danmu"
    }

    private fun isCountKey(name: String): Boolean {
        return name == "count" || name == "totalCount" || name == "danum"
    }

    private fun readIntOrNull(reader: JsonReader): Int? {
        return when (reader.peek()) {
            android.util.JsonToken.NUMBER,
            android.util.JsonToken.STRING -> reader.nextString().toIntOrNull()
            else -> {
                reader.skipValue()
                null
            }
        }
    }

    private fun JsonReader.nextStringOrSkip(): String {
        return when (peek()) {
            android.util.JsonToken.STRING,
            android.util.JsonToken.NUMBER -> runCatching { nextString() }.getOrDefault("")
            android.util.JsonToken.BOOLEAN -> nextBoolean().toString()
            android.util.JsonToken.NULL -> {
                nextNull()
                ""
            }
            else -> {
                skipValue()
                ""
            }
        }
    }

    private fun JsonReader.nextDoubleOrSkip(): Double? {
        return when (peek()) {
            android.util.JsonToken.NUMBER,
            android.util.JsonToken.STRING -> runCatching { nextString().toDouble() }
                .getOrNull()
                ?.takeIf(Double::isFinite)
            else -> {
                skipValue()
                null
            }
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
                    ?: root.optJSONObject("data")?.optJSONArray("danmaku")
                    ?: root.optJSONObject("data")?.optJSONArray("danmuku")
                    ?: root.optJSONObject("data")?.optJSONArray("danmu")
                    ?: JSONArray()
            }
            else -> JSONArray()
        }
    }

    private fun extractExplicitCount(root: Any?, format: DanmuDownloadFormat): Int? {
        val obj = root as? JSONObject ?: return null
        val raw = when (format) {
            DanmuDownloadFormat.BahaJson -> obj.optJSONObject("data")?.optInt("totalCount", -1)
            DanmuDownloadFormat.VodJson -> obj.optInt("danum", -1)
            DanmuDownloadFormat.Json,
            DanmuDownloadFormat.DdplayJson -> obj.optInt("count", -1)
            else -> null
        }
        return raw?.takeIf { it >= 0 }
    }

    private fun jsonPreviewItem(
        index: Int,
        item: Any,
        format: DanmuDownloadFormat
    ): DanmuPreviewItem {
        if (item is JSONArray) {
            val values = (0 until item.length()).map { position -> item.optString(position) }
            return tuplePreviewItem(index, values, format)
        }
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
            item.has("time") -> normalizeTime(
                item.optDouble("time", Double.NaN).takeIf(Double::isFinite),
                format
            )
            item.has("progress") -> item.optDouble("progress", Double.NaN)
                .takeIf(Double::isFinite)
                ?.div(1000.0)
            else -> null
        }
        return DanmuPreviewItem(
            index = index,
            timeSeconds = timeSeconds,
            mode = normalizeMode(
                firstNonBlank(
                    item.optString("mode"),
                    item.optString("ct"),
                    item.optString("position")
                ),
                format
            ),
            color = item.optString("color").trim(),
            source = firstNonBlank(
                item.optString("source"),
                item.optString("platform"),
                item.optString("type")
            ),
            text = text
        )
    }

    private fun tuplePreviewItem(
        index: Int,
        values: List<String>,
        format: DanmuDownloadFormat
    ): DanmuPreviewItem {
        if (format != DanmuDownloadFormat.DplayerJson && format != DanmuDownloadFormat.VodJson) {
            return DanmuPreviewItem(index = index, text = values.joinToString(","))
        }
        return DanmuPreviewItem(
            index = index,
            timeSeconds = values.getOrNull(0)?.toDoubleOrNull(),
            mode = normalizeMode(values.getOrNull(1).orEmpty(), format),
            color = values.getOrNull(2).orEmpty(),
            source = if (format == DanmuDownloadFormat.DplayerJson) values.getOrNull(3).orEmpty() else "",
            text = values.getOrNull(4).orEmpty()
        )
    }

    private fun normalizeTime(value: Double?, format: DanmuDownloadFormat): Double? {
        if (value == null || !value.isFinite()) return null
        return if (format == DanmuDownloadFormat.BahaJson) value / 10.0 else value
    }

    private fun normalizeMode(raw: String, format: DanmuDownloadFormat): String {
        val value = raw.trim().lowercase()
        return when (format) {
            DanmuDownloadFormat.ArtplayerJson,
            DanmuDownloadFormat.BahaJson,
            DanmuDownloadFormat.DplayerJson -> when (value) {
                "1" -> "5"
                "2" -> "4"
                else -> value
            }
            DanmuDownloadFormat.DanuniJson -> when (value) {
                "1", "bottom" -> "4"
                "2", "top" -> "5"
                else -> value
            }
            DanmuDownloadFormat.VodJson -> when (value) {
                "top" -> "5"
                "bottom" -> "4"
                else -> "1"
            }
            else -> value
        }
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
