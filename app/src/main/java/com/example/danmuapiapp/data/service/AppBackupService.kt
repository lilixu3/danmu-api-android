package com.example.danmuapiapp.data.service

import android.content.Context
import android.content.SharedPreferences
import com.example.danmuapiapp.BuildConfig
import com.example.danmuapiapp.data.util.DotEnvCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
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
    const val SCHEMA_VERSION = 1
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
}

@Singleton
class AppBackupService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val CORE_SOURCE_METADATA = ".danmuapiapp-core-source.json"
        private const val ACCESS_RULES_PATH = "config/access-control.json"
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

    fun restore(
        raw: String,
        selectedSections: Set<AppBackupSection>,
        currentEnvContent: String
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
        val mergedEnv = if (AppBackupSection.Environment in selected) {
            BackupEnvironmentPolicy.merge(currentEnvContent, bundle.environment.orEmpty())
        } else null

        AppBackupRestoreResult(
            restoredSections = selected,
            mergedEnvironment = mergedEnv,
            favoriteCount = validatedFavorite?.count
        )
    }

    private fun readPreferenceFiles(
        specs: Map<String, (String) -> Boolean>
    ): List<BackupPreferenceFile> = specs.mapNotNull { (name, allowKey) ->
        val entries = context.getSharedPreferences(name, Context.MODE_PRIVATE).all
            .filterKeys(allowKey)
            .mapNotNull { (key, value) -> value?.let { toEntry(key, it) } }
            .sortedBy { it.key }
        BackupPreferenceFile(name, entries).takeIf { entries.isNotEmpty() }
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
        val byName = files.associateBy { it.name }
        specs.forEach { (name, allowKey) ->
            val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            prefs.all.keys.filter(allowKey).forEach(editor::remove)
            byName[name]?.entries.orEmpty().forEach { putEntry(editor, it) }
            if (!editor.commit()) throw IOException("无法恢复设置组 $name")
        }
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

    private fun readAccessRules(): String? = File(
        RuntimePaths.normalProjectDir(context),
        ACCESS_RULES_PATH
    ).takeIf { it.isFile }?.readText(Charsets.UTF_8)

    private fun writeAccessRules(content: String?) {
        val target = File(RuntimePaths.normalProjectDir(context), ACCESS_RULES_PATH)
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

}

internal object BackupEnvironmentPolicy {
    private val secretKey = Regex("(?i).*(TOKEN|PASSWORD|PASSWD|SECRET|API[_-]?KEY|COOKIE|AUTH).*")

    fun exportValues(content: String): Map<String, String> {
        return DotEnvCodec.parse(content).filterKeys { !secretKey.matches(it) }
    }

    fun merge(current: String, restored: Map<String, String>): String {
        val pending = restored.toMutableMap()
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
