package com.example.danmuapiapp.domain.model

enum class GlassMaterialPreference(val storageValue: Int) {
    LiquidGlass(1),
    Off(4);

    companion object {
        val Default = LiquidGlass

        fun fromStorageValue(value: Int): GlassMaterialPreference {
            // Values 0..3 were the former enabled material choices. Preserve them
            // as the single liquid-glass mode while keeping the old Off value.
            return if (value == Off.storageValue) Off else LiquidGlass
        }
    }
}
