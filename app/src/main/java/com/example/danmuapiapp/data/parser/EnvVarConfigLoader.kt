package com.example.danmuapiapp.data.parser

import android.content.Context
import com.example.danmuapiapp.data.service.NodeProjectManager
import com.example.danmuapiapp.data.service.RootShell
import com.example.danmuapiapp.data.service.RuntimeModePrefs
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.EnvType
import com.example.danmuapiapp.domain.model.EnvVarDef
import java.io.File

object EnvVarConfigLoader {

    fun loadCatalog(context: Context): List<EnvVarDef> {
        val variant = selectedVariant(context)
        val content = readEnvsJs(context, variant) ?: return emptyList()
        return runCatching { parseEnvsJs(content) }.getOrElse { emptyList() }
    }

    fun loadDefaultValues(context: Context): Map<String, String> {
        val variant = selectedVariant(context)
        val content = readEnvsJs(context, variant) ?: return emptyMap()
        return runCatching { parseDefaultValues(content) }.getOrElse { emptyMap() }
    }

    fun loadDefaultValue(context: Context, key: String): String? {
        if (key.isBlank()) return null
        val target = key.trim()
        return loadDefaultValues(context)[target]
    }

    private fun selectedVariant(context: Context): String {
        val prefs = context.getSharedPreferences("runtime", Context.MODE_PRIVATE)
        val raw = prefs.getString("variant", null)
        val fromPrefs = normalizeVariant(raw)
        if (fromPrefs != null) return fromPrefs

        return readVariantFromEnv(context) ?: "stable"
    }

    private fun readVariantFromEnv(context: Context): String? {
        val mode = currentRunMode(context)
        if (mode == RunMode.Normal) {
            runCatching { NodeProjectManager.ensureProjectExtracted(context) }
        }
        val envFile = File(NodeProjectManager.projectDir(context, mode), "config/.env")
        val text = if (mode != RunMode.Normal) {
            rootReadText(envFile.absolutePath)
        } else {
            runCatching { if (envFile.exists()) envFile.readText(Charsets.UTF_8) else "" }.getOrNull()
        } ?: return null
        for (line in text.lineSequence()) {
            val raw = line.trim()
            if (raw.isEmpty() || raw.startsWith("#")) continue
            val idx = raw.indexOf('=')
            if (idx <= 0) continue
            if (raw.substring(0, idx).trim() != "DANMU_API_VARIANT") continue

            var value = raw.substring(idx + 1).trim()
            if ((value.startsWith('"') && value.endsWith('"')) ||
                (value.startsWith('\'') && value.endsWith('\''))) {
                value = value.substring(1, value.length - 1)
            }
            return normalizeVariant(value)
        }
        return null
    }

    private fun normalizeVariant(raw: String?): String? {
        return when (raw?.trim()?.lowercase()) {
            "dev", "develop", "development" -> "dev"
            "custom" -> "custom"
            "stable" -> "stable"
            else -> null
        }
    }

    private fun readEnvsJs(context: Context, variant: String): String? {
        val mode = currentRunMode(context)
        if (mode == RunMode.Normal) {
            runCatching { NodeProjectManager.ensureProjectExtracted(context) }
        }
        val base = NodeProjectManager.projectDir(context, mode)
        val subdir = "danmu_api_$variant"
        val candidates = listOf(
            File(base, "$subdir/danmu_api/configs/envs.js"),
            File(base, "$subdir/configs/envs.js"),
        )
        for (f in candidates) {
            if (mode != RunMode.Normal) {
                if (!rootFileExists(f.absolutePath)) continue
                return rootReadText(f.absolutePath)
            }
            if (!f.exists()) continue
            return runCatching { f.readText(Charsets.UTF_8) }.getOrNull()
        }
        return null
    }

    private fun currentRunMode(context: Context): RunMode {
        return RuntimeModePrefs.get(context)
    }

    private fun rootFileExists(path: String): Boolean {
        val script = "test -f ${shellQuote(path)}"
        return RootShell.exec(script, timeoutMs = 2500L).ok
    }

