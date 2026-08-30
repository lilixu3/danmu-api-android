package com.example.danmuapiapp.desktop.runtime

import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URI
import java.net.UnknownHostException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** The access-control counters exposed by the Node /__health endpoint. */
data class AccessControlSummary(
    val mode: String?,
    val whitelistCount: Int?,
    val blacklistCount: Int?,
    val blockedRequests: Long?,
)

/**
 * A nullable model of the Node /__health payload.
 *
 * Nullable fields are intentional: older or partially initialized runtimes may omit a
 * diagnostic field. A malformed payload, an invalid field type, or a non-health payload
 * is reported as a failed/unavailable read by [RuntimeHealthClient], rather than being
 * converted into a successful snapshot.
 */
data class RuntimeHealthSnapshot(
    val pid: Long?,
    val node: String?,
    val uptimeSec: Long?,
    val host: String?,
    val mainPort: Int?,
    val proxyPort: Int?,
    val cwd: String?,
    val envHome: String?,
    val resolvedHome: String?,
    val cacheProbeDir: String?,
    val cacheProbeWritable: Boolean?,
    val variant: String?,
    val variantLabel: String?,
    val runtimeIdentity: String?,
    val requestCount: Long?,
    val lastRequestAt: Long?,
    val lastRequestPath: String?,
    val lastClientIp: String?,
    val envFileMtimeMs: Long?,
    val logFile: String?,
    val logLevel: String?,
    val accessControl: AccessControlSummary?,
) {
    /** Descriptive alias for callers that prefer the longer property name. */
    val accessControlSummary: AccessControlSummary? get() = accessControl
}

/** State of a health read, independent of any UI framework. */
sealed class HealthReadState {
    data object Idle : HealthReadState()
    data object Loading : HealthReadState()
    data class Ready(val snapshot: RuntimeHealthSnapshot) : HealthReadState()
    data class Unavailable(val reason: String, val cause: Throwable? = null) : HealthReadState()
    data class Failed(val cause: Throwable) : HealthReadState()
}

/** Returned when the endpoint answered, but did not answer with a health document. */
class RuntimeHealthUnavailableException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

/** Returned when the endpoint answered with a non-success HTTP status. */
class RuntimeHealthHttpException(
    val statusCode: Int,
    val endpoint: URI,
) : IOException("Runtime health endpoint returned HTTP $statusCode: $endpoint")

/**
 * UI-free HTTP reader for the Node /__health endpoint.
 *
 * The host is supplied at construction time and the service port is supplied to [read].
 * The two-argument overload is provided for callers that choose the host for each read.
 */
