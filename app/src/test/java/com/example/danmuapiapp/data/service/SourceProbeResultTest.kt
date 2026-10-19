package com.example.danmuapiapp.data.service

import com.example.danmuapiapp.ui.component.connectivityStatusLabel
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SourceProbeResultTest {
    @Test fun `an unknown core capability is unconfirmed instead of offline`() {
        val result = decodeSourceProbeResult("aiyifan", JSONObject("""{"status":"unsupported","connected":false,"reason":"当前核心未提供线路解析"}"""), "")
        assertTrue(result.connectivityResult().unconfirmed)
        assertEquals("未确认", connectivityStatusLabel(result.connectivityResult(), false))
    }
    @Test fun `TMDB missing key keeps response and latency through network-page conversion`() {
        val result = decodeSourceProbeResult("tmdb", JSONObject("""{"status":"ok","connected":true,"httpStatus":401,"durationMs":42,"configurationRequired":true,"reason":"已响应，但 API Key 未配置"}"""), "")
        assertEquals("ok", result.status)
        assertEquals(401, result.httpStatus)
        val connectivity = result.connectivityResult()
        assertTrue(connectivity.connected)
        assertTrue(connectivity.configurationRequired)
        assertEquals(42L, connectivity.durationMs)
        assertEquals("已响应 · API Key 未配置", connectivityStatusLabel(connectivity, false))
    }

    @Test fun `unconfigured address is distinct from an offline source in both pages`() {
        val result = decodeSourceProbeResult("custom", JSONObject("""{"status":"unconfigured","connected":false,"reason":"来源地址未配置"}"""), "")
        assertTrue(result.configurationRequired)
        val connectivity = result.connectivityResult()
        assertFalse(connectivity.connected)
        assertNull(connectivity.durationMs)
        assertEquals("未配置", connectivityStatusLabel(connectivity, false))
    }

    @Test fun `configured rejected key remains a restricted but connected response`() {
        val result = decodeSourceProbeResult("tmdb", JSONObject("""{"status":"restricted","connected":true,"httpStatus":401,"durationMs":51,"configurationRequired":false,"reason":"已响应，但 API Key 无效或无权限"}"""), "")
        assertEquals("restricted", result.status)
        assertFalse(result.configurationRequired)
        assertEquals("51 ms", connectivityStatusLabel(result.connectivityResult(), false))
    }

    @Test fun `transport failure does not become missing configuration and progress hides old result`() {
        val result = decodeSourceProbeResult("tmdb", JSONObject("""{"status":"failed","connected":false,"durationMs":12000}"""), "")
        assertFalse(result.configurationRequired)
        val connectivity = result.connectivityResult()
        assertNull(connectivity.durationMs)
        assertEquals("未连通", connectivityStatusLabel(connectivity, false))
        assertEquals("检测中…", connectivityStatusLabel(connectivity, true))
    }

    @Test fun `failed response-body read cannot stay connected or retain latency`() {
        val result = decodeSourceProbeResult("tmdb", JSONObject("""{"status":"failed","connected":true,"durationMs":38}"""), "")
        val connectivity = result.connectivityResult()
        assertFalse(connectivity.connected)
        assertNull(connectivity.durationMs)
        assertEquals("未连通", connectivityStatusLabel(connectivity, false))
    }
}
