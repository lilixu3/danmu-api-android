package com.example.danmuapiapp.data.repository

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RefactorWiringContractTest {
    @Test
    fun `both Core state mutation callbacks retain atomic updates`() {
        val source = readSource("data/repository/CoreRepositoryImpl.kt")
        listOf("updateCoreInfoList", "writeCoreInfoList").forEach { callback ->
            val pattern = Regex("""$callback\s*=\s*\{\s*transform\s*->\s*_coreInfoList\.update\(transform\)\s*}""")
            assertTrue("$callback must preserve atomic StateFlow updates", pattern.containsMatchIn(source))
        }
    }

    @Test
    fun `Root synchronization and permissions include the manifest itself`() {
        val source = readSource("data/service/RootRuntimeSync.kt")
        val pattern = Regex("""managedPaths\s*=\s*AppRuntimeAssets\.names\(context\)""")
        assertEquals(2, pattern.findAll(source).count())
    }

    private fun readSource(relativePath: String): String {
        val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
        return File(root, "app/src/main/java/com/example/danmuapiapp/$relativePath").readText()
    }
}
