package com.example.danmuapiapp.ui.screen.config

/**
 * `;` 分隔条目 + `->` 分隔键值的映射表编解码工具。
 *
 * 历史：早期实现直接 split，值里含 `;` 或 `->` 时会被静默拆断、破坏数据。
 * 现在序列化时把 `\`、`;`、`->` 转义为 `\\`、`\;`、`\->`；
 * 解析时只识别这三种转义序列，其余 `\x` 原样保留，
 * 因此不含反斜杠的历史数据解析结果与旧版完全一致。
 */
internal object MappingTableCodec {

    const val ENTRY_SEPARATOR = ';'
    const val ARROW = "->"

    fun escape(segment: String): String {
        if (!segment.contains('\\') &&
            !segment.contains(ENTRY_SEPARATOR) &&
            !segment.contains(ARROW)
        ) {
            return segment
        }
        val out = StringBuilder(segment.length + 8)
        var i = 0
        while (i < segment.length) {
            val ch = segment[i]
            when {
                ch == '\\' -> {
                    out.append("\\\\")
                    i++
                }

                ch == ENTRY_SEPARATOR -> {
                    out.append("\\;")
                    i++
                }

                ch == '-' && segment.getOrNull(i + 1) == '>' -> {
                    out.append("\\->")
                    i += 2
                }

                else -> {
                    out.append(ch)
                    i++
                }
            }
        }
        return out.toString()
    }

    /** 还原 [escape] 的转义；未知转义 `\x` 保持两个字符原样，兼容历史数据。 */
    fun unescape(segment: String): String {
        if ('\\' !in segment) return segment
        val out = StringBuilder(segment.length)
        var i = 0
        while (i < segment.length) {
            val ch = segment[i]
            if (ch == '\\' && i < segment.lastIndex && isEscapable(segment[i + 1])) {
                out.append(segment[i + 1])
                i += 2
            } else {
                out.append(ch)
                i++
            }
        }
        return out.toString()
    }

    /** 按未转义的 ';' 拆分；条目内部保留转义序列，交由 [unescape] 还原。 */
    fun splitEntries(raw: String): List<String> {
        val entries = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        while (i < raw.length) {
            val ch = raw[i]
            when {
                ch == '\\' && i < raw.lastIndex -> {
                    current.append(ch).append(raw[i + 1])
                    i += 2
                }

                ch == ENTRY_SEPARATOR -> {
                    entries += current.toString()
                    current.clear()
                    i++
                }

                else -> {
                    current.append(ch)
                    i++
                }
            }
        }
        entries += current.toString()
        return entries
    }

    /**
     * 在条目内按第一个未转义的 "->" 切成 left/right；
     * 没有箭头时返回 null。返回的两个片段仍是转义形态，需要再 [unescape]。
     */
    fun findArrow(item: String): Int? {
        var i = 0
        while (i < item.length) {
            when {
                item[i] == '\\' && i < item.lastIndex -> i += 2

                item[i] == '-' && item.getOrNull(i + 1) == '>' -> return i

                else -> i++
            }
        }
        return null
    }

    /** 统计条目内未转义箭头数量，供“需要且只能包含一个 ->”类校验使用。 */
    fun countArrows(item: String): Int {
        var count = 0
        var searchFrom = 0
        while (true) {
            val found = findArrowFrom(item, searchFrom) ?: return count
            count++
            searchFrom = found + ARROW.length
        }
    }

    private fun findArrowFrom(item: String, from: Int): Int? {
        if (from >= item.length) return null
        return findArrow(item.substring(from))?.plus(from)
    }

    private fun isEscapable(next: Char): Boolean {
        return next == '\\' || next == ENTRY_SEPARATOR || next == '-'
    }
}
