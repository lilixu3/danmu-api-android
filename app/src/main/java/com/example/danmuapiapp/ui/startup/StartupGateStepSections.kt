package com.example.danmuapiapp.ui.startup

import com.example.danmuapiapp.ui.component.AppGlassSurface
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CoreDownloadProgress
import com.example.danmuapiapp.domain.model.CoreInfo
import com.example.danmuapiapp.domain.model.CoreDependencyRepairRequest
import com.example.danmuapiapp.domain.model.CoreVariantDisplayNames
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.RuntimeState
import com.example.danmuapiapp.ui.common.CustomCoreSettingsForm
import com.example.danmuapiapp.ui.common.rememberCustomCoreSettingsFormState
import com.example.danmuapiapp.ui.component.GradientButton
import com.example.danmuapiapp.ui.component.SettingsHintCard
import com.example.danmuapiapp.ui.screen.home.NormalModeKeepAliveGuideNavigator

@Composable
internal fun RunModeStepContent(
    compact: Boolean,
    currentMode: RunMode,
    selectedMode: RunMode,
    isRunModeSwitching: Boolean,
    onSelectMode: (RunMode) -> Unit,
    onConfirm: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp)
    ) {
        SetupChoiceCard(
            icon = Icons.Rounded.Tune,
            accent = MaterialTheme.colorScheme.primary,
            title = "普通模式",
            summary = "兼容性更高，适合大多数设备。后面会继续提醒你把通知和电池优化补齐。",
            badge = when {
                selectedMode == RunMode.Normal -> "已选中"
                currentMode == RunMode.Normal -> "当前"
                else -> "推荐"
            },
            selected = selectedMode == RunMode.Normal,
            onClick = { onSelectMode(RunMode.Normal) }
        )

        SetupChoiceCard(
            icon = Icons.Rounded.Verified,
            accent = MaterialTheme.colorScheme.tertiary,
            title = "Root 模式",
            summary = "后台限制更少，需要 Root 授权。兼容 Magisk / KernelSU / APatch。",
            badge = when {
                selectedMode == RunMode.Root -> "已选中"
                currentMode == RunMode.Root -> "当前"
                else -> "需授权"
            },
            selected = selectedMode == RunMode.Root,
            onClick = { onSelectMode(RunMode.Root) }
        )

        SettingsHintCard(
            text = if (selectedMode == RunMode.Root && currentMode != RunMode.Root) {
                "点击下方后会弹出系统 Root 授权，允许后才会真正切换到 Root 模式。"
            } else {
                "这里只确定运行方式，不会在这个页面直接启动或停止服务。"
            }
        )

        Spacer(modifier = Modifier.weight(1f))

        GradientButton(
            text = when {
                isRunModeSwitching && selectedMode == RunMode.Root -> "正在申请 Root..."
                isRunModeSwitching -> "正在切换..."
                selectedMode == currentMode && selectedMode == RunMode.Root -> "继续使用 Root 模式"
                selectedMode == currentMode -> "继续使用普通模式"
                selectedMode == RunMode.Root -> "申请 Root 并切换"
                else -> "切换到普通模式"
            },
            onClick = onConfirm,
            enabled = isRunModeSwitching.not(),
            modifier = Modifier.fillMaxWidth(),
            colors = listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.secondary
            )
        )
    }
}

