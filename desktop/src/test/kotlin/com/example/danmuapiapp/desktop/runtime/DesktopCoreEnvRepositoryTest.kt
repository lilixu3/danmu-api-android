package com.example.danmuapiapp.desktop.runtime

import com.example.danmuapiapp.desktop.core.CoreEnvType
import com.example.danmuapiapp.desktop.core.DesktopCoreVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DesktopCoreEnvRepositoryTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun readsEffectiveValuesAndWritesThroughSharedEnvFile() {
        val scriptDir = temp.newFolder("nodejs-project")
        val coreDir = File(scriptDir, "danmu_api_stable").apply { mkdirs() }
        File(coreDir, "worker.js").writeText("worker")
        File(coreDir, "configs").mkdirs()
        File(coreDir, "configs/envs.js").writeText(
            """
                const envVarConfig = {
                  TOKEN: { category: 'api', type: 'text', description: 'token' },
                  COUNT: { category: 'cache', type: 'number', min: 1, max: 5, description: 'count' },
                  ENABLED: { category: 'api', type: 'boolean', description: 'enabled' },
                  MODE: { category: 'system', type: 'select', options: ['fast', 'safe'], description: 'mode' },
                };
                this.get('TOKEN', 'default-token', 'string', true);
                this.get('COUNT', 3, 'number');
                this.get('ENABLED', false, 'boolean');
                this.get('MODE', 'fast', 'string');
            """.trimIndent(),
        )
        val envFile = File(scriptDir, "config/.env").apply {
            parentFile.mkdirs()
            writeText("# preserved\nTOKEN=hidden\nOTHER=keep\n", Charsets.UTF_8)
        }
        val repository = DesktopCoreEnvRepository(scriptDir, mapOf("COUNT" to "4"))

        val snapshot = repository.readSnapshot(DesktopCoreVariant.Stable)
        assertEquals("hidden", snapshot.values.getValue("TOKEN").configuredValue)
        assertEquals("hidden", snapshot.values.getValue("TOKEN").effectiveValue)
        assertTrue(snapshot.values.getValue("TOKEN").definition.sensitive)
        assertEquals("4", snapshot.values.getValue("COUNT").effectiveValue)
        assertEquals(CoreEnvType.Select, snapshot.values.getValue("MODE").definition.type)

        repository.updateValue(snapshot, "COUNT", "5")
        assertEquals("5", DesktopRuntimeEnv.readValue(envFile, "COUNT"))
        repository.deleteValue(snapshot, "TOKEN")
        assertEquals(null, DesktopRuntimeEnv.readValue(envFile, "TOKEN"))
        assertTrue(envFile.readText(Charsets.UTF_8).contains("OTHER=keep"))
    }

    @Test
    fun rejectsUnknownKeysAndInvalidTypedValues() {
        val scriptDir = temp.newFolder("nodejs-project")
        val coreDir = File(scriptDir, "danmu_api_stable").apply { mkdirs() }
        File(coreDir, "worker.js").writeText("worker")
        File(coreDir, "configs").mkdirs()
        File(coreDir, "configs/envs.js").writeText(
            "const envVarConfig = { COUNT: { category: 'cache', type: 'number', min: 1, max: 5 } }; this.get('COUNT', 3, 'number');",
        )
        val snapshot = DesktopCoreEnvRepository(scriptDir, emptyMap()).readSnapshot(DesktopCoreVariant.Stable)

        assertTrue(runCatching { DesktopCoreEnvRepository(scriptDir).updateValue(snapshot, "UNKNOWN", "x") }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { DesktopCoreEnvRepository(scriptDir).updateValue(snapshot, "COUNT", "9") }.exceptionOrNull() is IllegalArgumentException)
        assertFalse(snapshot.values.getValue("COUNT").isConfigured)
    }
}
