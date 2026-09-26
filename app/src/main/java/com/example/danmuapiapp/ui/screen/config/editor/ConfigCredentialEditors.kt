package com.example.danmuapiapp.ui.screen.config

import com.example.danmuapiapp.ui.component.AppDialog
import com.example.danmuapiapp.ui.component.AppDialogStyle
import com.example.danmuapiapp.ui.component.AppDialogTone

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.core.graphics.createBitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.ui.component.AppGlassSurface
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton
import com.example.danmuapiapp.ui.component.liquid.AppGlassIconButton
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.domain.model.EnvType
import com.example.danmuapiapp.domain.model.EnvVarDef
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun AiApiKeyEditor(
    value: String,
    onValueChange: (String) -> Unit,
    onVerifyAiConnectivity: suspend (String) -> Result<AiConnectivityVerifyResult>,
) {
    val scope = rememberCoroutineScope()
    var showKey by remember { mutableStateOf(false) }
    var verifyLoading by remember { mutableStateOf(false) }
    var verifyResult by remember { mutableStateOf<AiConnectivityVerifyResult?>(null) }
    var verifyError by remember { mutableStateOf<String?>(null) }

    val statusTitle = when {
        value.isBlank() -> "未配置"
        verifyLoading -> "检测中"
        verifyResult?.isReachable == true -> "连通正常"
        verifyResult != null -> "连通失败"
        else -> "待测试"
    }
    val statusSubtitle = when {
        value.isBlank() -> "请输入 AI API Key 后测试连通性"
        verifyLoading -> "正在校验 AI 服务连通性..."
        verifyResult?.isReachable == true -> verifyResult?.message.ifNullOrBlank("AI 服务可用")
        verifyResult != null -> verifyResult?.message.ifNullOrBlank("AI 服务不可用")
        else -> "点击“连通性测试”快速验证"
    }

    fun verifyNow(targetValue: String = value) {
        val apiKey = targetValue.trim()
        if (apiKey.isBlank()) {
            verifyResult = null
            verifyError = "请先输入 API Key"
            return
        }
        verifyLoading = true
        verifyError = null
        scope.launch {
            val result = onVerifyAiConnectivity(apiKey)
            verifyLoading = false
            result.onSuccess {
                verifyResult = it
                if (!it.isReachable) {
                    verifyError = it.message.ifBlank { "连通性测试失败" }
                }
            }.onFailure {
                verifyResult = null
                verifyError = it.message ?: "连通性测试失败"
            }
        }
    }

    AppGlassSurface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    onValueChange(it)
                    verifyError = null
                    verifyResult = null
                },
                label = { Text("API Key 值") },
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    AppGlassIconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            "显示/隐藏"
                        )
                    }
                },
                singleLine = false,
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                "支持 OpenAI 兼容的 API，需配合 AI_BASE_URL 和 AI_MODEL 配置使用；测试使用当前输入值，不必先保存配置。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AppGlassSurface(
                shape = RoundedCornerShape(12.dp),
                color = when {
                    value.isBlank() -> MaterialTheme.colorScheme.surfaceVariant
                    verifyLoading -> MaterialTheme.colorScheme.secondaryContainer
                    verifyResult?.isReachable == true -> MaterialTheme.colorScheme.primaryContainer
                    verifyResult != null -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        statusTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = when {
                            value.isBlank() -> MaterialTheme.colorScheme.onSurfaceVariant
                            verifyLoading -> MaterialTheme.colorScheme.onSecondaryContainer
                            verifyResult?.isReachable == true -> MaterialTheme.colorScheme.onPrimaryContainer
                            verifyResult != null -> MaterialTheme.colorScheme.onErrorContainer
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Text(
                        statusSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            value.isBlank() -> MaterialTheme.colorScheme.onSurfaceVariant
                            verifyLoading -> MaterialTheme.colorScheme.onSecondaryContainer
                            verifyResult?.isReachable == true -> MaterialTheme.colorScheme.onPrimaryContainer
                            verifyResult != null -> MaterialTheme.colorScheme.onErrorContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        "提供商：${verifyResult?.provider ?: "--"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "模型：${verifyResult?.model ?: "--"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "延迟：${formatLatencyMs(verifyResult?.latencyMs)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppGlassButton(
                    onClick = { verifyNow(value) },
                    enabled = value.isNotBlank() && !verifyLoading
                ) {
                    if (verifyLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    } else {
                        Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("连通性测试")
                }
            }

            if (!verifyError.isNullOrBlank()) {
                Text(
                    verifyError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
internal fun DandanplayPasswordEditor(
    value: String,
    account: String,
    onValueChange: (String) -> Unit,
    onVerifyDandanplayAccount: suspend (String, String) -> Result<DandanplayVerifyResult>,
) {
    val scope = rememberCoroutineScope()
    var showPassword by remember { mutableStateOf(false) }
    var verifyLoading by remember { mutableStateOf(false) }
    var verifyResult by remember { mutableStateOf<DandanplayVerifyResult?>(null) }
    var verifyError by remember { mutableStateOf<String?>(null) }

    val statusTitle = when {
        value.isBlank() -> "未配置"
        verifyLoading -> "检测中"
        verifyResult?.isReachable == true -> "连通正常"
        verifyResult != null -> "连通失败"
        else -> "待测试"
    }
    val statusSubtitle = when {
        value.isBlank() -> "请输入弹弹play密码后测试连通性"
        account.isBlank() -> "账号未配置：请先在 DANDANPLAY_ACCOUNT 中填写弹弹play账号"
        verifyLoading -> "正在校验弹弹play账号连通性..."
        verifyResult?.isReachable == true -> verifyResult?.message.ifNullOrBlank("弹弹play账号可用")
        verifyResult != null -> verifyResult?.message.ifNullOrBlank("弹弹play账号不可用")
        else -> "点击“连通性测试”快速验证"
    }

    fun verifyNow(targetValue: String = value) {
        val password = targetValue.trim()
        if (password.isBlank()) {
            verifyResult = null
            verifyError = "请先输入弹弹play密码"
            return
        }
        if (account.isBlank()) {
            verifyResult = null
            verifyError = "请先在 DANDANPLAY_ACCOUNT 中配置弹弹play账号"
            return
        }
        verifyLoading = true
        verifyError = null
        scope.launch {
            val result = onVerifyDandanplayAccount(account, password)
            verifyLoading = false
            result.onSuccess {
                verifyResult = it
                if (!it.isReachable) {
                    verifyError = it.message.ifBlank { "连通性测试失败" }
                }
            }.onFailure {
                verifyResult = null
                verifyError = it.message ?: "连通性测试失败"
            }
        }
    }

    AppGlassSurface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    onValueChange(it)
                    verifyError = null
                    verifyResult = null
                },
                label = { Text("弹弹play 密码") },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    AppGlassIconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            "显示/隐藏"
                        )
                    }
                },
                singleLine = false,
                minLines = 1,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                "账号在 DANDANPLAY_ACCOUNT 中配置，两者同时填写后 dandan 源经 NipaPlay 中转弹弹play服务端获取弹幕。测试使用当前输入值，不必先保存配置。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AppGlassSurface(
                shape = RoundedCornerShape(12.dp),
                color = when {
                    value.isBlank() -> MaterialTheme.colorScheme.surfaceVariant
                    verifyLoading -> MaterialTheme.colorScheme.secondaryContainer
                    verifyResult?.isReachable == true -> MaterialTheme.colorScheme.primaryContainer
                    verifyResult != null -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        statusTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = when {
                            value.isBlank() -> MaterialTheme.colorScheme.onSurfaceVariant
                            verifyLoading -> MaterialTheme.colorScheme.onSecondaryContainer
                            verifyResult?.isReachable == true -> MaterialTheme.colorScheme.onPrimaryContainer
                            verifyResult != null -> MaterialTheme.colorScheme.onErrorContainer
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Text(
                        statusSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            value.isBlank() -> MaterialTheme.colorScheme.onSurfaceVariant
                            verifyLoading -> MaterialTheme.colorScheme.onSecondaryContainer
                            verifyResult?.isReachable == true -> MaterialTheme.colorScheme.onPrimaryContainer
                            verifyResult != null -> MaterialTheme.colorScheme.onErrorContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppGlassButton(
                    onClick = { verifyNow(value) },
                    enabled = value.isNotBlank() && account.isNotBlank() && !verifyLoading
                ) {
                    if (verifyLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    } else {
                        Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("连通性测试")
                }
            }

            if (!verifyError.isNullOrBlank()) {
                Text(
                    verifyError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
internal fun BilibiliCookieEditor(
    value: String,
    onValueChange: (String) -> Unit,
    onGenerateBiliQr: suspend () -> Result<BilibiliQrGenerateResult>,
    onPollBiliQr: suspend (String) -> Result<BilibiliQrPollResult>,
    onVerifyBiliCookie: suspend (String) -> Result<BilibiliCookieVerifyResult>,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var showCookie by remember { mutableStateOf(false) }

    var verifyLoading by remember { mutableStateOf(false) }
    var verifyResult by remember { mutableStateOf<BilibiliCookieVerifyResult?>(null) }
    var verifyError by remember { mutableStateOf<String?>(null) }
    var fallbackExpiresAtMs by remember { mutableStateOf<Long?>(null) }

    var qrVisible by remember { mutableStateOf(false) }
    var qrLoading by remember { mutableStateOf(false) }
    var qrStatus by remember { mutableStateOf("准备生成二维码...") }
    var qrBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var qrPollingJob by remember { mutableStateOf<Job?>(null) }
    var qrKey by remember { mutableStateOf<String?>(null) }

    val cookieSnapshot = remember(value) { parseCookieSnapshot(value) }
    val displayExpire = verifyResult?.expiresAtMs ?: fallbackExpiresAtMs ?: inferCookieExpiryMs(value)
    val statusTitle = when {
        value.isBlank() -> "未配置"
        verifyLoading -> "检测中"
        verifyResult?.isValid == true -> "Cookie 有效"
        verifyResult != null -> "Cookie 无效"
        cookieSnapshot.hasRequired -> "待校验"
        else -> "字段不完整"
    }
    val statusSubtitle = when {
        value.isBlank() -> "请扫码登录或粘贴完整 Cookie"
        verifyLoading -> "正在校验 Cookie 状态..."
        verifyResult?.isValid == true -> verifyResult?.message.ifNullOrBlank("账号可用")
        verifyResult != null -> verifyResult?.message.ifNullOrBlank("Cookie 已失效或不完整")
        cookieSnapshot.hasRequired -> "已检测到 SESSDATA / bili_jct，建议点“校验状态”确认"
        else -> "缺少必要字段（SESSDATA 或 bili_jct）"
    }

    fun closeQrDialog() {
        qrVisible = false
        qrPollingJob?.cancel()
        qrPollingJob = null
    }

    fun verifyNow(targetCookie: String = value) {
        val cookie = targetCookie.trim()
        if (cookie.isBlank()) {
            verifyResult = null
            verifyError = "请先输入 Cookie"
            return
        }
        verifyLoading = true
        verifyError = null
        scope.launch {
            val result = onVerifyBiliCookie(cookie)
            verifyLoading = false
            result.onSuccess {
                verifyResult = it
                if (!it.isValid) {
                    verifyError = it.message.ifBlank { "Cookie 无效" }
                }
            }.onFailure {
                verifyResult = null
                verifyError = it.message ?: "校验失败"
            }
        }
    }

    fun startQrFlow() {
        qrVisible = true
        qrLoading = true
        qrStatus = "正在生成二维码..."
        qrBitmap = null
        qrKey = null
        qrPollingJob?.cancel()
        qrPollingJob = scope.launch {
            val generate = onGenerateBiliQr()
            if (generate.isFailure) {
                qrLoading = false
                qrStatus = "生成失败：${generate.exceptionOrNull()?.message ?: "未知错误"}"
                return@launch
            }

            val data = generate.getOrNull()
            if (data == null || data.qrUrl.isBlank() || data.qrcodeKey.isBlank()) {
                qrLoading = false
                qrStatus = "生成失败：返回内容为空"
                return@launch
            }

            qrKey = data.qrcodeKey
            val px = with(density) { 220.dp.toPx().toInt().coerceAtLeast(220) }
            val bitmap = runCatching {
                withContext(Dispatchers.Default) { buildQrBitmap(data.qrUrl, px) }
            }.getOrElse {
                qrLoading = false
                qrStatus = "二维码渲染失败"
                return@launch
            }

            qrBitmap = bitmap
            qrLoading = false
            qrStatus = "请使用 B 站 App 扫码，并在手机确认登录"

            while (isActive) {
                delay(2000)
                val key = qrKey ?: break
                val pollResult = onPollBiliQr(key)
                if (pollResult.isFailure) {
                    qrStatus = "轮询失败，正在重试..."
                    continue
                }
                val poll = pollResult.getOrNull() ?: continue
                when (poll.code) {
                    86101 -> qrStatus = "等待扫码..."
                    86090 -> qrStatus = "已扫码，请在手机端确认"
                    86038 -> {
                        qrStatus = "二维码已过期，请刷新"
                        break
                    }

                    0 -> {
                        val mergedCookie = buildCookieWithRefreshToken(poll.cookie, poll.refreshToken)
                        if (mergedCookie.isBlank()) {
                            qrStatus = "登录成功但未返回 Cookie，请刷新后重试"
                            break
                        }
                        fallbackExpiresAtMs = poll.expiresAtMs
                        onValueChange(mergedCookie)
                        qrStatus = "登录成功，Cookie 已自动填入"
                        verifyNow(mergedCookie)
                        delay(600)
                        closeQrDialog()
                        break
                    }

                    else -> {
                        qrStatus = poll.message.ifBlank { "状态异常（${poll.code}）" }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (value.isNotBlank()) {
            verifyNow(value)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            qrPollingJob?.cancel()
        }
    }

    AppGlassSurface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AppGlassSurface(
                shape = RoundedCornerShape(12.dp),
                color = when {
                    value.isBlank() -> MaterialTheme.colorScheme.surfaceVariant
                    verifyLoading -> MaterialTheme.colorScheme.secondaryContainer
                    verifyResult?.isValid == true || cookieSnapshot.hasRequired -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.errorContainer
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        statusTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = when {
                            value.isBlank() -> MaterialTheme.colorScheme.onSurfaceVariant
                            verifyLoading -> MaterialTheme.colorScheme.onSecondaryContainer
                            verifyResult?.isValid == true || cookieSnapshot.hasRequired -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onErrorContainer
                        }
                    )
                    Text(
                        statusSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            value.isBlank() -> MaterialTheme.colorScheme.onSurfaceVariant
                            verifyLoading -> MaterialTheme.colorScheme.onSecondaryContainer
                            verifyResult?.isValid == true || cookieSnapshot.hasRequired -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onErrorContainer
                        }
                    )
                    Text(
                        "用户名：${verifyResult?.uname ?: "--"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "到期时间：${formatEpochMs(displayExpire)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppGlassButton(onClick = { startQrFlow() }) {
                    Icon(Icons.Rounded.QrCode2, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("扫码登录")
                }
                AppGlassButton(
                    onClick = { verifyNow(value) },
                    enabled = value.isNotBlank() && !verifyLoading
                ) {
                    if (verifyLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("校验状态")
                }
            }

            OutlinedTextField(
                value = value,
                onValueChange = {
                    onValueChange(it)
                    verifyError = null
                },
                label = { Text("Cookie 值") },
                visualTransformation = if (showCookie) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    AppGlassIconButton(onClick = { showCookie = !showCookie }) {
                        Icon(
                            if (showCookie) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            "显示/隐藏"
                        )
                    }
                },
                singleLine = false,
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                "推荐使用扫码登录自动获取，或手动粘贴包含 SESSDATA 和 bili_jct 的完整 Cookie。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!verifyError.isNullOrBlank()) {
                Text(
                    verifyError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (qrVisible) {
        AppDialog(
            onDismissRequest = { closeQrDialog() },
            style = AppDialogStyle.Status,
            tone = AppDialogTone.Info,
            title = { Text("扫码登录 Bilibili") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (qrLoading) {
                        CircularProgressIndicator()
                    }
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap!!,
                            contentDescription = "Bilibili 登录二维码",
                            modifier = Modifier.size(220.dp)
                        )
                    }
                    Text(
                        qrStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                AppGlassButton(onClick = { startQrFlow() }) { Text("刷新二维码") }
            },
            dismissButton = {
                AppGlassButton(onClick = { closeQrDialog() }) { Text("关闭") }
            }
        )
    }
}

internal data class CookieSnapshot(
    val keys: List<String>,
    val hasRequired: Boolean
)
