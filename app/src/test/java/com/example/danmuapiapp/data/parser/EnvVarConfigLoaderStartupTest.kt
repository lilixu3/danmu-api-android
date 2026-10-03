package com.example.danmuapiapp.data.parser

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EnvVarConfigLoaderStartupTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun missingProjectMetadataDoesNotCreateOrExtractAProject() {
        val project = File(temporary.root, "not-prepared")
        assertNull(EnvVarConfigLoader.readLocalCoreSource(project, "stable"))
        assertFalse(project.exists())
    }

    @Test fun flatAndLegacyCoreDefaultsAreReadWithoutMigratingOrOverwritingUserFiles() {
        for ((index, layout) in listOf("configs/envs.js", "danmu_api/configs/envs.js", "danmu-api/configs/envs.js").withIndex()) {
            val project = temporary.newFolder("project-$index")
            val env = File(project, "config/.env").apply { parentFile.mkdirs(); writeText("TOKEN=my-user-token\n") }
            val source = File(project, "danmu_api_custom/$layout").apply {
                parentFile.mkdirs(); writeText("token: this.get('TOKEN', 'custom-default-token')")
            }
            val worker = File(source.parentFile.parentFile, "worker.js").apply { writeText("legacy worker") }
            val before = project.walkTopDown().map { it.relativeTo(project).path }.sorted().toList()

            assertEquals(source.readText(), EnvVarConfigLoader.readLocalCoreSource(project, "custom"))
            assertEquals("TOKEN=my-user-token\n", env.readText())
            assertEquals("legacy worker", worker.readText())
            assertEquals(before, project.walkTopDown().map { it.relativeTo(project).path }.sorted().toList())
            assertFalse(File(project, "main.js").exists())
        }
    }
}
