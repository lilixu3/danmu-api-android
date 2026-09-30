package com.example.danmuapiapp.data.tunnel

import org.junit.Assert.assertEquals
import org.junit.Test

class TunnelLinkStateRegressionTest {
    @Test fun `duplicate proxy is a conflict even after a previous success`() {
        assertEquals(TunnelLinkState.Conflict, parseFrpcLogLinkState("""
            [I] [api] start proxy success
            [W] [api] start error: proxy [user.api] already exists
        """.trimIndent()))
    }

    @Test fun `recovery clears history from a different process`() {
        assertEquals(TunnelLinkState.Unknown, parseFrpcLogLinkState("""
            [W] start error: proxy [api] already exists
            [app] frpc process adopted
        """.trimIndent()))
        assertEquals(TunnelLinkState.Unknown, parseFrpcLogLinkState("""
            [I] [api] start proxy success
            [I] start frpc service for config file [frpc.conf]
        """.trimIndent()))
    }

    @Test fun `reconnection requires a fresh successful registration`() {
        assertEquals(TunnelLinkState.Connecting, parseFrpcLogLinkState("""
            [I] [api] start proxy success
            [I] try to connect to server...
            [I] login to server success, get run id [new]
        """.trimIndent()))
        assertEquals(TunnelLinkState.Error, parseFrpcLogLinkState("[I] start proxy success\n[W] heartbeat timeout"))
    }

    @Test fun `fresh success resolves conflict and local service errors keep the tunnel connected`() {
        assertEquals(TunnelLinkState.Connected, parseFrpcLogLinkState("""
            [W] start error: proxy [api] already exists
            [I] [api] start proxy success
            [E] connect to local service [127.0.0.1:9321] error: connection refused
        """.trimIndent()))
    }
}
