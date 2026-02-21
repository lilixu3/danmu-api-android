package com.example.danmuapiapp.data.service

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.danmuapiapp.data.util.safeGetInt
import com.example.danmuapiapp.data.util.safeGetString
import com.example.danmuapiapp.domain.model.RunMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 统一管理 Node 运行目录与核心目录结构。
 */
object NodeProjectManager {

    private const val TAG = "NodeProjectManager"
    private const val APP_VERSION_FILE = ".app_version"
    private const val NODE_HANDLER_PATCH_MARK = "DanmuApiApp env path compatibility"

    private val json = Json { ignoreUnknownKeys = true }
    private val coreDirNameRegex = Regex("^danmu[-_]api$", RegexOption.IGNORE_CASE)
    private val projectDirLocks = ConcurrentHashMap<String, Any>()
    private val coreLayoutLocks = ConcurrentHashMap<String, Any>()
    private val versionRegexList = listOf(
        Regex("""(?m)\bVERSION\b\s*[:=]\s*['\"]([^'\"]+)['\"]"""),
        Regex("""(?m)\bversion\b\s*[:=]\s*['\"]([^'\"]+)['\"]""", RegexOption.IGNORE_CASE)
    )

    fun projectDir(context: Context): File = RuntimePaths.projectDir(context)

    fun projectDir(context: Context, mode: RunMode): File = RuntimePaths.projectDir(context, mode)

    fun coreDir(context: Context, variantKey: String): File {
        return File(projectDir(context), "danmu_api_$variantKey")
    }

    fun hasSelectedCoreInstalled(context: Context, targetProjectDir: File = projectDir(context)): Boolean {
        val prefs = context.getSharedPreferences("runtime", Context.MODE_PRIVATE)
        val variantKey = prefs.getString("variant", "stable").orEmpty().ifBlank { "stable" }
        val coreDir = File(targetProjectDir, "danmu_api_$variantKey")
        return hasValidCore(coreDir)
    }

    fun ensureProjectExtracted(context: Context, targetDir: File = projectDir(context)): File {
        val lock = lockForDir(projectDirLocks, targetDir)
        synchronized(lock) {
            val versionFile = File(targetDir, APP_VERSION_FILE)
            val entryFile = File(targetDir, "main.js")
            val currentVersion = currentAppVersion(context)
            val existingVersion = if (versionFile.exists()) versionFile.readText().trim() else ""
            val projectAlreadyExists = targetDir.exists() && entryFile.exists()

            if (targetDir.exists() && existingVersion == currentVersion && entryFile.exists()) {
                ensureRuntimeDirs(targetDir)
                migrateAllCoreLayouts(targetDir)
                return targetDir
            }

            targetDir.mkdirs()
            copyAssetFolder(
                context = context,
                assetPath = "nodejs-project",
                targetDir = targetDir,
                preserveExistingRuntimeEnv = projectAlreadyExists
            )
            versionFile.writeText(currentVersion)
            if (!entryFile.exists()) {
                throw IllegalStateException("工作目录不可用，缺少 main.js：${targetDir.absolutePath}")
            }
            migrateAllCoreLayouts(targetDir)
            ensureRuntimeDirs(targetDir)
            return targetDir
        }
    }

