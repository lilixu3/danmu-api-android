package com.example.danmuapiapp.desktop.core

import java.io.File

/**
 * Strict, deliberately small parser for the envVarConfig/default expressions used by danmu_api.
 * It is not a JavaScript interpreter: unsupported expressions are reported instead of becoming
 * an empty or guessed catalog.
 */
object CoreEnvCatalog {
    private val keyPattern = Regex("[A-Z][A-Z0-9_]*")
    private val sensitiveNamePattern = Regex(".*(TOKEN|COOKIE|API_KEY|_KEY)$", RegexOption.IGNORE_CASE)
    private val sensitiveUrlKeys = setOf(
        "CUSTOM_SOURCE_API_URL",
        "PROXY_URL",
        "LOCAL_REDIS_URL",
        "UPSTASH_REDIS_REST_URL",
    )

    private val defaultOutputFormats = listOf(
        "artplayer.json", "baha.json", "bili.xml", "danuni.json", "danuni.binpb",
        "ddplay.json", "dplayer.json", "vod.json",
    )

    fun parseFile(file: File): List<CoreEnvDefinition> {
        require(file.isFile) { "核心环境变量定义文件不存在：${file.absolutePath}" }
        val content = file.readText(Charsets.UTF_8)
        return parse(content)
    }

    fun parse(content: String): List<CoreEnvDefinition> {
        val envStart = content.indexOf("const envVarConfig")
        require(envStart >= 0) { "envs.js 缺少 envVarConfig 定义" }
        val openBrace = content.indexOf('{', envStart)
        require(openBrace >= 0) { "envVarConfig 缺少开始括号" }

        val identifiers = linkedMapOf<String, Any>(
            "this.ALLOWED_SOURCES" to extractStaticArray(content, "ALLOWED_SOURCES"),
            "this.ALLOWED_PLATFORMS" to extractStaticArray(content, "ALLOWED_PLATFORMS"),
            "this.VOD_ALLOWED_PLATFORMS" to extractStaticArray(content, "VOD_ALLOWED_PLATFORMS"),
            "this.MERGE_ALLOWED_SOURCES" to extractStaticArray(content, "MERGE_ALLOWED_SOURCES"),
            "danAnyFormats" to defaultOutputFormats,
        )
        val root = JsValueParser(content, openBrace, identifiers).parseValue()
        val map = root as? LinkedHashMap<*, *> ?: error("envVarConfig 必须是对象")
        require(map.isNotEmpty()) { "envVarConfig 为空，拒绝显示伪造的空配置目录" }

        val defaults = parseDefaults(content)
        val definitions = mutableListOf<CoreEnvDefinition>()
        map.forEach { (rawKey, rawDefinition) ->
            val key = rawKey?.toString()?.trim().orEmpty()
            require(key.matches(keyPattern)) { "envVarConfig 包含非法变量名：$key" }
            require(key !in CORE_ENV_HOST_KEYS) { "envVarConfig 暴露了 Desktop 宿主变量：$key" }
            require(definitions.none { it.key == key }) { "envVarConfig 包含重复变量：$key" }
            val obj = rawDefinition as? Map<*, *> ?: error("变量 $key 的定义不是对象")
            val category = obj.string("category")?.trim().orEmpty().ifBlank { "other" }
            val type = parseType(obj.string("type"), key)
            val description = obj.string("description")?.trim().orEmpty().ifBlank { key }
            val parsedOptions = obj.list("options")
            val options = resolveOptions(key, parsedOptions, identifiers)
            val min = obj.number("min")?.toInt()
            val max = obj.number("max")?.toInt()
            require(min == null || max == null || min <= max) { "$key 的 min 大于 max" }
            val defaultValue = defaults[key]
            val sensitive = obj.boolean("encrypt") == true || key in sensitiveUrlKeys || sensitiveNamePattern.matches(key)
            val encryptedByGet = parseGetSecurity(content, key)
            definitions += CoreEnvDefinition(
                key = key,
                category = category,
                type = type,
                description = description,
                options = options,
                min = min,
                max = max,
                defaultValue = defaultValue,
                sensitive = encryptedByGet || obj.boolean("encrypt") == true || key in sensitiveUrlKeys || sensitiveNamePattern.matches(key),
                applyMode = CoreEnvApplyMode.HotReload,
            )
        }
        return definitions
    }

