package com.example.danmuapiapp.data.service

import android.content.Context
import com.example.danmuapiapp.data.util.ShellUtils.shellQuote
import org.json.JSONObject
import java.io.File

object StartupFailureStore {

    data class FailureInfo(
        val summary: String,
        val detail: String,
        val stage: String?,
        val timestampMs: Long?
    ) {
        fun userMessage(): String {
            return summary.ifBlank {
                detail.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
            }.ifBlank { "未知启动错误" }
        }
    }

    private const val FILE_NAME = "startup-failure.json"
    private const val MAX_DETAIL_CHARS = 12_000

    fun clearNormal(context: Context) {
        runCatching { normalFile(context).delete() }
    }

    fun readNormal(context: Context): FailureInfo? {
        val file = normalFile(context)
        if (!file.exists() || !file.isFile) return null
        val raw = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return null
        return parse(raw)
    }

    fun clearRoot(context: Context) {
        val path = rootFilePath(context)
        RootShell.exec(
            """
                FILE=${shellQuote(path)}
                rm -f "${'$'}FILE" 2>/dev/null || true
            """.trimIndent(),
            timeoutMs = 2500L
        )
    }

    fun readRoot(context: Context): FailureInfo? {
        val path = rootFilePath(context)
        val result = RootShell.exec(
            """
                FILE=${shellQuote(path)}
                if [ -f "${'$'}FILE" ]; then
                  cat "${'$'}FILE"
                fi
            """.trimIndent(),
            timeoutMs = 2500L
        )
        val raw = result.stdout.trim()
        if (raw.isBlank()) return null
        return parse(raw)
    }

    private fun normalFile(context: Context): File {
        return File(RuntimePaths.normalProjectDir(context), "logs/$FILE_NAME")
    }

    private fun rootFilePath(context: Context): String {
        return "${RuntimePaths.rootProjectDir(context).absolutePath}/logs/$FILE_NAME"
    }

    private fun parse(raw: String): FailureInfo? {
        val text = raw.trim()
        if (text.isBlank()) return null
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val summary = json.optString("summary").trim()
        val detail = json.optString("detail").trim().take(MAX_DETAIL_CHARS)
        val stage = json.optString("stage").trim().ifBlank { null }
        val timestamp = json.optLong("ts").takeIf { it > 0L }
        if (summary.isBlank() && detail.isBlank()) return null
        return FailureInfo(
            summary = summary,
            detail = detail,
            stage = stage,
            timestampMs = timestamp
        )
    }
}
