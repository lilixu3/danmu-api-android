package com.example.danmuapiapp.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class GlassMaterialTarget {
    Card,
    Dialog,
    Button,
    DialogButton,
    PrimaryButton,
    DialogPrimaryButton,
    Selected,
    BottomBar
}

@Serializable
data class GlassEffectOverridePreference(
    val blurRadius: Float? = null,
    val refractionHeight: Float? = null,
    val refractionAmount: Float? = null,
    val surfaceAlpha: Float? = null,
    val brightness: Float? = null,
    val contrast: Float? = null,
    val saturation: Float? = null,
    val highlightAlpha: Float? = null,
    val highlightWidth: Float? = null,
    val depthEffect: Boolean? = null,
    val chromaticAberration: Boolean? = null
) {
    fun normalized(): GlassEffectOverridePreference = copy(
        blurRadius = blurRadius.normalized(0f, 40f),
        refractionHeight = refractionHeight.normalized(0f, 48f),
        refractionAmount = refractionAmount.normalized(0f, 64f),
        surfaceAlpha = surfaceAlpha.normalized(0f, 1f),
        brightness = brightness.normalized(-0.25f, 0.25f),
        contrast = contrast.normalized(0.5f, 1.8f),
        saturation = saturation.normalized(0f, 2.2f),
        highlightAlpha = highlightAlpha.normalized(0f, 1f),
        highlightWidth = highlightWidth.normalized(0.1f, 4f)
    )

    fun isEmpty(): Boolean {
        return blurRadius == null &&
            refractionHeight == null &&
            refractionAmount == null &&
            surfaceAlpha == null &&
            brightness == null &&
            contrast == null &&
            saturation == null &&
            highlightAlpha == null &&
            highlightWidth == null &&
            depthEffect == null &&
            chromaticAberration == null
    }
}

@Serializable
data class GlassTuningPreference(
    val adaptiveLuminance: Boolean = false,
    val overrides: Map<GlassMaterialTarget, GlassEffectOverridePreference> = emptyMap()
) {
    val isDefault: Boolean
        get() = !adaptiveLuminance && overrides.isEmpty()

    fun normalized(): GlassTuningPreference {
        return GlassTuningPreference(
            adaptiveLuminance = adaptiveLuminance,
            overrides = overrides.mapNotNull { (target, override) ->
                override.normalized().takeUnless { it.isEmpty() }?.let { target to it }
            }.toMap()
        )
    }
}

