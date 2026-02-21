package com.example.danmuapiapp.ui.screen.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.data.util.AppAppearancePrefs
import com.example.danmuapiapp.domain.model.NightModePreference
import com.example.danmuapiapp.ui.component.SettingsDivider
import com.example.danmuapiapp.ui.component.SettingsGroup
import com.example.danmuapiapp.ui.component.SettingsPageHeader
import com.example.danmuapiapp.ui.component.SettingsSwitchItem
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

@Composable
fun ThemeDisplayScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val snackbarHostState = remember { SnackbarHostState() }
    val nightMode by viewModel.nightMode.collectAsStateWithLifecycle()
    val appDpiOverride by viewModel.appDpiOverride.collectAsStateWithLifecycle()
    val hideFromRecents by viewModel.hideFromRecents.collectAsStateWithLifecycle()
    val systemDpi = remember { viewModel.currentSystemDensityDpi() }
    val appCurrentDpi = context.resources.displayMetrics.densityDpi
    val effectiveDpi = if (appDpiOverride > 0) appDpiOverride else systemDpi
    val presetDpi = remember(systemDpi) {
        listOf(
            (systemDpi * 0.85f).roundToInt(),
            (systemDpi * 0.95f).roundToInt(),
            systemDpi,
            (systemDpi * 1.08f).roundToInt(),
            (systemDpi * 1.18f).roundToInt()
        )
            .map { it.coerceIn(AppAppearancePrefs.APP_DPI_MIN, AppAppearancePrefs.APP_DPI_MAX) }
            .distinct()
    }

    var dpiInput by rememberSaveable(appDpiOverride, appCurrentDpi) {
        mutableStateOf((if (appDpiOverride > 0) appDpiOverride else appCurrentDpi).toString())
    }

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
                .padding(top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingsPageHeader(
                title = "主题与显示",
                subtitle = "界面主题与应用内显示缩放",
                onBack = onBack
            )

            SettingsGroup(title = "界面主题") {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("暗色为独立夜景风格，不影响当前亮色主题")
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        NightModePreference.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = nightMode == mode,
                                onClick = { viewModel.setNightMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = NightModePreference.entries.size
                                )
                            ) {
                                Text(nightModeLabel(mode))
                            }
                        }
                    }
                }
            }

            SettingsGroup(title = "显示缩放（App DPI）") {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("仅对本应用生效，不会修改系统 DPI。")
                    Text("系统 DPI：$systemDpi  ·  当前应用 DPI：$effectiveDpi")
                    Text("可用范围：${AppAppearancePrefs.APP_DPI_MIN}-${AppAppearancePrefs.APP_DPI_MAX}")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presetDpi.forEach { dpi ->
                            AssistChip(
                                onClick = {
                                    dpiInput = dpi.toString()
                                    viewModel.setAppDpiOverride(activity, dpi)
                                },
                                label = { Text("$dpi") }
                            )
                        }
                        AssistChip(
                            onClick = { viewModel.setAppDpiOverride(activity, AppAppearancePrefs.APP_DPI_SYSTEM) },
                            label = { Text("跟随系统") }
                        )
                    }
                    OutlinedTextField(
                        value = dpiInput,
                        onValueChange = { input ->
                            dpiInput = input.filter { it.isDigit() }.take(4)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("自定义 App DPI") },
                        placeholder = { Text("例如 360") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                val parsed = dpiInput.toIntOrNull()
                                if (parsed == null) {
                                    viewModel.postMessage("请输入有效 DPI 数值")
                                } else {
                                    viewModel.setAppDpiOverride(activity, parsed)
                                }
                            }
                        ) {
                            Text("应用 DPI")
                        }
                    }
                }
                SettingsDivider()
                SettingsSwitchItem(
                    title = "隐藏最近任务",
                    subtitle = if (hideFromRecents) {
                        "最近任务中已隐藏本应用"
                    } else {
                        "最近任务中显示本应用"
                    },
                    icon = Icons.Rounded.VisibilityOff,
                    checked = hideFromRecents,
                    onCheckedChange = viewModel::setHideFromRecents
                )
                SettingsDivider()
                Text(
                    text = "修改 DPI 后会自动刷新当前界面；如果无变化请手动重启应用。",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

private fun nightModeLabel(mode: NightModePreference): String {
    return when (mode) {
        NightModePreference.FollowSystem -> "跟随系统"
        NightModePreference.Light -> "浅色"
        NightModePreference.Dark -> "暗色"
    }
}
