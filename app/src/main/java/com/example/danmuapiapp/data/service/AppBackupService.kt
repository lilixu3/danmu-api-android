package com.example.danmuapiapp.data.service

import android.content.Context
import android.content.SharedPreferences
import com.example.danmuapiapp.BuildConfig
import com.example.danmuapiapp.data.util.DotEnvCodec
import com.example.danmuapiapp.data.util.ShellUtils.shellQuote
import com.example.danmuapiapp.domain.model.RunMode
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

@Serializable
enum class AppBackupSection {
    Environment,
    Favorites,
    AppSettings,
    CoreSources,
    AccessRules
}

@Serializable
private enum class PreferenceValueType { String, Int, Long, Float, Boolean, StringSet }

@Serializable
private data class BackupPreferenceEntry(
    val key: String,
    val type: PreferenceValueType,
    val value: String = "",
    val values: List<String> = emptyList()
)

@Serializable
private data class BackupPreferenceFile(
    val name: String,
    val entries: List<BackupPreferenceEntry>
)

@Serializable
private data class CoreInventoryEntry(
    val variantKey: String,
    val installed: Boolean,
    val version: String? = null,
    val sourceMetadata: String? = null
)

@Serializable
private data class AppBackupBundle(
    val format: String = AppBackupCodec.FORMAT,
    val schemaVersion: Int = AppBackupCodec.SCHEMA_VERSION,
    val createdAtMs: Long,
    val appVersion: String,
    val sections: List<AppBackupSection>,
    val environment: Map<String, String>? = null,
    val favorites: String? = null,
    val appPreferences: List<BackupPreferenceFile> = emptyList(),
    val corePreferences: List<BackupPreferenceFile> = emptyList(),
    val coreInventory: List<CoreInventoryEntry> = emptyList(),
    val accessRules: String? = null
)

data class AppBackupPreview(
    val createdAtMs: Long,
    val appVersion: String,
    val sections: Set<AppBackupSection>
)

data class AppBackupRestoreResult(
    val restoredSections: Set<AppBackupSection>,
    val mergedEnvironment: String?,
    val favoriteCount: Int? = null
)

internal object AppBackupCodec {
    const val FORMAT = "danmu-api-app-backup"
    const val SCHEMA_VERSION = 2
    private const val MAX_BACKUP_BYTES = 16 * 1024 * 1024
    private const val MAX_BACKUP_CHARS = 16 * 1024 * 1024
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(bundle: Any): String {
        require(bundle is AppBackupBundle)
        return json.encodeToString(bundle)
    }

    fun decode(raw: String): Any {
        if (raw.length > MAX_BACKUP_CHARS) throw IOException("备份文件超过 16 MB 上限")
        val bundle = json.decodeFromString<AppBackupBundle>(raw)
        if (bundle.format != FORMAT) throw IOException("不是弹幕 App 完整备份")
        if (bundle.schemaVersion !in 1..SCHEMA_VERSION) {
            throw IOException("备份版本 ${bundle.schemaVersion} 暂不支持")
        }
        return bundle
    }

    fun readUtf8(
        input: InputStream,
        maxBytes: Int = MAX_BACKUP_BYTES,
        label: String = "备份文件"
    ): String {
        require(maxBytes > 0) { "读取上限必须大于 0" }
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > maxBytes) {
                val limitMb = maxBytes / (1024 * 1024)
                throw IOException("$label 超过 $limitMb MB 上限")
            }
            output.write(buffer, 0, count)
        }
        return output.toString(Charsets.UTF_8.name())
    }
}

