package com.example.danmuapiapp.data.service

import java.security.MessageDigest
import java.io.File

internal object RuntimeDataIdentity {
    fun normalizeHome(path: String): String {
        val normalized = path.trim().replace('\\', '/')
        if (normalized.isBlank()) return ""
        return File(normalized).normalize().path.replace(Regex("^/data/user/0/"), "/data/data/").trimEnd('/')
    }

    fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun fileLibrary(home: String): String = "file:" + digest(normalizeHome(home) + "/.cache/local-danmu")

    fun matches(expectedHome: String, actualHome: String, expectedVariant: String, actualVariant: String): Boolean =
        normalizeHome(expectedHome).isNotBlank() && normalizeHome(expectedHome) == normalizeHome(actualHome) &&
            expectedVariant == actualVariant
}
