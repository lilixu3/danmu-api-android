package com.example.danmuapiapp.ui.compat

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.danmuapiapp.R
import com.example.danmuapiapp.data.service.TvConfigSyncCodec
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CoreDownloadProgress
import com.example.danmuapiapp.domain.model.CoreInfo
import com.example.danmuapiapp.domain.model.RuntimeState
import com.example.danmuapiapp.domain.model.ServiceStatus
import com.example.danmuapiapp.ui.screen.push.PushLanScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class CompatModeActivity : AppCompatActivity() {

    private val graph by lazy { CompatRuntimeGraph.get(applicationContext) }
    private val syncServer by lazy {
        CompatTvConfigSyncServer(
            envConfigRepository = graph.envConfigRepository,
            runtimeRepository = graph.runtimeRepository,
            settingsRepository = graph.settingsRepository,
            coreRepository = graph.coreRepository
        )
    }

    private lateinit var serviceStatusView: TextView
    private lateinit var currentCoreView: TextView
    private lateinit var currentVersionView: TextView
    private lateinit var runModeView: TextView
    private lateinit var portView: TextView
    private lateinit var localUrlView: TextView
    private lateinit var lanUrlView: TextView
    private lateinit var refreshButton: Button
    private lateinit var startButton: Button
    private lateinit var restartButton: Button
    private lateinit var stopButton: Button
    private lateinit var progressCard: View
    private lateinit var progressTitleView: TextView
    private lateinit var progressDetailView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var syncHintView: TextView
    private lateinit var syncStatusView: TextView
    private lateinit var syncUrlView: TextView
    private lateinit var syncQrView: ImageView
    private lateinit var coreLoadingView: TextView
    private lateinit var coreContainer: LinearLayout

    private var runtimeState: RuntimeState = RuntimeState()
    private var coreInfos: List<CoreInfo> = emptyList()
    private var downloadProgress: CoreDownloadProgress = CoreDownloadProgress()
    private var isCoreInfoLoading: Boolean = true
    private var isOperating: Boolean = false
    private var operationProgressTitle: String = ""
    private var syncUiState: CompatTvConfigSyncServer.UiState = CompatTvConfigSyncServer.UiState()
    private var lastRenderedSyncInvite: String = ""
    private var syncBitmapJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_DanmuApiApp)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_compat_mode)
        bindViews()
        styleFixedButtons()
        bindActions()
        renderRuntimeCard()
        renderProgressCard()
        renderSyncCard()
        renderCoreList()
        observeState()
        syncServer.start(resolveSyncHost(runtimeState))
        graph.coreRepository.refreshCoreInfo()
    }

    override fun onDestroy() {
        syncBitmapJob?.cancel()
        syncServer.stop()
        super.onDestroy()
    }

    private fun bindViews() {
        serviceStatusView = findViewById(R.id.text_service_status)
        currentCoreView = findViewById(R.id.text_current_core)
        currentVersionView = findViewById(R.id.text_current_version)
        runModeView = findViewById(R.id.text_run_mode)
        portView = findViewById(R.id.text_port)
        localUrlView = findViewById(R.id.text_local_url)
        lanUrlView = findViewById(R.id.text_lan_url)
        refreshButton = findViewById(R.id.button_refresh)
        startButton = findViewById(R.id.button_start)
        restartButton = findViewById(R.id.button_restart)
        stopButton = findViewById(R.id.button_stop)
        progressCard = findViewById(R.id.card_progress)
        progressTitleView = findViewById(R.id.text_progress_title)
        progressDetailView = findViewById(R.id.text_progress_detail)
        progressBar = findViewById(R.id.progress_download)
        syncHintView = findViewById(R.id.text_sync_hint)
        syncStatusView = findViewById(R.id.text_sync_status)
        syncUrlView = findViewById(R.id.text_sync_url)
        syncQrView = findViewById(R.id.image_sync_qr)
        coreLoadingView = findViewById(R.id.text_core_loading)
        coreContainer = findViewById(R.id.layout_core_container)
    }

    private fun styleFixedButtons() {
        styleActionButton(refreshButton, primary = false)
        styleActionButton(startButton, primary = true)
        styleActionButton(restartButton, primary = false)
        styleActionButton(stopButton, primary = false)
    }

    private fun bindActions() {
        refreshButton.setOnClickListener {
            graph.coreRepository.refreshCoreInfo()
            toast("正在刷新核心信息")
        }
        startButton.setOnClickListener { startService() }
        restartButton.setOnClickListener { graph.runtimeRepository.restartService() }
        stopButton.setOnClickListener { graph.runtimeRepository.stopService() }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    graph.runtimeRepository.runtimeState.collectLatest {
                        runtimeState = it
                        syncServer.updateHost(resolveSyncHost(it))
                        renderRuntimeCard()
                    }
                }
                launch {
                    graph.coreRepository.coreInfoList.collectLatest {
                        coreInfos = it
                        renderRuntimeCard()
                        renderCoreList()
                    }
                }
                launch {
                    graph.coreRepository.downloadProgress.collectLatest {
                        downloadProgress = it
                        renderProgressCard()
                    }
                }
                launch {
                    graph.coreRepository.isCoreInfoLoading.collectLatest {
                        isCoreInfoLoading = it
                        renderCoreList()
                    }
                }
                launch {
                    graph.settingsRepository.customRepo.collectLatest {
                        renderCoreList()
                    }
                }
                launch {
                    graph.settingsRepository.customRepoDisplayName.collectLatest {
                        renderRuntimeCard()
                        renderCoreList()
                    }
                }
                launch {
                    syncServer.uiState.collectLatest {
                        syncUiState = it
                        renderSyncCard()
                    }
                }
            }
        }
    }

    private fun renderRuntimeCard() {
        val status = runtimeState.status
        serviceStatusView.text = statusLabel(status)
        serviceStatusView.setTextColor(statusColor(status))
        serviceStatusView.background = GradientDrawable().apply {
            cornerRadius = dp(999).toFloat()
            setColor(ColorUtils.setAlphaComponent(statusColor(status), 36))
        }
        serviceStatusView.setPadding(dp(12), dp(6), dp(12), dp(6))

        currentCoreView.text = resolveVariantLabel(runtimeState.variant)
        currentVersionView.text = currentCoreVersionText()
        runModeView.text = runtimeState.runMode.label
        portView.text = runtimeState.port.toString()
        localUrlView.text = runtimeState.localUrl.ifBlank { "--" }
        lanUrlView.text = runtimeState.lanUrl.ifBlank { "--" }

        val running = status == ServiceStatus.Running
        updateButtonEnabled(startButton, !running && !isOperating)
        updateButtonEnabled(restartButton, running && !isOperating)
        updateButtonEnabled(stopButton, running && !isOperating)
        updateButtonEnabled(refreshButton, !isOperating)
    }

    private fun renderProgressCard() {
        val visible = downloadProgress.inProgress || isOperating
        progressCard.isVisible = visible
        if (!visible) return

        val actionLabel = downloadProgress.actionLabel.ifBlank {
            operationProgressTitle.ifBlank {
                if (isOperating) "处理中" else "下载中"
            }
        }
        progressTitleView.text = actionLabel
        progressDetailView.text = buildString {
            val stage = downloadProgress.stageText.ifBlank {
                if (isOperating) "请稍候" else "正在准备资源"
            }
            append(stage)
            val bytesText = formatByteProgress(downloadProgress)
            if (bytesText.isNotBlank()) {
                append("\n")
                append(bytesText)
            }
        }
        val progress = downloadProgress.progress
        progressBar.isIndeterminate = progress == null
        if (progress != null) {
            progressBar.max = 1000
            progressBar.progress = (progress.coerceIn(0f, 1f) * 1000).toInt()
        }
    }

    private fun renderSyncCard() {
        val inviteUrl = syncUiState.inviteUrl
        val ready = inviteUrl.isNotBlank()
        syncHintView.text = if (ready) {
            "手机端进入“设置 > 备份与恢复”，点击“扫码同步到电视”即可推送当前配置。"
        } else {
            "请让电视和手机连接到同一 Wi-Fi，获取局域网地址后这里会自动生成同步码。"
        }
        syncStatusView.text = buildString {
            append(syncUiState.statusText)
            if (syncUiState.lastSyncSummary.isNotBlank()) {
                append("\n")
                append(syncUiState.lastSyncSummary)
            }
        }
        syncUrlView.text = if (ready) {
            "配对地址：${syncUiState.host}:${syncUiState.port}"
        } else {
            "当前未检测到可用局域网地址"
        }

        if (!ready) {
            lastRenderedSyncInvite = ""
            syncBitmapJob?.cancel()
            syncQrView.setImageDrawable(null)
            syncQrView.alpha = 0.24f
            return
        }
        syncQrView.alpha = 1f
        if (lastRenderedSyncInvite == inviteUrl) return
        lastRenderedSyncInvite = inviteUrl
        syncBitmapJob?.cancel()
        syncBitmapJob = lifecycleScope.launch(Dispatchers.Default) {
            val sizePx = (resources.displayMetrics.density * 188).toInt().coerceAtLeast(360)
            val bitmap = TvConfigSyncCodec.buildQrBitmap(inviteUrl, sizePx)
            withContext(Dispatchers.Main) {
                if (lastRenderedSyncInvite == inviteUrl) {
                    syncQrView.setImageBitmap(bitmap)
                }
            }
        }
    }

    private fun renderCoreList() {
        coreLoadingView.isVisible = isCoreInfoLoading
        coreContainer.removeAllViews()
        coreInfos.forEach { info ->
            coreContainer.addView(buildCoreCard(info))
        }
    }

    private fun buildCoreCard(info: CoreInfo): View {
        val context = this
        val surfaceColor = resolveThemeColor(com.google.android.material.R.attr.colorSurfaceContainerHigh)
        val strokeColor = resolveThemeColor(com.google.android.material.R.attr.colorOutlineVariant)
        val titleColor = resolveThemeColor(com.google.android.material.R.attr.colorOnSurface)
        val secondaryColor = resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        val primaryColor = resolveThemeColor(androidx.appcompat.R.attr.colorPrimary)
        val highlightColor = ColorUtils.blendARGB(surfaceColor, primaryColor, 0.10f)

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(if (runtimeState.variant == info.variant) highlightColor else surfaceColor)
                setStroke(dp(1), strokeColor)
            }
        }
        card.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(12)
        }

        val title = TextView(context).apply {
            text = resolveVariantLabel(info.variant)
            textSize = 18f
            setTextColor(titleColor)
        }
        val subtitle = TextView(context).apply {
            text = coreVersionText(info)
            textSize = 13.5f
            setTextColor(if (info.hasUpdate) primaryColor else secondaryColor)
        }
        val repoText = resolveVariantRepo(info.variant).ifBlank {
            if (info.variant == ApiVariant.Custom) "自定义仓库尚未配置" else ""
        }
        val repo = TextView(context).apply {
            text = repoText
            textSize = 12.5f
            setTextColor(secondaryColor)
            isVisible = repoText.isNotBlank()
        }
        val badge = TextView(context).apply {
            text = when {
                runtimeState.variant == info.variant -> "当前使用"
                info.hasUpdate -> "可更新"
                info.isInstalled -> "已安装"
                else -> "未安装"
            }
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(5), dp(10), dp(5))
            setTextColor(if (runtimeState.variant == info.variant) primaryColor else secondaryColor)
            background = GradientDrawable().apply {
                cornerRadius = dp(999).toFloat()
                setColor(resolveThemeColor(com.google.android.material.R.attr.colorSurfaceVariant))
            }
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    addView(title)
                    addView(subtitle)
                    addView(repo)
                }
            )
            addView(badge)
        }
        card.addView(header)

        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, 0)
        }

        if (runtimeState.variant != info.variant) {
            buttonRow.addView(
                buildActionButton(
                    text = "切换使用",
                    primary = false,
                    enabled = info.isInstalled && !isOperating,
                    onClick = { switchVariant(info.variant) }
                ).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        rightMargin = dp(8)
                    }
                }
            )
        }

        val mainButtonText = when {
            !info.isInstalled -> "下载核心"
            info.hasUpdate -> "立即更新"
            else -> "检查更新"
        }
        val primaryAction = !info.isInstalled || info.hasUpdate
        buttonRow.addView(
            buildActionButton(
                text = mainButtonText,
                primary = primaryAction,
                enabled = !isOperating,
                onClick = {
                    when {
                        !info.isInstalled -> installCore(info.variant)
                        info.hasUpdate -> updateCore(info.variant)
                        else -> checkUpdate(info.variant)
                    }
                }
            ).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
        )

        card.addView(buttonRow)
        return card
    }

    private fun buildActionButton(
        text: String,
        primary: Boolean,
        enabled: Boolean,
        onClick: () -> Unit
    ): Button {
        return Button(this).apply {
            this.text = text
            isAllCaps = false
            minHeight = dp(46)
            minimumHeight = dp(46)
            maxLines = 1
            setPadding(dp(12), dp(10), dp(12), dp(10))
            styleActionButton(this, primary)
            updateButtonEnabled(this, enabled)
            setOnClickListener { onClick() }
        }
    }

    private fun styleActionButton(button: Button, primary: Boolean) {
        button.background = ContextCompat.getDrawable(
            this,
            if (primary) R.drawable.compat_button_primary else R.drawable.compat_button_secondary
        )
        button.setTextColor(
            if (primary) 0xFFFFFFFF.toInt() else resolveThemeColor(com.google.android.material.R.attr.colorOnSurface)
        )
        button.stateListAnimator = null
        button.setOnFocusChangeListener { view, hasFocus ->
            view.animate()
                .scaleX(if (hasFocus) 1.04f else 1f)
                .scaleY(if (hasFocus) 1.04f else 1f)
                .translationZ(if (hasFocus) dp(8).toFloat() else 0f)
                .setDuration(120L)
                .start()
        }
    }

    private fun updateButtonEnabled(button: Button, enabled: Boolean) {
        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else 0.48f
    }

    private fun startService() {
        if (isOperating) return
        lifecycleScope.launch {
            val variant = runtimeState.variant
            val installed = withContext(Dispatchers.IO) {
                graph.coreRepository.isCoreInstalled(variant)
            }
            if (!installed) {
                toast("${resolveVariantLabel(variant)} 未安装，请先下载核心")
                return@launch
            }
            graph.runtimeRepository.startService()
        }
    }

    private fun switchVariant(variant: ApiVariant) {
        if (isOperating) return
        val info = coreInfos.find { it.variant == variant }
        if (info?.isInstalled != true) {
            toast("${resolveVariantLabel(variant)} 未安装，请先下载核心")
            return
        }
        graph.runtimeRepository.updateVariant(variant)
        if (runtimeState.status == ServiceStatus.Running) {
            graph.runtimeRepository.restartService()
            toast("已切换到 ${resolveVariantLabel(variant)}，正在重启服务")
        } else {
            toast("已切换到 ${resolveVariantLabel(variant)}")
        }
        graph.coreRepository.refreshCoreInfo()
    }

    private fun installCore(variant: ApiVariant) {
        if (!canOperateVariant(variant)) return
        performCoreOperation("正在下载 ${resolveVariantLabel(variant)}") {
            graph.coreRepository.installCore(variant).fold(
                onSuccess = {
                    graph.coreRepository.refreshCoreInfo()
                    if (runtimeState.variant == variant && runtimeState.status == ServiceStatus.Running) {
                        graph.runtimeRepository.restartService()
                        toast("${resolveVariantLabel(variant)} 下载完成，正在重启服务")
                    } else {
                        toast("${resolveVariantLabel(variant)} 下载完成")
                    }
                },
                onFailure = {
                    toast("${resolveVariantLabel(variant)} 下载失败：${it.message ?: "未知错误"}")
                }
            )
        }
    }

    private fun updateCore(variant: ApiVariant) {
        if (!canOperateVariant(variant)) return
        performCoreOperation("正在更新 ${resolveVariantLabel(variant)}") {
            graph.coreRepository.updateCore(variant).fold(
                onSuccess = {
                    graph.coreRepository.refreshCoreInfo()
                    if (runtimeState.variant == variant && runtimeState.status == ServiceStatus.Running) {
                        graph.runtimeRepository.restartService()
                        toast("${resolveVariantLabel(variant)} 更新完成，正在重启服务")
                    } else {
                        toast("${resolveVariantLabel(variant)} 更新完成")
                    }
                },
                onFailure = {
                    toast("${resolveVariantLabel(variant)} 更新失败：${it.message ?: "未知错误"}")
                }
            )
        }
    }

    private fun checkUpdate(variant: ApiVariant) {
        if (!canOperateVariant(variant)) return
        performCoreOperation("正在检查 ${resolveVariantLabel(variant)} 更新") {
            runCatching {
                graph.coreRepository.checkAndMarkUpdate(variant)
                graph.coreRepository.refreshCoreInfo()
                val refreshed = graph.coreRepository.coreInfoList.value.find { it.variant == variant }
                if (refreshed?.hasUpdate == true && !refreshed.latestVersion.isNullOrBlank()) {
                    toast("${resolveVariantLabel(variant)} 有新版本 ${refreshed.latestVersion}")
                } else {
                    toast("${resolveVariantLabel(variant)} 已是最新版本")
                }
            }.onFailure {
                toast("${resolveVariantLabel(variant)} 检查更新失败：${it.message ?: "未知错误"}")
            }
        }
    }

    private fun performCoreOperation(progressTitle: String, block: suspend () -> Unit) {
        if (isOperating) return
        isOperating = true
        operationProgressTitle = progressTitle
        renderRuntimeCard()
        renderProgressCard()
        renderCoreList()
        lifecycleScope.launch {
            try {
                block()
            } finally {
                isOperating = false
                operationProgressTitle = ""
                renderRuntimeCard()
                renderProgressCard()
                renderCoreList()
            }
        }
    }

    private fun canOperateVariant(variant: ApiVariant): Boolean {
        if (variant != ApiVariant.Custom) return true
        if (graph.settingsRepository.customRepo.value.trim().isNotBlank()) return true
        toast("自定义版未配置仓库，请先在手机端完整界面配置后再同步")
        return false
    }

    private fun currentCoreVersionText(): String {
        val info = coreInfos.find { it.variant == runtimeState.variant }
        return coreVersionText(info)
    }

    private fun coreVersionText(info: CoreInfo?): String {
        return when {
            info == null -> if (isCoreInfoLoading) "读取中" else "未知"
            !info.isInstalled -> "未安装"
            info.hasUpdate && !info.version.isNullOrBlank() && !info.latestVersion.isNullOrBlank() ->
                "v${info.version} -> v${info.latestVersion}"
            !info.version.isNullOrBlank() -> "v${info.version}"
            else -> "版本未知"
        }
    }

    private fun resolveVariantLabel(variant: ApiVariant): String {
        val displayName = graph.settingsRepository.customRepoDisplayName.value.trim()
        return if (variant == ApiVariant.Custom && displayName.isNotBlank()) displayName else variant.label
    }

    private fun resolveVariantRepo(variant: ApiVariant): String {
        return if (variant == ApiVariant.Custom) graph.settingsRepository.customRepo.value.trim() else variant.repo
    }

    private fun resolveSyncHost(state: RuntimeState): String {
        return PushLanScanner.resolveSelfLanIpv4(state.lanUrl).orEmpty()
    }

    private fun statusLabel(status: ServiceStatus): String {
        return when (status) {
            ServiceStatus.Stopped -> "已停止"
            ServiceStatus.Starting -> "启动中"
            ServiceStatus.Running -> "运行中"
            ServiceStatus.Stopping -> "停止中"
            ServiceStatus.Error -> "异常"
        }
    }

    private fun statusColor(status: ServiceStatus): Int {
        return when (status) {
            ServiceStatus.Stopped -> 0xFF6B7280.toInt()
            ServiceStatus.Starting -> 0xFF2563EB.toInt()
            ServiceStatus.Running -> 0xFF16A34A.toInt()
            ServiceStatus.Stopping -> 0xFFF59E0B.toInt()
            ServiceStatus.Error -> 0xFFDC2626.toInt()
        }
    }

    private fun formatByteProgress(progress: CoreDownloadProgress): String {
        if (progress.downloadedBytes <= 0L && progress.totalBytes <= 0L) return ""
        return buildString {
            append(formatBytes(progress.downloadedBytes))
            if (progress.totalBytes > 0L) {
                append(" / ")
                append(formatBytes(progress.totalBytes))
            }
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        return when {
            bytes >= gb -> String.format(Locale.getDefault(), "%.2f GB", bytes / gb)
            bytes >= mb -> String.format(Locale.getDefault(), "%.2f MB", bytes / mb)
            bytes >= kb -> String.format(Locale.getDefault(), "%.2f KB", bytes / kb)
            else -> "$bytes B"
        }
    }

    private fun resolveThemeColor(attr: Int): Int {
        val typedArray = obtainStyledAttributes(intArrayOf(attr))
        return try {
            typedArray.getColor(0, ContextCompat.getColor(this, android.R.color.black))
        } finally {
            typedArray.recycle()
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun toast(message: String) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        } else {
            runOnUiThread {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