@Singleton
class AppBackupService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val CORE_SOURCE_METADATA = ".danmuapiapp-core-source.json"
        private const val ACCESS_RULES_PATH = "config/access-control.json"
        private val DEVICE_LOCAL_PREF_KEYS = mapOf(
            "danmu_node_run_mode" to setOf("run_mode", "root_mode")
        )
        private val APP_PREFS = linkedMapOf(
            "settings" to ::isAppSettingsKey,
            "danmu_ui_prefs" to { _: String -> true },
            "danmu_ui_scale_prefs" to { _: String -> true },
            "github_proxy_prefs" to { key: String -> key != "github_token" },
            "normal_autostart" to { _: String -> true },
            "danmu_root_autostart" to { _: String -> true },
            "danmu_keep_alive_prefs" to { key: String ->
                key !in setOf("desired_running", "recovery_failure_count", "recovery_block_until_ms")
            },
            "danmu_node_run_mode" to { _: String -> true },
            "device_compat_mode" to { _: String -> true },
            "runtime" to { key: String -> key in setOf("port", "variant", "listen_mode") },
            "danmu_download" to { key: String ->
                key !in setOf("records_json", "queue_json", "save_tree_uri", "save_dir_display")
            }
        )
        private val CORE_PREFS = linkedMapOf(
            "settings" to ::isCoreSettingsKey,
            "danmu_api_variant" to { key: String ->
                key == "custom_owner" || key == "custom_repo"
            }
        )

        private fun isCoreSettingsKey(key: String): Boolean {
            return key == "custom_repo" || key == "custom_repo_branch" ||
                key.endsWith("_repo_display_name") ||
                key.startsWith("custom_core_work_dir_v1.") ||
                key == "custom_core_work_dir_migration_owner_v1"
        }

        private fun isAppSettingsKey(key: String): Boolean {
            if (isCoreSettingsKey(key)) return false
            val lower = key.lowercase()
            return !lower.contains("token") && !lower.contains("password") &&
                !lower.contains("secret") && !lower.contains("cookie") &&
                !lower.contains("session")
        }
    }

    fun createBackup(
        sections: Set<AppBackupSection>,
        envContent: String
    ): Result<String> = runCatching {
        require(sections.isNotEmpty()) { "请至少选择一个备份类别" }
        val bundle = AppBackupBundle(
            createdAtMs = System.currentTimeMillis(),
            appVersion = BuildConfig.VERSION_NAME,
            sections = sections.sortedBy { it.ordinal },
            environment = if (AppBackupSection.Environment in sections) {
                BackupEnvironmentPolicy.exportValues(envContent)
            } else null,
            favorites = if (AppBackupSection.Favorites in sections) {
                FavoriteCacheStore.readCurrent(context).getOrThrow().content
            } else null,
            appPreferences = if (AppBackupSection.AppSettings in sections) {
                readPreferenceFiles(APP_PREFS)
            } else emptyList(),
            corePreferences = if (AppBackupSection.CoreSources in sections) {
                readPreferenceFiles(CORE_PREFS)
            } else emptyList(),
            coreInventory = if (AppBackupSection.CoreSources in sections) readCoreInventory() else emptyList(),
            accessRules = if (AppBackupSection.AccessRules in sections) readAccessRules() else null
        )
        AppBackupCodec.encode(bundle)
    }

    fun inspect(raw: String): Result<AppBackupPreview> = runCatching {
        val bundle = AppBackupCodec.decode(raw) as AppBackupBundle
        AppBackupPreview(bundle.createdAtMs, bundle.appVersion, bundle.sections.toSet())
    }

    suspend fun restore(
        raw: String,
        selectedSections: Set<AppBackupSection>,
        currentEnvContent: String,
        environmentWriter: suspend (String) -> Result<String>
    ): Result<AppBackupRestoreResult> = runCatching {
        val bundle = AppBackupCodec.decode(raw) as AppBackupBundle
        val available = bundle.sections.toSet()
        val selected = selectedSections.intersect(available)
        require(selected.isNotEmpty()) { "请至少选择一个恢复类别" }

        val validatedFavorite = if (AppBackupSection.Favorites in selected) {
            bundle.favorites?.let(FavoriteCacheStore::snapshotOf)
                ?: throw IOException("备份中缺少收藏数据")
        } else null
        if (AppBackupSection.AccessRules in selected && bundle.accessRules != null) {
            Json.parseToJsonElement(bundle.accessRules).jsonObject
        }
        if (AppBackupSection.AppSettings in selected) {
            validatePreferenceFiles(bundle.appPreferences, APP_PREFS)
        }
        if (AppBackupSection.CoreSources in selected) {
            validatePreferenceFiles(bundle.corePreferences, CORE_PREFS)
        }

        val mergedEnv = if (AppBackupSection.Environment in selected) {
            BackupEnvironmentPolicy.merge(currentEnvContent, bundle.environment.orEmpty())
        } else null

        val previousAppPreferences = if (AppBackupSection.AppSettings in selected) {
            readPreferenceFiles(APP_PREFS)
        } else emptyList()
        val previousCorePreferences = if (AppBackupSection.CoreSources in selected) {
            readPreferenceFiles(CORE_PREFS)
        } else emptyList()
        val previousFavorite = if (validatedFavorite != null) {
            FavoriteCacheStore.readCurrent(context).getOrThrow()
        } else null
        val previousAccessRules = if (AppBackupSection.AccessRules in selected) {
            readAccessRules()
        } else null

        try {
            if (mergedEnv != null) {
                environmentWriter(mergedEnv).getOrThrow()
            }
            if (AppBackupSection.AppSettings in selected) {
                restorePreferenceFiles(bundle.appPreferences, APP_PREFS)
            }
            if (AppBackupSection.CoreSources in selected) {
                restorePreferenceFiles(bundle.corePreferences, CORE_PREFS)
            }
            if (validatedFavorite != null) {
                FavoriteCacheStore.writeCurrent(context, validatedFavorite.content).getOrThrow()
            }
            if (AppBackupSection.AccessRules in selected) {
                writeAccessRules(bundle.accessRules)
            }
        } catch (restoreError: Throwable) {
            val rollbackErrors = mutableListOf<Throwable>()
            suspend fun rollback(block: suspend () -> Unit) {
                try {
                    block()
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (rollbackError: Throwable) {
                    rollbackErrors.add(rollbackError)
                }
            }
            // Restore preferences first so an environment rollback targets the original run mode.
            if (previousAppPreferences.isNotEmpty()) {
                rollback { restorePreferenceFiles(previousAppPreferences, APP_PREFS) }
            }
            if (previousCorePreferences.isNotEmpty()) {
                rollback { restorePreferenceFiles(previousCorePreferences, CORE_PREFS) }
            }
            if (previousFavorite != null) {
                rollback { FavoriteCacheStore.writeCurrent(context, previousFavorite.content).getOrThrow() }
            }
            if (AppBackupSection.AccessRules in selected) {
                rollback { writeAccessRules(previousAccessRules) }
            }
            if (mergedEnv != null) {
                rollback { environmentWriter(currentEnvContent).getOrThrow() }
            }
            rollbackErrors.forEach(restoreError::addSuppressed)
            throw restoreError
        }

        AppBackupRestoreResult(
            restoredSections = selected,
            mergedEnvironment = mergedEnv,
            favoriteCount = validatedFavorite?.count
        )
    }

    private fun readPreferenceFiles(
        specs: Map<String, (String) -> Boolean>
    ): List<BackupPreferenceFile> = specs.map { (name, allowKey) ->
        val entries = context.getSharedPreferences(name, Context.MODE_PRIVATE).all
            .filterKeys { key -> allowKey(key) && isTransferablePreference(name, key) }
            .mapNotNull { (key, value) -> value?.let { toEntry(key, it) } }
            .sortedBy { it.key }
        BackupPreferenceFile(name, entries)
    }

    private fun toEntry(key: String, value: Any): BackupPreferenceEntry? = when (value) {
        is String -> BackupPreferenceEntry(key, PreferenceValueType.String, value)
        is Int -> BackupPreferenceEntry(key, PreferenceValueType.Int, value.toString())
        is Long -> BackupPreferenceEntry(key, PreferenceValueType.Long, value.toString())
        is Float -> BackupPreferenceEntry(key, PreferenceValueType.Float, value.toString())
        is Boolean -> BackupPreferenceEntry(key, PreferenceValueType.Boolean, value.toString())
        is Set<*> -> BackupPreferenceEntry(
            key,
            PreferenceValueType.StringSet,
            values = value.filterIsInstance<String>().sorted()
        )
        else -> null
    }

    private fun validatePreferenceFiles(
        files: List<BackupPreferenceFile>,
        specs: Map<String, (String) -> Boolean>
    ) {
        files.forEach { file ->
            val allowKey = specs[file.name] ?: throw IOException("备份包含未知设置组 ${file.name}")
            file.entries.forEach { entry ->
                if (!allowKey(entry.key)) throw IOException("备份包含不允许恢复的设置项")
                when (entry.type) {
                    PreferenceValueType.Int -> entry.value.toIntOrNull() ?: throw IOException("设置值格式无效")
                    PreferenceValueType.Long -> entry.value.toLongOrNull() ?: throw IOException("设置值格式无效")
                    PreferenceValueType.Float -> entry.value.toFloatOrNull() ?: throw IOException("设置值格式无效")
                    PreferenceValueType.Boolean -> if (entry.value != "true" && entry.value != "false") {
                        throw IOException("设置值格式无效")
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun restorePreferenceFiles(
        files: List<BackupPreferenceFile>,
        specs: Map<String, (String) -> Boolean>
    ) {
        // Schema v1 omitted empty groups, so an absent group must mean "not backed up".
        // New backups contain explicit empty groups and can still intentionally clear one.
        files.forEach { file ->
            val allowKey = specs.getValue(file.name)
            val shouldRestore: (String) -> Boolean = { key ->
                allowKey(key) && isTransferablePreference(file.name, key)
            }
            val prefs = context.getSharedPreferences(file.name, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            prefs.all.keys.filter(shouldRestore).forEach(editor::remove)
            file.entries.filter { shouldRestore(it.key) }.forEach { putEntry(editor, it) }
            if (!editor.commit()) throw IOException("无法恢复设置组 ${file.name}")
        }
    }

    private fun isTransferablePreference(fileName: String, key: String): Boolean {
        return key !in DEVICE_LOCAL_PREF_KEYS[fileName].orEmpty()
    }

    private fun putEntry(editor: SharedPreferences.Editor, entry: BackupPreferenceEntry) {
        when (entry.type) {
            PreferenceValueType.String -> editor.putString(entry.key, entry.value)
            PreferenceValueType.Int -> editor.putInt(entry.key, entry.value.toInt())
            PreferenceValueType.Long -> editor.putLong(entry.key, entry.value.toLong())
            PreferenceValueType.Float -> editor.putFloat(entry.key, entry.value.toFloat())
            PreferenceValueType.Boolean -> editor.putBoolean(entry.key, entry.value.toBooleanStrict())
            PreferenceValueType.StringSet -> editor.putStringSet(entry.key, entry.values.toSet())
        }
    }

    private fun readCoreInventory(): List<CoreInventoryEntry> {
        val projectDir = RuntimePaths.normalProjectDir(context)
        return listOf("stable", "dev", "custom").map { key ->
            val directory = File(projectDir, "danmu_api_$key")
            CoreInventoryEntry(
                variantKey = key,
                installed = NodeProjectManager.hasValidCore(directory),
                version = NodeProjectManager.readCoreVersion(directory),
                sourceMetadata = File(directory, CORE_SOURCE_METADATA).takeIf { it.isFile }
                    ?.readText(Charsets.UTF_8)
            )
        }
    }

    private fun readAccessRules(): String? {
        val mode = RuntimePaths.currentRunMode(context)
        val target = File(RuntimePaths.projectDir(context, mode), ACCESS_RULES_PATH)
        if (mode == RunMode.Normal) {
            return target.takeIf { it.isFile }?.readText(Charsets.UTF_8)
        }

        val temporary = File.createTempFile("access-rules-read-", ".json", context.cacheDir)
        runCatching { temporary.delete() }
        try {
            val script = """
                SRC=${shellQuote(target.absolutePath)}
                DST=${shellQuote(temporary.absolutePath)}
                [ -f "${'$'}SRC" ] || exit 44
                cat "${'$'}SRC" > "${'$'}DST" || exit 2
                chmod 0644 "${'$'}DST" || exit 3
            """.trimIndent()
            val result = RootShell.exec(script, timeoutMs = 8_000L)
            if (result.exitCode == 44) return null
            if (!result.ok) throw IOException(result.stderr.ifBlank { "无法读取 Root 访问规则" })
            return temporary.readText(Charsets.UTF_8)
        } finally {
            runCatching { temporary.delete() }
        }
    }

    private fun writeAccessRules(content: String?) {
        val mode = RuntimePaths.currentRunMode(context)
        val target = File(RuntimePaths.projectDir(context, mode), ACCESS_RULES_PATH)
        if (mode == RunMode.Root) {
            writeRootAccessRules(target, content)
            return
        }
        if (content == null) {
            if (target.exists() && !target.delete()) throw IOException("无法清除现有访问规则")
            return
        }
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, "${target.name}.restore.tmp")
        temporary.writeText(content, Charsets.UTF_8)
        if (target.exists() && !target.delete()) throw IOException("无法替换访问规则")
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
    }

    private fun writeRootAccessRules(target: File, content: String?) {
        if (content == null) {
            val result = RootShell.exec(
                "rm -f -- ${shellQuote(target.absolutePath)}",
                timeoutMs = 8_000L
            )
            if (!result.ok) throw IOException(result.stderr.ifBlank { "无法清除 Root 访问规则" })
            return
        }

        val source = File.createTempFile("access-rules-write-", ".json", context.cacheDir)
        try {
            source.writeText(content, Charsets.UTF_8)
            val temporaryPath = "${target.absolutePath}.restore.tmp"
            val script = """
                SRC=${shellQuote(source.absolutePath)}
                DST=${shellQuote(target.absolutePath)}
                TMP=${shellQuote(temporaryPath)}
                mkdir -p "${'$'}(dirname "${'$'}DST")" || exit 2
                cat "${'$'}SRC" > "${'$'}TMP" || exit 3
                chmod 0644 "${'$'}TMP" || exit 4
                mv -f "${'$'}TMP" "${'$'}DST" || exit 5
            """.trimIndent()
            val result = RootShell.exec(script, timeoutMs = 8_000L)
            if (!result.ok) throw IOException(result.stderr.ifBlank { "无法写入 Root 访问规则" })
        } finally {
            runCatching { source.delete() }
        }
    }

}

internal object BackupEnvironmentPolicy {
    private val secretKey = Regex("(?i).*(TOKEN|PASSWORD|PASSWD|SECRET|API[_-]?KEY|COOKIE|AUTH).*")
    private val validKey = Regex("[A-Za-z_][A-Za-z0-9_]*")

    fun exportValues(content: String): Map<String, String> {
        return DotEnvCodec.parse(content).filterKeys { !secretKey.matches(it) }
    }

    fun merge(current: String, restored: Map<String, String>): String {
        val pending = restored
            .filterKeys { key -> validKey.matches(key) && !secretKey.matches(key) }
            .toMutableMap()
        val lines = current.lines().map { line ->
            val trimmed = line.trim()
            val separator = trimmed.indexOf('=')
            if (separator <= 0 || trimmed.startsWith("#")) return@map line
            val key = trimmed.substring(0, separator).trim()
            val value = pending.remove(key) ?: return@map line
            "$key=${DotEnvCodec.formatValue(value)}"
        }.toMutableList()
        pending.toSortedMap().forEach { (key, value) ->
            lines += "$key=${DotEnvCodec.formatValue(value)}"
        }
        return lines.joinToString("\n").trimEnd() + "\n"
    }
}
