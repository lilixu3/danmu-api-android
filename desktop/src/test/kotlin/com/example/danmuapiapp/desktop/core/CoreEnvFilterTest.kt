package com.example.danmuapiapp.desktop.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreEnvFilterTest {
    private val snapshot = CoreEnvSnapshot(
        variant = DesktopCoreVariant.Stable,
        scriptDir = File("script"),
        coreDir = File("core"),
        envsJsFile = File("envs.js"),
        envFile = File(".env"),
        rawContent = "",
        definitions = listOf(
            CoreEnvDefinition("TOKEN", "api", CoreEnvType.Text, "访问令牌"),
            CoreEnvDefinition("COUNT", "cache", CoreEnvType.Number, "缓存数量", min = 1, max = 5, defaultValue = "3"),
            CoreEnvDefinition("MODE", "other", CoreEnvType.Select, "工作模式", options = listOf("fast", "safe")),
            CoreEnvDefinition("EXTRA", "custom", CoreEnvType.Boolean, "自定义开关"),
        ),
        values = mapOf(
            "TOKEN" to value("TOKEN", configured = true),
            "COUNT" to value("COUNT", configured = false),
            "MODE" to value("MODE", configured = true),
            "EXTRA" to value("EXTRA", configured = false),
        ),
    )

    @Test
    fun filtersByQueryTypeCategoryAndConfiguredState() {
        assertEquals(listOf("TOKEN"), snapshot.filteredDefinitions(CoreEnvFilter(query = "令牌")).map { it.key })
        assertEquals(listOf("COUNT"), snapshot.filteredDefinitions(CoreEnvFilter(type = CoreEnvType.Number)).map { it.key })
        assertEquals(listOf("MODE"), snapshot.filteredDefinitions(CoreEnvFilter(category = "other")).map { it.key })
        assertEquals(listOf("TOKEN", "MODE"), snapshot.filteredDefinitions(CoreEnvFilter(configured = CoreEnvConfiguredFilter.Configured)).map { it.key })
        assertEquals(listOf("COUNT", "EXTRA"), snapshot.filteredDefinitions(CoreEnvFilter(configured = CoreEnvConfiguredFilter.Default)).map { it.key })
    }

    @Test
    fun groupsKnownCategoriesBeforeUnknownWithoutDroppingDefinitions() {
        val groups = snapshot.groupedDefinitions(CoreEnvFilter())
        assertEquals(listOf("api", "cache", "custom", "other"), groups.map { it.category })
        assertEquals(4, groups.sumOf { it.definitions.size })
        assertTrue(snapshot.categoryOptions().containsAll(listOf("api", "cache", "other", "custom")))
    }

    private fun value(key: String, configured: Boolean): CoreEnvValue {
        val definition = snapshotDefinition(key)
        return CoreEnvValue(
            definition = definition,
            configuredValue = if (configured) "configured" else null,
            effectiveValue = if (configured) "configured" else definition.defaultValue,
            isConfigured = configured,
            source = if (configured) CoreEnvValueSource.DotEnv else CoreEnvValueSource.CoreDefault,
        )
    }

    private fun snapshotDefinition(key: String): CoreEnvDefinition = when (key) {
        "TOKEN" -> CoreEnvDefinition("TOKEN", "api", CoreEnvType.Text, "访问令牌")
        "COUNT" -> CoreEnvDefinition("COUNT", "cache", CoreEnvType.Number, "缓存数量", min = 1, max = 5, defaultValue = "3")
        "MODE" -> CoreEnvDefinition("MODE", "other", CoreEnvType.Select, "工作模式", options = listOf("fast", "safe"))
        "EXTRA" -> CoreEnvDefinition("EXTRA", "custom", CoreEnvType.Boolean, "自定义开关")
        else -> error("unknown fixture key")
    }
}
