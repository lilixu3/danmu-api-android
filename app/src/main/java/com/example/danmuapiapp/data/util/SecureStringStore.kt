package com.example.danmuapiapp.data.util

import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 用 Android Keystore 对敏感字符串做本地加密存储。
 */
class SecureStringStore(
    private val prefs: SharedPreferences,
    private val keyAlias: String
) {

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val CIPHER_PREFIX = "enc:v1:"
        private const val GCM_TAG_BITS = 128
        private const val IV_BYTES = 12
    }

    fun get(key: String, defaultValue: String = ""): String {
        val raw = prefs.safeGetString(key, defaultValue)
        if (!raw.startsWith(CIPHER_PREFIX)) {
            migratePlainValue(key, raw)
            return raw
        }
        val cipherText = raw.removePrefix(CIPHER_PREFIX)
        return decrypt(cipherText) ?: defaultValue
    }

    fun put(key: String, value: String) {
        if (value.isBlank()) {
            prefs.edit().putString(key, "").apply()
            return
        }
        val encrypted = encrypt(value)
        prefs.edit().putString(key, encrypted ?: value).apply()
    }

    private fun migratePlainValue(key: String, value: String) {
        if (value.isBlank()) return
        val encrypted = encrypt(value) ?: return
        prefs.edit().putString(key, encrypted).apply()
    }

    private fun encrypt(value: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        return runCatching {
            val secretKey = getOrCreateSecretKey() ?: return null
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, secretKey)
            }
            val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val payload = ByteArray(IV_BYTES + encrypted.size)
            System.arraycopy(cipher.iv, 0, payload, 0, IV_BYTES)
            System.arraycopy(encrypted, 0, payload, IV_BYTES, encrypted.size)
            CIPHER_PREFIX + Base64.encodeToString(payload, Base64.NO_WRAP)
        }.getOrNull()
    }

    private fun decrypt(payloadText: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        return runCatching {
            val secretKey = getOrCreateSecretKey() ?: return null
            val payload = Base64.decode(payloadText, Base64.DEFAULT)
            if (payload.size <= IV_BYTES) return null
            val iv = payload.copyOfRange(0, IV_BYTES)
            val encrypted = payload.copyOfRange(IV_BYTES, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            }
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun getOrCreateSecretKey(): SecretKey? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val existing = ks.getKey(keyAlias, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }
}
