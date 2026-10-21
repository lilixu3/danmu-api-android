package com.example.danmuapiapp.data.service

import android.content.Context
import com.example.danmuapiapp.data.util.RuntimeTokenNormalizer
import com.example.danmuapiapp.data.util.ShellUtils.shellQuote
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.RuntimeListenMode
import java.io.File
import java.security.MessageDigest

/**
 * Root 运行目录、环境变量、核心与依赖的同步。
 *
 * 保持 Root 目录与普通模式目录彻底隔离：只同步 App 托管文件、必要依赖与
 * Root 自身环境变量，不回灌 Root 独立的 config、core 与 .cache 数据。
 */
internal object RootRuntimeSync {
    private data class RuntimeEnvSnapshot(
        val variant: String,
        val port: Int,
        val listenHost: String,
        val logLevel: String,
        val tokenConfigured: Boolean,
        val token: String
    )

    private fun rootProjectMainJsExists(context: Context): Boolean {
        val script = """
            FILE=${shellQuote("${RootRuntimeLocation.rootProjectDir(context)}/main.js")}
            [ -f "${'$'}FILE" ]
        """.trimIndent()
        return RootShell.exec(script, timeoutMs = 2500L).ok
    }

    internal fun syncCoreDirectoryFromNormal(sourceDir: File, destinationPath: String): RootRuntimeController.OpResult {
        val token = "${android.os.Process.myPid()}-${System.currentTimeMillis()}"
        val result = RootShell.exec(
            RootRuntimeScripts.buildAtomicCoreSyncShell(
                sourcePath = sourceDir.absolutePath,
                destinationPath = destinationPath,
                operationToken = token
            ),
            timeoutMs = RootRuntimeLocation.ROOT_SYNC_TIMEOUT_MS
        )
        if (!result.ok) {
            val detail = (result.stderr.ifBlank { result.stdout }).trim().take(500)
                .ifBlank { "复制或完整性校验失败" }
            return RootRuntimeController.OpResult(false, "同步 Root 核心失败", detail)
        }
        return RootRuntimeController.OpResult(true, "Root 核心已原子同步")
    }

    internal fun buildNodeModulesFingerprint(
        nodeModulesDir: File,
        identityFiles: List<File>
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun addFile(label: String, file: File) {
            digest.update(label.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
            if (file.isFile) {
                file.inputStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                    }
                }
            }
            digest.update(0.toByte())
        }

        identityFiles.sortedBy { it.absolutePath }.forEach { file ->
            addFile("identity:${file.name}", file)
        }
        nodeModulesDir.listFiles().orEmpty()
            .filter { it.isDirectory && !it.name.startsWith('.') }
            .flatMap { entry ->
                if (entry.name.startsWith('@')) {
                    entry.listFiles().orEmpty().filter { it.isDirectory }
                } else {
                    listOf(entry)
                }
            }
            .map { packageDir -> File(packageDir, "package.json") }
            .filter { it.isFile }
            .sortedBy { it.relativeTo(nodeModulesDir).invariantSeparatorsPath }
            .forEach { packageJson ->
                addFile(packageJson.relativeTo(nodeModulesDir).invariantSeparatorsPath, packageJson)
            }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun syncNodeModulesAtomically(
        sourceDir: File,
        destinationPath: String,
        identityFiles: List<File>,
        failureMessage: String
    ): RootRuntimeController.OpResult {
        if (!sourceDir.isDirectory) return RootRuntimeController.OpResult(true, "没有需要同步的本地依赖")
        val fingerprint = buildNodeModulesFingerprint(sourceDir, identityFiles)
        val token = "${android.os.Process.myPid()}-${System.currentTimeMillis()}"
        val result = RootShell.exec(
            RootRuntimeScripts.buildAtomicNodeModulesSyncShell(
                sourcePath = sourceDir.absolutePath,
                destinationPath = destinationPath,
                fingerprint = fingerprint,
                operationToken = token
            ),
            timeoutMs = RootRuntimeLocation.ROOT_SYNC_TIMEOUT_MS
        )
        if (!result.ok) {
            val detail = (result.stderr.ifBlank { result.stdout }).trim().take(500)
                .ifBlank { "复制或完整性校验失败" }
            return RootRuntimeController.OpResult(false, failureMessage, detail)
        }
        return RootRuntimeController.OpResult(true, "Root 依赖已同步")
    }

