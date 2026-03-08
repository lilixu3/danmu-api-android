package com.example.danmuapiapp.ui.screen.config

import com.example.danmuapiapp.ui.component.AppBottomSheetDialog
import com.example.danmuapiapp.ui.component.AppBottomSheetStyle
import com.example.danmuapiapp.ui.component.AppBottomSheetTone

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

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
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
                OutlinedButton(
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

            OutlinedTextField(
                value = value,
                onValueChange = {
                    onValueChange(it)
                    verifyError = null
                    verifyResult = null
                },
                label = { Text("AI API Key") },
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
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
                "测试使用当前输入值，不必先保存配置。",
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

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
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

            Text(
                "检测到的字段：${cookieSnapshot.keys.joinToString("、").ifBlank { "无" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { startQrFlow() }) {
                    Icon(Icons.Rounded.QrCode2, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("扫码获取")
                }
                OutlinedButton(
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
                label = { Text("Bilibili Cookie") },
                visualTransformation = if (showCookie) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showCookie = !showCookie }) {
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
                "建议使用扫码登录自动获取。手动粘贴时请确保至少包含 SESSDATA 与 bili_jct。",
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
        AppBottomSheetDialog(
            onDismissRequest = { closeQrDialog() },
            style = AppBottomSheetStyle.Status,
            tone = AppBottomSheetTone.Info,
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
                TextButton(onClick = { startQrFlow() }) { Text("刷新二维码") }
            },
            dismissButton = {
                TextButton(onClick = { closeQrDialog() }) { Text("关闭") }
            }
        )
    }
}

internal data class CookieSnapshot(
    val keys: List<String>,
    val hasRequired: Boolean
)

