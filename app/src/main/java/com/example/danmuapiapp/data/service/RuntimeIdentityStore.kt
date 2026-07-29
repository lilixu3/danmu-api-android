package com.example.danmuapiapp.data.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.core.content.edit
import com.example.danmuapiapp.NodeBridge
import com.example.danmuapiapp.data.util.SecureStringStore
import java.security.SecureRandom

internal object RuntimeIdentityStore {
    private const val PREFS_NAME = "runtime_identity"
    private const val KEY_INSTANCE_ID = "instance_id_v2"
    private const val KEY_LEGACY_INSTANCE_ID = "instance_id"
    private const val ENV_KEY = "DANMU_API_RUNTIME_IDENTITY"
    private const val KEY_ALIAS = "danmuapi_runtime_identity_v1"
    private val rng = SecureRandom()
    @Volatile
    private var cachedInstanceId: String? = null

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun secureStore(context: Context): SecureStringStore {
        return SecureStringStore(prefs(context), KEY_ALIAS)
    }

    @Synchronized
    fun ensureInstanceId(context: Context): String {
        cachedInstanceId?.takeIf { it.isNotBlank() }?.let { return it }
        val preferences = prefs(context)
        val existing = preferences.getString(KEY_INSTANCE_ID, null)?.trim().orEmpty()
        if (existing.isNotBlank()) {
            cachedInstanceId = existing
            return existing
        }

        // This value identifies one app installation; it is not a credential. Read the
        // encrypted legacy value once, then commit the plain marker synchronously so a
        // fast process restart cannot generate a different identity.
        val migrated = secureStore(context).get(KEY_LEGACY_INSTANCE_ID).trim()
        val resolved = migrated.ifBlank(::generateInstanceId)
        val committed = preferences.edit().putString(KEY_INSTANCE_ID, resolved).commit()
        cachedInstanceId = resolved
        if (!committed) {
            runCatching {
                AppDiagnosticLogger.w(
                    context,
                    "RuntimeIdentityStore",
                    "运行时实例 ID 写入磁盘失败，本进程将继续使用内存身份"
                )
            }
        }
        return resolved
    }

    fun exportToEnv(context: Context) {
        val id = ensureInstanceId(context)
        runCatching {
            NodeBridge.setEnvironmentVariable(ENV_KEY, id, true)
        }
    }

    fun extractHealthIdentity(body: String): String? {
        return Regex(""""runtimeIdentity"\s*:\s*"([^"]+)"""")
            .find(body)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    fun clearForTests(context: Context) {
        cachedInstanceId = null
        prefs(context).edit(commit = true) {
            remove(KEY_INSTANCE_ID)
            remove(KEY_LEGACY_INSTANCE_ID)
        }
    }

    private fun generateInstanceId(): String {
        val bytes = ByteArray(16)
        rng.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
    }
}
