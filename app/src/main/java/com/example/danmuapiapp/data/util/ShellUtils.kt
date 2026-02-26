package com.example.danmuapiapp.data.util

object ShellUtils {
    fun shellQuote(input: String): String {
        return "'" + input.replace("'", "'\"'\"'") + "'"
    }
}