    internal fun syncRuntimeEnvOnly(context: Context): RootRuntimeController.OpResult {
        if (!RootShell.hasRoot(2500L)) {
            return RootRuntimeController.OpResult(false, "同步 Root 环境失败", "缺少 Root 权限")
        }

        val envSyncResult = syncRuntimeEnvToRootFromPrefs(context)
        if (!envSyncResult.ok) return envSyncResult
        val normalize = normalizeRootProjectPermissions(context, fullScan = false)
        if (!normalize.ok) return normalize
        return RootRuntimeController.OpResult(true, "Root 环境同步完成")
    }

    internal fun ensureRootRuntimeReady(
        context: Context,
        refreshEnvWhenReady: Boolean = true
    ): RootRuntimeController.OpResult {
        return if (rootProjectMainJsExists(context)) {
            if (refreshEnvWhenReady) {
                val normalProject = runCatching {
                    val sourceProjectDir = RuntimePaths.normalProjectDir(context)
                    val dir = NodeProjectManager.ensureProjectExtracted(context, sourceProjectDir)
                    NodeProjectManager.migrateAllCoreLayouts(dir)
                    dir
                }.getOrElse {
                    return RootRuntimeController.OpResult(false, "运行时准备失败", it.message ?: "无法初始化工作目录")
                }

                val depsSyncResult = syncRootNodeModulesIfNeeded(context, normalProject)
                if (!depsSyncResult.ok) return depsSyncResult

                val projectSyncResult = syncProjectToRoot(context, normalProject, bootstrap = false)
                if (!projectSyncResult.ok) return projectSyncResult

                val coreReady = ensureSelectedCoreReady(context, normalProject)
                if (!coreReady.ok) return coreReady

                RootRuntimeSync.syncRuntimeEnvOnly(context)
            } else {
                RootRuntimeController.OpResult(true, "Root 运行目录已就绪")
            }
        } else {
            RootRuntimeSync.syncWorkDirToRoot(context)
        }
    }

    internal fun syncWorkDirToRoot(context: Context): RootRuntimeController.OpResult {
        val project = runCatching {
            val sourceProjectDir = RuntimePaths.normalProjectDir(context)
            val dir = NodeProjectManager.ensureProjectExtracted(context, sourceProjectDir)
            NodeProjectManager.migrateAllCoreLayouts(dir)
            NodeProjectManager.writeRuntimeEnv(context, dir)
            dir
        }.getOrElse {
            return RootRuntimeController.OpResult(false, "运行时准备失败", it.message ?: "无法初始化工作目录")
        }
        val syncResult = syncProjectToRoot(context, project, bootstrap = true)
        if (!syncResult.ok) return syncResult
        val depsSyncResult = syncRootNodeModulesIfNeeded(context, project)
        if (!depsSyncResult.ok) return depsSyncResult
        val envSyncResult = syncRuntimeEnvToRoot(context, project)
        if (!envSyncResult.ok) return envSyncResult
        val coreReady = ensureSelectedCoreReady(context, project)
        if (!coreReady.ok) return coreReady
        val normalize = normalizeRootProjectPermissions(context, fullScan = true)
        if (!normalize.ok) return normalize
        return RootRuntimeController.OpResult(true, "同步完成")
    }

