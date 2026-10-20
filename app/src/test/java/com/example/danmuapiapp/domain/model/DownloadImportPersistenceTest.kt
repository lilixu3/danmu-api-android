package com.example.danmuapiapp.domain.model

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class DownloadImportPersistenceTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    @Test fun `old history without import fields still loads`() {
        val record = json.decodeFromString<DanmuDownloadRecord>("""{"id":1,"animeTitle":"测试剧","episodeTitle":"第1集","episodeId":88,"episodeNo":1,"source":"tencent","format":"xml","status":"success"}""")
        assertFalse(record.importedToLocal)
        assertEquals("", record.localResourceKey)
    }
    @Test fun `local import marker survives persisted record round trip`() {
        val record = DanmuDownloadRecord(animeTitle = "测试剧", episodeTitle = "第1集", episodeId = 88,
            episodeNo = 1, source = "tencent", format = "xml", status = "success", localImportedAt = 12345,
            localResourceKey = "测试剧|2023|tv|1")
        val restored = json.decodeFromString<DanmuDownloadRecord>(json.encodeToString(DanmuDownloadRecord.serializer(), record))
        assertTrue(restored.importedToLocal)
        assertEquals(record.localResourceKey, restored.localResourceKey)
    }
    @Test fun `successful API downloads can auto import positive and negative episode IDs`() {
        val record = DanmuDownloadRecord(animeTitle = "测试剧", episodeTitle = "第1集", episodeId = 88,
            episodeNo = 1, source = "tencent", format = "xml", status = "success")
        assertTrue(record.canAutoImportToLocal)
        assertTrue(record.copy(episodeId = -88).canAutoImportToLocal)
        assertFalse(record.copy(episodeId = 0, source = "目录同步").canAutoImportToLocal)
    }
    @Test fun `unsuccessful downloads never auto import regardless of episode ID sign`() {
        val record = DanmuDownloadRecord(animeTitle = "测试剧", episodeTitle = "第1集", episodeId = 88,
            episodeNo = 1, source = "tencent", format = "xml", status = "success")
        for (episodeId in listOf(88L, -88L, 0L)) {
            for (status in listOf("failed", "skipped", "unknown")) {
                assertFalse(record.copy(episodeId = episodeId, status = status).canAutoImportToLocal)
            }
        }
    }
    @Test fun `old settings keep safe defaults and new options persist`() {
        val defaults = json.decodeFromString<DanmuDownloadSettings>("{}")
        assertFalse(defaults.autoImportToLocal)
        assertFalse(defaults.deleteSourceAfterImport)
        assertEquals("match", defaults.matchMode)
        val updated = defaults.copy(autoImportToLocal = true, deleteSourceAfterImport = true, matchMode = "search", saveDirPath = "/storage/emulated/0/Download/弹幕")
        assertEquals(updated, json.decodeFromString<DanmuDownloadSettings>(json.encodeToString(DanmuDownloadSettings.serializer(), updated)))
    }
    @Test fun `top and bottom modes follow core danmu protocol`() {
        assertEquals(DanmuPreviewFilter.Top, DanmuPreviewItem(1, mode = "5").previewFilter())
        assertEquals(DanmuPreviewFilter.Bottom, DanmuPreviewItem(1, mode = "4").previewFilter())
    }
}
