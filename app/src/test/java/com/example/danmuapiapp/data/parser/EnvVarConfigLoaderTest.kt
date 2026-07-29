package com.example.danmuapiapp.data.parser

import com.example.danmuapiapp.domain.model.EnvType
import org.junit.Assert.assertEquals
import org.junit.Test

class EnvVarConfigLoaderTest {
    @Test
    fun `弹幕输出格式应展开核心导入的全部格式`() {
        val source = """
            import { danAnyFormats } from '../utils/dan-any.js';

            const envVarConfig = {
              'DANMU_OUTPUT_FORMAT': {
                category: 'danmu',
                type: 'select',
                options: ['json', 'xml', ...danAnyFormats],
                description: '弹幕输出格式'
              }
            };
        """.trimIndent()

        val definition = EnvVarConfigLoader.parseCatalogContent(source).single()

        assertEquals("DANMU_OUTPUT_FORMAT", definition.key)
        assertEquals(EnvType.SELECT, definition.type)
        assertEquals(
            listOf(
                "json",
                "xml",
                "artplayer.json",
                "baha.json",
                "bili.xml",
                "danuni.json",
                "danuni.binpb",
                "ddplay.json",
                "dplayer.json",
                "vod.json",
            ),
            definition.options
        )
    }
}