    private fun syncRuntimeEnvToRootFromPrefs(context: Context): RootRuntimeController.OpResult {
        val snapshot = buildRuntimeEnvSnapshot(context)
        val rootEnvPath = "${RootRuntimeLocation.rootProjectDir(context)}/config/.env"
        val tokenConfigured = if (snapshot.tokenConfigured) "1" else "0"

        val script = """
            ENV_FILE=${shellQuote(rootEnvPath)}
            VARIANT=${shellQuote(snapshot.variant)}
            PORT=${shellQuote(snapshot.port.toString())}
            LISTEN_HOST=${shellQuote(snapshot.listenHost)}
            LOG_LEVEL=${shellQuote(snapshot.logLevel)}
            TOKEN_CONFIGURED=${shellQuote(tokenConfigured)}
            TOKEN_VALUE=${shellQuote(snapshot.token)}
            TMP_PREFIX="${'$'}ENV_FILE.tmp"

            mkdir -p "${'$'}(dirname "${'$'}ENV_FILE")" >/dev/null 2>&1 || true
            [ -f "${'$'}ENV_FILE" ] || touch "${'$'}ENV_FILE"

            upsert_env() {
              K="${'$'}1"
              V="${'$'}2"
              TMP="${'$'}TMP_PREFIX.${'$'}$"
              awk -v k="${'$'}K" -v v="${'$'}V" '
                BEGIN { done = 0 }
                $0 ~ "^[[:space:]]*" k "=" {
                  if (!done) {
                    print k "=" v
                    done = 1
                  }
                  next
                }
                { print }
                END {
                  if (!done) print k "=" v
                }
              ' "${'$'}ENV_FILE" > "${'$'}TMP" && mv "${'$'}TMP" "${'$'}ENV_FILE"
            }

            remove_env() {
              K="${'$'}1"
              TMP="${'$'}TMP_PREFIX.${'$'}$"
              awk -v k="${'$'}K" '
                $0 ~ "^[[:space:]]*" k "=" { next }
                { print }
              ' "${'$'}ENV_FILE" > "${'$'}TMP" && mv "${'$'}TMP" "${'$'}ENV_FILE"
            }

            ensure_env_default() {
              K="${'$'}1"
              V="${'$'}2"
              if grep -Eq "^[[:space:]]*${'$'}K=" "${'$'}ENV_FILE" 2>/dev/null; then
                return 0
              fi
              upsert_env "${'$'}K" "${'$'}V"
            }

            upsert_env "DANMU_API_VARIANT" "${'$'}VARIANT"
            upsert_env "DANMU_API_PORT" "${'$'}PORT"
            upsert_env "DANMU_API_HOST" "${'$'}LISTEN_HOST"
            upsert_env "LOG_LEVEL" "${'$'}LOG_LEVEL"
            ensure_env_default "DANMU_API_LOG_TO_FILE" "0"
            ensure_env_default "DANMU_API_LOG_MAX_BYTES" "1048576"
            ensure_env_default "APP_LOG_TO_FILE" "0"
            ensure_env_default "APP_LOG_MAX_BYTES" "1048576"

            if [ "${'$'}TOKEN_CONFIGURED" = "1" ]; then
              if [ -n "${'$'}TOKEN_VALUE" ]; then
                upsert_env "TOKEN" "${'$'}TOKEN_VALUE"
              else
                remove_env "TOKEN"
              fi
            fi

            [ -f "${'$'}ENV_FILE" ]
        """.trimIndent()

        val result = RootShell.exec(script, timeoutMs = 12_000L)
        if (!result.ok) {
            val err = (result.stderr.ifBlank { result.stdout }).trim().take(400)
            return RootRuntimeController.OpResult(false, "同步 Root 环境失败", if (err.isBlank()) "未知错误" else err)
        }
        return RootRuntimeController.OpResult(true, "Root 环境同步完成")
    }

    private fun buildRuntimeEnvSnapshot(context: Context): RuntimeEnvSnapshot {
        val prefs = context.getSharedPreferences("runtime", Context.MODE_PRIVATE)
        val rawVariant = prefs.getString("variant", "stable").orEmpty().trim().lowercase()
        val variant = when (rawVariant) {
            "dev", "develop", "development" -> "dev"
            "custom" -> "custom"
            else -> "stable"
        }
        val port = prefs.getInt("port", 9321).coerceIn(1, 65535)
        val listenHost = RuntimeListenMode.fromKey(
            prefs.getString(RuntimeListenMode.PREFERENCE_KEY, null)
        )?.bindHost ?: RuntimeListenMode.Ipv4Only.bindHost
        val logLevel = prefs.getString("log_level", "info").orEmpty().trim().ifBlank { "info" }
        val tokenConfigured = prefs.contains("token")
        val token = RuntimeTokenNormalizer.normalizeInput(prefs.getString("token", ""))
        return RuntimeEnvSnapshot(
            variant = variant,
            port = port,
            listenHost = listenHost,
            logLevel = logLevel,
            tokenConfigured = tokenConfigured,
            token = token
        )
    }

    private fun syncProjectToRoot(
        context: Context,
        srcProjectDir: File,
        bootstrap: Boolean = !rootProjectMainJsExists(context)
    ): RootRuntimeController.OpResult {
        val src = srcProjectDir.absolutePath
        val dst = RootRuntimeLocation.rootProjectDir(context)

        val script = if (bootstrap) {
            // 首次引导使用同目录 staging，完整校验后再切换，不破坏旧 Root 目录。
            RootRuntimeScripts.buildAtomicProjectBootstrapShell(
                sourcePath = src,
                destinationPath = dst,
                operationToken = "${android.os.Process.myPid()}-${System.currentTimeMillis()}"
            )
        } else {
            // 增量同步：只同步 App 托管的顶层包装文件，保留 Root 工作目录中的
            // config、danmu_api_*、node_modules 与 .cache。避免每次启动都复制整个 core/node_modules 到临时目录。
            RootRuntimeScripts.buildRootProjectIncrementalSyncShell(
                srcProjectPath = src,
                dstProjectPath = dst,
                managedPaths = AppRuntimeAssets.relativePaths(context)
            )
        }

        val result = RootShell.exec(script, timeoutMs = if (bootstrap) RootRuntimeLocation.ROOT_SYNC_TIMEOUT_MS else 25_000L)
        if (!result.ok) {
            val err = (result.stderr.ifBlank { result.stdout }).trim().take(400)
            return RootRuntimeController.OpResult(false, "同步 Root 运行目录失败", if (err.isBlank()) "未知错误" else err)
        }
        return RootRuntimeController.OpResult(true, "同步完成")
    }

