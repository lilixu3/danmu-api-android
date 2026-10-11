package com.example.danmuapiapp.data.tunnel

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TunnelLinkSnapshotTest {
    private val identity = TunnelProcessIdentity(42424L, "12345", "boot-A", "launch-A")
    private fun status() = JSONObject().put("pid", identity.pid).put("startTicks", identity.startTicks)
        .put("bootId", identity.bootId).put("instanceId", identity.instanceId)
    private fun snapshot(state: String = "Connected") = JSONObject()
        .put("instanceId", identity.instanceId).put("state", state)

    @Test fun `boot connection evidence remains connected without log text`() {
        assertEquals(TunnelLinkState.Connected, resolveTunnelLinkState(true, identity, status(), snapshot()))
    }

    @Test fun `old pid boot or launch cannot provide connection evidence`() {
        for (old in listOf(status().put("pid", 31313), status().put("startTicks", "different"),
            status().put("bootId", "boot-B"), status().put("instanceId", "launch-B"), JSONObject())) {
            assertEquals(TunnelLinkState.Unknown, resolveTunnelLinkState(true, identity, old, snapshot()))
        }
        assertEquals(TunnelLinkState.Unknown, resolveTunnelLinkState(true, identity, status(), snapshot().put("instanceId", "launch-B")))
        assertEquals(TunnelLinkState.Unknown, resolveTunnelLinkState(false, identity, status(), snapshot()))
        assertEquals(TunnelLinkState.Unknown, resolveTunnelLinkState(true, identity, status(), JSONObject()))
    }

    @Test fun `observer handles fragmented logs and keeps evidence through unrelated traffic`() {
        val events = mutableListOf<TunnelLinkState>()
        val observer = FrpcLinkObserver { events += it }
        "[I] start proxy success\n".forEach { observer.accept(it.toString()) }
        repeat(200) { observer.accept("connect to local service error: connection refused\n") }
        assertEquals(listOf(TunnelLinkState.Connected), events)
        observer.accept("heartbeat timeout\ntry to connect to server\nlogin to server success\n")
        observer.accept("start error: proxy already exists\nstart proxy success")
        observer.finish()
        assertEquals(listOf(TunnelLinkState.Connected, TunnelLinkState.Error, TunnelLinkState.Connecting,
            TunnelLinkState.Conflict, TunnelLinkState.Connected), events)
    }
}
