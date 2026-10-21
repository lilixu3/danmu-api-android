package com.example.danmuapiapp.ui.common

import java.util.Locale

private const val KIB = 1024.0
private const val MIB = KIB * 1024.0
private const val GIB = MIB * 1024.0

/**
 * Formats a byte count for presentation with one shared unit scale and decimal separator.
 * Negative values are clamped to zero so partial or unknown progress never prints a minus sign.
 */
internal fun formatByteSize(bytes: Long): String {
    val value = bytes.coerceAtLeast(0L)
    return when {
        value >= GIB.toLong() -> String.format(Locale.ROOT, "%.2f GB", value / GIB)
        value >= MIB.toLong() -> String.format(Locale.ROOT, "%.1f MB", value / MIB)
        value >= KIB.toLong() -> String.format(Locale.ROOT, "%.1f KB", value / KIB)
        else -> "$value B"
    }
}

internal fun formatByteSize(bytes: Int): String = formatByteSize(bytes.toLong())
