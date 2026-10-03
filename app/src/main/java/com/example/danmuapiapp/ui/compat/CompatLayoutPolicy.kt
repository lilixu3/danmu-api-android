package com.example.danmuapiapp.ui.compat

/** Resolve from usable window constraints, never from device model or physical pixels. */
internal data class CompatLayoutPolicy(
    val compact: Boolean,
    val useRail: Boolean,
    val twoColumns: Boolean,
    val outerPadding: Int,
    val cardPadding: Int,
    val gap: Int,
    val contentWidth: Float
) {
    companion object {
        fun resolve(width: Float, height: Float, fontScale: Float): CompatLayoutPolicy {
            val scale = fontScale.coerceAtLeast(1f)
            val compact = height < 480f || width < 600f
            val rail = width / scale >= 940f && height / scale >= 480f
            val padding = if (compact) 12 else 28
            val available = (width - padding * 2 - if (rail) 192f else 0f).coerceAtLeast(0f)
            return CompatLayoutPolicy(
                compact = compact,
                useRail = rail,
                twoColumns = available / scale >= 720f,
                outerPadding = padding,
                cardPadding = if (compact) 16 else 24,
                gap = if (compact) 12 else 20,
                contentWidth = available
            )
        }
    }
}
