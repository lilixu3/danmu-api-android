package com.example.danmuapiapp.data.service

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.danmuapiapp.data.util.safeGetInt
import com.example.danmuapiapp.data.util.safeGetString
import com.example.danmuapiapp.domain.model.RunMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * 统一管理 Node 运行目录与核心目录结构。
 */
object NodeProjectManager {

    private const val TAG = "NodeProjectManager"
    private const val APP_VERSION_FILE = ".app_version"
    private const val LEGACY_NODE_HANDLER_PATCH_MARK = "DanmuApiApp env path compatibility"
    private const val BUNDLED_PACKAGE_LOCK_ASSET = "nodejs-project/package-lock.json"
    private const val BUNDLED_PACKAGE_JSON_ASSET = "nodejs-project/package.json"
    private const val OPTIONAL_REDIS_ENV_KEY = "LOCAL_REDIS_URL"
    private const val OPTIONAL_REDIS_ASSET_BASE = "nodejs-optional/redis/node_modules"
    private const val OPTIONAL_REDIS_ASSET_PACKAGE = "$OPTIONAL_REDIS_ASSET_BASE/redis/package.json"
    private val runtimeBundledDependencyVersions = linkedMapOf(
        "https-proxy-agent" to "7.0.6",
        "agent-base" to "7.1.4",
        "debug" to "4.4.3",
        "ms" to "2.1.3",
        "data-uri-to-buffer" to "4.0.1",
        "fetch-blob" to "3.2.0",
        "formdata-polyfill" to "4.0.10",
        "node-domexception" to "1.0.0",
        "web-streams-polyfill" to "3.3.3",
        "node-fetch" to "3.3.2",
        "pako" to "2.1.0"
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val coreDirNameRegex = Regex("^danmu[-_]api$", RegexOption.IGNORE_CASE)
    private val projectDirLocks = ConcurrentHashMap<String, Any>()
    private val coreLayoutLocks = ConcurrentHashMap<String, Any>()

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
            val preserveBundledNodeModules =
                projectAlreadyExists && shouldPreserveBundledNodeModules(context, targetDir)

            if (targetDir.exists() && existingVersion == currentVersion && entryFile.exists()) {
                ensureRuntimeDirs(targetDir)
                ensureOptionalRuntimeDependencies(context, targetDir)
                migrateAllCoreLayouts(targetDir)
                return targetDir
            }

            targetDir.mkdirs()
            if (projectAlreadyExists && !preserveBundledNodeModules) {
                runCatching { File(targetDir, "node_modules").deleteRecursively() }
            }
            copyAssetFolder(
                context = context,
                assetPath = "nodejs-project",
                targetDir = targetDir,
                preserveExistingRuntimeEnv = projectAlreadyExists,
                preserveExistingNodeModules = preserveBundledNodeModules
            )
            versionFile.writeText(currentVersion)
            if (!entryFile.exists()) {
                throw IllegalStateException("工作目录不可用，缺少 main.js：${targetDir.absolutePath}")
            }
            migrateAllCoreLayouts(targetDir)
            ensureRuntimeDirs(targetDir)
            ensureOptionalRuntimeDependencies(context, targetDir)
            return targetDir
        }
    }

    fun hasProjectEntry(targetProjectDir: File): Boolean {
        return targetProjectDir.exists() &&
            targetProjectDir.isDirectory &&
            File(targetProjectDir, "main.js").exists()
    }

    fun syncRuntimeEnvIfProjectReady(
        context: Context,
        targetProjectDir: File = projectDir(context),
        preferredVariantKey: String? = null
    ): Boolean {
        if (!hasProjectEntry(targetProjectDir)) return false
        ensureRuntimeDirs(targetProjectDir)
        writeRuntimeEnv(
            context = context,
            targetProjectDir = targetProjectDir,
            preferredVariantKey = preferredVariantKey
        )
        return true
    }

    fun writeRuntimeEnv(
        context: Context,
        targetProjectDir: File = projectDir(context),
        preferredVariantKey: String? = null
    ) {
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

        fun normalizeVariantOrNull(raw: String?): String? {
            val value = raw?.trim()?.lowercase().orEmpty()
            return when (value) {
                "stable" -> "stable"
                "dev", "develop", "development" -> "dev"
                "custom" -> "custom"
                else -> null
            }
        }

        val variantFromPreferred = normalizeVariantOrNull(preferredVariantKey)
        val variantFromPrefs = normalizeVariantOrNull(
            if (prefs.contains("variant")) prefs.safeGetString("variant") else null
        )
        val variantFromLegacy = normalizeVariantOrNull(
            context.getSharedPreferences("danmu_api_variant", Context.MODE_PRIVATE)
                .safeGetString("variant")
        )
        val variantFromEnv = normalizeVariantOrNull(existingEnv["DANMU_API_VARIANT"])

        val variantValue =
            variantFromPreferred ?: variantFromPrefs ?: variantFromLegacy ?: variantFromEnv ?: "stable"

        val portFromEnv = existingEnv["DANMU_API_PORT"]?.toIntOrNull()?.takeIf { it in 1..65535 }
        val portValue = if (prefs.contains("port")) {
            prefs.safeGetInt("port", portFromEnv ?: 9321).coerceIn(1, 65535)
        } else {
            portFromEnv ?: 9321
        }
        val runtimeProfile = NormalModeRuntimeProfiles.current(context)

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
        updates["DANMU_API_WORKER"] = if (runtimeProfile.workerEnabled) "1" else "0"
        updates["DANMU_API_HOT_RELOAD"] = if (runtimeProfile.hotReloadEnabled) "1" else "0"
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
        ensureOptionalRuntimeDependencies(context, targetProjectDir)
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
                cleanupLegacyNodeHandlerPatch(coreDir)
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
                cleanupLegacyNodeHandlerPatch(coreDir)
                true
            }.getOrElse {
                Log.w(TAG, "normalizeCoreLayout failed: ${coreDir.absolutePath}", it)
                false
            }
        }
    }

    fun hasValidCore(coreDir: File): Boolean {
        if (!coreDir.exists() || !coreDir.isDirectory) return false
        if (hasRequiredCoreFiles(coreDir)) return true
        return normalizeCoreLayout(coreDir) && hasRequiredCoreFiles(coreDir)
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
            val version = CoreVersionParser.extractSourceVersion(text)
            if (!version.isNullOrBlank()) return version.removePrefix("v")
        }

        val pkgVersion = readVersionFromPackageJson(coreDir)
        if (!pkgVersion.isNullOrBlank()) return pkgVersion.removePrefix("v")
        return null
    }

    fun ensureCorePackageJson(coreDir: File, version: String? = null) {
        val pkg = File(coreDir, "package.json")
        val normalizedVersion = version?.trim()?.removePrefix("v").orEmpty()

        if (!pkg.exists()) {
            val body = buildCorePackageJsonText(version = normalizedVersion)
            pkg.writeText(body)
            return
        }

        runCatching {
            val obj = json.parseToJsonElement(pkg.readText()).jsonObject
            val existingType = runCatching { obj["type"]?.jsonPrimitive?.content?.trim() }.getOrNull()
            val existingVersion = runCatching { obj["version"]?.jsonPrimitive?.content?.trim() }.getOrNull().orEmpty()
            val dependencies = obj["dependencies"] as? JsonObject
            val shouldRewrite =
                existingType != "module" ||
                    (normalizedVersion.isNotBlank() && existingVersion.isBlank()) ||
                    (dependencies == null && looksLikeCoreDir(coreDir))

            if (shouldRewrite) {
                val merged = buildJsonObject {
                    put("type", "module")
                    val versionValue = existingVersion.ifBlank { normalizedVersion }
                    if (versionValue.isNotBlank()) {
                        put("version", versionValue)
                    }
                    if (dependencies != null) {
                        put("dependencies", dependencies)
                    } else if (looksLikeCoreDir(coreDir)) {
                        put("dependencies", buildCoreBundledDependenciesJson())
                    }
                    obj.forEach { (key, value) ->
                        if (key !in setOf("type", "version", "dependencies")) {
                            put(key, value)
                        }
                    }
                }
                pkg.writeText(json.encodeToString(JsonObject.serializer(), merged) + "\n")
            }
        }
    }

    fun collectMissingRuntimeDepsForCore(coreDir: File, runtimeNodeModulesDir: File): List<String> {
        val dependencies = readCoreDependencies(coreDir)
        if (dependencies.isEmpty()) return emptyList()
        return dependencies.mapNotNull { (name, version) ->
            if (!runtimeBundledDependencyVersions.containsKey(name)) return@mapNotNull null
            val pkgFile = File(runtimeNodeModulesDir, "$name/package.json")
            val installedVersion = runCatching {
                runCatching {
                    json.parseToJsonElement(pkgFile.readText()).jsonObject["version"]?.jsonPrimitive?.content?.trim()
                }.getOrNull()
            }.getOrNull().orEmpty()
            if (!pkgFile.exists() || installedVersion.isBlank() || installedVersion != version.removePrefix("^").removePrefix("~")) {
                "$name@$version"
            } else {
                null
            }
        }.sorted()
    }

    fun readCoreDependencies(coreDir: File): Map<String, String> {
        val pkgJson = File(coreDir, "package.json")
        if (!pkgJson.exists() || !pkgJson.isFile) return emptyMap()
        return runCatching {
            val obj = json.parseToJsonElement(pkgJson.readText()).jsonObject
            val dependencies = obj["dependencies"] as? JsonObject ?: return@runCatching emptyMap()
            buildMap {
                dependencies.forEach { (key, value) ->
                    val version = (value as? JsonPrimitive)?.content?.trim().orEmpty()
                    if (key.isNotBlank() && version.isNotBlank()) {
                        put(key, version)
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun buildCorePackageJsonText(version: String): String {
        val obj = buildJsonObject {
            put("type", "module")
            if (version.isNotBlank()) {
                put("version", version)
            }
            put("dependencies", buildCoreBundledDependenciesJson())
        }
        return json.encodeToString(JsonObject.serializer(), obj) + "\n"
    }

    private fun buildCoreBundledDependenciesJson(): JsonObject {
        return buildJsonObject {
            runtimeBundledDependencyVersions.forEach { (name, version) ->
                put(name, version)
            }
        }
    }

    private fun looksLikeCoreDir(coreDir: File): Boolean {
        return File(coreDir, "worker.js").exists() ||
            File(coreDir, "server.js").exists() ||
            File(coreDir, "configs").exists() ||
            File(coreDir, "utils").exists()
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

    private fun cleanupLegacyNodeHandlerPatch(coreDir: File) {
        val candidates = listOf(
            File(coreDir, "configs/handlers/node-handler.js"),
            File(coreDir, "config/handlers/node-handler.js")
        )
        candidates.forEach { file ->
            if (!file.exists() || !file.isFile) return@forEach
            cleanupSingleLegacyNodeHandlerPatch(file)
        }
    }

    private fun cleanupSingleLegacyNodeHandlerPatch(handlerFile: File) {
        val source = runCatching { handlerFile.readText(Charsets.UTF_8) }
            .getOrElse {
                Log.w(TAG, "读取 node-handler 失败：${handlerFile.absolutePath}", it)
                return
            }
        if (!source.contains(LEGACY_NODE_HANDLER_PATCH_MARK)) return

        val legacyHelperRegex = Regex(
            """(?s)\n*// DanmuApiApp env path compatibility\s*function resolveEnvPath\(importMetaUrl\) \{.*?\n\}\n\n"""
        )

        val withoutHelper = source.replace(legacyHelperRegex, "\n")
        val cleaned = withoutHelper.replace(
            "const envPath = resolveEnvPath(import.meta.url);",
            "const envPath = path.join(__dirname, '..', '..', '..', 'config', '.env');"
        )
        if (cleaned == source) return

        runCatching {
            handlerFile.writeText(cleaned, Charsets.UTF_8)
        }.onFailure {
            Log.w(TAG, "回滚 node-handler 兼容补丁失败：${handlerFile.absolutePath}", it)
        }
    }



    private fun ensureOptionalRedisDependency(context: Context, targetProjectDir: File) {
        if (!hasOptionalRedisConfigured(targetProjectDir)) return

        val assetRedisSignature = assetSha256(context, OPTIONAL_REDIS_ASSET_PACKAGE) ?: return
        val runtimeRedisSignature = fileSha256(File(targetProjectDir, "node_modules/redis/package.json"))
        val runtimeRedisClient = File(targetProjectDir, "node_modules/@redis/client/package.json")
        val runtimeClusterKeySlot = File(targetProjectDir, "node_modules/cluster-key-slot/package.json")

        if (
            runtimeRedisSignature == assetRedisSignature &&
            runtimeRedisClient.exists() &&
            runtimeClusterKeySlot.exists()
        ) {
            return
        }

        val nodeModulesDir = File(targetProjectDir, "node_modules")
        nodeModulesDir.mkdirs()
        runCatching { File(nodeModulesDir, "redis").deleteRecursively() }
        runCatching { File(nodeModulesDir, "@redis").deleteRecursively() }
        runCatching { File(nodeModulesDir, "cluster-key-slot").deleteRecursively() }
        copyAssetFolder(
            context = context,
            assetPath = OPTIONAL_REDIS_ASSET_BASE,
            targetDir = nodeModulesDir
        )
    }

    private fun shouldPreserveBundledNodeModules(context: Context, targetDir: File): Boolean {
        val nodeModulesDir = File(targetDir, "node_modules")
        if (!nodeModulesDir.exists() || !nodeModulesDir.isDirectory) return false

        val assetSignature = assetSha256(context, BUNDLED_PACKAGE_LOCK_ASSET)
            ?: assetSha256(context, BUNDLED_PACKAGE_JSON_ASSET)
            ?: return false
        val runtimeSignature = fileSha256(File(targetDir, "package-lock.json"))
            ?: fileSha256(File(targetDir, "package.json"))
            ?: return false
        return assetSignature == runtimeSignature
    }

    private fun hasOptionalRedisConfigured(targetProjectDir: File): Boolean {
        val value = readEnvValue(File(targetProjectDir, "config/.env"), OPTIONAL_REDIS_ENV_KEY)
        return !value.isNullOrBlank()
    }

    private fun readEnvValue(envFile: File, key: String): String? {
        if (!envFile.exists() || !envFile.isFile) return null
        return runCatching {
            envFile.readLines(Charsets.UTF_8).firstNotNullOfOrNull { line ->
                val raw = line.trim()
                if (raw.isEmpty() || raw.startsWith("#")) return@firstNotNullOfOrNull null
                val eq = raw.indexOf('=')
                if (eq <= 0) return@firstNotNullOfOrNull null
                if (raw.substring(0, eq).trim() != key) return@firstNotNullOfOrNull null
                val value = raw.substring(eq + 1).trim()
                when {
                    value.length >= 2 && value.startsWith('"') && value.endsWith('"') -> {
                        value.substring(1, value.length - 1)
                    }
                    value.length >= 2 && value.startsWith("'") && value.endsWith("'") -> {
                        value.substring(1, value.length - 1)
                    }
                    else -> value
                }
            }
        }.getOrNull()
    }

    private fun assetSha256(context: Context, assetPath: String): String? {
        return runCatching {
            context.assets.open(assetPath).use(::sha256Hex)
        }.getOrNull()
    }

    private fun fileSha256(file: File): String? {
        if (!file.exists() || !file.isFile) return null
        return runCatching {
            file.inputStream().use(::sha256Hex)
        }.getOrNull()
    }

    private fun sha256Hex(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read > 0) digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun ensureOptionalRuntimeDependencies(
        context: Context,
        targetProjectDir: File = projectDir(context)
    ) {
        ensureBundledRuntimeDependencies(context, targetProjectDir)
        ensureOptionalRedisDependency(context, targetProjectDir)
    }

    private fun ensureBundledRuntimeDependencies(
        context: Context,
        targetProjectDir: File
    ) {
        val nodeModulesDir = File(targetProjectDir, "node_modules")
        nodeModulesDir.mkdirs()

        val missingBasePackages = runtimeBundledDependencyVersions.keys.filter { name ->
            val assetPkg = "nodejs-project/node_modules/$name/package.json"
            val assetSignature = assetSha256(context, assetPkg) ?: return@filter false
            val runtimeSignature = fileSha256(File(nodeModulesDir, "$name/package.json"))
            runtimeSignature != assetSignature
        }
        if (missingBasePackages.isEmpty()) return

        missingBasePackages.forEach { name ->
            val packageDir = File(nodeModulesDir, name)
            runCatching { packageDir.deleteRecursively() }
            copyAssetFolder(
                context = context,
                assetPath = "nodejs-project/node_modules/$name",
                targetDir = packageDir
            )
        }
    }

    private fun copyAssetFolder(
        context: Context,
        assetPath: String,
        targetDir: File,
        preserveExistingRuntimeEnv: Boolean = false,
        preserveExistingNodeModules: Boolean = false
    ) {
        if (
            preserveExistingNodeModules &&
            assetPath.replace('\\', '/').endsWith("nodejs-project/node_modules")
        ) {
            return
        }

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
                    preserveExistingRuntimeEnv = preserveExistingRuntimeEnv,
                    preserveExistingNodeModules = preserveExistingNodeModules
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

internal fun hasRequiredCoreFiles(coreDir: File): Boolean {
    if (!coreDir.exists() || !coreDir.isDirectory) return false
    if (!File(coreDir, "worker.js").exists()) return false
    val globalsCandidates = listOf(
        File(coreDir, "configs/globals.js"),
        File(coreDir, "config/globals.js"),
        File(coreDir, "globals.js")
    )
    return globalsCandidates.any { it.exists() }
}