    private fun syncRuntimeEnvToRoot(context: Context, srcProjectDir: File): RootRuntimeController.OpResult {
        val srcEnv = File(srcProjectDir, "config/.env")
        if (!srcEnv.exists() || !srcEnv.isFile) {
            return RootRuntimeController.OpResult(false, "同步 Root 环境失败", "未找到运行时配置文件：${srcEnv.absolutePath}")
        }

        val script = """
            SRC=${shellQuote(srcEnv.absolutePath)}
            DST=${shellQuote("${RootRuntimeLocation.rootProjectDir(context)}/config/.env")}
            mkdir -p "${'$'}(dirname "${'$'}DST")" >/dev/null 2>&1 || true
            cp -f "${'$'}SRC" "${'$'}DST" 2>/dev/null || cat "${'$'}SRC" > "${'$'}DST"
            test -f "${'$'}DST"
        """.trimIndent()
        val result = RootShell.exec(script, timeoutMs = 10000L)
        if (!result.ok) {
            val err = (result.stderr.ifBlank { result.stdout }).trim().take(400)
            return RootRuntimeController.OpResult(false, "同步 Root 环境失败", if (err.isBlank()) "未知错误" else err)
        }
        return RootRuntimeController.OpResult(true, "Root 环境同步完成")
    }

    private fun normalizeRootProjectPermissions(context: Context, fullScan: Boolean): RootRuntimeController.OpResult {
        val script = RootRuntimeScripts.buildRootProjectPermissionNormalizeShell(
            rootProjectPath = RootRuntimeLocation.rootProjectDir(context),
            fullScan = fullScan,
            managedPaths = AppRuntimeAssets.relativePaths(context)
        )
        val result = RootShell.exec(script, timeoutMs = if (fullScan) 15_000L else 5_000L)
        if (!result.ok) {
            val err = (result.stderr.ifBlank { result.stdout }).trim().take(400)
            return RootRuntimeController.OpResult(false, "Root 目录权限修复失败", if (err.isBlank()) "未知错误" else err)
        }
        return RootRuntimeController.OpResult(true, "Root 目录权限已修复")
    }

    private fun ensureSelectedCoreReady(context: Context, normalProjectDir: File): RootRuntimeController.OpResult {
        val prefs = context.getSharedPreferences("runtime", Context.MODE_PRIVATE)
        val variant = ApiVariant.entries.find { it.key == prefs.getString("variant", "stable") }
            ?: ApiVariant.Stable
        val normalCoreDir = File(normalProjectDir, "danmu_api_${variant.key}")
        val rootCoreDirPath = "${RootRuntimeLocation.rootProjectDir(context)}/danmu_api_${variant.key}"

        if (rootCoreHasWorker(rootCoreDirPath)) {
            val requirementsSync = syncRootCoreRequirements(normalCoreDir, rootCoreDirPath)
            if (!requirementsSync.ok) return requirementsSync
            return syncRootCoreNodeModulesIfNeeded(normalCoreDir, rootCoreDirPath)
        }

        // Root 缺少当前核心时，仅补齐当前核心，避免恢复时覆盖用户已修改的其它核心。
        if (!NodeProjectManager.hasValidCore(normalCoreDir)) {
            return RootRuntimeController.OpResult(
                false,
                "Root 缺少当前核心",
                "当前核心 ${variant.label} 未安装或不完整，请先安装后再启动 Root 模式"
            )
        }

        val coreSync = RootRuntimeSync.syncCoreDirectoryFromNormal(normalCoreDir, rootCoreDirPath)
        if (!coreSync.ok) return coreSync
        val depsSync = syncRootCoreNodeModulesIfNeeded(normalCoreDir, rootCoreDirPath)
        if (!depsSync.ok) return depsSync
        return RootRuntimeController.OpResult(true, "同步完成")
    }