    fun writeRuntimeEnv(context: Context, targetProjectDir: File = projectDir(context)) {
        val prefs = context.getSharedPreferences("runtime", Context.MODE_PRIVATE)
        val envFile = File(targetProjectDir, "config/.env")
        envFile.parentFile?.mkdirs()

        val lines = if (envFile.exists()) {
            runCatching { envFile.readLines(Charsets.UTF_8).toMutableList() }.getOrDefault(mutableListOf())
        } else {
            mutableListOf()
        }

        val existingEnv = linkedMapOf<String, String>()
        lines.forEach { line ->
            val raw = line.trim()
            if (raw.isEmpty() || raw.startsWith("#")) return@forEach
            val eq = raw.indexOf('=')
            if (eq <= 0) return@forEach
            val key = raw.substring(0, eq).trim()
            val value = raw.substring(eq + 1).trim()
            if (key.isNotBlank()) {
                existingEnv[key] = value
            }
        }

        val legacyVariant = context
            .getSharedPreferences("danmu_api_variant", Context.MODE_PRIVATE)
            .safeGetString("variant")
            .trim()

        fun normalizeVariant(raw: String): String {
            return when (raw.trim().lowercase()) {
                "dev", "develop", "development" -> "dev"
                "custom" -> "custom"
                "stable" -> "stable"
                else -> "stable"
            }
        }

        val variantValue = when {
            prefs.contains("variant") -> {
                val fromPrefs = prefs.safeGetString("variant").trim()
                when {
                    fromPrefs.isNotBlank() -> normalizeVariant(fromPrefs)
                    existingEnv["DANMU_API_VARIANT"].isNullOrBlank().not() ->
                        normalizeVariant(existingEnv["DANMU_API_VARIANT"].orEmpty())
                    legacyVariant.isNotBlank() -> normalizeVariant(legacyVariant)
                    else -> "stable"
                }
            }
            existingEnv["DANMU_API_VARIANT"].isNullOrBlank().not() ->
                normalizeVariant(existingEnv["DANMU_API_VARIANT"].orEmpty())
            legacyVariant.isNotBlank() -> normalizeVariant(legacyVariant)
            else -> "stable"
        }

        val portFromEnv = existingEnv["DANMU_API_PORT"]?.toIntOrNull()?.takeIf { it in 1..65535 }
        val portValue = if (prefs.contains("port")) {
            prefs.safeGetInt("port", portFromEnv ?: 9321).coerceIn(1, 65535)
        } else {
            portFromEnv ?: 9321
        }

        val updates = linkedMapOf<String, String>()
        updates["DANMU_API_VARIANT"] = variantValue
        updates["DANMU_API_PORT"] = portValue.toString()
        val removeKeys = linkedSetOf<String>()
        val tokenFromPrefs = prefs.safeGetString("token").trim()
        if (prefs.contains("token")) {
            if (tokenFromPrefs.isNotBlank()) {
                updates["TOKEN"] = tokenFromPrefs
            } else {
                // 用户显式清空 token 时才移除，避免覆盖旧项目保存在 .env 的 token。
                removeKeys += "TOKEN"
            }
        } else {
            val tokenFromEnv = existingEnv["TOKEN"].orEmpty().trim()
            if (tokenFromEnv.isNotBlank()) {
                updates["TOKEN"] = tokenFromEnv
            }
        }
        updates["LOG_LEVEL"] = prefs.safeGetString("log_level", "info").ifBlank { "info" }
        // 统一走 /api/logs 内存日志，禁用文件日志落盘。
        val logSwitch = 0
        val logMaxBytes = 1048576
        updates["DANMU_API_LOG_TO_FILE"] = logSwitch.toString()
        updates["DANMU_API_LOG_MAX_BYTES"] = logMaxBytes.toString()
        updates["APP_LOG_TO_FILE"] = logSwitch.toString()
        updates["APP_LOG_MAX_BYTES"] = logMaxBytes.toString()

        val touched = linkedSetOf<String>()
        var i = 0
        while (i < lines.size) {
            val raw = lines[i].trim()
            if (raw.isEmpty() || raw.startsWith("#")) {
                i++
                continue
            }
            val eq = raw.indexOf('=')
            if (eq <= 0) {
                i++
                continue
            }
            val key = raw.substring(0, eq).trim()
            if (key in removeKeys) {
                lines.removeAt(i)
                continue
            }
            val newValue = updates[key]
            if (newValue == null) {
                i++
                continue
            }
            lines[i] = "$key=$newValue"
            touched += key
            i++
        }

        val missing = updates.filterKeys { it !in touched }
        if (missing.isNotEmpty()) {
            if (lines.isNotEmpty() && lines.last().isNotBlank()) {
                lines += ""
            }
            lines += "# App 运行时关键配置"
            missing.forEach { (key, value) ->
                lines += "$key=$value"
            }
        }

        envFile.writeText(lines.joinToString("\n").trimEnd() + "\n")
        cleanupLegacyLogFiles(targetProjectDir)
    }

    fun migrateAllCoreLayouts(projectDir: File) {
        listOf("stable", "dev", "custom").forEach { key ->
            normalizeCoreLayout(File(projectDir, "danmu_api_$key"))
        }
    }

