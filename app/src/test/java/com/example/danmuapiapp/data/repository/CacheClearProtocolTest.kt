package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.CacheClearItem
import com.example.danmuapiapp.domain.model.CacheClearSupport
import org.junit.Assert.assertEquals
import org.junit.Test

class CacheClearProtocolTest {
    @Test
    fun `detects selective protocol only when all actions and parser markers exist`() {
        val actions = CacheClearItem.entries.joinToString("\n") { "${it.wireKey}: () => {}," }
        val source = """
            const clearActions = {
              $actions
            };
            const items = parsed.items;
            const effectiveItems = Array.isArray(items) ? items : [];
        """.trimIndent()

        val worker = """
            if (path === "/api/cache/clear" && method === "POST") {
              return handleClearCache(req);
            }
        """.trimIndent()

        assertEquals(CacheClearSupport.Selective, CacheClearProtocol.detectSupport(source, worker))
        assertEquals(
            CacheClearSupport.LegacyAllOnly,
            CacheClearProtocol.detectSupport("function clearCache() {}", worker)
        )
        assertEquals(
            CacheClearSupport.LegacyAllOnly,
            CacheClearProtocol.detectSupport(source, "return handleClearCache();")
        )
        assertEquals(CacheClearSupport.Unknown, CacheClearProtocol.detectSupport(null, worker))
        assertEquals(CacheClearSupport.Unknown, CacheClearProtocol.detectSupport(source, null))
    }

    @Test
    fun `request body uses stable wire order`() {
        val body = CacheClearProtocol.requestBody(
            setOf(CacheClearItem.LastSelectMap, CacheClearItem.SearchCache)
        )

        assertEquals("{\"items\":[\"searchCache\",\"lastSelectMap\"]}", body)
    }

    @Test
    fun `parses cleared item keys from response`() {
        val response = CacheClearProtocol.parseResponse(
            """{"success":true,"clearedItems":{"commentCache":0,"requestHistory":0}}"""
        )

        assertEquals(true, response.success)
        assertEquals(setOf("commentCache", "requestHistory"), response.clearedItems)
    }

    @Test
    fun `parses explicit application failure from successful http response`() {
        val response = CacheClearProtocol.parseResponse(
            """{"success":false,"message":"清理被核心拒绝"}"""
        )

        assertEquals(false, response.success)
        assertEquals("清理被核心拒绝", response.message)
        assertEquals(null, response.clearedItems)
    }

    @Test
    fun `successful response without cleared items remains unverified`() {
        val response = CacheClearProtocol.parseResponse("""{"success":true}""")

        assertEquals(true, response.success)
        assertEquals(null, response.clearedItems)
    }
}
