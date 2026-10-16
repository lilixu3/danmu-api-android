package com.example.danmuapiapp.data.service

import org.junit.Assert.*
import org.junit.Test

class SourceProbeCatalogTest {
    @Test fun `default scope is enabled sources in configured order`() {
        val catalog = ProxySourceCatalog(listOf("douban", "aiyifan", "local"), linkedSetOf("local", "aiyifan"))
        assertEquals(listOf("local", "aiyifan"), catalog.visibleSources())
    }

    @Test fun `all scope includes disabled and newly configured sources only once`() {
        val catalog = ProxySourceCatalog(listOf("douban", "aiyifan", "douban"), linkedSetOf("aiyifan", "newsource"))
        assertEquals(listOf("douban", "aiyifan", "newsource"), catalog.visibleSources(true))
        assertEquals(listOf("aiyifan", "newsource"), catalog.visibleSources(false))
        assertEquals(linkedSetOf("aiyifan", "newsource"), catalog.enabled)
    }

    @Test fun `empty enabled scope does not silently display or probe all sources`() {
        val catalog = ProxySourceCatalog(listOf("douban"), emptySet())
        assertTrue(catalog.visibleSources().isEmpty())
        assertEquals(listOf("douban"), catalog.visibleSources(true))
    }
}
