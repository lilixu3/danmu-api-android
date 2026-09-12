package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.LocalDanmuResource
import com.example.danmuapiapp.domain.model.LocalDanmuSourceRef
import com.example.danmuapiapp.domain.model.LocalDanmuType
import java.io.File
import java.util.Locale

/**
 * 「已导入」索引：把核心列表里的资源和本地记录过的来源，整理成可按
 * ①绝对路径 ②文件名+字节数 两级匹配的查询表，用于在目录浏览里标记已导入的剧集。
 */
internal object LocalDanmuImportedIndex {

    private const val PATH_PREFIX = "p:"
    private const val NAME_PREFIX = "n:"

    fun build(
        resources: List<LocalDanmuResource>,
        sources: List<LocalDanmuSourceRef>
    ): Map<String, String> {
        val index = LinkedHashMap<String, String>()
        val labelByKey = resources.associate { it.resourceKey to labelOf(it) }

        resources.forEach { resource ->
            if (resource.filename.isNotBlank() && resource.sizeBytes > 0L) {
                index.putIfAbsent(
                    NAME_PREFIX + nameKey(resource.filename, resource.sizeBytes),
                    labelOf(resource)
                )
            }
        }
        sources.forEach { source ->
            // 只认当前核心列表里真实存在的资源：切换工作目录（或更换核心）后，
            // 本地来源表里可能还留着别的目录导入过的记录，不能据此显示「已导入」。
            val label = labelByKey[source.resourceKey] ?: return@forEach
            pathFromUri(source.uri)?.let { path ->
                index[PATH_PREFIX + normalizePath(path)] = label
            }
            if (source.displayName.isNotBlank() && source.sizeBytes > 0L) {
                index.putIfAbsent(NAME_PREFIX + nameKey(source.displayName, source.sizeBytes), label)
            }
        }
        return index
    }

    fun lookup(
        index: Map<String, String>,
        path: String,
        name: String,
        sizeBytes: Long
    ): String? {
        if (index.isEmpty()) return null
        index[PATH_PREFIX + normalizePath(path)]?.let { return it }
        if (name.isBlank() || sizeBytes <= 0L) return null
        return index[NAME_PREFIX + nameKey(name, sizeBytes)]
    }

    fun labelOf(resource: LocalDanmuResource): String {
        val type = LocalDanmuType.fromWire(resource.type)
        return when {
            type == LocalDanmuType.Movie -> "已导入 · 正片"
            resource.episode != null -> "已导入 · 第${resource.season}季 第${resource.episode}集"
            else -> "已导入"
        }
    }

    fun nameKey(name: String, sizeBytes: Long): String =
        "${name.trim().lowercase(Locale.ROOT)}|$sizeBytes"

    fun normalizePath(path: String): String =
        runCatching { File(path).canonicalPath }.getOrDefault(path.trim())

    /** 把 file:// 或 SAF 的 document/tree URI 还原成绝对路径；无法还原时返回 null。 */
    fun pathFromUri(uri: String): String? {
        val raw = uri.trim()
        if (raw.isEmpty()) return null
        if (raw.startsWith("file:")) {
            val path = raw.removePrefix("file:")
                .removePrefix("//")
                .substringBefore('?')
                .substringBefore('#')
            return percentDecode(path).takeIf { it.isNotBlank() }
        }
        if (!raw.startsWith("content:")) return null
        val marker = when {
            raw.contains("/document/") -> "/document/"
            raw.contains("/tree/") -> "/tree/"
            else -> return null
        }
        val documentId = raw.substringAfter(marker, "").substringBefore('/')
        if (documentId.isBlank()) return null
        val decoded = percentDecode(documentId.substringBefore('?'))
        if (decoded.startsWith("raw:")) {
            return decoded.removePrefix("raw:").trim().takeIf { it.isNotBlank() }
        }
        val separator = decoded.indexOf(':')
        if (separator <= 0) return null
        val volume = decoded.substring(0, separator)
        val rest = decoded.substring(separator + 1)
        return when {
            rest.startsWith("/") -> rest
            volume.equals("primary", ignoreCase = true) ->
                "/storage/emulated/0/${rest.trimStart('/')}"
            else -> "/storage/$volume/${rest.trimStart('/')}"
        }
    }

    /** 纯字符串百分号解码，避免依赖 Android 的 Uri（单元测试直接在 JVM 上跑）。 */
    private fun percentDecode(value: String): String {
        if (!value.contains('%')) return value
        val bytes = java.io.ByteArrayOutputStream()
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char == '%' && index + 2 < value.length) {
                val parsed = value.substring(index + 1, index + 3).toIntOrNull(16)
                if (parsed != null) {
                    bytes.write(parsed)
                    index += 3
                    continue
                }
            }
            bytes.write(char.toString().toByteArray(Charsets.UTF_8))
            index++
        }
        return bytes.toByteArray().toString(Charsets.UTF_8)
    }
}
