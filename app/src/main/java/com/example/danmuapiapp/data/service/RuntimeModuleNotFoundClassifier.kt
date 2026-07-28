package com.example.danmuapiapp.data.service

internal object RuntimeModuleNotFoundClassifier {
    private val missingSpecifier = Regex(
        pattern = "(?:Cannot find package|Cannot find module)\\s+['\"]([^'\"]+)['\"]",
        option = RegexOption.IGNORE_CASE
    )

    fun extractPackageName(message: String?): String? {
        val specifier = missingSpecifier.find(message.orEmpty())
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: return null
        if (
            specifier.startsWith(".") ||
            specifier.startsWith("/") ||
            specifier.startsWith("file:") ||
            specifier.startsWith("node:")
        ) {
            return null
        }
        val segments = specifier.split('/').filter(String::isNotBlank)
        return if (specifier.startsWith("@")) {
            segments.takeIf { it.size >= 2 }?.take(2)?.joinToString("/")
        } else {
            segments.firstOrNull()
        }
    }
}
