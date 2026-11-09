package com.example.danmuapiapp.data.service

import android.content.Context
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

internal data class RuntimeEnvMergeResult(
    val content: String,
    val addedKeys: List<String>,
    val changed: Boolean
)

/**
 * Seeds config/.env from the core repository's .env.example and merges newer defaults.
 *
 * Existing assignments, comments, blank values and unknown user keys are never rewritten.
 * A marker containing the template fingerprint makes the merge idempotent, so deleting a
 * default later remains an explicit user choice until a different template is bundled.
 */
internal object RuntimeEnvDefaults {
    private const val EXAMPLE_ASSET_PATH = "nodejs-project/config/env.example"
    private const val ENV_RELATIVE_PATH = "config/.env"
    private const val MARKER_PREFIX = "# DanmuApiApp env defaults merged: "
    private val markerRegex = Regex("""^\s*#\s*DanmuApiApp env defaults merged:\s*([0-9a-fA-F]{64})\s*$""")
    private val assignmentRegex = Regex("""^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$""")
    private val keyOccurrenceRegex = Regex("""^\s*#?\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=""")
    private val commentRegex = Regex("""^\s*#""")

    @Synchronized
    fun ensure(context: Context, targetProjectDir: File) {
        val template = readBundledExample(context)
        require(template.isNotBlank()) { "内置 .env.example 为空" }
        val envFile = File(targetProjectDir, ENV_RELATIVE_PATH)
        val existing = envFile.takeIf { it.isFile }?.readText(Charsets.UTF_8)
        if (existing == null || existing.isBlank()) {
            writeAtomically(envFile, initialContent(template))
            return
        }
        val merged = merge(existing, template)
        if (merged.changed) writeAtomically(envFile, merged.content)
    }

    internal fun merge(existing: String, template: String): RuntimeEnvMergeResult {
        val templateText = normalizeDocument(template)
        if (templateText.isBlank()) return RuntimeEnvMergeResult(existing, emptyList(), false)

        val fingerprint = sha256(templateText)
        val marker = "$MARKER_PREFIX$fingerprint"
        val lineSeparator = if (existing.contains("\r\n")) "\r\n" else "\n"
        val lines = existing.removePrefix("\uFEFF").replace("\r\n", "\n").split('\n').toMutableList()
        val markerIndex = lines.indexOfFirst { line ->
            markerRegex.matchEntire(line)?.groupValues?.getOrNull(1)?.equals(fingerprint, ignoreCase = true) == true
        }
        if (markerIndex >= 0) return RuntimeEnvMergeResult(existing, emptyList(), false)

        val existingMarkerIndex = lines.indexOfFirst { markerRegex.matches(it) }
        val entries = extractEntries(templateText)
        if (entries.isEmpty()) return RuntimeEnvMergeResult(existing, emptyList(), false)
        val occupiedKeys = lines.mapNotNull { line ->
            keyOccurrenceRegex.find(line)?.groupValues?.getOrNull(1)?.uppercase()
        }.toSet()
        val missing = entries.filter { it.key.uppercase() !in occupiedKeys }

        if (existingMarkerIndex >= 0) lines[existingMarkerIndex] = marker
        if (missing.isNotEmpty()) {
            trimTrailingBlankLines(lines)
            if (lines.isNotEmpty()) lines += ""
            lines += "# ==================== 核心默认配置（自动补充） ===================="
            lines += "# 以下缺失项来自当前核心 config/.env.example；已有配置不会被覆盖。"
            if (existingMarkerIndex < 0) lines += marker
            missing.forEachIndexed { index, entry ->
                if (index > 0) lines += ""
                lines += entry.comments
                lines += entry.assignment
            }
        } else if (existingMarkerIndex < 0) {
            trimTrailingBlankLines(lines)
            if (lines.isNotEmpty()) lines += ""
            lines += marker
        }

        val normalized = lines.joinToString("\n").trimEnd() + "\n"
        val content = if (lineSeparator == "\r\n") normalized.replace("\n", "\r\n") else normalized
        return RuntimeEnvMergeResult(content, missing.map(EnvTemplateEntry::key), content != existing)
    }

    private fun readBundledExample(context: Context): String {
        return context.assets.open(EXAMPLE_ASSET_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun initialContent(template: String): String {
        val normalized = normalizeDocument(template)
        return "$normalized\n$MARKER_PREFIX${sha256(normalized)}\n"
    }

    private fun extractEntries(template: String): List<EnvTemplateEntry> {
        val lines = template.split('\n')
        val entries = linkedMapOf<String, EnvTemplateEntry>()
        lines.forEachIndexed { index, line ->
            if (commentRegex.containsMatchIn(line)) return@forEachIndexed
            val match = assignmentRegex.matchEntire(line) ?: return@forEachIndexed
            val key = match.groupValues[1]
            val comments = mutableListOf<String>()
            var previous = index - 1
            while (previous >= 0) {
                val comment = lines[previous]
                if (!commentRegex.containsMatchIn(comment)) break
                comments.add(0, comment)
                previous--
            }
            entries.putIfAbsent(key, EnvTemplateEntry(key, line.trimEnd(), comments))
        }
        return entries.values.toList()
    }

    private fun trimTrailingBlankLines(lines: MutableList<String>) {
        while (lines.isNotEmpty() && lines.last().isBlank()) lines.removeAt(lines.lastIndex)
    }

    private fun normalizeDocument(raw: String): String = raw.replace("\r\n", "\n").trimEnd() + "\n"

    private fun sha256(text: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun writeAtomically(file: File, content: String) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, ".env.tmp-${android.os.Process.myPid()}-${System.nanoTime()}")
        try {
            temporary.writeText(content, Charsets.UTF_8)
            try {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: Exception) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            runCatching { temporary.delete() }
        }
    }

    private data class EnvTemplateEntry(
        val key: String,
        val assignment: String,
        val comments: List<String>
    )
}
