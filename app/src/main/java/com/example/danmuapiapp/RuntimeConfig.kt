package com.example.danmuapiapp

import android.content.Context
import java.io.File

/**
 * Runtime config helper.
 *
 * The embedded Node server reads env vars from:
 *  - filesDir/nodejs-project/config/.env
 *  - filesDir/nodejs-project/config/config.yaml
 *
 * We keep a tiny parser here so both MainActivity and QS Tile can be consistent.
 */
object RuntimeConfig {

    private const val DEFAULT_MAIN_PORT = 9321

    /**
     * Read the port used by the embedded server.
     *
     * android-server.mjs uses: Number(process.env.DANMU_API_PORT || 9321)
     */
    fun getMainPort(context: Context): Int {
        val raw = readValue(context, "DANMU_API_PORT")
        return raw?.toIntOrNull() ?: DEFAULT_MAIN_PORT
    }

    /**
     * Read a value from runtime config files.
     *
     * - Prefer .env
     * - Fallback to config.yaml
     */
    fun readValue(context: Context, keyName: String): String? {
        // Ensure the node project has been extracted so runtime config files exist.
        runCatching { AssetCopier.ensureNodeProjectExtracted(context) }

        val configDir = File(context.filesDir, "nodejs-project/config")
        val envFile = File(configDir, ".env")
        val yamlFile = File(configDir, "config.yaml")

        // 1) .env
        runCatching {
            if (envFile.exists()) {
                val lines = envFile.readLines(Charsets.UTF_8)
                for (rawLine in lines) {
                    val line = rawLine.trim()
                    if (line.isEmpty() || line.startsWith('#')) continue
                    val idx = line.indexOf('=')
                    if (idx <= 0) continue
                    val key = line.substring(0, idx).trim()
                    if (key != keyName) continue
                    var value = line.substring(idx + 1).trim()
                    // strip quotes if any
                    if ((value.startsWith('"') && value.endsWith('"')) ||
                        (value.startsWith('\'') && value.endsWith('\''))) {
                        if (value.length >= 2) value = value.substring(1, value.length - 1)
                    }
                    return value
                }
            }
        }

        // 2) config.yaml (minimal parser; handles KEY: "..." / KEY: ...)
        runCatching {
            if (yamlFile.exists()) {
                val lines = yamlFile.readLines(Charsets.UTF_8)
                for (rawLine in lines) {
                    val line = rawLine.trim()
                    if (line.isEmpty() || line.startsWith('#')) continue
                    if (!line.startsWith(keyName)) continue
                    val idx = line.indexOf(':')
                    if (idx <= 0) continue
                    val key = line.substring(0, idx).trim()
                    if (key != keyName) continue
                    var value = line.substring(idx + 1).trim()
                    if ((value.startsWith('"') && value.endsWith('"')) ||
                        (value.startsWith('\'') && value.endsWith('\''))) {
                        if (value.length >= 2) value = value.substring(1, value.length - 1)
                    }
                    return value
                }
            }
        }

        return null
    }
}
