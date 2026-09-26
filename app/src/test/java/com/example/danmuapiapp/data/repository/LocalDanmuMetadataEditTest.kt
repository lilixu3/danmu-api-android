package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.LocalDanmuEditScope
import com.example.danmuapiapp.domain.model.LocalDanmuMetadataPatch
import com.example.danmuapiapp.domain.model.LocalDanmuResource
import com.example.danmuapiapp.domain.model.LocalDanmuType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDanmuMetadataEditTest {

    private fun resource(
        resourceKey: String,
        videoId: String = "",
        title: String = "逐玉",
        year: Int = 2026,
        type: String = "tv",
        season: Int = 1,
        episode: Int? = 5,
        filename: String = "逐玉.第05集.xml"
    ) = LocalDanmuResource(
        resourceKey = resourceKey,
        videoId = videoId,
        title = title,
        year = year,
        type = type,
        season = season,
        episode = episode,
        filename = filename
    )

    @Test
    fun `resource scope 只带集数与文件名`() {
        val body = JSONObject(
            buildLocalDanmuPatchBody(
                LocalDanmuMetadataPatch(
                    scope = LocalDanmuEditScope.Resource,
                    title = "不该出现",
                    year = 2026,
                    type = LocalDanmuType.Tv,
                    season = 2,
                    episode = 7,
                    filename = "逐玉.第07集.xml"
                )
            )
        )
        assertEquals("resource", body.getString("scope"))
        assertEquals(7, body.getInt("episode"))
        assertEquals("逐玉.第07集.xml", body.getString("filename"))
        assertFalse(body.has("title"))
        assertFalse(body.has("year"))
        assertFalse(body.has("type"))
        assertFalse(body.has("season"))
    }

    @Test
    fun `resource scope 电影用显式 null 集数`() {
        val body = JSONObject(
            buildLocalDanmuPatchBody(
                LocalDanmuMetadataPatch(
                    scope = LocalDanmuEditScope.Resource,
                    episode = null,
                    filename = "movie.xml"
                )
            )
        )
        // 核心用 hasOwnProperty('episode') 判断是否修改，所以 null 必须真实出现在 body 里。
        assertTrue(body.has("episode"))
        assertTrue(body.isNull("episode"))
    }

    @Test
    fun `group scope 只带整组字段`() {
        val body = JSONObject(
            buildLocalDanmuPatchBody(
                LocalDanmuMetadataPatch(
                    scope = LocalDanmuEditScope.Group,
                    title = "凡人修仙传",
                    year = 2020,
                    type = LocalDanmuType.Tv,
                    season = 2,
                    episode = 9,
                    filename = "不该出现.xml"
                )
            )
        )
        assertEquals("group", body.getString("scope"))
        assertEquals("凡人修仙传", body.getString("title"))
        assertEquals(2020, body.getInt("year"))
        assertEquals("tv", body.getString("type"))
        assertEquals(2, body.getInt("season"))
        assertFalse(body.has("episode"))
        assertFalse(body.has("filename"))
    }

    @Test
    fun `resource scope 校验集数与文件名`() {
        assertNull(
            validateLocalDanmuEdit(
                scope = LocalDanmuEditScope.Resource,
                title = "逐玉",
                year = 2026,
                type = LocalDanmuType.Tv,
                season = 1,
                episode = 5,
                filename = "逐玉.第05集.xml",
                currentYear = 2026
            )
        )
        assertEquals(
            "电视剧集数必须是大于 0 的整数",
            validateLocalDanmuEdit(
                scope = LocalDanmuEditScope.Resource,
                title = "逐玉",
                year = 2026,
                type = LocalDanmuType.Tv,
                season = 1,
                episode = null,
                filename = "逐玉.xml",
                currentYear = 2026
            )
        )
        assertEquals(
            "文件名不能为空",
            validateLocalDanmuEdit(
                scope = LocalDanmuEditScope.Resource,
                title = "逐玉",
                year = 2026,
                type = LocalDanmuType.Movie,
                season = 1,
                episode = null,
                filename = "   ",
                currentYear = 2026
            )
        )
    }

    @Test
    fun `group scope 校验标题年份与季数`() {
        assertEquals(
            "标题为必填项",
            validateLocalDanmuEdit(
                scope = LocalDanmuEditScope.Group,
                title = " ",
                year = 2026,
                type = LocalDanmuType.Tv,
                season = 1,
                episode = null,
                filename = "x.xml",
                currentYear = 2026
            )
        )
        assertEquals(
            "年份必须在 1900–2026 年之间",
            validateLocalDanmuEdit(
                scope = LocalDanmuEditScope.Group,
                title = "逐玉",
                year = 1899,
                type = LocalDanmuType.Tv,
                season = 1,
                episode = null,
                filename = "x.xml",
                currentYear = 2026
            )
        )
        assertEquals(
            "季数必须是大于 0 的整数",
            validateLocalDanmuEdit(
                scope = LocalDanmuEditScope.Group,
                title = "逐玉",
                year = 2026,
                type = LocalDanmuType.Tv,
                season = null,
                episode = null,
                filename = "x.xml",
                currentYear = 2026
            )
        )
    }

    @Test
    fun `按 videoId 匹配编辑前后的资源`() {
        val before = listOf(
            resource("逐玉|2026|tv|5", videoId = "id-5"),
            resource("逐玉|2026|tv|6", videoId = "id-6", episode = 6)
        )
        val after = listOf(
            resource("逐玉(剧版)|2026|tv|5", videoId = "id-5"),
            resource("逐玉(剧版)|2026|tv|6", videoId = "id-6", episode = 6)
        )
        assertEquals(
            mapOf(
                "逐玉|2026|tv|5" to "逐玉(剧版)|2026|tv|5",
                "逐玉|2026|tv|6" to "逐玉(剧版)|2026|tv|6"
            ),
            matchMigratedResourceKeys(before, after)
        )
    }

    @Test
    fun `key 没变化时不产生迁移`() {
        val before = listOf(resource("逐玉|2026|tv|5", videoId = "id-5"))
        assertEquals(emptyMap<String, String>(), matchMigratedResourceKeys(before, before))
    }

    @Test
    fun `没有 videoId 时按文件名与集数兜底`() {
        val before = listOf(
            resource("逐玉|2026|tv|5", videoId = "", filename = "a.xml"),
            resource("逐玉|2026|tv|6", videoId = "", filename = "b.xml", episode = 6)
        )
        val after = listOf(
            resource("逐玉|2026|tv|7", videoId = "", filename = "b.xml", episode = 6),
            resource("逐玉|2026|tv|8", videoId = "", filename = "a.xml", episode = 5)
        )
        assertEquals(
            mapOf(
                "逐玉|2026|tv|5" to "逐玉|2026|tv|8",
                "逐玉|2026|tv|6" to "逐玉|2026|tv|7"
            ),
            matchMigratedResourceKeys(before, after)
        )
    }
}
