package com.example.danmuapiapp.desktop.core

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreEnvInstalledProbeTest {
    @Test
    fun parsesInstalledStableCore() {
        val file = File(System.getenv("LOCALAPPDATA"), "DanmuApi/runtime/nodejs-project/danmu_api_stable/configs/envs.js")
        if (!file.isFile) return
        val definitions = CoreEnvCatalog.parseFile(file)
        assertTrue(definitions.size >= 60)
        assertTrue(definitions.first { it.key == "AI_MATCH_PROMPT" }.defaultValue.orEmpty().isNotBlank())
        assertTrue(definitions.first { it.key == "AI_API_KEY" }.sensitive)
        assertTrue(definitions.none { it.key.startsWith("DANMU_API_") })
    }
}
