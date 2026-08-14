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
        val keys = CacheClearProtocol.parseClearedItems(
            """{"success":true,"clearedItems":{"commentCache":0,"requestHistory":0}}"""
        )

        assertEquals(setOf("commentCache", "requestHistory"), keys)
    }
}