    private fun parseType(raw: String?, key: String): CoreEnvType = when (raw?.trim()?.lowercase()) {
        "text", "string", null, "" -> CoreEnvType.Text
        "number", "int", "integer" -> CoreEnvType.Number
        "boolean", "bool" -> CoreEnvType.Boolean
        "select" -> CoreEnvType.Select
        "multi-select", "multiselect" -> CoreEnvType.MultiSelect
        "map", "color-list", "colorlist", "custom-merge-rules", "custommergerules", "timeline-offset", "timelineoffset" -> CoreEnvType.Map
        else -> error("变量 $key 使用了不支持的类型：$raw")
    }

    private fun resolveOptions(key: String, parsed: List<String>, identifiers: Map<String, Any>): List<String> {
        val direct = sanitize(parsed)
        val source = identifiers["this.ALLOWED_SOURCES"] as List<*>
        val platforms = identifiers["this.ALLOWED_PLATFORMS"] as List<*>
        val merge = identifiers["this.MERGE_ALLOWED_SOURCES"] as List<*>
        return when (key) {
            "SOURCE_ORDER" -> direct.ifEmpty { sanitize(source.map { it.toString() }) }
            "MERGE_SOURCE_PAIRS", "CUSTOM_MERGE_RULES" -> direct.ifEmpty { sanitize(merge.map { it.toString() }) }
            "PLATFORM_ORDER", "MATCH_PLATFORM_RULES" -> direct.ifEmpty { sanitize(platforms.map { it.toString() }) }
            "AUTO_MATCH_MAPPING_TABLE" -> sanitize(platforms.map { it.toString() })
            "DANMU_OFFSET" -> listOf("all") + direct.filterNot { it.equals("all", true) }.ifEmpty { sanitize(source.map { it.toString() }) }
            else -> direct
        }
    }

    private fun sanitize(values: List<String>): List<String> = buildList {
        val seen = linkedSetOf<String>()
        values.map { it.trim() }.filter { it.isNotBlank() }.forEach {
            val normalized = if (it == "*") "all" else it
            if (seen.add(normalized.lowercase())) add(normalized)
        }
    }

    private fun extractStaticArray(content: String, field: String): List<String> {
        val match = Regex("static\\s+$field\\s*=\\s*(\\[[\\s\\S]*?\\])\\s*;", RegexOption.IGNORE_CASE).find(content)
            ?: return emptyList()
        return Regex("['\"]([^'\"\\\\]*(?:\\\\.[^'\"\\\\]*)*)['\"]")
            .findAll(match.groupValues[1])
            .map { unescapeJs(it.groupValues[1]) }
            .toList()
    }

    private fun parseDefaults(content: String): Map<String, String> {
        val constants = parseConstants(content)
        val values = linkedMapOf<String, String>()
        var cursor = 0
        while (cursor < content.length) {
            val index = content.indexOf("this.get(", cursor)
            if (index < 0) break
            val call = parseGetCall(content, index + "this.get".length)
                ?: error("无法解析 envs.js 中的 this.get 调用（位置 $index）")
            cursor = call.end
            if (call.key in values) continue
            values[call.key] = evaluate(call.defaultExpression, constants)
        }
        return values
    }

    private data class GetCall(val key: String, val defaultExpression: String, val encrypt: Boolean, val end: Int)

    private fun parseGetSecurity(content: String, key: String): Boolean {
        var cursor = 0
        while (cursor < content.length) {
            val index = content.indexOf("this.get(", cursor)
            if (index < 0) return false
            val call = parseGetCall(content, index + "this.get".length) ?: return false
            cursor = call.end
            if (call.key == key) return call.encrypt
        }
        return false
    }

    private fun parseGetCall(content: String, open: Int): GetCall? {
        if (open !in content.indices || content[open] != '(') return null
        var index = skipSpaces(content, open + 1)
        val key = parseQuoted(content, index) ?: return null
        index = skipSpaces(content, key.second)
        if (index !in content.indices || content[index] != ',') return null
        val args = mutableListOf<String>()
        var argStart = index + 1
        var depth = 0
        var quote: Char? = null
        var escaped = false
        index++
        while (index < content.length) {
            val ch = content[index]
            if (quote != null) {
                if (escaped) escaped = false
                else if (ch == '\\') escaped = true
                else if (ch == quote) quote = null
                index++
                continue
            }
            when (ch) {
                '\'', '"', '`' -> quote = ch
                '(', '[', '{' -> depth++
                ')', ']', '}' -> {
                    if (ch == ')' && depth == 0) {
                        args += content.substring(argStart, index).trim()
                        val defaultExpression = args.getOrNull(0) ?: return null
                        val encrypt = args.getOrNull(2)?.equals("true", ignoreCase = true) == true
                        return GetCall(key.first, defaultExpression, encrypt, index + 1)
                    }
                    if (depth > 0) depth--
                }
                ',' -> if (depth == 0) {
                    args += content.substring(argStart, index).trim()
                    argStart = index + 1
                }
            }
            index++
        }
        return null
    }

