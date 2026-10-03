package com.example.danmuapiapp.ui.compat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.ui.component.remoteFocusHighlight

private enum class CompatSettingSection(val key: String, val label: String, val icon: ImageVector) {
    Background("background", "后台运行", Icons.Rounded.Shield),
    Access("access", "网络访问", Icons.Rounded.Lan),
    Network("network", "下载线路", Icons.Rounded.Public),
    Appearance("appearance", "界面显示", Icons.Rounded.Contrast),
    Interface("interface", "兼容模式", Icons.Rounded.Devices),
    Update("update", "应用更新", Icons.Rounded.SystemUpdate),
    Exit("exit", "退出操作", Icons.Rounded.PowerSettingsNew)
}

@Composable
internal fun CompatSettingsPane(
    state: CompatModeUiState,
    proxy: CompatProxyPickerState,
    actions: CompatModeActions,
    busy: Boolean,
    interactionBlocked: Boolean,
    onScale: () -> Unit,
    onInterface: () -> Unit,
    onStopExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    var sectionKey by rememberSaveable { mutableStateOf(CompatSettingSection.Background.key) }
    var showNarrowDetail by rememberSaveable { mutableStateOf(false) }
    val section = CompatSettingSection.entries.firstOrNull { it.key == sectionKey } ?: CompatSettingSection.Background
    val lists = CompatSettingSection.entries.map { rememberLazyListState() }
    val savedDetails = rememberSaveableStateHolder()
    BoxWithConstraints(modifier) {
        val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        val effectiveWidth = maxWidth.value / scale
        val twoPane = effectiveWidth >= 520f
        BackHandler(enabled = !twoPane && showNarrowDetail && !interactionBlocked) { showNarrowDetail = false }
        val select: (CompatSettingSection) -> Unit = {
            sectionKey = it.key
            showNarrowDetail = true
        }
        val detail: @Composable (Modifier) -> Unit = { detailModifier ->
            savedDetails.SaveableStateProvider(section.key) {
                CompatSettingsDetail(section, state, proxy, actions, busy, onScale, onInterface, onStopExit,
                    lists[section.ordinal], detailModifier)
            }
        }
        if (twoPane) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(LocalCompatLayout.current.gap.dp)) {
                CompatSettingsCategories(section, select,
                    Modifier.width(((if (effectiveWidth >= 900f) 180f else 144f) * scale).dp).fillMaxHeight())
                detail(Modifier.weight(1f).fillMaxHeight())
            }
        } else if (showNarrowDetail) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CompatButton("设置分类", { showNarrowDetail = false }, icon = Icons.Rounded.ArrowBack)
                detail(Modifier.weight(1f).fillMaxWidth())
            }
        } else {
            CompatSettingsCategories(section, select, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun CompatSettingsCategories(selected: CompatSettingSection,
    onSelect: (CompatSettingSection) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(8.dp).focusGroup(),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("设置分类", modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            CompatSettingSection.entries.forEach { entry ->
                val active = selected == entry
                val shape = RoundedCornerShape(12.dp)
                Row(Modifier.fillMaxWidth().clip(shape)
                    .background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
                    .remoteFocusHighlight(shape).selectable(active, role = Role.Tab, onClick = { onSelect(entry) })
                    .heightIn(min = 52.dp).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val ink = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    Icon(entry.icon, null, tint = ink, modifier = Modifier.size(20.dp))
                    Text(entry.label, color = ink, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CompatSettingsDetail(
    section: CompatSettingSection,
    state: CompatModeUiState,
    proxy: CompatProxyPickerState,
    actions: CompatModeActions,
    busy: Boolean,
    onScale: () -> Unit,
    onInterface: () -> Unit,
    onStopExit: () -> Unit,
    listState: LazyListState,
    modifier: Modifier
) {
    LazyColumn(modifier, state = listState, contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(LocalCompatLayout.current.gap.dp)) {
        item(section.key) {
            when (section) {
                CompatSettingSection.Background -> CompatKeepAliveCard(state, actions)
                CompatSettingSection.Access -> CompatIpv6Card(state, busy, actions.onSetIpv6Enabled)
                CompatSettingSection.Network -> CompatNetworkCard(proxy, actions.onOpenProxyPicker)
                CompatSettingSection.Appearance -> CompatAppearanceCard(state, actions, onScale)
                CompatSettingSection.Interface -> CompatCard {
                    CompatCardTitle("兼容模式", "选择手机或遥控器使用的界面", Icons.Rounded.Devices)
                    CompatBadge("当前使用兼容界面")
                    Text("可切换为普通界面，或恢复自动选择。自动选择会在电视、盒子及无触摸屏设备上使用兼容界面。",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CompatButton("选择界面模式", onInterface, icon = Icons.Rounded.SwapHoriz,
                        tone = CompatActionTone.Primary)
                    Text("切换界面不会改变服务、工作目录和运行方式；两种界面的 DPI 分别保存。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                CompatSettingSection.Update -> CompatAppUpdateCard(state.appUpdate, actions)
                CompatSettingSection.Exit -> CompatCard {
                    CompatCardTitle("后台与退出", "关闭界面时，服务可以继续运行", Icons.Rounded.PowerSettingsNew)
                    CompatActions {
                        CompatButton("退到后台", actions.onExitToBackground, icon = Icons.Rounded.Minimize)
                        CompatButton("停止并退出", onStopExit, icon = Icons.Rounded.Stop,
                            tone = CompatActionTone.Danger, enabled = !busy)
                    }
                }
            }
        }
        if (state.isOperating || state.downloadProgress.inProgress) item("operation") { CompatOperationCard(state) }
    }
}