@Composable
internal fun CoreStepContent(
    compact: Boolean,
    runtimeState: RuntimeState,
    coreInfoList: List<CoreInfo>,
    isCoreInfoLoading: Boolean,
    downloadProgress: CoreDownloadProgress,
    selectedVariant: ApiVariant,
    coreDisplayNames: CoreVariantDisplayNames,
    customRepo: String,
    customRepoBranch: String,
    customRepoConfigured: Boolean,
    onSelectVariant: (ApiVariant) -> Unit,
    onUseSelectedVariant: () -> Unit,
    onInstallSelectedVariant: () -> Unit,
    onSaveCustomSettings: (displayName: String, repo: String, branch: String) -> Boolean,
    onSkipForNow: () -> Unit
) {
    val stableInfo = coreInfoList.find { it.variant == ApiVariant.Stable }
    val devInfo = coreInfoList.find { it.variant == ApiVariant.Dev }
    val customInfo = coreInfoList.find { it.variant == ApiVariant.Custom }
    val selectedInfo = coreInfoList.find { it.variant == selectedVariant }
    val selectedInstalled = selectedInfo?.isInstalled == true
    val downloadForSelected = downloadProgress.inProgress && downloadProgress.variant == selectedVariant
    val showCustomOption = true
    val stableLabel = coreDisplayNames.resolve(ApiVariant.Stable)
    val devLabel = coreDisplayNames.resolve(ApiVariant.Dev)
    val customLabel = coreDisplayNames.resolve(ApiVariant.Custom)
    val selectedVariantLabel = coreDisplayNames.resolve(selectedVariant)
    val customFormState = rememberCustomCoreSettingsFormState(
        initialDisplayName = coreDisplayNames.custom,
        initialRepo = customRepo,
        initialBranch = customRepoBranch
    )
    val customSettingsDirty = selectedVariant == ApiVariant.Custom && customFormState.isDirty
    val canSaveCustomSettings = customFormState.canSaveConfig
    val needsCustomRepo = selectedVariant == ApiVariant.Custom && customRepoConfigured.not()
    val canInstallSelectedCustom = if (selectedVariant == ApiVariant.Custom) {
        when {
            needsCustomRepo -> customFormState.canInstall
            customSettingsDirty -> customFormState.canInstall
            else -> true
        }
    } else {
        true
    }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp)
        ) {
            SetupChoiceCard(
                icon = Icons.Rounded.CheckCircle,
                accent = MaterialTheme.colorScheme.primary,
                title = stableLabel,
                summary = "更适合长期使用，优先推荐新设备第一次先下这个版本。",
                badge = coreBadgeText(
                    variant = ApiVariant.Stable,
                    runtimeState = runtimeState,
                    info = stableInfo,
                    selectedVariant = selectedVariant
                ),
                selected = selectedVariant == ApiVariant.Stable,
                onClick = { onSelectVariant(ApiVariant.Stable) }
            )

            SetupChoiceCard(
                icon = Icons.Rounded.DownloadForOffline,
                accent = MaterialTheme.colorScheme.secondary,
                title = devLabel,
                summary = "更新更快，适合愿意第一时间体验新特性的用户。",
                badge = coreBadgeText(
                    variant = ApiVariant.Dev,
                    runtimeState = runtimeState,
                    info = devInfo,
                    selectedVariant = selectedVariant
                ),
                selected = selectedVariant == ApiVariant.Dev,
                onClick = { onSelectVariant(ApiVariant.Dev) }
            )

            if (showCustomOption) {
                SetupChoiceCard(
                    icon = Icons.Rounded.Tune,
                    accent = MaterialTheme.colorScheme.tertiary,
                    title = customLabel,
                    summary = "适合已经导入自定义仓库或本地核心的场景，首次使用前请先确认核心已准备好。",
                    badge = coreBadgeText(
                        variant = ApiVariant.Custom,
                        runtimeState = runtimeState,
                        info = customInfo,
                        selectedVariant = selectedVariant
                    ),
                    selected = selectedVariant == ApiVariant.Custom,
                    onClick = { onSelectVariant(ApiVariant.Custom) }
                )
            }

            if (selectedVariant == ApiVariant.Custom) {
                AppGlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "自定义核心设置",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        CustomCoreSettingsForm(
                            state = customFormState,
                            displayNamePlaceholder = customLabel
                        )
                        if (customSettingsDirty) {
                            AppGlassButton(
                                onClick = {
                                    val input = customFormState.toInput()
                                    onSaveCustomSettings(
                                        input.displayName,
                                        input.repo,
                                        input.branch
                                    )
                                },
                                enabled = canSaveCustomSettings,
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("仅保存配置")
                            }
                        }
                    }
                }
            }

            if (downloadProgress.inProgress) {
                DownloadProgressCard(
                    progress = downloadProgress,
                    compact = compact
                )
            } else {
                SettingsHintCard(
                    text = if (needsCustomRepo) {
                        "先在这里填好 $customLabel 的仓库和分支，再点下载即可。"
                    } else if (runtimeState.runMode == RunMode.Root) {
                        "Root 模式下准备完核心后，会直接进入首页，不再显示权限步骤。"
                    } else {
                        "首次下载前会先让你选 GitHub 线路。这里只准备核心，不会在这里启动服务。"
                    }
                )
            }

            Spacer(modifier = Modifier.height(if (compact) 12.dp else 16.dp))
        }

        GradientButton(
            text = when {
                isCoreInfoLoading -> "正在读取核心信息..."
                downloadForSelected -> "正在下载$selectedVariantLabel..."
                selectedVariant == ApiVariant.Custom && (needsCustomRepo || customSettingsDirty) ->
                    "保存并下载$customLabel"
                selectedInstalled -> "使用$selectedVariantLabel"
                else -> "下载$selectedVariantLabel"
            },
            onClick = {
                if (selectedVariant == ApiVariant.Custom && (needsCustomRepo || customSettingsDirty)) {
                    val input = customFormState.toInput()
                    val saved = onSaveCustomSettings(
                        input.displayName,
                        input.repo,
                        input.branch
                    )
                    if (saved) {
                        onInstallSelectedVariant()
                    }
                } else if (selectedInstalled) {
                    onUseSelectedVariant()
                } else {
                    onInstallSelectedVariant()
                }
            },
            enabled = isCoreInfoLoading.not() &&
                downloadProgress.inProgress.not() &&
                canInstallSelectedCustom,
            modifier = Modifier.fillMaxWidth(),
            colors = listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.secondary
            )
        )

        AppGlassButton(
            onClick = onSkipForNow,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("先去首页处理")
        }
    }
}