    private fun rootReadText(path: String): String? {
        val script = """
            FILE=${shellQuote(path)}
            if [ ! -f "${'$'}FILE" ]; then
              exit 1
            fi
            cat "${'$'}FILE"
        """.trimIndent()
        val result = RootShell.exec(script, timeoutMs = 4500L)
        return if (result.ok) result.stdout else null
    }

    private fun shellQuote(input: String): String {
        return "'" + input.replace("'", "'\"'\"'") + "'"
    }

    private fun parseEnvsJs(content: String): List<EnvVarDef> {
        val allowedSources = extractStaticArray(content, "ALLOWED_SOURCES")
        val allowedPlatforms = extractStaticArray(content, "ALLOWED_PLATFORMS")
        val vodPlatforms = extractStaticArray(content, "VOD_ALLOWED_PLATFORMS")
        val mergeSources = extractStaticArray(content, "MERGE_ALLOWED_SOURCES")

        val identifiers = mutableMapOf<String, Any>(
            "this.ALLOWED_SOURCES" to allowedSources,
            "this.ALLOWED_PLATFORMS" to allowedPlatforms,
            "this.VOD_ALLOWED_PLATFORMS" to vodPlatforms,
            "this.MERGE_ALLOWED_SOURCES" to mergeSources,
        )

        val start = content.indexOf("const envVarConfig")
        if (start < 0) return emptyList()
        val brace = content.indexOf('{', start)
        if (brace < 0) return emptyList()

        val parser = EnvVarConfigJsLiteParser(content, brace, identifiers)
        val root = parser.parseValue()
        val map = root as? LinkedHashMap<*, *> ?: return emptyList()

        return map.mapNotNull { (kAny, vAny) ->
            val key = kAny?.toString()?.trim().orEmpty()
            val obj = vAny as? Map<*, *> ?: return@mapNotNull null
            val category = (obj["category"] as? String)?.trim().orEmpty().ifBlank { "other" }
            val typeRaw = (obj["type"] as? String)?.trim().orEmpty().ifBlank { "text" }
            val desc = (obj["description"] as? String)?.trim().orEmpty().ifBlank { key }
            val parsedOptions = when (val o = obj["options"]) {
                is List<*> -> o.mapNotNull { it?.toString() }
                else -> emptyList()
            }
            val options = resolveOptionsByKey(
                key = key,
                parsedOptions = parsedOptions,
                allowedSources = allowedSources,
                allowedPlatforms = allowedPlatforms,
                mergeSources = mergeSources,
            )
            val min = (obj["min"] as? Number)?.toInt()
            val max = (obj["max"] as? Number)?.toInt()
            val type = when (typeRaw.lowercase()) {
                "text", "string" -> EnvType.TEXT
                "number", "int", "integer" -> EnvType.NUMBER
                "boolean", "bool" -> EnvType.BOOLEAN
                "select" -> EnvType.SELECT
                "multi-select", "multiselect" -> EnvType.MULTI_SELECT
                "map" -> EnvType.MAP
                "color-list", "colorlist" -> EnvType.COLOR_LIST
                else -> EnvType.TEXT
            }
            EnvVarDef(key, category, type, desc, options, min, max, inferSensitive(key))
        }
    }

    private fun inferSensitive(key: String): Boolean {
        val u = key.uppercase()
        return u.contains("TOKEN") || u.contains("COOKIE") || u.contains("API_KEY") || u.endsWith("_KEY")
    }

