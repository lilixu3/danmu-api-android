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
    private const val KEY_INSTANCE_ID = "instance_id"
    private const val ENV_KEY = "DANMU_API_RUNTIME_IDENTITY"
    private const val KEY_ALIAS = "danmuapi_runtime_identity_v1"
    private val rng = SecureRandom()

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun secureStore(context: Context): SecureStringStore {
        return SecureStringStore(prefs(context), KEY_ALIAS)
    }

    fun ensureInstanceId(context: Context): String {
        val existing = secureStore(context).get(KEY_INSTANCE_ID).trim()
        if (existing.isNotBlank()) return existing

        val generated = generateInstanceId()
        secureStore(context).put(KEY_INSTANCE_ID, generated)
        return generated
    }

    fun exportToEnv(context: Context) {
        val id = ensureInstanceId(context)
        runCatching {
            NodeBridge.setEnvironmentVariable(ENV_KEY, id, true)
        }
    }

    fun readInstanceId(context: Context): String {
        return secureStore(context).get(KEY_INSTANCE_ID).trim()
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
        prefs(context).edit(commit = true) {
            remove(KEY_INSTANCE_ID)
        }
    }

    private fun generateInstanceId(): String {
        val bytes = ByteArray(16)
        rng.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
    }
}
