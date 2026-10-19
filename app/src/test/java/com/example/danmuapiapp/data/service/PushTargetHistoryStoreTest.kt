package com.example.danmuapiapp.data.service

import org.junit.Assert.assertEquals
import org.junit.Test

class PushTargetHistoryStoreTest {

    @Test
    fun `最近目标按最近使用排序并去重`() {
        val first = PushTargetHistoryStore.mergeRecent(
            emptyList(),
            " http://192.168.1.8:9978/action?do=refresh "
        )
        val second = PushTargetHistoryStore.mergeRecent(
            first,
            "http://192.168.1.9:9978/action?do=refresh"
        )
        val repeated = PushTargetHistoryStore.mergeRecent(
            second,
            " http://192.168.1.8:9978/action?do=refresh "
        )

        assertEquals(
            listOf(
                "http://192.168.1.8:9978/action?do=refresh",
                "http://192.168.1.9:9978/action?do=refresh",
            ),
            repeated
        )
    }

    @Test
    fun `最近目标最多保留三条`() {
        val values = (1..5).map { "http://192.168.1.$it:9978/action?do=refresh" }
        val result = values.fold(emptyList<String>()) { recent, target ->
            PushTargetHistoryStore.mergeRecent(recent, target)
        }

        assertEquals(values.takeLast(PushTargetHistoryStore.MAX_ENTRIES).reversed(), result)
    }

    @Test
    fun `空地址不会挤掉已有历史`() {
        val existing = listOf("http://192.168.1.8:9978/action?do=refresh")

        assertEquals(existing, PushTargetHistoryStore.mergeRecent(existing, "   "))
    }

    @Test
    fun `相同主机不同大小写只占一条但 HTTP HTTPS 分开记录`() {
        val http = "http://tv.example:9978/action?path="
        val https = "https://tv.example:9978/action?path="
        val latest = "http://TV.EXAMPLE:9978/action?path="
        assertEquals(listOf(latest, https), PushTargetHistoryStore.mergeRecent(listOf(http, https), latest))
    }

    @Test
    fun `损坏地址不会占用历史名额`() {
        val valid = "http://192.168.1.8:9978/action?path="
        assertEquals(listOf(valid), PushTargetHistoryStore.normalizeRecent(listOf("", "http://", "bad address", valid)))
    }
}
