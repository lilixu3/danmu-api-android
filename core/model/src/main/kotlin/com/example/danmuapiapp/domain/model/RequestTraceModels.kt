package com.example.danmuapiapp.domain.model

data class RequestTraceDetail(
    val id: String,
    val method: String,
    val path: String,
    val query: String,
    val clientIp: String,
    val statusCode: Int?,
    val timestamp: Long,
    val totalMs: Long,
    val prepareMs: Long,
    val handlerMs: Long,
    val diagnosis: RequestTraceDiagnosis?,
    val steps: List<RequestTraceStep>,
    val configs: List<RequestTraceConfig>
)

data class RequestTraceDiagnosis(
    val conclusion: String,
    val zeroStep: String?,
    val chain: List<RequestTraceDiagnosticStep>
)

data class RequestTraceDiagnosticStep(
    val name: String,
    val count: Int,
    val rawCount: Int?,
    val zero: Boolean
)

data class RequestTraceStep(
    val name: String,
    val status: String,
    val durationMs: Long,
    val reason: String?,
    val inputs: TraceValue?,
    val outputs: TraceValue?,
    val error: TraceValue?,
    val table: TraceTable?,
    val changes: List<TraceChange>
)

data class RequestTraceConfig(
    val name: String,
    val category: String,
    val description: String,
    val value: TraceValue?,
    val used: Boolean,
    val hit: Boolean?,
    val step: String?,
    val effectStep: String?
)

sealed interface TraceValue {
    data class Scalar(val text: String) : TraceValue
    data class Flag(val enabled: Boolean) : TraceValue
    data class Fields(val entries: Map<String, TraceValue>) : TraceValue
    data class Items(val values: List<TraceValue>) : TraceValue
    data object Empty : TraceValue
}

data class TraceTable(val columns: List<String>, val rows: List<List<TraceValue>>)

data class TraceChange(
    val label: String?,
    val config: String?,
    val kind: String?,
    val hit: Boolean?,
    val summary: String?,
    val context: TraceValue?,
    val before: TraceValue?,
    val after: TraceValue?,
    val table: TraceTable?,
    val kept: List<TraceValue>,
    val keptCount: Int,
    val removed: List<TraceValue>,
    val removedCount: Int
)
