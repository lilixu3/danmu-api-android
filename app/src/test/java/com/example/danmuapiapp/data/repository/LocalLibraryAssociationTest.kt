package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.DanmuDownloadRecord
import com.example.danmuapiapp.domain.model.LocalDanmuResource
import com.example.danmuapiapp.domain.model.LocalDanmuSnapshot
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class LocalLibraryAssociationTest {
    private fun record() = DanmuDownloadRecord(animeTitle = "电影", episodeTitle = "正片", episodeId = 1,
        episodeNo = 1, source = "test", format = "xml", status = "success", localImportedAt = 100, localResourceKey = "same-key")

    @Test fun `legacy imports are history not proof of current library existence`() {
        assertTrue(record().importedToLocal)
        assertFalse(record().importedInto("root"))
        assertFalse(record().importedInto("normal"))
        assertNotEquals(scopedLocalResourceKey("root", "same-key"), scopedLocalResourceKey("normal", "same-key"))
        assertNotEquals(scopedLocalResourceKey("", "same-key"), scopedLocalResourceKey("root", "same-key"))
    }

    @Test fun `reconciliation removes only the absent library and preserves import history`() {
        val normal = reconcileLocalLibraryRecord(record(), "normal", "same-key", false, "movie", 200)
        val both = reconcileLocalLibraryRecord(normal, "root", "same-key", false, "", 300)
        val removed = reconcileLocalLibraryRecord(both, "root", null, true, "", 400)
        assertTrue(removed.importedInto("normal"))
        assertFalse(removed.importedInto("root"))
        assertTrue(removed.importedToLocal)
        assertEquals("movie", removed.mediaType)
        assertEquals(both, reconcileLocalLibraryRecord(both, "root", null, false, "", 400))
        val restored = Json.decodeFromString<DanmuDownloadRecord>(Json.encodeToString(DanmuDownloadRecord.serializer(), both))
        assertEquals(both, restored)
    }

    @Test fun `corrected import metadata remains associated only while the real current resource exists`() {
        val record = record().copy(id = 42, fileName = "没有年份.xml", localImports = mapOf("root" to "corrected-key"))
        val resource = LocalDanmuResource("corrected-key", title = "订正后的标题", year = 2014, type = "movie", season = 1)
        val root = LocalDanmuSnapshot(listOf(resource), emptyList(), libraryId = "root")
        assertEquals(mapOf(42L to "corrected-key"), DownloadLocalImportPlanner.matches(listOf(record), root))
        assertTrue(DownloadLocalImportPlanner.matches(listOf(record), root.copy(libraryId = "normal")).isEmpty())
        assertTrue(DownloadLocalImportPlanner.matches(listOf(record), root.copy(resources = emptyList())).isEmpty())
    }
}
