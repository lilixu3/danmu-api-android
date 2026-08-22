package com.example.danmuapiapp.ui.screen.settings

import com.example.danmuapiapp.ui.component.AppSnackbarHost

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.data.util.AppAppearancePrefs
import com.example.danmuapiapp.domain.model.AppBackgroundMode
import com.example.danmuapiapp.domain.model.AppBackgroundPreference
import com.example.danmuapiapp.domain.model.AppBackgroundRefreshPolicy
import com.example.danmuapiapp.domain.model.GlassMaterialPreference
import com.example.danmuapiapp.domain.model.NightModePreference
import com.example.danmuapiapp.ui.component.SettingsDivider
import com.example.danmuapiapp.ui.component.SettingsGroup
import com.example.danmuapiapp.ui.component.SettingsPageHeader
import com.example.danmuapiapp.ui.component.SettingsSwitchItem
import com.example.danmuapiapp.ui.component.liquid.AppGlassAssistChip
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton
import com.example.danmuapiapp.ui.component.liquid.AppGlassFilterChip
import com.example.danmuapiapp.ui.theme.isLiquidGlassSupported
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
    val glassMaterial by viewModel.glassMaterial.collectAsStateWithLifecycle()
    val appBackground by viewModel.appBackground.collectAsStateWithLifecycle()
    val liquidGlassSupported = remember { isLiquidGlassSupported(Build.VERSION.SDK_INT) }
    val liquidGlassEnabled = liquidGlassSupported &&
        glassMaterial == GlassMaterialPreference.LiquidGlass
    val appDpiOverride by viewModel.appDpiOverride.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val systemDpi = remember { viewModel.currentSystemDensityDpi() }
    val appCurrentDpi = configuration.densityDpi
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
    var onlineUrlInput by rememberSaveable(appBackground.onlineImageUrl) {
        mutableStateOf(appBackground.onlineImageUrl)
    }
    var randomUrlInput by rememberSaveable(appBackground.randomImageUrl) {
        mutableStateOf(appBackground.randomImageUrl)
    }
    var customRefreshInput by rememberSaveable(appBackground.customRandomRefreshSeconds) {
        mutableStateOf(
            appBackground.customRandomRefreshSeconds
                .takeIf { it > 0L }
                ?.toString()
                .orEmpty()
        )
    }
    var customRefreshEditing by rememberSaveable {
        mutableStateOf(appBackground.randomRefreshPolicy == AppBackgroundRefreshPolicy.Custom)
    }
    val localImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        viewModel.setLocalAppBackground(uri.toString())
    }

    LaunchedEffect(viewModel.operationMessage) {
        viewModel.operationMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
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
                subtitle = "界面主题、背景、液态玻璃与显示缩放",
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

            SettingsGroup(title = "液态玻璃") {
                SettingsSwitchItem(
                    title = "启用液态玻璃",
                    subtitle = if (liquidGlassSupported) {
                        "背景模糊与半透明"
                    } else {
                        "需要 Android 13 或更高版本"
                    },
                    icon = Icons.Rounded.BlurOn,
                    checked = liquidGlassEnabled,
                    enabled = liquidGlassSupported,
                    disabledOnClick = {
                        viewModel.postMessage("当前系统不支持完整液态玻璃效果")
                    },
                    onCheckedChange = { enabled ->
                        viewModel.setGlassMaterial(
                            if (enabled) GlassMaterialPreference.LiquidGlass
                            else GlassMaterialPreference.Off
                        )
                    }
                )
                if (liquidGlassEnabled) {
                    SettingsDivider()
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            AppBackgroundMode.entries.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    selected = appBackground.mode == mode,
                                    onClick = {
                                        if (mode == AppBackgroundMode.LocalImage &&
                                            appBackground.localImageUri.isBlank()
                                        ) {
                                            localImagePicker.launch(arrayOf("image/*"))
                                        } else {
                                            viewModel.setAppBackgroundMode(mode)
                                        }
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = AppBackgroundMode.entries.size
                                    )
                                ) {
                                    Text(backgroundModeLabel(mode))
                                }
                            }
                        }

                        when (appBackground.mode) {
                            AppBackgroundMode.Solid -> {
                                Text("使用当前主题的纯色背景")
                            }

                            AppBackgroundMode.LocalImage -> {
                                Text(
                                    text = if (appBackground.localImageUri.isBlank()) {
                                        "尚未选择本地图片"
                                    } else {
                                        "已选择本地图片"
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    AppGlassButton(
                                        onClick = { localImagePicker.launch(arrayOf("image/*")) }
                                    ) {
                                        Icon(Icons.Rounded.Image, contentDescription = null)
                                        Text(if (appBackground.localImageUri.isBlank()) "选择图片" else "重新选择")
                                    }
                                }
                            }

                            AppBackgroundMode.OnlineImage -> {
                                OutlinedTextField(
                                    value = onlineUrlInput,
                                    onValueChange = { onlineUrlInput = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = { Text("在线图片链接") },
                                    placeholder = { Text("https://example.com/image.jpg") },
                                    leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    AppGlassButton(
                                        onClick = {
                                            viewModel.setOnlineAppBackground(
                                                url = onlineUrlInput,
                                                random = false
                                            )
                                        }
                                    ) {
                                        Icon(Icons.Rounded.Check, contentDescription = null)
                                        Text("应用链接")
                                    }
                                }
                            }

                            AppBackgroundMode.RandomOnlineImage -> {
                                OutlinedTextField(
                                    value = randomUrlInput,
                                    onValueChange = { randomUrlInput = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = { Text("随机图片接口") },
                                    placeholder = { Text(AppBackgroundPreference.DEFAULT_RANDOM_IMAGE_URL) },
                                    leadingIcon = { Icon(Icons.Rounded.Shuffle, contentDescription = null) },
                                    supportingText = {
                                        Text("接口可使用 {random} 占位符")
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AppGlassAssistChip(
                                        onClick = {
                                            randomUrlInput = AppBackgroundPreference.DEFAULT_RANDOM_IMAGE_URL
                                        },
                                        label = { Text("LoliAPI 默认") }
                                    )
                                    AppGlassAssistChip(
                                        onClick = {
                                            randomUrlInput = AppBackgroundPreference.PICSUM_BACKUP_IMAGE_URL
                                        },
                                        label = { Text("Picsum 备用") }
                                    )
                                }
                                Text("前台刷新条件")
                                Text("仅在进入 App 前台时检查；超过所选间隔才刷新，不会常驻计时。")
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AppBackgroundRefreshPolicy.entries.forEach { policy ->
                                        AppGlassFilterChip(
                                            selected = appBackground.randomRefreshPolicy == policy,
                                            onClick = {
                                                if (policy == AppBackgroundRefreshPolicy.Custom) {
                                                    customRefreshEditing = true
                                                    if (appBackground.customRandomRefreshSeconds > 0L) {
                                                        viewModel.setRandomBackgroundRefreshPolicy(policy)
                                                    }
                                                } else {
                                                    customRefreshEditing = false
                                                    viewModel.setRandomBackgroundRefreshPolicy(policy)
                                                }
                                            },
                                            label = { Text(randomRefreshPolicyLabel(policy)) }
                                        )
                                    }
                                }
                                if (
                                    customRefreshEditing ||
                                        appBackground.randomRefreshPolicy == AppBackgroundRefreshPolicy.Custom
                                ) {
                                    OutlinedTextField(
                                        value = customRefreshInput,
                                        onValueChange = { value ->
                                            customRefreshInput = value
                                                .filter { it in '0'..'9' }
                                                .take(18)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        label = { Text("自定义前台刷新间隔（秒）") },
                                        supportingText = {
                                            Text("例如输入 90 表示 1 分 30 秒；仅在进入前台时检查。")
                                        },
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Number
                                        )
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        AppGlassButton(
                                            onClick = {
                                                if (viewModel.setCustomRandomBackgroundRefreshInterval(
                                                        customRefreshInput
                                                    )
                                                ) {
                                                    customRefreshEditing = false
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Rounded.Check, contentDescription = null)
                                            Text("应用自定义间隔")
                                        }
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    AppGlassButton(
                                        onClick = {
                                            viewModel.setOnlineAppBackground(
                                                url = randomUrlInput,
                                                random = true
                                            )
                                        }
                                    ) {
                                        Icon(Icons.Rounded.Check, contentDescription = null)
                                        Text("应用接口")
                                    }
                                }
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
                            AppGlassAssistChip(
                                onClick = {
                                    dpiInput = dpi.toString()
                                    viewModel.setAppDpiOverride(activity, dpi)
                                },
                                label = { Text("$dpi") }
                            )
                        }
                        AppGlassAssistChip(
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
                        AppGlassButton(
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

private fun backgroundModeLabel(mode: AppBackgroundMode): String {
    return when (mode) {
        AppBackgroundMode.Solid -> "纯色"
        AppBackgroundMode.LocalImage -> "本地"
        AppBackgroundMode.OnlineImage -> "链接"
        AppBackgroundMode.RandomOnlineImage -> "随机"
    }
}

private fun randomRefreshPolicyLabel(policy: AppBackgroundRefreshPolicy): String {
    return when (policy) {
        AppBackgroundRefreshPolicy.OnForeground -> "每次进入前台"
        AppBackgroundRefreshPolicy.Seconds30 -> "超过 30 秒"
        AppBackgroundRefreshPolicy.Minute1 -> "超过 1 分钟"
        AppBackgroundRefreshPolicy.Minutes3 -> "超过 3 分钟"
        AppBackgroundRefreshPolicy.Minutes5 -> "超过 5 分钟"
        AppBackgroundRefreshPolicy.Minutes10 -> "超过 10 分钟"
        AppBackgroundRefreshPolicy.Custom -> "自定义"
    }
}