    private fun resolveOptionsByKey(
        key: String,
        parsedOptions: List<String>,
        allowedSources: List<String>,
        allowedPlatforms: List<String>,
        mergeSources: List<String>,
    ): List<String> {
        val cleanedParsed = sanitizeOptions(parsedOptions)
        val upperKey = key.trim().uppercase()

        return when (upperKey) {
            "SOURCE_ORDER" -> {
                if (cleanedParsed.isNotEmpty()) cleanedParsed else sanitizeOptions(allowedSources)
            }

            "MERGE_SOURCE_PAIRS" -> {
                if (cleanedParsed.isNotEmpty()) cleanedParsed else sanitizeOptions(mergeSources)
            }

            "PLATFORM_ORDER" -> {
                if (cleanedParsed.isNotEmpty()) cleanedParsed else sanitizeOptions(allowedPlatforms)
            }

            "TITLE_PLATFORM_OFFSET_TABLE" -> {
                val parsedWithoutAll = cleanedParsed.filterNot { it.equals("all", ignoreCase = true) }
                val sourcePlatforms = if (parsedWithoutAll.isNotEmpty()) {
                    parsedWithoutAll
                } else {
                    sanitizeOptions(allowedPlatforms)
                }
                withAllFirst(sourcePlatforms)
            }

            else -> cleanedParsed
        }
    }

    private fun sanitizeOptions(raw: List<String>): List<String> {
        if (raw.isEmpty()) return emptyList()
        val out = mutableListOf<String>()
        val seen = linkedSetOf<String>()
        raw.map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { option ->
                val normalized = if (option == "*") "all" else option
                val key = normalized.lowercase()
                if (seen.add(key)) out += normalized
            }
        return out
    }

    private fun withAllFirst(platforms: List<String>): List<String> {
        val cleaned = sanitizeOptions(platforms)
        if (cleaned.isEmpty()) return listOf("all")
        val withoutAll = cleaned.filterNot { it.equals("all", ignoreCase = true) }
        return listOf("all") + withoutAll
    }

    private fun extractStaticArray(content: String, field: String): List<String> {
        val r = Regex("static\\s+$field\\s*=\\s*(\\[[\\s\\S]*?\\])\\s*;", setOf(RegexOption.IGNORE_CASE))
        val m = r.find(content) ?: return emptyList()
        val raw = m.groupValues.getOrNull(1) ?: return emptyList()
        return Regex("['\"]([^'\"\\\\]*(?:\\\\.[^'\"\\\\]*)*)['\"]").findAll(raw)
            .mapNotNull { it.groupValues.getOrNull(1) }.toList()
    }

    private data class ParsedGetCall(
        val key: String,
        val defaultExpr: String,
        val nextIndex: Int
    )

    private fun parseDefaultValues(content: String): Map<String, String> {
        val out = linkedMapOf<String, String>()
        val constLiterals = parseConstLiteralMap(content)
        var cursor = 0
        while (cursor < content.length) {
            val idx = content.indexOf("this.get(", startIndex = cursor)
            if (idx < 0) break
            val parsed = parseGetCall(content, idx + "this.get".length)
            if (parsed == null) {
                cursor = idx + 1
                continue
            }
            cursor = parsed.nextIndex.coerceAtLeast(idx + 1)
            if (parsed.key.isBlank() || out.containsKey(parsed.key)) continue
            val value = evalDefaultExpr(parsed.defaultExpr, constLiterals) ?: continue
            out[parsed.key] = value
        }
        return out
    }

    private fun parseConstLiteralMap(content: String): Map<String, String> {
        val out = linkedMapOf<String, String>()
        val regex = Regex("(?m)^\\s*const\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*=\\s*([^;\\n]+);")
        regex.findAll(content).forEach { m ->
            val name = m.groupValues.getOrNull(1)?.trim().orEmpty()
            val expr = m.groupValues.getOrNull(2)?.trim().orEmpty()
            if (name.isBlank() || expr.isBlank()) return@forEach
            val value = evalSimpleLiteral(expr) ?: return@forEach
            out[name] = value
        }
        return out
    }