    private fun parseConstants(content: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        Regex("(?ms)^\\s*(?:static\\s+)?(?:const|let|var)\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*=\\s*`([\\s\\S]*?)`;" ).findAll(content).forEach { match ->
            result[match.groupValues[1]] = unescapeJs(match.groupValues[2])
        }
        Regex("(?ms)^\\s*static\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*=\\s*`([\\s\\S]*?)`;" ).findAll(content).forEach { match ->
            result[match.groupValues[1]] = unescapeJs(match.groupValues[2])
        }
        Regex("(?m)^\\s*(?:static\\s+)?(?:const|let|var)\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*=\\s*([^;\\n]+);").findAll(content).forEach { match ->
            val expression = match.groupValues[2].trim()
            // envVarConfig is the catalog object itself, not a default literal. It is parsed by
            // the object parser above and must not be evaluated as a scalar constant.
            if (expression.startsWith("{")) return@forEach
            // envs.js also contains implementation helpers whose scalar expressions are unrelated to
            // environment defaults (for example encrypted-value plumbing). Do not treat those helpers
            // as catalog constants; if a default references one, evaluate() still fails explicitly.
            if (isSupportedConstantExpression(expression)) {
                result[match.groupValues[1]] = evaluate(expression, result)
            }
        }
        return result
    }

    private fun isSupportedConstantExpression(expression: String): Boolean {
        val value = expression.trim()
        return value.isEmpty() ||
            value == "null" ||
            value == "undefined" ||
            value.equals("true", true) ||
            value.equals("false", true) ||
            value.toLongOrNull() != null ||
            value.toDoubleOrNull() != null ||
            (value.length >= 2 && value.first() in charArrayOf('\'', '"', '`') && value.last() == value.first())
    }

    private fun evaluate(expression: String, constants: Map<String, String>): String {
        val value = expression.trim()
        if (value.isEmpty() || value == "null" || value == "undefined") return ""
        constants[value]?.let { return it }
        if (value.startsWith("this.")) {
            constants[value.removePrefix("this.")]?.let { return it }
        }
        if (value.startsWith("this.get(")) {
            val nested = parseGetCall(value, value.indexOf('(')) ?: error("无法解析嵌套默认值：$value")
            return evaluate(nested.defaultExpression, constants)
        }
        if (value.equals("true", true) || value.equals("false", true) || value.toLongOrNull() != null || value.toDoubleOrNull() != null) return value.lowercase()
        if ((value.startsWith("'") && value.endsWith("'")) || (value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("`") && value.endsWith("`"))) {
            return unescapeJs(value.substring(1, value.length - 1))
        }
        error("无法安全解析默认值表达式：$value")
    }

    private fun parseQuoted(content: String, start: Int): Pair<String, Int>? {
        if (start !in content.indices || content[start] !in charArrayOf('\'', '"')) return null
        val quote = content[start]
        val result = StringBuilder()
        var index = start + 1
        var escaped = false
        while (index < content.length) {
            val ch = content[index]
            if (escaped) { result.append('\\').append(ch); escaped = false }
            else if (ch == '\\') escaped = true
            else if (ch == quote) return unescapeJs(result.toString()) to (index + 1)
            else result.append(ch)
            index++
        }
        return null
    }

    private fun skipSpaces(content: String, start: Int): Int = content.indexOfFirstFrom(start) { !it.isWhitespace() }.takeIf { it >= 0 } ?: content.length

    private fun String.indexOfFirstFrom(start: Int, predicate: (Char) -> Boolean): Int {
        for (index in start until length) if (predicate(this[index])) return index
        return -1
    }