enum class GlassTuningPreset(
    val label: String,
    val description: String,
    val tuning: GlassTuningPreference
) {
    Default(
        label = "默认",
        description = "当前默认效果 · 柔和磨砂",
        tuning = GlassTuningPreference()
    ),
    Crystal(
        label = "晶透",
        description = "低模糊 · 高透光 · 清晰折射",
        tuning = glassPreset(
            card = glassEffect(2f, 18f, 36f, 0.22f, 0.04f, 1.04f, 1.35f, 0.32f, 0.8f, true),
            dialog = glassEffect(7f, 24f, 48f, 0.64f, 0.06f, 1.04f, 1.25f, 0.42f, 1f, true),
            button = glassEffect(1f, 12f, 24f, 0.18f, 0.03f, 1.05f, 1.4f, 0.4f, 0.8f, true),
            dialogButton = glassEffect(1f, 12f, 24f, 0.12f, 0.03f, 1.05f, 1.4f, 0.4f, 0.8f, true),
            primaryButton = glassEffect(1f, 12f, 24f, 0.58f, 0.03f, 1.05f, 1.25f, 0.4f, 0.8f, true),
            dialogPrimaryButton = glassEffect(1f, 12f, 24f, 0.26f, 0.03f, 1.05f, 1.2f, 0.4f, 0.8f, true),
            selected = glassEffect(1f, 10f, 16f, 0.13f, 0.03f, 1.05f, 1.2f, 0.22f, 0.8f, true),
            bottomBar = glassEffect(3f, 24f, 30f, 0.26f, 0.03f, 1.04f, 1.4f, 0.34f, 1f, true)
        )
    ),
    Balanced(
        label = "均衡",
        description = "适中透光 · 稳定清晰",
        tuning = glassPreset(
            card = glassEffect(6f, 16f, 30f, 0.42f, 0.01f, 1.03f, 1.35f, 0.24f, 0.7f, true),
            dialog = glassEffect(12f, 22f, 42f, 0.76f, 0.05f, 1.03f, 1.15f, 0.38f, 1f, true),
            button = glassEffect(2f, 12f, 24f, 0.34f, 0.02f, 1.04f, 1.4f, 0.3f, 0.7f, true),
            dialogButton = glassEffect(2f, 12f, 24f, 0.14f, 0.02f, 1.04f, 1.4f, 0.3f, 0.7f, true),
            primaryButton = glassEffect(2f, 12f, 24f, 0.72f, 0.02f, 1.04f, 1.25f, 0.3f, 0.7f, true),
            dialogPrimaryButton = glassEffect(2f, 12f, 24f, 0.28f, 0.02f, 1.04f, 1.18f, 0.3f, 0.7f, true),
            selected = glassEffect(2f, 10f, 14f, 0.14f, 0.02f, 1.04f, 1.15f, 0.18f, 0.7f, true),
            bottomBar = glassEffect(7f, 24f, 26f, 0.38f, 0.02f, 1.03f, 1.4f, 0.28f, 0.8f, true)
        )
    ),
    Adaptive(
        label = "随景",
        description = "随背景明暗 · 自动平衡可读性",
        tuning = glassPreset(
            adaptiveLuminance = true,
            card = glassEffect(6f, 18f, 34f, 0.40f, 0f, 1.05f, 1.3f, 0.28f, 0.8f, true),
            dialog = glassEffect(12f, 24f, 46f, 0.74f, 0.02f, 1.04f, 1.15f, 0.4f, 1f, true),
            button = glassEffect(2f, 12f, 26f, 0.32f, 0f, 1.05f, 1.35f, 0.32f, 0.8f, true),
            dialogButton = glassEffect(2f, 12f, 26f, 0.14f, 0f, 1.05f, 1.35f, 0.32f, 0.8f, true),
            primaryButton = glassEffect(2f, 12f, 26f, 0.70f, 0f, 1.05f, 1.25f, 0.32f, 0.8f, true),
            dialogPrimaryButton = glassEffect(2f, 12f, 26f, 0.28f, 0f, 1.05f, 1.18f, 0.32f, 0.8f, true),
            selected = glassEffect(2f, 10f, 16f, 0.14f, 0f, 1.05f, 1.15f, 0.2f, 0.8f, true),
            bottomBar = glassEffect(7f, 24f, 30f, 0.36f, 0f, 1.05f, 1.35f, 0.3f, 1f, true)
        )
    ),
    Vivid(
        label = "鲜活",
        description = "高饱和 · 清晰折射 · 无色散",
        tuning = glassPreset(
            card = glassEffect(6f, 16f, 34f, 0.36f, 0.02f, 1.04f, 1.8f, 0.3f, 0.8f, true),
            dialog = glassEffect(10f, 24f, 48f, 0.74f, 0.05f, 1.03f, 1.45f, 0.44f, 1.1f, true),
            button = glassEffect(2f, 12f, 26f, 0.28f, 0.02f, 1.04f, 1.8f, 0.36f, 0.8f, true),
            dialogButton = glassEffect(2f, 12f, 26f, 0.14f, 0.02f, 1.04f, 1.8f, 0.36f, 0.8f, true),
            primaryButton = glassEffect(2f, 12f, 26f, 0.70f, 0.02f, 1.04f, 1.55f, 0.36f, 0.8f, true),
            dialogPrimaryButton = glassEffect(2f, 12f, 26f, 0.28f, 0.02f, 1.04f, 1.45f, 0.36f, 0.8f, true),
            selected = glassEffect(2f, 10f, 16f, 0.14f, 0.02f, 1.04f, 1.4f, 0.22f, 0.8f, true),
            bottomBar = glassEffect(6f, 24f, 30f, 0.34f, 0.02f, 1.04f, 1.8f, 0.34f, 1f, true)
        )
    ),
    Clarity(
        label = "明晰",
        description = "高对比 · 强分层 · 复杂背景",
        tuning = glassPreset(
            card = glassEffect(4f, 20f, 38f, 0.46f, 0.01f, 1.18f, 1.18f, 0.28f, 0.8f, true),
            dialog = glassEffect(8f, 26f, 50f, 0.80f, 0.03f, 1.12f, 1.12f, 0.42f, 1f, true),
            button = glassEffect(2f, 14f, 28f, 0.38f, 0.01f, 1.18f, 1.2f, 0.32f, 0.8f, true),
            dialogButton = glassEffect(2f, 14f, 28f, 0.17f, 0.01f, 1.18f, 1.2f, 0.32f, 0.8f, true),
            primaryButton = glassEffect(2f, 14f, 28f, 0.78f, 0.01f, 1.12f, 1.16f, 0.32f, 0.8f, true),
            dialogPrimaryButton = glassEffect(2f, 14f, 28f, 0.32f, 0.01f, 1.12f, 1.12f, 0.32f, 0.8f, true),
            selected = glassEffect(2f, 12f, 18f, 0.18f, 0.01f, 1.15f, 1.1f, 0.24f, 0.8f, true),
            bottomBar = glassEffect(5f, 26f, 34f, 0.44f, 0.01f, 1.16f, 1.2f, 0.32f, 1f, true)
        )
    ),
    Spatial(
        label = "空间",
        description = "厚层次 · 宽折射 · 柔和高光",
        tuning = glassPreset(
            card = glassEffect(16f, 28f, 52f, 0.46f, 0.04f, 1f, 1.35f, 0.5f, 1.4f, true),
            dialog = glassEffect(22f, 32f, 60f, 0.80f, 0.06f, 0.98f, 1.2f, 0.58f, 1.6f, true),
            button = glassEffect(4f, 16f, 30f, 0.26f, 0.02f, 1.03f, 1.4f, 0.42f, 1f, true),
            dialogButton = glassEffect(4f, 16f, 30f, 0.14f, 0.02f, 1.03f, 1.4f, 0.42f, 1f, true),
            primaryButton = glassEffect(4f, 16f, 30f, 0.70f, 0.02f, 1.03f, 1.28f, 0.42f, 1f, true),
            dialogPrimaryButton = glassEffect(4f, 16f, 30f, 0.30f, 0.02f, 1.03f, 1.2f, 0.42f, 1f, true),
            selected = glassEffect(3f, 14f, 22f, 0.15f, 0.02f, 1.03f, 1.18f, 0.3f, 1f, true),
            bottomBar = glassEffect(12f, 28f, 48f, 0.40f, 0.03f, 1.02f, 1.4f, 0.5f, 1.4f, true)
        )
    ),
    SoftFrost(
        label = "柔雾",
        description = "柔和散射 · 优先可读性",
        tuning = glassPreset(
            card = glassEffect(14f, 10f, 20f, 0.56f, 0.03f, 0.96f, 1.1f, 0.16f, 0.7f, false),
            dialog = glassEffect(20f, 14f, 28f, 0.84f, 0.06f, 0.97f, 1.05f, 0.28f, 1f, true),
            button = glassEffect(8f, 8f, 16f, 0.46f, 0.03f, 0.98f, 1.12f, 0.2f, 0.7f, false),
            dialogButton = glassEffect(8f, 8f, 16f, 0.18f, 0.03f, 0.98f, 1.12f, 0.2f, 0.7f, false),
            primaryButton = glassEffect(8f, 8f, 16f, 0.8f, 0.03f, 0.98f, 1.08f, 0.2f, 0.7f, false),
            dialogPrimaryButton = glassEffect(8f, 8f, 16f, 0.34f, 0.03f, 0.98f, 1.08f, 0.2f, 0.7f, false),
            selected = glassEffect(8f, 8f, 12f, 0.18f, 0.03f, 0.98f, 1.08f, 0.16f, 0.7f, false),
            bottomBar = glassEffect(14f, 14f, 20f, 0.5f, 0.03f, 0.97f, 1.12f, 0.2f, 0.8f, true)
        )
    ),
    Prism(
        label = "棱镜",
        description = "强折射 · 鲜明高光",
        tuning = glassPreset(
            card = glassEffect(1f, 24f, 48f, 0.24f, 0.02f, 1.08f, 1.55f, 0.48f, 1.2f, true),
            dialog = glassEffect(4f, 28f, 56f, 0.68f, 0.04f, 1.06f, 1.35f, 0.56f, 1.4f, true),
            button = glassEffect(0f, 16f, 32f, 0.2f, 0.02f, 1.08f, 1.55f, 0.52f, 1.1f, true, true),
            dialogButton = glassEffect(0f, 16f, 32f, 0.13f, 0.02f, 1.08f, 1.55f, 0.52f, 1.1f, true, true),
            primaryButton = glassEffect(0f, 16f, 32f, 0.62f, 0.02f, 1.08f, 1.4f, 0.52f, 1.1f, true, true),
            dialogPrimaryButton = glassEffect(0f, 16f, 32f, 0.27f, 0.02f, 1.08f, 1.3f, 0.52f, 1.1f, true, true),
            selected = glassEffect(0f, 14f, 22f, 0.14f, 0.02f, 1.08f, 1.3f, 0.38f, 1.1f, true, true),
            bottomBar = glassEffect(2f, 28f, 42f, 0.28f, 0.02f, 1.07f, 1.5f, 0.48f, 1.2f, true, true)
        )
    )
}

