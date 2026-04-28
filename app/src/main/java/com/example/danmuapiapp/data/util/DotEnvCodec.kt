package com.example.danmuapiapp.data.util

/**
 * Small .env codec shared by Android-side readers/writers.
 *
 * The runtime only supports one-line KEY=VALUE entries.  Visual editing must
 * therefore be idempotent: reading a regex value and saving it again must not
 * add extra backslashes on every round-trip.
 */
object DotEnvCodec {
    fun parse(content: String): Map<String, String> {
        val map = linkedMapOf<String, String>()
        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim().removePrefix("\uFEFF")
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            val index = line.indexOf('=')
            if (index <= 0) return@forEach
            val key = line.substring(0, index).trim()
            if (key.isBlank()) return@forEach
            val rawValue = line.substring(index + 1).trim()
            map[key] = parseValue(rawValue)
        }
        return map
    }

    fun parseValue(rawValue: String): String {
        if (rawValue.length < 2) return rawValue
        val first = rawValue.first()
        val last = rawValue.last()
        return when {
            first == '"' && last == '"' -> unescapeDoubleQuoted(rawValue.substring(1, rawValue.length - 1))
            first == '\'' && last == '\'' -> rawValue.substring(1, rawValue.length - 1)
            else -> rawValue
        }
    }

    fun formatValue(value: String): String {
        if (!needsQuotes(value)) return value
        return buildString(value.length + 2) {
            append('"')
            value.forEachIndexed { index, ch ->
                when (ch) {
                    '\\' -> {
                        val next = value.getOrNull(index + 1)
                        if (next != null && isRecognizedEscapedCharacter(next)) {
                            append("\\\\")
                        } else {
                            append(ch)
                        }
                    }
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
            append('"')
        }
    }

    private fun needsQuotes(value: String): Boolean {
        if (value.isEmpty()) return false
        return value.first().isWhitespace() ||
            value.last().isWhitespace() ||
            value.any { it.isWhitespace() || it == '=' || it == '#' || it == '"' }
    }

    private fun isRecognizedEscapedCharacter(ch: Char): Boolean {
        return ch == '\\' ||
            ch == '"' ||
            ch == 'n' ||
            ch == 'r' ||
            ch == 't' ||
            ch == '\n' ||
            ch == '\r' ||
            ch == '\t'
    }

    private fun unescapeDoubleQuoted(value: String): String {
        val out = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (ch != '\\' || i == value.lastIndex) {
                out.append(ch)
                i++
                continue
            }

            val next = value[i + 1]
            when (next) {
                '\\' -> out.append('\\')
                '"' -> out.append('"')
                'n' -> out.append('\n')
                'r' -> out.append('\r')
                't' -> out.append('\t')
                else -> {
                    out.append('\\')
                    out.append(next)
                }
            }
            i += 2
        }
        return out.toString()
    }
}