    private fun unescapeJs(value: String): String = buildString {
        var index = 0
        while (index < value.length) {
            if (value[index] != '\\' || index == value.lastIndex) append(value[index++])
            else {
                when (val next = value[index + 1]) {
                    'n' -> append('\n')
                    'r' -> append('\r')
                    't' -> append('\t')
                    '\\' -> append('\\')
                    '\'' -> append('\'')
                    '"' -> append('"')
                    else -> append(next)
                }
                index += 2
            }
        }
    }

    private fun Map<*, *>.string(key: String): String? = this[key]?.toString()
    private fun Map<*, *>.list(key: String): List<String> = (this[key] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
    private fun Map<*, *>.number(key: String): Number? = this[key] as? Number
    private fun Map<*, *>.boolean(key: String): Boolean? = this[key] as? Boolean

    private class JsValueParser(
        private val source: String,
        private val start: Int,
        private val identifiers: Map<String, Any>,
    ) {
        private var index = start

        fun parseValue(): Any? {
            skipWhitespaceAndComments()
            require(index < source.length) { "JavaScript 值意外结束" }
            return when (source[index]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '\'', '"', '`' -> parseString()
                else -> parseToken()
            }
        }

        private fun parseObject(): LinkedHashMap<String, Any?> {
            require(source[index++] == '{')
            val result = linkedMapOf<String, Any?>()
            skipWhitespaceAndComments()
            while (index < source.length && source[index] != '}') {
                val key = when (source[index]) {
                    '\'', '"' -> parseString().toString()
                    else -> parseIdentifier()
                }
                skipWhitespaceAndComments()
                require(index < source.length && source[index++] == ':') { "对象字段 $key 缺少冒号" }
                result[key] = parseValue()
                skipWhitespaceAndComments()
                if (index < source.length && source[index] == ',') { index++; skipWhitespaceAndComments() }
                else require(index < source.length && source[index] == '}') { "对象字段 $key 后缺少逗号或结束括号" }
            }
            require(index < source.length && source[index++] == '}') { "对象缺少结束括号" }
            return result
        }

        private fun parseArray(): List<Any?> {
            require(source[index++] == '[')
            val result = mutableListOf<Any?>()
            skipWhitespaceAndComments()
            while (index < source.length && source[index] != ']') {
                if (source[index] == '.' && source.startsWith("...", index)) {
                    index += 3
                    val identifier = parseIdentifier()
                    val expanded = identifiers[identifier] as? List<*> ?: error("不支持展开表达式：$identifier")
                    result.addAll(expanded)
                } else result += parseValue()
                skipWhitespaceAndComments()
                if (index < source.length && source[index] == ',') { index++; skipWhitespaceAndComments() }
                else require(index < source.length && source[index] == ']') { "数组元素后缺少逗号或结束括号" }
            }
            require(index < source.length && source[index++] == ']') { "数组缺少结束括号" }
            return result
        }

        private fun parseString(): String {
            val quote = source[index++]
            val result = StringBuilder()
            var escaped = false
            while (index < source.length) {
                val ch = source[index++]
                if (escaped) { result.append('\\').append(ch); escaped = false }
                else if (ch == '\\') escaped = true
                else if (ch == quote) return unescapeJs(result.toString())
                else result.append(ch)
            }
            error("字符串缺少结束引号")
        }

        private fun parseIdentifier(): String {
            val begin = index
            while (index < source.length && (source[index].isLetterOrDigit() || source[index] in "_.$")) index++
            return source.substring(begin, index)
        }

        private fun parseToken(): Any? {
            val begin = index
            while (index < source.length && source[index] !in ",]}\n\r") index++
            return when (val token = source.substring(begin, index).trim()) {
                "true" -> true
                "false" -> false
                "null" -> null
                else -> token.toLongOrNull() ?: token.toDoubleOrNull() ?: identifiers[token] ?: error("不支持的 envVarConfig 表达式：$token")
            }
        }

        private fun skipWhitespaceAndComments() {
            while (index < source.length) {
                while (index < source.length && source[index].isWhitespace()) index++
                if (source.startsWith("//", index)) {
                    index = source.indexOf('\n', index + 2).takeIf { it >= 0 } ?: source.length
                } else if (source.startsWith("/*", index)) {
                    val end = source.indexOf("*/", index + 2)
                    require(end >= 0) { "块注释缺少结束标记" }
                    index = end + 2
                } else return
            }
        }
    }
}
