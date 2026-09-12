package com.example.danmuapiapp.ui.screen.localdanmu

import com.example.danmuapiapp.domain.model.LocalDanmuParseConfidence
import com.example.danmuapiapp.domain.model.LocalDanmuType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDanmuBatchModelsTest {

    private fun item(
        id: Long,
        uri: String,
        episode: Int?,
        season: Int? = 1,
        title: String = "凡人修仙传",
        year: Int? = 2020
    ) = LocalDanmuBatchItem(
        id = id,
        sourceUri = uri,
        displayName = "凡人修仙传_E%02d.xml".format(episode ?: 0),
        sizeBytes = 1024L,
        formatHint = "xml",
        mimeType = "text/xml",
        title = title,
        year = year,
        type = LocalDanmuType.Tv,
        season = season,
        episode = episode,
        confidence = LocalDanmuParseConfidence.High
    )

    @Test
    fun `skips duplicated sources`() {
        val items = markBatchDuplicates(
            listOf(
                item(1, "file:///a/E01.xml", 1),
                item(2, "file:///a/E01.xml", 1)
            )
        )

        assertEquals(LocalDanmuBatchStatus.Skipped, items[1].status)
        assertFalse(items[1].selected)
        assertTrue(items[1].message.contains("重复选择"))
    }

    @Test
    fun `warns when two files map to the same episode`() {
        val items = markBatchDuplicates(
            listOf(
                item(1, "file:///a/E01.xml", 1),
                item(2, "file:///b/E01.xml", 1)
            )
        )

        assertEquals(LocalDanmuBatchStatus.Ready, items[1].status)
        assertTrue(items[1].message.contains("会覆盖"))
    }

    @Test
    fun `keeps different episodes independent`() {
        val items = markBatchDuplicates(
            listOf(
                item(1, "file:///a/E01.xml", 1),
                item(2, "file:///a/E02.xml", 2)
            )
        )

        assertTrue(items.none { it.message.isNotBlank() })
        assertTrue(items.all { it.status == LocalDanmuBatchStatus.Ready })
    }

    @Test
    fun `caps importable items and validates metadata`() {
        val valid = item(1, "file:///a/E01.xml", 1)
        val missingYear = item(2, "file:///a/E02.xml", 2, year = null)
        val skipped = item(3, "file:///a/E03.xml", 3)
            .copy(status = LocalDanmuBatchStatus.Skipped, selected = false)

        val state = LocalDanmuBatchState(items = listOf(valid, missingYear, skipped))

        assertTrue(valid.canImport)
        assertFalse(missingYear.canImport)
        assertFalse(skipped.canImport)
        assertEquals(1, state.pendingCount)
    }
}
