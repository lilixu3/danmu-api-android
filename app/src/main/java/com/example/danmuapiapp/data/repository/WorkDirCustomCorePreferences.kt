package com.example.danmuapiapp.data.repository

import android.content.SharedPreferences
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal data class StoredCustomCoreConfig(
    val displayName: String = "",
    val repo: String = "",
    val branch: String = ""
)

internal class WorkDirCustomCorePreferences(
    private val prefs: SharedPreferences
) {
    fun migrateLegacyConfigIfNeeded(
        workDirIdentity: String,
        legacyConfig: StoredCustomCoreConfig,
        hasLegacyConfig: Boolean
    ): Boolean {
        if (prefs.contains(KEY_MIGRATION_OWNER)) return false

        val editor = prefs.edit()
        if (hasLegacyConfig) {
            putConfig(editor, workDirIdentity, legacyConfig)
        }
        editor.putString(KEY_MIGRATION_OWNER, workDirIdentity)
        editor.commit()
        return hasLegacyConfig
    }

    fun read(workDirIdentity: String): StoredCustomCoreConfig {
        val prefix = prefix(workDirIdentity)
        return StoredCustomCoreConfig(
            displayName = prefs.getString(prefix + DISPLAY_NAME_SUFFIX, "").orEmpty(),
            repo = prefs.getString(prefix + REPO_SUFFIX, "").orEmpty(),
            branch = prefs.getString(prefix + BRANCH_SUFFIX, "").orEmpty()
        )
    }

    fun write(workDirIdentity: String, config: StoredCustomCoreConfig) {
        putConfig(prefs.edit(), workDirIdentity, config).apply()
    }

    fun isKeyForWorkDir(key: String?, workDirIdentity: String): Boolean {
        return key?.startsWith(prefix(workDirIdentity)) == true
    }

    private fun putConfig(
        editor: SharedPreferences.Editor,
        workDirIdentity: String,
        config: StoredCustomCoreConfig
    ): SharedPreferences.Editor {
        val prefix = prefix(workDirIdentity)
        return editor
            .putString(prefix + DISPLAY_NAME_SUFFIX, config.displayName)
            .putString(prefix + REPO_SUFFIX, config.repo)
            .putString(prefix + BRANCH_SUFFIX, config.branch)
    }

    private fun prefix(workDirIdentity: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(workDirIdentity.toByteArray(StandardCharsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return "$KEY_PREFIX$digest."
    }

    private companion object {
        const val KEY_MIGRATION_OWNER = "custom_core_work_dir_migration_owner_v1"
        const val KEY_PREFIX = "custom_core_work_dir_v1."
        const val DISPLAY_NAME_SUFFIX = "display_name"
        const val REPO_SUFFIX = "repo"
        const val BRANCH_SUFFIX = "branch"
    }
}