    /**
     * 兼容不同仓库目录名：danmu_api / danmu-api。
     */
    fun normalizeCoreLayout(coreDir: File): Boolean {
        val lock = lockForDir(coreLayoutLocks, coreDir)
        synchronized(lock) {
            if (!coreDir.exists() || !coreDir.isDirectory) return false

            if (File(coreDir, "worker.js").exists()) {
                ensureCorePackageJson(coreDir)
                patchCoreNodeHandlerEnvPath(coreDir)
                return true
            }

            val nested = coreDir.listFiles()?.firstOrNull { child ->
                child.isDirectory && coreDirNameRegex.matches(child.name) && File(child, "worker.js").exists()
            } ?: return false

            return runCatching {
                nested.listFiles()?.forEach { child ->
                    val target = File(coreDir, child.name)
                    if (target.exists()) {
                        target.deleteRecursively()
                    }
                    if (!child.renameTo(target)) {
                        if (child.isDirectory) {
                            child.copyRecursively(target, overwrite = true)
                            child.deleteRecursively()
                        } else {
                            child.copyTo(target, overwrite = true)
                            child.delete()
                        }
                    }
                }
                nested.deleteRecursively()
                ensureCorePackageJson(coreDir)
                patchCoreNodeHandlerEnvPath(coreDir)
                true
            }.getOrElse {
                Log.w(TAG, "normalizeCoreLayout failed: ${coreDir.absolutePath}", it)
                false
            }
        }
    }

    fun hasValidCore(coreDir: File): Boolean {
        if (!coreDir.exists() || !coreDir.isDirectory) return false
        if (File(coreDir, "worker.js").exists()) return true
        return normalizeCoreLayout(coreDir) && File(coreDir, "worker.js").exists()
    }

    fun readCoreVersion(coreDir: File): String? {
        val globalsCandidates = listOf(
            File(coreDir, "configs/globals.js"),
            File(coreDir, "config/globals.js"),
            File(coreDir, "globals.js")
        )

        for (globals in globalsCandidates) {
            if (!globals.exists()) continue
            val text = runCatching { globals.readText() }.getOrNull().orEmpty()
            for (regex in versionRegexList) {
                val hit = regex.find(text)?.groupValues?.getOrNull(1)?.trim()
                if (!hit.isNullOrBlank()) return hit.removePrefix("v")
            }
        }

        val pkgVersion = readVersionFromPackageJson(coreDir)
        if (!pkgVersion.isNullOrBlank()) return pkgVersion.removePrefix("v")
        return null
    }

    fun ensureCorePackageJson(coreDir: File, version: String? = null) {
        val pkg = File(coreDir, "package.json")
        val normalizedVersion = version?.trim()?.removePrefix("v").orEmpty()

        if (!pkg.exists()) {
            val body = buildString {
                append("{\n")
                append("  \"type\": \"module\"")
                if (normalizedVersion.isNotBlank()) {
                    append(",\n  \"version\": \"")
                    append(normalizedVersion)
                    append("\"")
                }
                append("\n}\n")
            }
            pkg.writeText(body)
            return
        }

        if (normalizedVersion.isBlank()) return

        runCatching {
            val obj = json.parseToJsonElement(pkg.readText()).jsonObject
            val oldVersion = obj["version"]?.jsonPrimitive?.content?.trim()
            if (oldVersion.isNullOrBlank()) {
                val body = "{\n  \"type\": \"module\",\n  \"version\": \"$normalizedVersion\"\n}\n"
                pkg.writeText(body)
            }
        }
    }

    private fun readVersionFromPackageJson(coreDir: File): String? {
        val pkgJson = File(coreDir, "package.json")
        if (!pkgJson.exists()) return null
        return runCatching {
            val obj = json.parseToJsonElement(pkgJson.readText()).jsonObject
            obj["version"]?.jsonPrimitive?.content?.trim()
        }.getOrNull()
    }

