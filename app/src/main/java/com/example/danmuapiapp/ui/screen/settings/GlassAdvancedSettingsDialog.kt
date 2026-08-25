package com.example.danmuapiapp.ui.screen.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.domain.model.GlassTuningPreference
import com.example.danmuapiapp.ui.component.AppGlassSurface
import com.example.danmuapiapp.ui.component.AppGlassSurfaceRole
import com.example.danmuapiapp.ui.component.AppModalPanel
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton
import com.example.danmuapiapp.ui.component.liquid.AppGlassFilterChip
import com.example.danmuapiapp.ui.component.liquid.AppGlassIconButton
import com.example.danmuapiapp.ui.component.liquid.AppGlassPrimaryButton
import com.example.danmuapiapp.ui.component.liquid.AppLiquidButton
import com.example.danmuapiapp.ui.theme.GlassEffectOverride
import com.example.danmuapiapp.ui.theme.GlassEffectStyle
import com.example.danmuapiapp.ui.theme.GlassMaterialRole
import com.example.danmuapiapp.ui.theme.LocalAppDialogContext
import com.example.danmuapiapp.ui.theme.LocalGlassBackgroundBackdrop
import com.example.danmuapiapp.ui.theme.LocalGlassMaterial
import com.example.danmuapiapp.ui.theme.LocalGlassMaterialTuning
import com.example.danmuapiapp.ui.theme.glassEffectStyle
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.shapes.Capsule
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun GlassAdvancedSettingsDialog(
    onDismissRequest: () -> Unit,
    onSave: (GlassTuningPreference) -> Unit
) {
    val tuning = LocalGlassMaterialTuning.current
    val spec = LocalGlassMaterial.current
    var selectedRole by remember { mutableStateOf(GlassMaterialRole.Card) }
    val style = spec.styleFor(selectedRole)

    fun persist() {
        onSave(tuning.toPreference())
    }

    fun dismiss() {
        persist()
        onDismissRequest()
    }

    AppModalPanel(
        onDismissRequest = ::dismiss,
        maxWidth = 680.dp,
        expanded = true,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val compactHeight = maxHeight < 620.dp
            val previewHeight = if (compactHeight) 132.dp else 176.dp
            Column(modifier = Modifier.fillMaxSize()) {
                GlassEditorHeader(onClose = ::dismiss)
                GlassRoleSelector(
                    selectedRole = selectedRole,
                    onSelected = { selectedRole = it }
                )
                GlassLivePreview(
                    role = selectedRole,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(previewHeight)
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    GlassParameterHeader(
                        role = selectedRole,
                        customized = tuning.overrideFor(selectedRole) != null,
                        onReset = {
                            tuning.setOverride(selectedRole, null)
                            persist()
                        }
                    )
                    GlassParameterControls(
                        style = style,
                        override = tuning.overrideFor(selectedRole),
                        onChange = { tuning.setOverride(selectedRole, it) },
                        onCommit = ::persist
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f))
                GlassEditorActions(
                    canReset = !tuning.toPreference().isDefault,
                    onResetAll = {
                        tuning.reset()
                        persist()
                    },
                    onDone = ::dismiss
                )
            }
        }
    }
}

@Composable
private fun GlassEditorHeader(onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 16.dp, end = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppGlassSurface(
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            contentColor = MaterialTheme.colorScheme.primary,
            materialRole = GlassMaterialRole.DialogButton
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Tune, contentDescription = null)
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "高级玻璃设置",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "精细材质参数",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AppGlassIconButton(
            onClick = onClose,
            size = 40.dp,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "关闭")
        }
    }
}

@Composable
private fun GlassRoleSelector(
    selectedRole: GlassMaterialRole,
    onSelected: (GlassMaterialRole) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GlassMaterialRole.entries.forEach { role ->
            AppGlassFilterChip(
                selected = selectedRole == role,
                onClick = { onSelected(role) },
                label = { Text(role.label()) }
            )
        }
    }
}

