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

    @Test
    fun `自动匹配映射表应获得核心平台选项`() {
        val source = """
            export class Envs {
              static ALLOWED_PLATFORMS = ['qiyi', 'qq', 'youku'];
            }
            const envVarConfig = {
              'AUTO_MATCH_MAPPING_TABLE': {
                category: 'match',
                type: 'map',
                description: '自动匹配映射表'
              }
            };
        """.trimIndent()

        val definition = EnvVarConfigLoader.parseCatalogContent(source).single()

        assertEquals(listOf("qiyi", "qq", "youku"), definition.options)
    }
}
