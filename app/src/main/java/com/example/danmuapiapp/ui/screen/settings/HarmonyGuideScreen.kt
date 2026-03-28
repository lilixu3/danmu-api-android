package com.example.danmuapiapp.ui.screen.settings

import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.danmuapiapp.ui.component.SettingsGroup
import com.example.danmuapiapp.ui.component.SettingsHintCard
import com.example.danmuapiapp.ui.component.SettingsItem
import com.example.danmuapiapp.ui.component.SettingsDivider
import com.example.danmuapiapp.ui.component.SettingsPageHeader

@Composable
fun HarmonyGuideScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.operationMessage) {
        viewModel.operationMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingsPageHeader(
                title = "鸿蒙后台权限引导",
                subtitle = "鸿蒙兼容环境下后台常驻受系统限制，需手动放行相关权限以提升服务恢复概率",
                onBack = onBack
            )

            SettingsGroup(title = "操作建议") {
                SettingsItem(
                    title = "通知权限",
                    subtitle = "允许前台服务通知，通知被关闭会导致服务无法正常拉起",
                    icon = Icons.Rounded.Notifications,
                    onClick = {
                        val packageUri = "package:${context.packageName}".toUri()
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        }.onFailure {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = packageUri
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            }
                        }
                    },
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "前往设置",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
                SettingsDivider()
                SettingsItem(
                    title = "电池与启动管理",
                    subtitle = "关闭自动管理，允许自启动、关联启动和后台活动",
                    icon = Icons.Rounded.BatterySaver,
                    onClick = {
                        val packageUri = "package:${context.packageName}".toUri()
                        val candidates = listOf(
                            Intent("android.settings.APP_BATTERY_SETTINGS").apply { data = packageUri },
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = packageUri }
                        )
                        val opened = candidates.any { intent ->
                            runCatching {
                                context.startActivity(intent.apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                                true
                            }.getOrDefault(false)
                        }
                        if (!opened) {
                            viewModel.postMessage("无法跳转，请在系统设置中手动操作")
                        }
                    },
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "前往设置",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
                SettingsDivider()
                SettingsItem(
                    title = "应用详情页",
                    subtitle = "查看完整权限列表，确认所有后台相关权限均已放行",
                    icon = Icons.Rounded.Apps,
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = "package:${context.packageName}".toUri()
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        }.onFailure {
                            viewModel.postMessage("无法打开应用详情页")
                        }
                    },
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "前往设置",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
            }

            SettingsGroup(title = "手动操作路径") {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val manualText = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Medium)) {
                            append("1. 应用启动管理（如界面存在）\n")
                        }
                        append("   常见路径：设置 → 应用和服务 / 应用 → 应用启动管理\n")
                        append("   找到本应用，关闭「自动管理」\n")
                        append("   如界面提供选项，手动开启：自启动、关联启动、后台活动\n\n")
                        withStyle(SpanStyle(fontWeight = FontWeight.Medium)) {
                            append("2. 电池策略（如界面存在）\n")
                        }
                        append("   常见路径：设置 → 电池，或 应用详情 → 电池\n")
                        append("   将本应用设为「不受限制」/「不优化」\n\n")
                        withStyle(SpanStyle(fontWeight = FontWeight.Medium)) {
                            append("3. 通知权限\n")
                        }
                        append("   开启本应用「允许通知」\n")
                        append("   确保前台服务通知不会被系统静默关闭\n\n")
                        withStyle(SpanStyle(fontWeight = FontWeight.Medium)) {
                            append("4. 可选增强\n")
                        }
                        append("   最近任务页可尝试把本应用卡片上锁\n")
                        append("   关闭省电模式 / 超级省电\n")
                        append("   如系统提供「休眠时始终保持网络连接」，可按需开启\n")
                        append("   如果系统里能单独看到卓易通/兼容层应用，也可尝试同步放行其后台相关权限")
                    }
                    Text(
                        text = manualText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.3f
                    )
                }
            }

            SettingsHintCard(
                text = "即使以上权限全部放行，鸿蒙系统的后台管理仍可能在特定场景下暂停服务。" +
                    "放行权限可提升服务在后台被恢复的概率，但无法保证绝对常驻。" +
                    "如服务停止，可回到 App 首页重新点击启动。"
            )

            SettingsHintCard(
                text = "不同机型、鸿蒙版本与兼容层版本的页面名称可能不同，请以当前设备界面为准。" +
                    "此引导页不会因为环境检测未命中而隐藏，任何设备上均可手动查看。" +
                    "如果当前设备不是鸿蒙兼容环境，可忽略此页。"
            )
        }
    }
}
