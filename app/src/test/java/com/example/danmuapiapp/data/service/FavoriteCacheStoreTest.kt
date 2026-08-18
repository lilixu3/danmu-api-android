package com.example.danmuapiapp.data.service

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteCacheStoreTest {

    @Test
    fun `核心双层字符串缓存可规范化为普通对象`() {
        val inner = """{"凡人修仙传":{"timestamp":100,"results":[]}}"""
        val encoded = JSONObject.quote(inner)

        val snapshot = FavoriteCacheStore.snapshotOf(encoded)

        assertEquals(1, snapshot.count)
        assertTrue(JSONObject(snapshot.content).has("凡人修仙传"))
    }

    @Test
    fun `迁移合并保留两侧收藏并选择更新时间较新的条目`() {
        val preferred = """
            {
              "共同收藏": {"timestamp": 100, "source": "preferred"},
              "普通模式": {"timestamp": 50}
            }
        """.trimIndent()
        val secondary = """
            {
              "共同收藏": {"lastRefreshAt": 200, "source": "secondary"},
              "Root 模式": {"timestamp": 60, "refreshSchedule": {"time": "03:00"}}
            }
        """.trimIndent()

        val merged = JSONObject(FavoriteCacheStore.mergeDocuments(preferred, secondary))

        assertEquals(3, merged.length())
        assertEquals("secondary", merged.getJSONObject("共同收藏").getString("source"))
        assertTrue(merged.has("普通模式"))
        assertTrue(merged.has("Root 模式"))
    }

    @Test
    fun `模式切换只复制当前模式快照避免旧模式恢复已删除收藏`() {
        val current = """
            {
              "当前收藏": {"timestamp": 200}
            }
        """.trimIndent()

        val snapshot = FavoriteCacheStore.authoritativeModeSnapshot(current)
        val copied = JSONObject(snapshot.content)

        assertEquals(1, copied.length())
        assertTrue(copied.has("当前收藏"))
        assertEquals(1, snapshot.count)
    }

    @Test
    fun `无效收藏条目会拒绝导入`() {
        val result = runCatching {
            FavoriteCacheStore.snapshotOf("""{"错误条目":"not-an-object"}""")
        }

        assertTrue(result.isFailure)
    }
}