    private fun syncRootCoreRequirements(normalCoreDir: File, rootCoreDirPath: String): RootRuntimeController.OpResult {
        val source = File(normalCoreDir, NodeProjectManager.CORE_RUNTIME_REQUIREMENTS_FILE)
        if (!source.isFile) return RootRuntimeController.OpResult(true, "核心依赖清单无需同步")
        val destination = "$rootCoreDirPath/${NodeProjectManager.CORE_RUNTIME_REQUIREMENTS_FILE}"
        val script = """
            SRC=${shellQuote(source.absolutePath)}
            DST=${shellQuote(destination)}
            TMP="${'$'}DST.tmp-${System.nanoTime()}"
            [ -f "${'$'}SRC" ] || exit 0
            [ -d "${'$'}(dirname "${'$'}DST")" ] || exit 2
            cp "${'$'}SRC" "${'$'}TMP" || exit 3
            cmp -s "${'$'}SRC" "${'$'}TMP" || exit 4
            chmod 0644 "${'$'}TMP" || exit 5
            mv "${'$'}TMP" "${'$'}DST" || exit 6
        """.trimIndent()
        val result = RootShell.exec(script, timeoutMs = 5000L)
        if (!result.ok) {
            val detail = (result.stderr.ifBlank { result.stdout }).trim().take(400)
            return RootRuntimeController.OpResult(
                false,
                "同步 Root 核心依赖清单失败",
                detail.ifBlank { "Root 文件写入失败" }
            )
        }
        return RootRuntimeController.OpResult(true, "Root 核心依赖清单已同步")
    }

    internal fun verifyRootRuntimeDependencies(context: Context): RootRuntimeController.OpResult {
        val prefs = context.getSharedPreferences("runtime", Context.MODE_PRIVATE)
        val variant = ApiVariant.entries.find { it.key == prefs.getString("variant", "stable") }
            ?: ApiVariant.Stable
        val result = RootShell.exec(
            RootRuntimeScripts.buildRootRuntimeDependencyProbeShell(RootRuntimeLocation.rootProjectDir(context), variant.key),
            timeoutMs = 5000L
        )
        if (result.ok) return RootRuntimeController.OpResult(true, "Root 运行时依赖完整")

        val missing = result.stdout.lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .filterNot { it == "__invalid_dependency_name__" }
            .distinct()
            .sorted()
            .toList()
        if (missing.isNotEmpty()) {
            return RootRuntimeController.OpResult(
                false,
                "Root 运行时依赖缺失",
                RuntimeDependencyHealthChecker.missingMessage(missing)
            )
        }
        val detail = (result.stderr.ifBlank { result.stdout }).trim().take(400)
        return RootRuntimeController.OpResult(
            false,
            "Root 运行时依赖检查失败",
            detail.ifBlank { "Root 依赖清单无效或无法读取" }
        )
    }

    private fun rootCoreHasWorker(rootCoreDirPath: String): Boolean {
        val script = """
            DIR=${shellQuote(rootCoreDirPath)}
            [ -f "${'$'}DIR/worker.js" ] && exit 0
            [ -f "${'$'}DIR/danmu_api/worker.js" ] && exit 0
            [ -f "${'$'}DIR/danmu-api/worker.js" ] && exit 0
            exit 1
        """.trimIndent()
        return RootShell.exec(script, timeoutMs = 3000L).ok
    }

    private fun syncRootNodeModulesIfNeeded(context: Context, normalProjectDir: File): RootRuntimeController.OpResult {
        return syncNodeModulesAtomically(
            sourceDir = File(normalProjectDir, "node_modules"),
            destinationPath = "${RootRuntimeLocation.rootProjectDir(context)}/node_modules",
            identityFiles = listOf(
                File(normalProjectDir, "package-lock.json"),
                File(normalProjectDir, "package.json"),
                File(normalProjectDir, ".app_version"),
                File(normalProjectDir, "runtime_asset_layout.txt")
            ),
            failureMessage = "同步 Root 依赖失败"
        )
    }

    private fun syncRootCoreNodeModulesIfNeeded(normalCoreDir: File, rootCoreDirPath: String): RootRuntimeController.OpResult {
        return syncNodeModulesAtomically(
            sourceDir = File(normalCoreDir, "node_modules"),
            destinationPath = "$rootCoreDirPath/node_modules",
            identityFiles = listOf(
                File(normalCoreDir, "package.json"),
                File(normalCoreDir, ".danmuapiapp-runtime-pack.json"),
                File(normalCoreDir, ".danmuapiapp-runtime-import.json")
            ),
            failureMessage = "同步 Root 核心依赖失败"
        )
    }
}