@Composable
internal fun DependencyStepContent(
    compact: Boolean,
    request: CoreDependencyRepairRequest?,
    downloadProgress: CoreDownloadProgress,
    isRepairing: Boolean,
    onRepair: () -> Unit,
    onSkipForNow: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp)
    ) {
        SetupNoticeCard(
            title = "核心文件已下载，运行依赖尚未齐全",
            summary = "依赖修复只处理当前候选核心；完成校验后会自动继续安装。",
            compact = compact
        )

        AppGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
            )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "待补充依赖",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = request?.missingDependencies
                        ?.joinToString("\n") { "• $it" }
                        .orEmpty()
                        .ifBlank { "正在重新检测依赖..." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        if (downloadProgress.inProgress || isRepairing) {
            DownloadProgressCard(
                progress = downloadProgress.takeIf { it.inProgress }
                    ?: CoreDownloadProgress(
                        inProgress = true,
                        actionLabel = "修复依赖",
                        stageText = "正在校验运行时依赖"
                    ),
                compact = compact
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        GradientButton(
            text = if (isRepairing) "正在修复依赖..." else "修复依赖",
            onClick = onRepair,
            enabled = request != null && !isRepairing,
            modifier = Modifier.fillMaxWidth(),
            colors = listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.secondary
            )
        )

        AppGlassButton(
            onClick = onSkipForNow,
            enabled = !isRepairing,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("先去首页处理")
        }
    }
}

