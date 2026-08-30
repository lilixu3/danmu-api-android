package com.example.danmuapiapp.desktop.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DesktopRuntimeEnvTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun writesAdminTokenAndPreservesOtherValuesAndComments() {
        val scriptDir = temp.newFolder("nodejs-project")
        val configDir = File(scriptDir, "config").apply { mkdirs() }
        val envFile = File(configDir, ".env")
        envFile.writeText(
            "# keep this comment\n" +
                "TOKEN=87654321\n" +
                "OTHER=value\n" +
                "# ADMIN_TOKEN=example\n",
            Charsets.UTF_8,
        )

        assertTrue(
            DesktopRuntimeEnv.applyAdminToken(
                scriptDir,
                "admin token/with spaces#and\"quotes",
            ),
        )

        assertEquals(
            "admin token/with spaces#and\"quotes",
            DesktopRuntimeEnv.readValue(envFile, "ADMIN_TOKEN"),
        )
        val output = envFile.readText(Charsets.UTF_8)
        assertTrue(output.contains("# keep this comment"))
        assertTrue(output.contains("TOKEN=87654321"))
        assertTrue(output.contains("OTHER=value"))
        assertTrue(output.contains("# ADMIN_TOKEN=example"))
    }

    @Test
    fun replacingAdminTokenRemovesItWhenValueIsBlank() {
        val scriptDir = temp.newFolder("nodejs-project")
        val envFile = File(scriptDir, "config/.env").apply {
            parentFile.mkdirs()
            writeText("ADMIN_TOKEN=old\nOTHER=kept\n", Charsets.UTF_8)
        }

        DesktopRuntimeEnv.applyAdminToken(scriptDir, "new-value")
        assertEquals("new-value", DesktopRuntimeEnv.readValue(envFile, "ADMIN_TOKEN"))

        DesktopRuntimeEnv.applyAdminToken(scriptDir, "")
        assertEquals(null, DesktopRuntimeEnv.readValue(envFile, "ADMIN_TOKEN"))
        assertEquals("OTHER=kept\n", envFile.readText(Charsets.UTF_8))
    }

    @Test
    fun missingRuntimeDirectoryIsReportedAsNotApplied() {
        val scriptDir = File(temp.root, "not-created")

        assertFalse(DesktopRuntimeEnv.applyAdminToken(scriptDir, "token"))
        assertFalse(File(scriptDir, "config/.env").exists())
    }

    @Test
    fun invalidEnvironmentKeyFailsExplicitly() {
        val envFile = temp.newFile(".env")

        val error = runCatching {
            DesktopRuntimeEnv.updateValue(envFile, "not-valid", "value")
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }
}
