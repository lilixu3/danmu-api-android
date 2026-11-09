package com.example.danmuapiapp.data.service

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeEnvDefaultsTest {

    @Test
    fun `log defaults should be generated only for missing keys`() {
        val defaults = NodeProjectManager.buildRuntimeEnvLogDefaults(
            mapOf(
                "DANMU_API_LOG_TO_FILE" to "1",
                "APP_LOG_TO_FILE" to "1"
            )
        )

        assertEquals("1048576", defaults["DANMU_API_LOG_MAX_BYTES"])
        assertEquals("1048576", defaults["APP_LOG_MAX_BYTES"])
        assertEquals(null, defaults["DANMU_API_LOG_TO_FILE"])
        assertEquals(null, defaults["APP_LOG_TO_FILE"])
    }

    @Test
    fun `missing defaults are appended without changing user assignments or blank values`() {
        val existing = """
            # 用户配置
            TOKEN=personal-token
            EMPTY_VALUE=
            CUSTOM_ONLY=keep-me
        """.trimIndent() + "\n"
        val template = """
            # 默认 Token
            TOKEN=87654321

            # 默认来源顺序
            SOURCE_ORDER=a,b

            # 空值示例
            EMPTY_VALUE=example-default
        """.trimIndent() + "\n"

        val merged = RuntimeEnvDefaults.merge(existing, template)

        assertTrue(merged.changed)
        assertEquals(listOf("SOURCE_ORDER"), merged.addedKeys)
        assertTrue(merged.content.startsWith("# 用户配置\nTOKEN=personal-token\nEMPTY_VALUE=\nCUSTOM_ONLY=keep-me"))
        assertTrue(merged.content.contains("# 默认来源顺序\nSOURCE_ORDER=a,b"))
        assertFalse(merged.content.contains("TOKEN=87654321"))
        assertFalse(merged.content.contains("EMPTY_VALUE=example-default"))
    }

    @Test
    fun `the same template is merged only once`() {
        val template = "TOKEN=87654321\nSOURCE_ORDER=a,b\n"
        val first = RuntimeEnvDefaults.merge("TOKEN=custom\n", template)
        val second = RuntimeEnvDefaults.merge(first.content, template)

        assertTrue(first.changed)
        assertFalse(second.changed)
        assertEquals(first.content, second.content)
        assertEquals(1, Regex("(?m)^# DanmuApiApp env defaults merged:").findAll(first.content).count())
    }

    @Test
    fun `an explicitly commented key is treated as user-managed`() {
        val merged = RuntimeEnvDefaults.merge(
            existing = "# SOURCE_ORDER=user-disabled\nCUSTOM=1\n",
            template = "# 默认来源顺序\nSOURCE_ORDER=a,b\n"
        )

        assertEquals(emptyList<String>(), merged.addedKeys)
        assertEquals(0, Regex("(?m)^SOURCE_ORDER=").findAll(merged.content).count())
        assertTrue(merged.content.contains("# SOURCE_ORDER=user-disabled"))
    }

    @Test
    fun `a newer template adds only keys missing from the previous merge`() {
        val existing = "TOKEN=custom\nCUSTOM_ONLY=keep\n"
        val first = RuntimeEnvDefaults.merge(existing, "SOURCE_ORDER=a,b\n")
        val second = RuntimeEnvDefaults.merge(
            first.content,
            "SOURCE_ORDER=changed-default\nNEW_OPTION=42\n"
        )

        assertEquals(listOf("NEW_OPTION"), second.addedKeys)
        assertTrue(second.content.contains("TOKEN=custom"))
        assertTrue(second.content.contains("CUSTOM_ONLY=keep"))
        assertTrue(second.content.contains("SOURCE_ORDER=a,b"))
        assertTrue(second.content.contains("NEW_OPTION=42"))
        assertFalse(second.content.contains("SOURCE_ORDER=changed-default"))
    }

    @Test
    fun `CRLF files keep CRLF line endings after a merge`() {
        val merged = RuntimeEnvDefaults.merge(
            existing = "\uFEFFTOKEN=custom\r\nCUSTOM=1\r\n",
            template = "TOKEN=87654321\nNEW_OPTION=42\n"
        )

        assertTrue(merged.changed)
        assertEquals(listOf("NEW_OPTION"), merged.addedKeys)
        assertFalse(merged.content.replace("\r\n", "").contains("\n"))
    }

    @Test
    fun `the bundled example comes from the core template`() {
        val example = sequenceOf(
            File("app/src/main/assets/nodejs-project/config/env.example"),
            File("../app/src/main/assets/nodejs-project/config/env.example")
        ).firstOrNull(File::isFile) ?: error("missing bundled core .env.example")
        val template = example.readText(Charsets.UTF_8)
        assertTrue(template.contains("\nSOURCE_ORDER=douban,360,renren,hanjutv\n"))
        assertTrue(template.contains("\nTENCENT_CONCURRENCY=100\n"))
        assertTrue(template.contains("\nIQIYI_CONCURRENCY=100\n"))
        assertTrue(template.contains("\nVOD_SERVERS=\n"))
    }
}