@Composable
private fun GlassLivePreview(
    role: GlassMaterialRole,
    modifier: Modifier = Modifier
) {
    val backgroundBackdrop = LocalGlassBackgroundBackdrop.current
    val stageShape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .clip(stageShape)
            .then(
                if (backgroundBackdrop != null) {
                    Modifier.drawPlainBackdrop(
                        backdrop = backgroundBackdrop,
                        shape = { stageShape },
                        effects = {}
                    )
                } else {
                    Modifier.background(MaterialTheme.colorScheme.background)
                }
            )
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.44f)),
                stageShape
            )
    ) {
        CompositionLocalProvider(
            LocalGlassBackgroundBackdrop provides backgroundBackdrop,
            LocalAppDialogContext provides false
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GlassRoleSample(role)
            }
        }
    }
}

@Composable
private fun GlassRoleSample(role: GlassMaterialRole) {
    val colors = MaterialTheme.colorScheme
    val style = glassEffectStyle(role)
    when (role) {
        GlassMaterialRole.Button,
        GlassMaterialRole.DialogButton,
        GlassMaterialRole.PrimaryButton,
        GlassMaterialRole.DialogPrimaryButton -> {
            val emphasized = role == GlassMaterialRole.PrimaryButton ||
                role == GlassMaterialRole.DialogPrimaryButton
            AppLiquidButton(
                onClick = {},
                modifier = Modifier.widthIn(min = 148.dp),
                shape = RoundedCornerShape(14.dp),
                tint = if (emphasized) colors.primary.copy(alpha = 0.12f) else Color.Unspecified,
                surfaceColor = (if (emphasized) colors.primaryContainer else colors.surfaceContainerHighest)
                    .copy(alpha = style.surfaceAlpha),
                materialRole = role,
                contentColor = if (emphasized) colors.onPrimaryContainer else colors.onSurface
            ) {
                Text(role.label(), fontWeight = FontWeight.Medium)
            }
        }

        GlassMaterialRole.Selected -> AppGlassSurface(
            modifier = Modifier
                .widthIn(min = 172.dp)
                .height(52.dp),
            shape = Capsule(),
            color = colors.primary,
            contentColor = colors.onPrimaryContainer,
            materialRole = role
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("已选中", fontWeight = FontWeight.SemiBold)
            }
        }

        GlassMaterialRole.BottomBar -> AppGlassSurface(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .height(58.dp),
            shape = Capsule(),
            color = colors.surfaceContainerLowest,
            materialRole = role
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("首页", fontWeight = FontWeight.SemiBold)
                Text("核心")
                Text("设置")
            }
        }

        GlassMaterialRole.Card,
        GlassMaterialRole.Dialog -> AppGlassSurface(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .height(76.dp),
            shape = RoundedCornerShape(if (role == GlassMaterialRole.Dialog) 20.dp else 16.dp),
            color = colors.surfaceContainer,
            materialRole = role,
            role = if (role == GlassMaterialRole.Dialog) {
                AppGlassSurfaceRole.Dialog
            } else {
                AppGlassSurfaceRole.Card
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(role.label(), fontWeight = FontWeight.SemiBold)
                Text(
                    text = "Material preview",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GlassParameterHeader(
    role: GlassMaterialRole,
    customized: Boolean,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = role.label(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (customized) "自定义参数" else "默认参数",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AppGlassButton(onClick = onReset, enabled = customized) {
            Icon(Icons.Rounded.RestartAlt, contentDescription = null)
            Text("重置当前")
        }
    }
}

@Composable
private fun GlassParameterControls(
    style: GlassEffectStyle,
    override: GlassEffectOverride?,
    onChange: (GlassEffectOverride) -> Unit,
    onCommit: () -> Unit
) {
    GlassSlider(
        label = "模糊半径",
        value = style.blurRadius.value,
        range = 0f..40f,
        valueText = { it.dpText() },
        emphasized = true,
        onValueChange = {
            onChange((override ?: GlassEffectOverride()).copy(blurRadius = it.dp))
        },
        onValueChangeFinished = onCommit
    )
    GlassSlider(
        label = "表面透明度",
        value = style.surfaceAlpha,
        range = 0f..1f,
        valueText = { "${(it * 100f).roundToInt()}%" },
        emphasized = true,
        onValueChange = {
            onChange((override ?: GlassEffectOverride()).copy(surfaceAlpha = it))
        },
        onValueChangeFinished = onCommit
    )
    GlassSlider(
        label = "折射强度",
        value = style.refractionAmount.value,
        range = 0f..64f,
        valueText = { it.dpText() },
        onValueChange = {
            onChange((override ?: GlassEffectOverride()).copy(refractionAmount = it.dp))
        },
        onValueChangeFinished = onCommit
    )
    GlassSlider(
        label = "折射高度",
        value = style.refractionHeight.value,
        range = 0f..48f,
        valueText = { it.dpText() },
        onValueChange = {
            onChange((override ?: GlassEffectOverride()).copy(refractionHeight = it.dp))
        },
        onValueChangeFinished = onCommit
    )

    HorizontalDivider(
        modifier = Modifier.padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f)
    )
    Text(
        text = "细节微调",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
    GlassSlider(
        label = "亮度",
        value = style.brightness,
        range = -0.25f..0.25f,
        valueText = { String.format(Locale.US, "%+.2f", it) },
        onValueChange = {
            onChange((override ?: GlassEffectOverride()).copy(brightness = it))
        },
        onValueChangeFinished = onCommit
    )
    GlassSlider(
        label = "对比度",
        value = style.contrast,
        range = 0.5f..1.8f,
        valueText = { String.format(Locale.US, "%.2f", it) },
        onValueChange = {
            onChange((override ?: GlassEffectOverride()).copy(contrast = it))
        },
        onValueChangeFinished = onCommit
    )
    GlassSlider(
        label = "饱和度",
        value = style.saturation,
        range = 0f..2.2f,
        valueText = { String.format(Locale.US, "%.2f", it) },
        onValueChange = {
            onChange((override ?: GlassEffectOverride()).copy(saturation = it))
        },
        onValueChangeFinished = onCommit
    )
    GlassSlider(
        label = "边缘高光",
        value = style.highlightAlpha,
        range = 0f..1f,
        valueText = { "${(it * 100f).roundToInt()}%" },
        onValueChange = {
            onChange((override ?: GlassEffectOverride()).copy(highlightAlpha = it))
        },
        onValueChangeFinished = onCommit
    )
    GlassSlider(
        label = "高光宽度",
        value = style.highlightWidth.value,
        range = 0.1f..4f,
        valueText = { it.dpText() },
        onValueChange = {
            onChange((override ?: GlassEffectOverride()).copy(highlightWidth = it.dp))
        },
        onValueChangeFinished = onCommit
    )
    GlassToggle(
        label = "深度折射",
        checked = style.depthEffect,
        onCheckedChange = {
            onChange((override ?: GlassEffectOverride()).copy(depthEffect = it))
            onCommit()
        }
    )
    GlassToggle(
        label = "色差折射",
        checked = style.chromaticAberration,
        onCheckedChange = {
            onChange((override ?: GlassEffectOverride()).copy(chromaticAberration = it))
            onCommit()
        }
    )
}

@Composable
private fun GlassSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: (Float) -> String,
    emphasized: Boolean = false,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = if (emphasized) {
                    MaterialTheme.typography.titleSmall
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal
            )
            Text(
                text = valueText(value),
                style = if (emphasized) {
                    MaterialTheme.typography.titleSmall
                } else {
                    MaterialTheme.typography.labelMedium
                },
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = range
        )
    }
}

@Composable
private fun GlassToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun GlassEditorActions(
    canReset: Boolean,
    onResetAll: () -> Unit,
    onDone: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppGlassButton(onClick = onResetAll, enabled = canReset) {
            Icon(Icons.Rounded.RestartAlt, contentDescription = null)
            Text("恢复全部")
        }
        AppGlassPrimaryButton(onClick = onDone) {
            Text("完成")
        }
    }
}

private fun Float.dpText(): String = String.format(Locale.US, "%.1f dp", this)

private fun GlassMaterialRole.label(): String = when (this) {
    GlassMaterialRole.Card -> "卡片"
    GlassMaterialRole.Dialog -> "弹窗"
    GlassMaterialRole.Button -> "按钮"
    GlassMaterialRole.DialogButton -> "弹窗按钮"
    GlassMaterialRole.PrimaryButton -> "主按钮"
    GlassMaterialRole.DialogPrimaryButton -> "弹窗主按钮"
    GlassMaterialRole.Selected -> "列表选中"
    GlassMaterialRole.BottomBar -> "底部栏"
}
