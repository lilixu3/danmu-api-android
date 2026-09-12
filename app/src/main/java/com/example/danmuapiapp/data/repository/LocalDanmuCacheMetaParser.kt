package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.LocalDanmuResource

/**
 * 解析核心本地弹幕缓存文件（工作目录下 `.cache/local-danmu/` 里的 JSON）的元数据。
 *
 * 核心把资源写成 `{元数据…,"comments":[…],"updatedAt":"…"}`：元数据在文件最前面，
 * 评论数组紧随其后，更新时间在文件末尾。所以读取时**只需要头部一小段 + 尾部一小段**，
 * 完全不用碰几 MB 的评论数组——这也是不走核心列表接口（避免核心全量解析）的关键。
 *
 * 纯字符串实现，便于 JVM 单测；不依赖 org.json（单测里不可用）。
 */
internal object LocalDanmuCacheMetaParser {

    /** 从文件头部截出「comments 之前」的元数据片段，并补成完整的 JSON 对象。 */
    fun metaObject(head: String): String? {
        val marker = head.indexOf("\"comments\"")
        if (marker <= 0) return null
        val prefix = head.substring(0, marker).trimEnd().trimEnd(',').trimEnd()
        if (!prefix.startsWith("{")) return null
        return "$prefix}"
    }

    fun parse(head: String, tail: String): LocalDanmuResource? {
        val meta = metaObject(head) ?: return null
        val resourceKey = stringField(meta, "resourceKey")?.takeIf { it.isNotBlank() } ?: return null
        return LocalDanmuResource(
            resourceKey = resourceKey,
            videoId = stringField(meta, "videoId").orEmpty(),
            title = stringField(meta, "title").orEmpty(),
            year = intField(meta, "year") ?: 0,
            type = stringField(meta, "type").orEmpty(),
            season = intField(meta, "season") ?: 1,
            episode = intField(meta, "episode"),
            filename = stringField(meta, "filename").orEmpty(),
            sizeBytes = longField(meta, "size") ?: 0L,
            format = stringField(meta, "format").orEmpty(),
            status = stringField(meta, "status") ?: "ready",
            count = intField(meta, "count") ?: 0,
            updatedAt = parseUpdatedAt(tail).orEmpty()
        )
    }

    /** 文件尾部形如 `…],"updatedAt":"2026-09-12T11:32:19.322Z"}`。 */
    fun parseUpdatedAt(tail: String): String? =
        valueAfterKey(tail, keyEnd(tail, "updatedAt")) as? String

    private fun stringField(json: String, key: String): String? =
        valueAfterKey(json, keyEnd(json, key)) as? String

    private fun intField(json: String, key: String): Int? =
        (valueAfterKey(json, keyEnd(json, key)) as? Long)?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()

    private fun longField(json: String, key: String): Long? =
        valueAfterKey(json, keyEnd(json, key)) as? Long

    /** 返回 key 冒号之后的位置；找不到返回 -1。 */
    private fun keyEnd(json: String, key: String): Int {
        val marker = "\"$key\""
        var index = json.indexOf(marker)
        while (index >= 0) {
            var cursor = index + marker.length
            while (cursor < json.length && json[cursor].isWhitespace()) cursor++
            if (cursor < json.length && json[cursor] == ':') {
                cursor++
                while (cursor < json.length && json[cursor].isWhitespace()) cursor++
                return cursor
            }
            index = json.indexOf(marker, index + 1)
        }
        return -1
    }

    private fun valueAfterKey(json: String, start: Int): Any? {
        if (start < 0 || start >= json.length) return null
        return when (json[start]) {
            '"' -> readString(json, start)
            'n' -> if (json.startsWith("null", start)) null else null
            else -> readNumber(json, start)
        }
    }

    private fun readNumber(json: String, start: Int): Long? {
        var cursor = start
        if (cursor < json.length && (json[cursor] == '-' || json[cursor] == '+')) cursor++
        val digitsStart = cursor
        while (cursor < json.length && (json[cursor].isDigit() || json[cursor] == '.')) cursor++
        if (cursor == digitsStart) return null
        val token = json.substring(digitsStart, cursor).substringBefore('.')
        return token.toLongOrNull()
    }

    private fun readString(json: String, start: Int): String? {
        if (start >= json.length || json[start] != '"') return null
        val out = StringBuilder()
        var cursor = start + 1
        while (cursor < json.length) {
            when (val char = json[cursor]) {
                '"' -> return out.toString()
                '\\' -> {
                    cursor++
                    if (cursor >= json.length) return null
                    when (val escape = json[cursor]) {
                        '"', '\\', '/' -> out.append(escape)
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000C')
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        't' -> out.append('\t')
                        'u' -> {
                            if (cursor + 4 >= json.length) return null
                            val hex = json.substring(cursor + 1, cursor + 5)
                            val code = hex.toIntOrNull(16) ?: return null
                            out.append(code.toChar())
                            cursor += 4
                        }
                        else -> out.append(escape)
                    }
                }
                else -> out.append(char)
            }
            cursor++
        }
        return null
    }
}