    private fun parseGetCall(source: String, parenIndex: Int): ParsedGetCall? {
        if (parenIndex !in source.indices || source[parenIndex] != '(') return null
        var i = skipSpaces(source, parenIndex + 1)
        val keyParsed = parseQuoted(source, i) ?: return null
        val key = keyParsed.first
        i = skipSpaces(source, keyParsed.second)
        if (i !in source.indices || source[i] != ',') return null

        i = skipSpaces(source, i + 1)
        val exprStart = i
        var depth = 0
        var quote: Char? = null
        var escape = false

        while (i < source.length) {
            val ch = source[i]
            if (quote != null) {
                if (escape) {
                    escape = false
                } else if (ch == '\\') {
                    escape = true
                } else if (ch == quote) {
                    quote = null
                }
                i++
                continue
            }

            when (ch) {
                '\'', '"', '`' -> {
                    quote = ch
                    i++
                }

                '(', '[', '{' -> {
                    depth++
                    i++
                }

                ')', ']', '}' -> {
                    if (depth == 0) {
                        val expr = source.substring(exprStart, i).trim()
                        return ParsedGetCall(key = key, defaultExpr = expr, nextIndex = i + 1)
                    }
                    depth--
                    i++
                }

                ',' -> {
                    if (depth == 0) {
                        val expr = source.substring(exprStart, i).trim()
                        return ParsedGetCall(key = key, defaultExpr = expr, nextIndex = i + 1)
                    }
                    i++
                }

                else -> i++
            }
        }
        return null
    }

    private fun evalDefaultExpr(
        expr: String,
        constLiterals: Map<String, String>,
        depth: Int = 0
    ): String? {
        if (depth > 8) return null
        val raw = expr.trim()
        if (raw.isEmpty()) return ""

        evalSimpleLiteral(raw)?.let { return it }
        constLiterals[raw]?.let { return it }

        if (raw.startsWith("this.get(")) {
            val openIdx = raw.indexOf('(')
            if (openIdx < 0) return null
            val nested = parseGetCall(raw, openIdx) ?: return null
            return evalDefaultExpr(nested.defaultExpr, constLiterals, depth + 1)
        }
        return null
    }

    private fun evalSimpleLiteral(rawExpr: String): String? {
        val raw = rawExpr.trim()
        if (raw.equals("true", ignoreCase = true)) return "true"
        if (raw.equals("false", ignoreCase = true)) return "false"
        if (raw.equals("null", ignoreCase = true)) return ""
        if (raw.equals("undefined", ignoreCase = true)) return ""

        if (raw.toLongOrNull() != null || raw.toDoubleOrNull() != null) {
            return raw
        }

        if ((raw.startsWith('"') && raw.endsWith('"') && raw.length >= 2) ||
            (raw.startsWith('\'') && raw.endsWith('\'') && raw.length >= 2)
        ) {
            return unescapeJsString(raw.substring(1, raw.length - 1))
        }
        return null
    }

    private fun parseQuoted(source: String, start: Int): Pair<String, Int>? {
        if (start !in source.indices) return null
        val quote = source[start]
        if (quote != '\'' && quote != '"') return null
        val sb = StringBuilder()
        var i = start + 1
        var escape = false
        while (i < source.length) {
            val ch = source[i]
            if (escape) {
                sb.append('\\').append(ch)
                escape = false
                i++
                continue
            }
            if (ch == '\\') {
                escape = true
                i++
                continue
            }
            if (ch == quote) {
                return unescapeJsString(sb.toString()) to (i + 1)
            }
            sb.append(ch)
            i++
        }
        return null
    }

    private fun skipSpaces(source: String, start: Int): Int {
        var i = start
        while (i < source.length && source[i].isWhitespace()) i++
        return i
    }

    private fun unescapeJsString(raw: String): String {
        if (raw.isEmpty()) return raw
        val out = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val ch = raw[i]
            if (ch == '\\' && i + 1 < raw.length) {
                val n = raw[i + 1]
                when (n) {
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    't' -> out.append('\t')
                    '\\' -> out.append('\\')
                    '\'' -> out.append('\'')
                    '"' -> out.append('"')
                    else -> out.append(n)
                }
                i += 2
            } else {
                out.append(ch)
                i++
            }
        }
        return out.toString()
    }
}
