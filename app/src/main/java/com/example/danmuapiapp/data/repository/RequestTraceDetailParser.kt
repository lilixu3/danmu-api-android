package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.data.util.ParseUtils.parseTimestamp
import com.example.danmuapiapp.domain.model.RequestTraceConfig
import com.example.danmuapiapp.domain.model.RequestTraceDetail
import com.example.danmuapiapp.domain.model.FileNameMatchSummary
import com.example.danmuapiapp.domain.model.RequestTraceDiagnosticStep
import com.example.danmuapiapp.domain.model.RequestTraceDiagnosis
import com.example.danmuapiapp.domain.model.RequestTraceStep
import com.example.danmuapiapp.domain.model.TraceChange
import com.example.danmuapiapp.domain.model.TraceTable
import com.example.danmuapiapp.domain.model.TraceValue
import org.json.JSONArray
import org.json.JSONObject

internal fun parseRequestTraceDetail(raw: String): RequestTraceDetail? {
    val trace = runCatching { JSONObject(raw).optJSONObject("trace") }.getOrNull() ?: return null
    val timestamp = trace.opt("timestamp")
    val diagnosis = trace.optJSONObject("diagnosis")?.let { rawDiagnosis ->
        val chain = rawDiagnosis.optJSONArray("chain").toObjectList().map { step ->
            RequestTraceDiagnosticStep(
                name = step.optString("name"),
                count = step.optInt("count"),
                rawCount = step.optInt("rawCount", -1).takeIf { it >= 0 },
                zero = step.optBoolean("zero")
            )
        }
        RequestTraceDiagnosis(
            conclusion = rawDiagnosis.optString("conclusion"),
            zeroStep = rawDiagnosis.optString("zeroStep").takeUnless { it.isBlank() || it == "null" },
            chain = chain
        )
    }
    val steps = trace.optJSONArray("steps").toObjectList().map { step ->
        RequestTraceStep(
            name = step.optString("name", "处理步骤"),
            status = step.optString("status"),
            durationMs = step.optLong("ms", step.optLong("durationMs", 0L)).coerceAtLeast(0L),
            reason = step.optString("reason").nullableJsonText(),
            inputs = step.stepValue("inputs"),
            outputs = step.stepValue("outputs"),
            error = step.stepValue("error"),
            table = step.optJSONObject("table")?.toTraceTable(),
            changes = step.optJSONArray("changes").toObjectList().map { it.toTraceChange() }
        )
    }
    val configs = trace.optJSONArray("configs").toObjectList().map { config ->
        RequestTraceConfig(
            name = config.optString("name"),
            category = config.optString("category", "system"),
            description = config.optString("description"),
            value = config.optionalValue("value"),
            used = config.optBoolean("used"),
            hit = config.opt("hit").takeUnless { it == null || it == JSONObject.NULL }?.let {
                config.optBoolean("hit")
            },
            step = config.optString("step").nullableJsonText(),
            effectStep = config.optString("effectStep").nullableJsonText()
        )
    }
    return RequestTraceDetail(
        id = trace.optString("id"),
        method = trace.optString("method", "GET"),
        path = trace.optString("path"),
        query = trace.optString("query"),
        clientIp = trace.optString("clientIp"),
        statusCode = trace.optInt("statusCode", Int.MIN_VALUE).takeIf { it in 100..599 },
        timestamp = when (timestamp) {
            is Number -> timestamp.toLong()
            is String -> parseTimestamp(timestamp)
            else -> 0L
        },
        totalMs = trace.optLong("totalMs").coerceAtLeast(0L),
        prepareMs = trace.optLong("prepareMs").coerceAtLeast(0L),
        handlerMs = trace.optLong("handlerMs").coerceAtLeast(0L),
        diagnosis = diagnosis,
        steps = steps,
        configs = configs
    )
}

internal fun parseFileNameOnlyMatch(params: JSONObject?): FileNameMatchSummary? {
    val fileName = params?.optString("fileName")?.trim().orEmpty()
    if (params?.optString("matchMode") != "fileNameOnly" || fileName.isBlank()) return null
    return FileNameMatchSummary(
        fileName = fileName,
        fileHash = params.optString("fileHash").trim().takeIf { it.isNotBlank() && it != "null" },
        fileSize = params.optionalLong("fileSize"),
        videoDuration = params.optionalLong("videoDuration")
    )
}

private fun JSONObject.optionalLong(name: String): Long? =
    if (!has(name) || isNull(name)) null else optLong(name, 0L).coerceAtLeast(0L)

private fun JSONArray?.toObjectList(): List<JSONObject> {
    if (this == null) return emptyList()
    return List(length()) { index -> optJSONObject(index) }.filterNotNull()
}

private fun Any?.toTraceValue(): TraceValue = when (this) {
    null, JSONObject.NULL -> TraceValue.Empty
    is JSONObject -> TraceValue.Fields(keys().asSequence().associateWith { opt(it).toTraceValue() })
    is JSONArray -> TraceValue.Items(List(length()) { opt(it).toTraceValue() })
    is Boolean -> TraceValue.Flag(this)
    else -> TraceValue.Scalar(toString())
}

private fun JSONObject.optionalValue(key: String): TraceValue? =
    if (has(key)) opt(key).toTraceValue() else null

private fun JSONObject.stepValue(key: String): TraceValue? =
    when (val value = optionalValue(key)) {
        null, TraceValue.Empty -> null
        is TraceValue.Items -> value.takeIf { it.values.isNotEmpty() }
        is TraceValue.Fields -> value.takeIf { it.entries.isNotEmpty() }
        else -> value
    }

private fun JSONObject.toTraceTable(): TraceTable? {
    val headers = optJSONArray("columns") ?: return null
    val columns = List(headers.length()) { headers.optString(it) }
    val rows = optJSONArray("rows").toObjectList().map { row ->
        columns.map { row.opt(it).toTraceValue() }
    }
    return if (columns.isEmpty() || rows.isEmpty()) null else TraceTable(columns, rows)
}

private fun JSONObject.toTraceChange(): TraceChange {
    fun items(key: String): List<TraceValue> =
        optJSONArray(key)?.let { array -> List(array.length()) { array.opt(it).toTraceValue() } }.orEmpty()
    val kept = items("kept")
    val removed = items("removed")
    return TraceChange(
        label = optString("label").nullableJsonText(),
        config = optString("config").nullableJsonText(),
        kind = optString("kind").nullableJsonText(),
        hit = opt("hit") as? Boolean,
        summary = optString("summary").nullableJsonText(),
        context = optionalValue("context"),
        before = optionalValue("before"),
        after = optionalValue("after"),
        table = optJSONObject("table")?.toTraceTable(),
        kept = kept,
        keptCount = optInt("keptCount", kept.size).coerceAtLeast(kept.size),
        removed = removed,
        removedCount = optInt("removedCount", removed.size).coerceAtLeast(removed.size)
    )
}

private fun String.nullableJsonText(): String? = takeUnless { isBlank() || it == "null" }
