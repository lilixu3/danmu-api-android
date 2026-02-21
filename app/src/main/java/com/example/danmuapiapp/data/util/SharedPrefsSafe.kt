package com.example.danmuapiapp.data.util

import android.content.SharedPreferences

fun SharedPreferences.safeGetString(key: String, defaultValue: String = ""): String {
    return runCatching { getString(key, defaultValue).orEmpty() }
        .getOrElse {
            when (val raw = all[key]) {
                is String -> raw
                is Number -> raw.toString()
                is Boolean -> raw.toString()
                else -> defaultValue
            }
        }
}

fun SharedPreferences.safeGetBoolean(key: String, defaultValue: Boolean): Boolean {
    return runCatching { getBoolean(key, defaultValue) }
        .getOrElse {
            when (val raw = all[key]) {
                is Boolean -> raw
                is Number -> raw.toInt() != 0
                is String -> when (raw.trim().lowercase()) {
                    "1", "true", "yes", "on" -> true
                    "0", "false", "no", "off" -> false
                    else -> defaultValue
                }
                else -> defaultValue
            }
        }
}

fun SharedPreferences.safeGetInt(key: String, defaultValue: Int): Int {
    return runCatching { getInt(key, defaultValue) }
        .getOrElse {
            when (val raw = all[key]) {
                is Number -> raw.toInt()
                is String -> raw.trim().toIntOrNull() ?: defaultValue
                is Boolean -> if (raw) 1 else 0
                else -> defaultValue
            }
        }
}
