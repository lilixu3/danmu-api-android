package com.example.danmuapiapp.desktop.runtime

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Direct edits to the running Node project's config/.env file. */
object DesktopRuntimeEnv {

    /**
     * Updates ADMIN_TOKEN without restarting Node. The core watches config/.env and reloads it.
     * A missing runtime directory is reported as not applied so the persisted desktop setting can
     * still be picked up by the next service start.
     */
    fun applyAdminToken(scriptDir: File, token: String?): Boolean {
        if (!scriptDir.isDirectory) return false
        val normalized = token?.trim().orEmpty()
        val envFile = File(scriptDir, "config/.env")
        updateValue(envFile, "ADMIN_TOKEN", normalized)
        return true
    }

    internal fun updateValue(envFile: File, key: String, value: String) {
        require(key.matches(Regex("[A-Z][A-Z0-9_]*"))) {
            "环境变量名非法：$key"
        }
        val existing = if (envFile.isFile) {
            envFile.readText(Charsets.UTF_8)
        } else {
            ""
        }
        val sourceLines = if (existing.isBlank()) {
            emptyList()
        } else {
            existing.replace("\r\n", "\n").split('\n').dropLastWhile { it.isEmpty() }
        }
        val lines = mutableListOf<String>()
        var found = false
        sourceLines.forEach { line ->
            val equals = line.indexOf('=')
            val lineKey = if (equals > 0) line.substring(0, equals).trim() else ""
            if (lineKey.equals(key, ignoreCase = true)) {
                found = true
                if (value.isNotEmpty()) lines += "$key=${formatValue(value)}"
            } else {
                lines += line
            }
        }
        if (!found && value.isNotEmpty()) lines += "$key=${formatValue(value)}"

        val output = lines.joinToString("\n").trimEnd() + "\n"
        val parent = envFile.parentFile
            ?: throw IOException(".env 路径没有父目录：${envFile.absolutePath}")
        parent.mkdirs()
        val temporary = File(parent, "${envFile.name}.tmp-${System.nanoTime()}")
        try {
            temporary.writeText(output, Charsets.UTF_8)
            try {
                Files.move(
                    temporary.toPath(),
                    envFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: Exception) {
                Files.move(
                    temporary.toPath(),
                    envFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
            val actual = readValue(envFile, key).orEmpty()
            if (actual != value) {
                throw IOException("写入 .env 后校验失败：${envFile.absolutePath}")
            }
        } finally {
            temporary.delete()
        }
    }

    internal fun readValue(envFile: File, key: String): String? {
        if (!envFile.isFile) return null
        return envFile.readLines(Charsets.UTF_8).asSequence()
            .mapNotNull { line ->
                val equals = line.indexOf('=')
                if (equals <= 0 || line.trimStart().startsWith('#')) return@mapNotNull null
                val lineKey = line.substring(0, equals).trim()
                if (!lineKey.equals(key, ignoreCase = true)) return@mapNotNull null
                parseValue(line.substring(equals + 1).trim())
            }
            .firstOrNull()
    }

    private fun formatValue(value: String): String {
        if (value.isNotEmpty() && value.all { it.isLetterOrDigit() || it in "_./:@-" }) {
            return value
        }
        return buildString {
            append('"')
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
            append('"')
        }
    }

    private fun parseValue(value: String): String {
        if (value.length >= 2 && value.first() == '"' && value.last() == '"') {
            return value.substring(1, value.length - 1)
                .replace("\\t", "\t")
                .replace("\\r", "\r")
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
        }
        if (value.length >= 2 && value.first() == '\'' && value.last() == '\'') {
            return value.substring(1, value.length - 1)
        }
        return value
    }
}