class RuntimeHealthClient(
    private val host: String = DEFAULT_HOST,
    connectTimeout: Duration = DEFAULT_CONNECT_TIMEOUT,
    private val requestTimeout: Duration = DEFAULT_REQUEST_TIMEOUT,
    private val httpClient: HttpClient = newHttpClient(connectTimeout),
) {
    private val stateRef = AtomicReference<HealthReadState>(HealthReadState.Idle)

    val state: HealthReadState get() = stateRef.get()

    init {
        validateTimeout(connectTimeout, "connectTimeout")
        validateTimeout(requestTimeout, "requestTimeout")
        validateHost(host)
    }

    /** Read http://[host]:[port]/__health. */
    suspend fun read(port: Int): Result<RuntimeHealthSnapshot> = read(host, port)

    /** Read http://[host]:[port]/__health with a per-call host. */
    suspend fun read(host: String, port: Int): Result<RuntimeHealthSnapshot> {
        val endpoint = try {
            validateHost(host)
            validatePort(port)
            healthUri(host, port)
        } catch (error: IllegalArgumentException) {
            stateRef.set(HealthReadState.Failed(error))
            return Result.failure(error)
        }

        stateRef.set(HealthReadState.Loading)
        val request = HttpRequest.newBuilder(endpoint)
            .GET()
            .timeout(requestTimeout)
            .header("Accept", "application/json")
            .build()

        val response = try {
            val future = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                // HttpRequest.timeout does not cover every connection-stage failure on all
                // JDK implementations, so keep an explicit future timeout as well.
                .orTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
            await(future)
        } catch (error: Throwable) {
            val cause = unwrap(error)
            return unavailable(cause)
        }

        if (response.statusCode() != HttpURLConnection.HTTP_OK) {
            val error = RuntimeHealthHttpException(response.statusCode(), endpoint)
            stateRef.set(HealthReadState.Unavailable(error.message ?: "HTTP error", error))
            return Result.failure(error)
        }

        return try {
            val snapshot = StrictHealthJsonParser.parse(response.body())
            stateRef.set(HealthReadState.Ready(snapshot))
            Result.success(snapshot)
        } catch (error: RuntimeHealthUnavailableException) {
            stateRef.set(HealthReadState.Unavailable(error.message ?: "Health data unavailable", error))
            Result.failure(error)
        } catch (error: Throwable) {
            stateRef.set(HealthReadState.Failed(error))
            Result.failure(error)
        }
    }

    private fun unavailable(error: Throwable): Result<RuntimeHealthSnapshot> {
        val reason = error.message ?: error::class.java.simpleName
        stateRef.set(HealthReadState.Unavailable(reason, error))
        return Result.failure(error)
    }

    private companion object {
        const val DEFAULT_HOST = "127.0.0.1"
        val DEFAULT_CONNECT_TIMEOUT: Duration = Duration.ofSeconds(2)
        val DEFAULT_REQUEST_TIMEOUT: Duration = Duration.ofSeconds(3)
        const val MAX_BODY_CHARS = 1_048_576

        fun newHttpClient(connectTimeout: Duration): HttpClient = HttpClient.newBuilder()
            .connectTimeout(connectTimeout)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()

        fun validateTimeout(timeout: Duration, name: String) {
            require(!timeout.isNegative && !timeout.isZero) { "$name must be greater than zero" }
            require(timeout.toMillis() > 0) { "$name must be at least one millisecond" }
        }

        fun validatePort(port: Int) {
            require(port in 1..65_535) { "port must be between 1 and 65535: $port" }
        }

        fun validateHost(host: String) {
            require(host.isNotBlank()) { "host must not be blank" }
            require(!host.any { it.isWhitespace() }) { "host must not contain whitespace" }
            require(!host.contains("//")) { "host must not contain a URI scheme" }
            require(!host.contains('/') && !host.contains('?') && !host.contains('#')) {
                "host must be a host name or address, not a URI"
            }
        }

        fun healthUri(host: String, port: Int): URI {
            val authority = if (host.startsWith("[") || host.count { it == ':' } < 2) {
                host
            } else {
                "[$host]"
            }
            return URI("http", "$authority:$port", "/__health", null, null)
        }

        fun unwrap(error: Throwable): Throwable {
            var current = error
            while (current is CompletionException || current is java.util.concurrent.ExecutionException) {
                current = current.cause ?: break
            }
            return current
        }

        suspend fun <T> await(future: CompletableFuture<T>): T = suspendCoroutine { continuation ->
            future.whenComplete { value, error ->
                if (error == null) {
                    continuation.resume(value)
                } else {
                    continuation.resumeWith(Result.failure(unwrap(error)))
                }
            }
        }
    }
}

/** Explicitly named alias for code that wants to emphasize the HTTP implementation. */
typealias HttpRuntimeHealthClient = RuntimeHealthClient

internal object StrictHealthJsonParser {
    fun parse(body: String): RuntimeHealthSnapshot {
        if (body.length > MAX_BODY_CHARS) {
            throw JsonSyntaxException("health response exceeds $MAX_BODY_CHARS characters")
        }
        val root = JsonParser(body).parseDocument().asObject("root")
        val ok = root.optionalBoolean("ok")
            ?: throw RuntimeHealthUnavailableException("health response is missing boolean field 'ok'")
        if (!ok) {
            throw RuntimeHealthUnavailableException("health endpoint reported ok=false")
        }

        val ports = root.optionalObject("ports")
        val accessControl = root.optionalObject("accessControl")?.let { access ->
            AccessControlSummary(
                mode = access.optionalString("mode"),
                whitelistCount = access.optionalInt("whitelistCount"),
                blacklistCount = access.optionalInt("blacklistCount"),
                blockedRequests = access.optionalLong("blockedRequests"),
            )
        }

        return RuntimeHealthSnapshot(
            pid = root.optionalLong("pid"),
            node = root.optionalString("node"),
            uptimeSec = root.optionalLong("uptimeSec"),
            host = root.optionalString("host"),
            mainPort = ports?.optionalInt("main"),
            proxyPort = ports?.optionalInt("proxy"),
            cwd = root.optionalString("cwd"),
            envHome = root.optionalString("envHome"),
            resolvedHome = root.optionalString("resolvedHome"),
            cacheProbeDir = root.optionalString("cacheProbeDir"),
            cacheProbeWritable = root.optionalBoolean("cacheProbeWritable"),
            variant = root.optionalString("variant"),
            variantLabel = root.optionalString("variantLabel"),
            runtimeIdentity = root.optionalString("runtimeIdentity"),
            requestCount = root.optionalLong("requestCount"),
            lastRequestAt = root.optionalLong("lastRequestAt"),
            lastRequestPath = root.optionalString("lastRequestPath"),
            lastClientIp = root.optionalString("lastClientIp"),
            // Node's fs.Stats.mtimeMs is a JSON number and may contain fractional milliseconds.
            // Keep the rest of the health contract integer-only; only this timestamp has that
            // documented fractional representation.
            envFileMtimeMs = root.optionalTimestampMillis("envFileMtimeMs"),
            logFile = root.optionalString("logFile"),
            logLevel = root.optionalString("logLevel"),
            accessControl = accessControl,
        )
    }