fun GlassTuningPreference.matchingPreset(): GlassTuningPreset? {
    val normalized = normalized()
    return GlassTuningPreset.entries.firstOrNull { it.tuning == normalized }
}

private fun glassPreset(
    adaptiveLuminance: Boolean = false,
    card: GlassEffectOverridePreference,
    dialog: GlassEffectOverridePreference,
    button: GlassEffectOverridePreference,
    dialogButton: GlassEffectOverridePreference,
    primaryButton: GlassEffectOverridePreference,
    dialogPrimaryButton: GlassEffectOverridePreference,
    selected: GlassEffectOverridePreference,
    bottomBar: GlassEffectOverridePreference
): GlassTuningPreference = GlassTuningPreference(
    adaptiveLuminance = adaptiveLuminance,
    overrides = mapOf(
        GlassMaterialTarget.Card to card,
        GlassMaterialTarget.Dialog to dialog,
        GlassMaterialTarget.Button to button,
        GlassMaterialTarget.DialogButton to dialogButton,
        GlassMaterialTarget.PrimaryButton to primaryButton,
        GlassMaterialTarget.DialogPrimaryButton to dialogPrimaryButton,
        GlassMaterialTarget.Selected to selected,
        GlassMaterialTarget.BottomBar to bottomBar
    )
).normalized()

@Suppress("LongParameterList")
private fun glassEffect(
    blurRadius: Float,
    refractionHeight: Float,
    refractionAmount: Float,
    surfaceAlpha: Float,
    brightness: Float,
    contrast: Float,
    saturation: Float,
    highlightAlpha: Float,
    highlightWidth: Float,
    depthEffect: Boolean,
    chromaticAberration: Boolean = false
) = GlassEffectOverridePreference(
    blurRadius = blurRadius,
    refractionHeight = refractionHeight,
    refractionAmount = refractionAmount,
    surfaceAlpha = surfaceAlpha,
    brightness = brightness,
    contrast = contrast,
    saturation = saturation,
    highlightAlpha = highlightAlpha,
    highlightWidth = highlightWidth,
    depthEffect = depthEffect,
    chromaticAberration = chromaticAberration
)

private fun Float?.normalized(min: Float, max: Float): Float? {
    return this?.takeIf(Float::isFinite)?.coerceIn(min, max)
}
