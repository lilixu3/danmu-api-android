package com.example.danmuapiapp.ui.screen.apitest

import com.example.danmuapiapp.domain.model.DanmuDownloadFormat
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiTestExportModelsTest {

    @Test
    fun `export catalog exposes every backend format once`() {
        assertEquals(
            DanmuDownloadFormat.entries.toSet(),
            ApiTestExportCatalog.options.map { it.format }.toSet()
        )
        assertEquals(
            DanmuDownloadFormat.entries.size,
            ApiTestExportCatalog.options.size
        )
    }

    @Test
    fun `export url supports episode and direct video url`() {
        assertEquals(
            "http://127.0.0.1:9321/token/api/v2/comment/1001?format=danuni.binpb",
            buildDanmuExportUrl(
                apiBaseUrl = "http://127.0.0.1:9321/token/",
                target = DanmuExportTarget.Episode(1001),
                format = DanmuDownloadFormat.DanuniBinPb
            )
        )

        val direct = buildDanmuExportUrl(
            apiBaseUrl = "https://example.com",
            target = DanmuExportTarget.VideoUrl("https://v.qq.com/play?id=1&ep=2"),
            format = DanmuDownloadFormat.BiliXml
        )
        assertTrue(direct.contains("url=https%3A%2F%2Fv.qq.com%2Fplay%3Fid%3D1%26ep%3D2"))
        assertTrue(direct.endsWith("format=bili.xml"))
    }

    @Test
    fun `export filename is safe and preserves compound extension`() {
        val name = buildDanmuExportFileName(
            animeTitle = "测试/动画",
            episodeTitle = "第 01 集:开始",
            target = DanmuExportTarget.Episode(99),
            format = DanmuDownloadFormat.ArtplayerJson
        )

        assertEquals("测试_动画 - 第 01 集_开始.artplayer.json", name)
    }

    @Test
    fun `json export is pretty printed without changing data`() {
        val normalized = normalizeDanmuExportPayload(
            bytes = "{\"comments\":[{\"m\":\"hello\"}]}".toByteArray(),
            format = DanmuDownloadFormat.Json
        ).toString(Charsets.UTF_8)

        assertTrue(normalized.endsWith("\n"))
        assertEquals("hello", JSONObject(normalized).getJSONArray("comments").getJSONObject(0).getString("m"))
    }
}