    private const val MAX_BODY_CHARS = 1_048_576

    private fun JsonValue.asObject(label: String): JsonObject = when (this) {
        is JsonObject -> this
        else -> throw JsonSyntaxException("$label must be a JSON object")
    }

    private fun JsonObject.optionalObject(name: String): JsonObject? = when (val value = fields[name]) {
        null, JsonNull -> null
        is JsonObject -> value
        else -> throw JsonTypeException(name, "object or null")
    }

    private fun JsonObject.optionalString(name: String): String? = when (val value = fields[name]) {
        null, JsonNull -> null
        is JsonString -> value.value
        else -> throw JsonTypeException(name, "string or null")
    }

    private fun JsonObject.optionalBoolean(name: String): Boolean? = when (val value = fields[name]) {
        null, JsonNull -> null
        is JsonBoolean -> value.value
        else -> throw JsonTypeException(name, "boolean or null")
    }

    private fun JsonObject.optionalLong(name: String): Long? = when (val value = fields[name]) {
        null, JsonNull -> null
        is JsonNumber -> value.raw.toLongOrNull()
            ?: throw JsonTypeException(name, "integer or null")
        else -> throw JsonTypeException(name, "integer or null")
    }

    private fun JsonObject.optionalTimestampMillis(name: String): Long? = when (val value = fields[name]) {
        null, JsonNull -> null
        is JsonNumber -> parseTimestampMillis(value.raw, name)
        else -> throw JsonTypeException(name, "number or null")
    }

    private fun parseTimestampMillis(raw: String, fieldName: String): Long {
        val decimal = try {
            BigDecimal(raw)
        } catch (_: NumberFormatException) {
            throw JsonTypeException(fieldName, "number or null")
        }
        if (decimal.signum() < 0 || decimal > BigDecimal.valueOf(Long.MAX_VALUE)) {
            throw JsonTypeException(fieldName, "non-negative 64-bit number or null")
        }
        return try {
            decimal.toBigInteger()
                .coerceAtMost(BigInteger.valueOf(Long.MAX_VALUE))
                .longValueExact()
        } catch (_: ArithmeticException) {
            throw JsonTypeException(fieldName, "non-negative 64-bit number or null")
        }
    }

    private fun JsonObject.optionalInt(name: String): Int? {
        val value = optionalLong(name) ?: return null
        return value.toIntOrNull(name)
    }

    private fun Long.toIntOrNull(fieldName: String): Int {
        if (this < Int.MIN_VALUE || this > Int.MAX_VALUE) {
            throw JsonTypeException(fieldName, "32-bit integer or null")
        }
        return toInt()
    }

    private sealed interface JsonValue

    private data class JsonObject(val fields: Map<String, JsonValue>) : JsonValue
    private data class JsonArray(val values: List<JsonValue>) : JsonValue
    private data class JsonString(val value: String) : JsonValue
    private data class JsonNumber(val raw: String) : JsonValue
    private data class JsonBoolean(val value: Boolean) : JsonValue
    private data object JsonNull : JsonValue

    private class JsonParser(private val input: String) {
        private var index = 0

        fun parseDocument(): JsonValue {
            skipWhitespace()
            val value = parseValue()
            skipWhitespace()
            if (index != input.length) fail("unexpected character after JSON value")
            return value
        }

        private fun parseValue(): JsonValue {
            if (index >= input.length) fail("expected JSON value")
            return when (input[index]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> JsonString(parseString())
                't' -> {
                    consumeLiteral("true")
                    JsonBoolean(true)
                }
                'f' -> {
                    consumeLiteral("false")
                    JsonBoolean(false)
                }
                'n' -> {
                    consumeLiteral("null")
                    JsonNull
                }
                '-', in '0'..'9' -> JsonNumber(parseNumber())
                else -> fail("unexpected character '${input[index]}'")
            }
        }

