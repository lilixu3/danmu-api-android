package com.example.danmuapiapp.desktop.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreEnvCatalogTest {
    @Test
    fun parsesDeclaredTypesOptionsDefaultsAndSensitiveValues() {
        val content = """
            class Envs {
              static ALLOWED_SOURCES = ['douban', '360'];
              static ALLOWED_PLATFORMS = ['qiyi', 'bilibili1'];
              static MERGE_ALLOWED_SOURCES = ['bilibili', 'dandan'];
              static load() {
                const envVarConfig = {
                  'TOKEN': { category: 'api', type: 'text', description: '令牌' },
                  'MODE': { category: 'system', type: 'select', options: ['fast', 'safe'], description: '模式' },
                  'SOURCES': { category: 'source', type: 'multi-select', options: this.ALLOWED_SOURCES, description: '源' },
                  'COUNT': { category: 'cache', type: 'number', min: 1, max: 5, description: '数量' },
                  'ENABLED': { category: 'api', type: 'boolean', description: '开关' },
                  'RULES': { category: 'match', type: 'map', description: '规则' },
                };
                return {
                  token: this.get('TOKEN', 'secret', 'string', true),
                  mode: this.get('MODE', 'fast', 'string'),
                  count: this.get('COUNT', 3, 'number'),
                  enabled: this.get('ENABLED', false, 'boolean'),
                };
              }
            }
        """.trimIndent()

        val definitions = CoreEnvCatalog.parse(content)
        assertEquals(listOf("TOKEN", "MODE", "SOURCES", "COUNT", "ENABLED", "RULES"), definitions.map { it.key })
        assertEquals(CoreEnvType.Number, definitions.first { it.key == "COUNT" }.type)
        assertEquals(listOf("fast", "safe"), definitions.first { it.key == "MODE" }.options)
        assertEquals(listOf("douban", "360"), definitions.first { it.key == "SOURCES" }.options)
        assertEquals("3", definitions.first { it.key == "COUNT" }.defaultValue)
        assertTrue(definitions.first { it.key == "TOKEN" }.sensitive)
        assertFalse(definitions.first { it.key == "RULES" }.sensitive)
    }

    @Test
    fun rejectsMissingCatalogAndDesktopHostVariables() {
        val missing = runCatching { CoreEnvCatalog.parse("class Envs {}") }.exceptionOrNull()
        assertTrue(missing is IllegalArgumentException)

        val host = "const envVarConfig = { 'DANMU_API_PORT': { type: 'number' } }; this.get('DANMU_API_PORT', 9321, 'number');"
        val error = runCatching { CoreEnvCatalog.parse(host) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}
