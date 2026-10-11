package com.example.danmuapiapp.data.tunnel

import org.json.JSONObject

/** Identity and connection evidence must belong to the same process and boot. */
internal data class TunnelProcessIdentity(
    val pid: Long = 0L,
    val startTicks: String = "",
    val bootId: String = "",
    val instanceId: String = ""
) {
    val valid: Boolean get() = pid > 1L && startTicks.toLongOrNull() != null &&
        bootId.isNotBlank() && instanceId.isNotBlank()

    fun matches(status: JSONObject): Boolean = valid &&
        status.optLong("pid", 0L) == pid &&
        status.optString("startTicks") == startTicks &&
        status.optString("bootId") == bootId &&
        status.optString("instanceId") == instanceId
}

/** Missing evidence stays unknown; process liveness alone never proves registration. */
internal fun resolveTunnelLinkState(
    running: Boolean,
    identity: TunnelProcessIdentity,
    status: JSONObject,
    snapshot: JSONObject
): TunnelLinkState {
    if (!running || !identity.matches(status) ||
        snapshot.optString("instanceId") != identity.instanceId
    ) return TunnelLinkState.Unknown
    return TunnelLinkState.entries.firstOrNull { it.name == snapshot.optString("state") }
        ?: TunnelLinkState.Unknown
}

/** Null means no connection event. In particular, local target errors retain the link. */
internal fun frpcLogLinkEvent(line: String): TunnelLinkState? {
    val lower = line.lowercase()
    if (lower.contains("local service")) return null
    return when {
        lower.contains("start frpc service for config file") || lower.contains("[app] frpc process adopted") -> TunnelLinkState.Unknown
        lower.contains("start error") && lower.contains("already exists") -> TunnelLinkState.Conflict
        lower.contains("start proxy success") -> TunnelLinkState.Connected
        lower.contains("login to server success") || lower.contains("try to connect to server") -> TunnelLinkState.Connecting
        lower.contains("login to server failed") || lower.contains("heartbeat timeout") ||
            lower.contains("connect to server error") || lower.contains("start error") ||
            lower.contains("i/o timeout") || lower.contains("connection refused") -> TunnelLinkState.Error
        else -> null
    }
}

/** Parse UTF-8 chunks without depending on read boundaries or retaining unbounded lines. */
internal class FrpcLinkObserver(private val publish: (TunnelLinkState) -> Unit) {
    private val pending = StringBuilder()
    private var dropping = false
    private var state = TunnelLinkState.Unknown

    fun accept(text: String) {
        text.forEach { char ->
            if (char == '\n') {
                if (!dropping) frpcLogLinkEvent(pending.toString())?.let { next ->
                    if (next != state) { state = next; publish(next) }
                }
                pending.setLength(0)
                dropping = false
            } else if (!dropping) {
                if (pending.length >= 64 * 1024) { pending.setLength(0); dropping = true }
                else pending.append(char)
            }
        }
    }

    fun finish() { accept("\n") }
}
