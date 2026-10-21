package com.example.danmuapiapp.ui.screen.home

import com.example.danmuapiapp.ui.component.AppDialog
import com.example.danmuapiapp.ui.component.AppDialogStyle
import com.example.danmuapiapp.ui.component.AppDialogTone
import com.example.danmuapiapp.ui.component.AppDialogOption
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.ServiceStatus
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton
import com.example.danmuapiapp.domain.model.RuntimeState

@Composable
internal fun HomeRuntimeInfoDialogHost(
    viewModel: HomeViewModel,
    state: RuntimeState,
    presentedStatus: ServiceStatus,
    uptimeText: String,
    currentVariantLabel: String,
    tunnelUrl: String,
    maskedToken: String,
    tokenVisible: Boolean,
    activeOverlay: HomeOverlay?,
    onDismissOverlay: () -> Unit,
) {
    if (activeOverlay == HomeOverlay.RuntimeInfo) {
        ServiceRuntimeInfoDialog(
            status = presentedStatus,
            uptime = uptimeText,
            runMode = state.runMode,
            variantLabel = currentVariantLabel,
            port = state.port,
            pid = state.pid,
            localUrl = state.localUrl,
            lanUrl = state.lanUrl,
            lanIpv6Url = state.lanIpv6Url,
            tunnelUrl = tunnelUrl,
            listenMode = state.listenMode,
            token = state.token,
            maskedToken = maskedToken,
            tokenVisible = tokenVisible,
            onDismiss = { onDismissOverlay() }
        )
    }
}

@Composable
internal fun HomeQuickPortDialogHost(
    viewModel: HomeViewModel,
    state: RuntimeState,
    isBusy: Boolean,
    quickPortText: String,
    quickPortError: String?,
    activeOverlay: HomeOverlay?,
    onDismissOverlay: () -> Unit,
    onQuickPortTextChange: (String) -> Unit,
    onQuickPortErrorChange: (String?) -> Unit,
) {
    if (activeOverlay == HomeOverlay.QuickPort) {
        QuickPortDialog(
            isBusy = isBusy,
            status = state.status,
            runMode = state.runMode,
            currentPort = state.port,
            quickPortText = quickPortText,
            quickPortError = quickPortError,
            onPortTextChange = {
                onQuickPortTextChange(it)
                onQuickPortErrorChange(null)
            },
            onDismiss = { onDismissOverlay() },
            onApply = { port ->
                viewModel.applyPortQuick(port)
                onDismissOverlay()
            },
            onPortError = { onQuickPortErrorChange(it) }
        )
    }
}

@Composable
internal fun HomeQuickTokenDialogHost(
    viewModel: HomeViewModel,
    state: RuntimeState,
    isBusy: Boolean,
    quickTokenText: String,
    quickTokenError: String?,
    activeOverlay: HomeOverlay?,
    onDismissOverlay: () -> Unit,
    onQuickTokenTextChange: (String) -> Unit,
    onQuickTokenErrorChange: (String?) -> Unit,
) {
    if (activeOverlay == HomeOverlay.QuickToken) {
        QuickTokenDialog(
            isBusy = isBusy,
            status = state.status,
            currentToken = state.token,
            quickTokenText = quickTokenText,
            quickTokenError = quickTokenError,
            onTokenTextChange = {
                onQuickTokenTextChange(it)
                onQuickTokenErrorChange(null)
            },
            onDismiss = { onDismissOverlay() },
            onApply = { token ->
                viewModel.applyTokenQuick(token)
                onDismissOverlay()
            },
            onTokenError = { onQuickTokenErrorChange(it) }
        )
    }
}

@Composable
internal fun HomeCoreUpdateConfirmDialogHost(
    viewModel: HomeViewModel,
    currentVariantLabel: String,
    currentCoreVersion: String?,
    availableVersion: String?,
    activeOverlay: HomeOverlay?,
    onDismissOverlay: () -> Unit,
) {
    if (activeOverlay == HomeOverlay.CoreUpdateConfirm) {
        CoreUpdateConfirmDialog(
            variantLabel = currentVariantLabel,
            currentVersion = currentCoreVersion,
            availableVersion = availableVersion,
            isChecking = viewModel.isCheckingCoreUpdate,
            resultMessage = viewModel.coreUpdateCheckDialogMessage,
            resultIsError = viewModel.coreUpdateCheckDialogIsError,
            onDismiss = {
                onDismissOverlay()
                viewModel.resetCoreUpdateCheckDialogState()
            },
            onConfirm = {
                viewModel.quickCheckCurrentCoreUpdate()
                if (viewModel.showProxyPickerDialog) {
                    onDismissOverlay()
                    viewModel.resetCoreUpdateCheckDialogState()
                }
            }
        )
    }
}

@Composable
internal fun HomeRunModePickerDialogHost(
    viewModel: HomeViewModel,
    state: RuntimeState,
    isHeroChipBusy: Boolean,
    pendingRunModeTarget: RunMode?,
    activeOverlay: HomeOverlay?,
    onDismissOverlay: () -> Unit,
    onPendingRunModeTargetChange: (RunMode?) -> Unit,
) {
    if (activeOverlay == HomeOverlay.RunModePicker) {
        val availableModes = RunMode.entries.filter { it != state.runMode }
        AppDialog(
            onDismissRequest = {
                onDismissOverlay()
                onPendingRunModeTargetChange(null)
            },
            style = AppDialogStyle.Selection,
            tone = AppDialogTone.Brand,
            icon = { Icon(Icons.Rounded.PowerSettingsNew, null) },
            title = { Text("切换运行模式") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "当前模式：${state.runMode.label}，请选择要切换到的模式。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    availableModes.forEach { mode ->
                        val selected = pendingRunModeTarget == mode
                        AppDialogOption(
                            selected = selected,
                            onClick = { onPendingRunModeTargetChange(mode) }
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                                if (mode.requiresRoot) {
                                    Text(
                                        "需要 Root 权限",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                AppGlassButton(
                    enabled = !isHeroChipBusy && pendingRunModeTarget != null,
                    onClick = {
                        val target = pendingRunModeTarget ?: return@AppGlassButton
                        onDismissOverlay()
                        onPendingRunModeTargetChange(null)
                        viewModel.switchRunModeQuick(target)
                    }
                ) {
                    Text("确认切换")
                }
            },
            dismissButton = {
                AppGlassButton(onClick = {
                    onDismissOverlay()
                    onPendingRunModeTargetChange(null)
                }) {
                    Text("取消")
                }
            }
        )
    }
}

