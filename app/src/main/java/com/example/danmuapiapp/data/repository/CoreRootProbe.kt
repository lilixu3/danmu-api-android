package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.data.service.CoreVersionParser
import com.example.danmuapiapp.data.service.RootShell
import com.example.danmuapiapp.data.util.ShellUtils.shellQuote
import java.io.IOException

internal object CoreRootProbe {
    fun hasValidCore(rootDirPath: String): Boolean {
        val script = """
            DIR=${shellQuote(rootDirPath)}
            [ -f "${'$'}DIR/worker.js" ] && exit 0
            [ -f "${'$'}DIR/danmu_api/worker.js" ] && exit 0
            [ -f "${'$'}DIR/danmu-api/worker.js" ] && exit 0
            exit 1
        """.trimIndent()
        return RootShell.exec(script, timeoutMs = 3000L).ok
    }

    fun readVersion(rootDirPath: String): String? {
        val globalsCandidates = listOf(
            "$rootDirPath/configs/globals.js",
            "$rootDirPath/config/globals.js",
            "$rootDirPath/globals.js",
            "$rootDirPath/danmu_api/configs/globals.js",
            "$rootDirPath/danmu_api/config/globals.js",
            "$rootDirPath/danmu_api/globals.js",
            "$rootDirPath/danmu-api/configs/globals.js",
            "$rootDirPath/danmu-api/config/globals.js",
            "$rootDirPath/danmu-api/globals.js"
        )
        val packageCandidates = listOf(
            "$rootDirPath/package.json",
            "$rootDirPath/danmu_api/package.json",
            "$rootDirPath/danmu-api/package.json"
        )
        val candidateArgs = (globalsCandidates + packageCandidates)
            .joinToString(separator = " ") { shellQuote(it) }
        val script = """
            for FILE in \
            $candidateArgs
            do
              [ -f "${'$'}FILE" ] || continue
              sed -n '1,220p' "${'$'}FILE" 2>/dev/null || cat "${'$'}FILE" 2>/dev/null || true
              printf '\n'
            done
        """.trimIndent()
        val result = RootShell.exec(script, timeoutMs = 4500L)
        if (!result.ok) return null
        val text = result.stdout
        if (text.isBlank()) return null
        return CoreVersionParser.extractVersion(text)?.removePrefix("v")?.takeIf { it.isNotBlank() }
    }

    fun deleteDirectory(rootDirPath: String) {
        val script = """
            DIR=${shellQuote(rootDirPath)}
            rm -rf "${'$'}DIR"
            [ ! -e "${'$'}DIR" ]
        """.trimIndent()
        val result = RootShell.exec(script, timeoutMs = 8000L)
        if (!result.ok) {
            val detail = (result.stderr.ifBlank { result.stdout }).trim().ifBlank { "未知错误" }
            throw IOException("删除 Root 核心目录失败: $detail")
        }
    }
}