    private fun currentAppVersion(context: Context): String {
        return runCatching {
            val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkg.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkg.versionCode.toLong()
            }
            "$code-${pkg.lastUpdateTime}"
        }.getOrElse { "0" }
    }

    private fun ensureRuntimeDirs(projectDir: File) {
        runCatching { File(projectDir, "config").mkdirs() }
        runCatching { File(projectDir, "logs").mkdirs() }
        // 核心本地缓存目录，缺失会导致缓存不可持久化
        runCatching { File(projectDir, ".cache").mkdirs() }
    }

    private fun cleanupLegacyLogFiles(projectDir: File) {
        val logsDir = File(projectDir, "logs")
        if (!logsDir.exists() || !logsDir.isDirectory) return
        runCatching {
            logsDir.listFiles()?.forEach { file ->
                val name = file.name
                if (name.startsWith("danmuapi.log") || name.startsWith("root_node_boot.log")) {
                    file.delete()
                }
            }
        }
    }

    private fun patchCoreNodeHandlerEnvPath(coreDir: File) {
        val handlerFiles = findNodeHandlerFiles(coreDir)
        if (handlerFiles.isEmpty()) return
        handlerFiles.forEach { patchSingleNodeHandler(it) }
    }

    private fun findNodeHandlerFiles(coreDir: File): List<File> {
        if (!coreDir.exists() || !coreDir.isDirectory) return emptyList()
        val out = ArrayList<File>()
        val stack = ArrayDeque<File>()
        stack.add(coreDir)
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            val children = runCatching { dir.listFiles() }.getOrNull() ?: continue
            for (child in children) {
                if (child.isDirectory) {
                    stack.add(child)
                    continue
                }
                if (!child.isFile) continue
                if (child.name != "node-handler.js") continue
                val normalized = child.absolutePath.replace('\\', '/')
                if (!normalized.contains("/handlers/")) continue
                out.add(child)
            }
        }
        return out
    }

    private fun patchSingleNodeHandler(handlerFile: File) {
        val source = runCatching { handlerFile.readText(Charsets.UTF_8) }
            .getOrElse {
                Log.w(TAG, "读取 node-handler 失败：${handlerFile.absolutePath}", it)
                return
            }
        if (source.contains(NODE_HANDLER_PATCH_MARK)) return
        if (!source.contains("const envPath")) return

        val envPathRegex = Regex(
            """const\s+envPath\s*=\s*path\.join\([\s\S]*?['"]config['"][\s\S]*?['"]\.env['"][\s\S]*?\);"""
        )
        if (!envPathRegex.containsMatchIn(source)) return

        val importRegex = Regex("""import\s+\{\s*fileURLToPath\s*\}\s+from\s+['"]url['"];""")
        val importMatch = importRegex.find(source) ?: return

        val helper = """
// $NODE_HANDLER_PATCH_MARK
function resolveEnvPath(importMetaUrl) {
  const __filename = fileURLToPath(importMetaUrl);
  const __dirname = path.dirname(__filename);

  const candidates = [];
  const home = String((typeof process !== 'undefined' && process.env && process.env.DANMU_API_HOME) || '').trim();
  if (home) {
    candidates.push(path.join(home, 'config', '.env'));
  }
  candidates.push(path.join(__dirname, '..', '..', '..', '..', 'config', '.env'));
  candidates.push(path.join(__dirname, '..', '..', '..', 'config', '.env'));

  const cwd = (typeof process !== 'undefined' && typeof process.cwd === 'function')
    ? String(process.cwd() || '').trim()
    : '';
  if (cwd) {
    candidates.push(path.join(cwd, 'config', '.env'));
  }

  for (const p of candidates) {
    if (!p) continue;
    try {
      if (fs.existsSync(p)) return p;
    } catch {}
  }
  return candidates.find(Boolean) || path.join(__dirname, '..', '..', '..', 'config', '.env');
}
""".trimIndent()

        val withImport = source.replaceRange(
            importMatch.range.first,
            importMatch.range.last + 1,
            importMatch.value + "\n\n" + helper
        )
        val replaced = envPathRegex.replace(withImport, "const envPath = resolveEnvPath(import.meta.url);")
        if (replaced == source || replaced == withImport) return

        runCatching {
            handlerFile.writeText(replaced, Charsets.UTF_8)
        }.onFailure {
            Log.w(TAG, "写入 node-handler 兼容补丁失败：${handlerFile.absolutePath}", it)
        }
    }

    private fun copyAssetFolder(
        context: Context,
        assetPath: String,
        targetDir: File,
        preserveExistingRuntimeEnv: Boolean = false
    ) {
        val assetManager = context.assets
        val files = assetManager.list(assetPath) ?: return

        targetDir.mkdirs()

        for (file in files) {
            val subAssetPath = "$assetPath/$file"
            val subFiles = assetManager.list(subAssetPath)

            if (!subFiles.isNullOrEmpty()) {
                copyAssetFolder(
                    context = context,
                    assetPath = subAssetPath,
                    targetDir = File(targetDir, file),
                    preserveExistingRuntimeEnv = preserveExistingRuntimeEnv
                )
            } else {
                val outFile = File(targetDir, file)
                val shouldKeepRuntimeEnv = preserveExistingRuntimeEnv &&
                    subAssetPath.replace('\\', '/').endsWith("config/.env") &&
                    outFile.exists()
                if (shouldKeepRuntimeEnv) continue
                assetManager.open(subAssetPath).use { input ->
                    outFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    private fun lockForDir(store: ConcurrentHashMap<String, Any>, dir: File): Any {
        val key = runCatching { dir.canonicalPath }.getOrElse { dir.absolutePath }
        return store.getOrPut(key) { Any() }
    }
}
