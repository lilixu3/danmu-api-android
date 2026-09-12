package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.LocalDanmuResource
import com.example.danmuapiapp.domain.model.LocalDanmuSourceKind
import com.example.danmuapiapp.domain.model.LocalDanmuSourceRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalDanmuImportedIndexTest {

    @Test
    fun `decodes file uri to absolute path`() {
        assertEquals(
            "/storage/emulated/0/Download/弹幕下载/凡人修仙传_E01.xml",
            LocalDanmuImportedIndex.pathFromUri(
                "file:///storage/emulated/0/Download/%E5%BC%B9%E5%B9%95%E4%B8%8B%E8%BD%BD/%E5%87%A1%E4%BA%BA%E4%BF%AE%E4%BB%99%E4%BC%A0_E01.xml"
            )
        )
    }

    @Test
    fun `decodes saf document uri from the oem file manager`() {
        assertEquals(
            "/storage/emulated/0/Download/弹幕下载/凡人修仙传(2020)【动漫】from bilibili/凡人修仙传_E03.xml",
            LocalDanmuImportedIndex.pathFromUri(
                "content://com.android.fileexplorer.documents/document/primary%3A%2Fstorage%2Femulated%2F0%2FDownload" +
                    "%2F%E5%BC%B9%E5%B9%95%E4%B8%8B%E8%BD%BD%2F%E5%87%A1%E4%BA%BA%E4%BF%AE%E4%BB%99%E4%BC%A0(2020)" +
                    "%E3%80%90%E5%8A%A8%E6%BC%AB%E3%80%91from%20bilibili%2F%E5%87%A1%E4%BA%BA%E4%BF%AE%E4%BB%99%E4%BC%A0_E03.xml"
            )
        )
    }

    @Test
    fun `decodes externalstorage tree uri with relative document id`() {
        assertEquals(
            "/storage/emulated/0/Download/弹幕下载",
            LocalDanmuImportedIndex.pathFromUri(
                "content://com.android.externalstorage.documents/tree/primary%3ADownload%2F%E5%BC%B9%E5%B9%95%E4%B8%8B%E8%BD%BD"
            )
        )
    }

    @Test
    fun `matches by path first and then by name plus size`() {
        val resource = LocalDanmuResource(
            resourceKey = "凡人修仙传|2020|tv|3",
            title = "凡人修仙传",
            year = 2020,
            type = "tv",
            season = 1,
            episode = 3,
            filename = "凡人修仙传_E03.xml",
            sizeBytes = 2003546
        )
        val source = LocalDanmuSourceRef(
            resourceKey = resource.resourceKey,
            kind = LocalDanmuSourceKind.File,
            uri = "file:///storage/emulated/0/Download/弹幕下载/凡人修仙传_E03.xml",
            displayName = resource.filename,
            sizeBytes = resource.sizeBytes
        )

        val index = LocalDanmuImportedIndex.build(listOf(resource), listOf(source))

        assertEquals(
            "已导入 · 第1季 第3集",
            LocalDanmuImportedIndex.lookup(
                index = index,
                path = "/storage/emulated/0/Download/弹幕下载/凡人修仙传_E03.xml",
                name = "凡人修仙传_E03.xml",
                sizeBytes = 2003546
            )
        )
        // 路径不同但文件名+大小一致（例如换了一个目录）也能命中。
        assertEquals(
            "已导入 · 第1季 第3集",
            LocalDanmuImportedIndex.lookup(
                index = index,
                path = "/storage/emulated/0/Download/other/凡人修仙传_E03.xml",
                name = "凡人修仙传_E03.xml",
                sizeBytes = 2003546
            )
        )
        assertNull(
            LocalDanmuImportedIndex.lookup(
                index = index,
                path = "/storage/emulated/0/Download/other/凡人修仙传_E04.xml",
                name = "凡人修仙传_E04.xml",
                sizeBytes = 2003546
            )
        )
    }

    @Test
    fun `ignores sources that no longer exist in the current core`() {
        val current = LocalDanmuResource(
            resourceKey = "凡人修仙传|2020|tv|1",
            title = "凡人修仙传",
            year = 2020,
            type = "tv",
            season = 1,
            episode = 1,
            filename = "凡人修仙传_E01.xml",
            sizeBytes = 1000L
        )
        // 另一个工作目录里导入过的资源：核心当前列表里没有它，
        // 所以即使来源表还留着记录，也不应该标记为「已导入」。
        val staleSource = LocalDanmuSourceRef(
            resourceKey = "斗破苍穹|2022|tv|3",
            kind = LocalDanmuSourceKind.File,
            uri = "file:///storage/emulated/0/Download/弹幕下载/斗破苍穹_E03.xml",
            displayName = "斗破苍穹_E03.xml",
            sizeBytes = 2000L
        )

        val index = LocalDanmuImportedIndex.build(listOf(current), listOf(staleSource))

        assertNull(
            LocalDanmuImportedIndex.lookup(
                index = index,
                path = "/storage/emulated/0/Download/弹幕下载/斗破苍穹_E03.xml",
                name = "斗破苍穹_E03.xml",
                sizeBytes = 2000L
            )
        )
        // 当前核心里真实存在的资源，即使换了目录也按「文件名+大小」命中。
        assertEquals(
            "已导入 · 第1季 第1集",
            LocalDanmuImportedIndex.lookup(
                index = index,
                path = "/storage/emulated/0/Download/弹幕下载/凡人修仙传_E01.xml",
                name = "凡人修仙传_E01.xml",
                sizeBytes = 1000L
            )
        )
    }
}
