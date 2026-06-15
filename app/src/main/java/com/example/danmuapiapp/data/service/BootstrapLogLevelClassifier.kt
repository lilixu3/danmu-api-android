package com.example.danmuapiapp.data.service

import com.example.danmuapiapp.domain.model.LogLevel
import org.json.JSONObject

/**
 * Classifies loose bootstrap/runtime log lines that do not carry an explicit
 * `[INFO]` / `[WARN]` / `[ERROR]` prefix.
 *
 * Some core API success payloads contain fields such as `errorCode` and
 * `errorMessage`; those field names must not make the Android log UI show a
 * successful response as an error.
 */
object BootstrapLogLevelClassifier {

    private val errorTokenRegex = Regex(
        pattern = """(^|[^A-Za-z0-9_])(error|failed|failure|exception|fatal|crash|crashed|uncaughtexception|unhandledrejection)(?=$|[^A-Za-z0-9_])""",
        option = RegexOption.IGNORE_CASE
    )

    private val warnTokenRegex = Regex(
        pattern = """(^|[^A-Za-z0-9_])(warn|warning)(?=$|[^A-Za-z0-9_])""",
        option = RegexOption.IGNORE_CASE
    )

    fun infer(line: String): LogLevel {
        inferJsonLineLevel(line)?.let { return it }

        return when {
            errorTokenRegex.containsMatchIn(line) -> LogLevel.Error
            warnTokenRegex.containsMatchIn(line) -> LogLevel.Warn
            else -> LogLevel.Info
        }
    }

    private fun inferJsonLineLevel(line: String): LogLevel? {
        val trimmed = line.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null

        val obj = runCatching { JSONObject(trimmed) }.getOrNull() ?: return null
        val hasSuccess = obj.has("success")
        val hasErrorCode = obj.has("errorCode")

        if (hasSuccess && !obj.optBoolean("success", false)) {
            return LogLevel.Error
        }

        if (hasErrorCode && obj.optInt("errorCode", 0) != 0) {
            return LogLevel.Error
        }

        if (hasSuccess || hasErrorCode) {
            return LogLevel.Info
        }

        return null
    }
}