        private fun parseObject(): JsonObject {
            expect('{')
            skipWhitespace()
            val values = LinkedHashMap<String, JsonValue>()
            if (consumeIf('}')) return JsonObject(values)
            while (true) {
                if (index >= input.length || input[index] != '"') {
                    fail("object key must be a JSON string")
                }
                val key = parseString()
                if (values.containsKey(key)) fail("duplicate object key '$key'")
                skipWhitespace()
                expect(':')
                skipWhitespace()
                values[key] = parseValue()
                skipWhitespace()
                when {
                    consumeIf('}') -> return JsonObject(values)
                    consumeIf(',') -> {
                        skipWhitespace()
                        if (index >= input.length || input[index] == '}') {
                            fail("trailing comma in object")
                        }
                    }
                    else -> fail("expected ',' or '}' in object")
                }
            }
        }

        private fun parseArray(): JsonArray {
            expect('[')
            skipWhitespace()
            val values = ArrayList<JsonValue>()
            if (consumeIf(']')) return JsonArray(values)
            while (true) {
                values += parseValue()
                skipWhitespace()
                when {
                    consumeIf(']') -> return JsonArray(values)
                    consumeIf(',') -> {
                        skipWhitespace()
                        if (index >= input.length || input[index] == ']') {
                            fail("trailing comma in array")
                        }
                    }
                    else -> fail("expected ',' or ']' in array")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val out = StringBuilder()
            while (index < input.length) {
                when (val character = input[index++]) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (index >= input.length) fail("unterminated escape sequence")
                        when (val escaped = input[index++]) {
                            '"' -> out.append('"')
                            '\\' -> out.append('\\')
                            '/' -> out.append('/')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> out.append(parseUnicodeEscape())
                            else -> fail("invalid escape '\\$escaped'")
                        }
                    }
                    else -> {
                        if (character.code < 0x20) fail("unescaped control character in string")
                        out.append(character)
                    }
                }
            }
            fail("unterminated string")
        }

        private fun parseUnicodeEscape(): Char {
            if (index + 4 > input.length) fail("incomplete unicode escape")
            var value = 0
            repeat(4) {
                val digit = input[index++].digitToIntOrNull(16)
                    ?: fail("invalid unicode escape")
                value = (value shl 4) or digit
            }
            return value.toChar()
        }

        private fun parseNumber(): String {
            val start = index
            consumeIf('-')
            when {
                consumeIf('0') -> {
                    if (index < input.length && input[index].isDigit()) {
                        fail("leading zero in number")
                    }
                }
                index < input.length && input[index] in '1'..'9' -> {
                    index++
                    while (index < input.length && input[index] in '0'..'9') index++
                }
                else -> fail("invalid number")
            }
            if (consumeIf('.')) {
                if (index >= input.length || !input[index].isDigit()) {
                    fail("fraction requires at least one digit")
                }
                while (index < input.length && input[index].isDigit()) index++
            }
            if (index < input.length && (input[index] == 'e' || input[index] == 'E')) {
                index++
                if (index < input.length && (input[index] == '+' || input[index] == '-')) index++
                if (index >= input.length || !input[index].isDigit()) {
                    fail("exponent requires at least one digit")
                }
                while (index < input.length && input[index].isDigit()) index++
            }
            return input.substring(start, index)
        }

        private fun consumeLiteral(literal: String) {
            if (!input.startsWith(literal, index)) fail("expected '$literal'")
            index += literal.length
        }

        private fun expect(expected: Char) {
            if (!consumeIf(expected)) fail("expected '$expected'")
        }

        private fun consumeIf(expected: Char): Boolean {
            if (index < input.length && input[index] == expected) {
                index++
                return true
            }
            return false
        }

        private fun skipWhitespace() {
            while (index < input.length) {
                when (input[index]) {
                    ' ', '\t', '\r', '\n' -> index++
                    else -> return
                }
            }
        }

        private fun fail(message: String): Nothing {
            throw JsonSyntaxException("$message at character $index")
        }
    }

    private open class JsonReadException(message: String) : IOException(message)
    private class JsonSyntaxException(message: String) : JsonReadException(message)
    private class JsonTypeException(field: String, expected: String) :
        JsonReadException("field '$field' must be $expected")
}