@Composable
internal fun PermissionStepContent(
    compact: Boolean,
    permissionState: StartupPermissionState,
    notificationAction: NotificationAction?,
    localNetworkAction: LocalNetworkPermissionAction?,
    onOpenLocalNetworkPermission: () -> Unit,
    onOpenNotificationPermission: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onContinueHome: () -> Unit
) {
    val showLocalNetwork = permissionState.localNetworkReady.not()
    val showNotification = permissionState.notificationReady.not()
    val showBattery = permissionState.batteryRequired && permissionState.batteryReady.not()
    val itemSpacing = if (compact) 10.dp else 12.dp

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(itemSpacing)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(itemSpacing)
        ) {
            SetupNoticeCard(
                title = when {
                    showLocalNetwork -> "开启局域网访问，其他设备才能连接"
                    showNotification && showBattery -> "把这两项补齐后，普通模式会更省心"
                    showNotification -> "建议把通知权限补齐"
                    else -> "建议把电池优化设为不受限制"
                },
                summary = when {
                    showLocalNetwork -> "Android 17 会默认拦截局域网入站连接，授权后同一网络中的设备才能访问弹幕服务。"
                    showNotification && showBattery -> "启动、停止和运行状态会更清楚，后台被系统清理的概率也会更低。"
                    showNotification -> "这样更容易确认服务是不是已经真的启动成功。"
                    else -> "这样普通模式在后台会更稳定。"
                },
                compact = compact
            )

            if (showLocalNetwork) {
                PermissionStepCard(
                    compact = compact,
                    icon = Icons.Rounded.Wifi,
                    accent = MaterialTheme.colorScheme.tertiary,
                    title = "局域网访问",
                    summary = "允许同一 Wi-Fi 或有线网络中的设备访问本机弹幕服务。",
                    stateLabel = if (localNetworkAction == LocalNetworkPermissionAction.Settings) {
                        "前往设置"
                    } else {
                        "必须授权"
                    },
                    stateAccent = MaterialTheme.colorScheme.tertiary,
                    buttonText = if (localNetworkAction == LocalNetworkPermissionAction.Settings) {
                        "前往应用设置"
                    } else {
                        "允许局域网访问"
                    },
                    helper = if (localNetworkAction == LocalNetworkPermissionAction.Settings) {
                        "如果之前拒绝或系统不再弹窗，请在应用权限中允许局域网访问。"
                    } else {
                        "拒绝后服务仍可在本机使用，但其他设备访问时会连接超时。"
                    },
                    onClick = onOpenLocalNetworkPermission
                )
            }

            if (showLocalNetwork && (showNotification || showBattery)) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)
                )
            }

            if (showNotification) {
                PermissionStepCard(
                    compact = compact,
                    icon = Icons.Rounded.NotificationsActive,
                    accent = MaterialTheme.colorScheme.primary,
                    title = "通知权限",
                    summary = "建议开启，方便确认服务是否真的已经启动。",
                    stateLabel = if (notificationAction == NotificationAction.Settings) "前往设置" else "待开启",
                    stateAccent = MaterialTheme.colorScheme.primary,
                    buttonText = if (notificationAction == NotificationAction.Settings) {
                        "前往设置"
                    } else {
                        "立即开启"
                    },
                    helper = if (notificationAction == NotificationAction.Settings) {
                        "如果之前点过拒绝，可以到系统设置里重新打开。"
                    } else {
                        "授权后回到应用，这里的状态会自动刷新。"
                    },
                    onClick = onOpenNotificationPermission
                )
            }

            if (showNotification && showBattery) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)
                )
            }

            if (showBattery) {
                PermissionStepCard(
                    compact = compact,
                    icon = Icons.Rounded.BatterySaver,
                    accent = MaterialTheme.colorScheme.secondary,
                    title = "关闭电池优化",
                    summary = "建议改为不受限制，减少后台被系统清理。",
                    stateLabel = "建议处理",
                    stateAccent = MaterialTheme.colorScheme.secondary,
                    buttonText = "前往设置",
                    helper = NormalModeKeepAliveGuideNavigator.batterySettingsHint(),
                    onClick = onOpenBatterySettings
                )
            }

            SettingsHintCard(
                text = if (showLocalNetwork) {
                    "未授权可能影响其他设备连接；无法申请或不授予时会跳过启动检查，可稍后在设置中处理。"
                } else {
                    "通知和电池项只影响提示与后台稳定，不影响你现在直接进入首页。"
                }
            )
        }

        GradientButton(
            text = if (showLocalNetwork) "仅本机使用并进入首页" else "进入首页",
            onClick = onContinueHome,
            enabled = true,
            modifier = Modifier.fillMaxWidth(),
            colors = listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.secondary
            )
        )
    }
}

private fun coreBadgeText(
    variant: ApiVariant,
    runtimeState: RuntimeState,
    info: CoreInfo?,
    selectedVariant: ApiVariant
): String {
    return when {
        selectedVariant == variant -> "已选中"
        runtimeState.variant == variant -> "当前"
        info?.isInstalled == true -> "已安装"
        variant == ApiVariant.Stable -> "推荐"
        else -> "可选"
    }
}
