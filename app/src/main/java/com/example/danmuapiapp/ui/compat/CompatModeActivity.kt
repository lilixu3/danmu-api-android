package com.example.danmuapiapp.ui.compat

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
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
import com.example.danmuapiapp.data.service.AppUpdateService
import com.example.danmuapiapp.data.service.TvConfigSyncCodec
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CoreDownloadProgress
import com.example.danmuapiapp.domain.model.CoreInfo
import com.example.danmuapiapp.domain.model.NightModePreference
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

    // Root
    private lateinit var rootLayout: LinearLayout

    // Title bar
    private lateinit var statusPill: TextView
    private lateinit var themeModeButton: Button

    // Dashboard tiles
    private lateinit var tileCoreValue: TextView
    private lateinit var tileVersionValue: TextView
    private lateinit var tileModeValue: TextView
    private lateinit var tilePortValue: TextView
    private lateinit var tileLocalValue: TextView
    private lateinit var tileLanValue: TextView

    // Service buttons
    private lateinit var btnStart: Button
    private lateinit var btnRestart: Button
    private lateinit var btnStop: Button

    // Progress
    private lateinit var progressCard: LinearLayout
    private lateinit var progressTitle: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var progressDetail: TextView

    // App update
    private lateinit var appUpdateCard: LinearLayout
    private lateinit var appUpdateTitle: TextView
    private lateinit var appUpdateNotes: TextView
    private lateinit var appUpdateProgress: ProgressBar
    private lateinit var appUpdateProgressText: TextView
    private lateinit var btnAppUpdate: Button
    private lateinit var btnAppInstall: Button

    // Core management
    private lateinit var coreLoadingView: TextView
    private lateinit var coreContainer: LinearLayout

    // Sync
    private lateinit var syncHintView: TextView
    private lateinit var syncStatusView: TextView
    private lateinit var syncUrlView: TextView
    private lateinit var syncQrView: ImageView

    // State
    private var runtimeState = RuntimeState()
    private var coreInfos: List<CoreInfo> = emptyList()
    private var downloadProgress = CoreDownloadProgress()
    private var isCoreInfoLoading = true
    private var isOperating = false
    private var operationProgressTitle = ""
    private var syncUiState = CompatTvConfigSyncServer.UiState()
    private var lastRenderedSyncInvite = ""
    private var syncBitmapJob: Job? = null

    // App update state
    private var appCheckResult: AppUpdateService.CheckResult? = null
    private var isAppDownloading = false
    private var appDownloadPercent = -1
    private var appDownloadDetail = ""
    private var downloadedApk: AppUpdateService.DownloadedApk? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_DanmuApiApp)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_compat_mode)
        rootLayout = findViewById(R.id.layout_root)
        buildUi()
        observeState()
        syncServer.start(resolveSyncHost(runtimeState))
        graph.coreRepository.refreshCoreInfo()
        checkAppUpdate()
    }

    override fun onResume() {
        super.onResume()
        graph.appUpdateService.tryResumePendingInstall(this)
        // 前台静默检查核心更新
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                graph.coreRepository.checkAllUpdates()
            }
        }
    }

    override fun onDestroy() {
        syncBitmapJob?.cancel()
        syncServer.stop()
        super.onDestroy()
    }

    // ── Build UI ──

    private fun buildUi() {
        rootLayout.removeAllViews()
        rootLayout.addView(buildTitleBar())
        rootLayout.addView(buildDashboardCard())
        rootLayout.addView(buildProgressCard())
        rootLayout.addView(buildAppUpdateCard())
        rootLayout.addView(buildCoreSectionHeader())
        coreLoadingView = TextView(this).apply {
            text = "正在读取核心信息..."
            textSize = 14f
            setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
        }
        rootLayout.addView(coreLoadingView, marginLp(top = 6))
        coreContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        rootLayout.addView(coreContainer, marginLp(top = 10))
        rootLayout.addView(buildSyncCard())
    }

    // ── Title Bar ──

    private fun buildTitleBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = marginLp(bottom = 18)
        }
        val title = TextView(this).apply {
            text = "DanmuApi"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurface))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        bar.addView(title)

        // 暗色/亮色切换按钮
        val themeBtn = makeButton(nightModeLabel(), primary = false).apply {
            textSize = 13f
            minHeight = dp(38)
            minimumHeight = dp(38)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener { toggleNightMode() }
        }
        themeModeButton = themeBtn
        bar.addView(themeBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = dp(10) })

        statusPill = TextView(this).apply {
            text = "读取中"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(5), dp(14), dp(5))
            setTextColor(0xFFFFFFFF.toInt())
            background = GradientDrawable().apply {
                cornerRadius = dp(999).toFloat()
                setColor(0xFF6B7280.toInt())
            }
        }
        bar.addView(statusPill, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        return bar
    }

    // ── Dashboard Card ──

    private fun buildDashboardCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = ContextCompat.getDrawable(this@CompatModeActivity, R.drawable.compat_card_panel)
            layoutParams = marginLp(bottom = 14)
        }

        // 2x3 grid
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 3f
        }
        tileCoreValue = addTile(row1, "当前核心", "--")
        tileVersionValue = addTile(row1, "核心版本", "--")
        tileModeValue = addTile(row1, "运行模式", "--")
        grid.addView(row1)

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 3f
            layoutParams = marginLp(top = 10)
        }
        tilePortValue = addTile(row2, "端口", "--")
        tileLocalValue = addTile(row2, "本机地址", "--")
        tileLanValue = addTile(row2, "局域网地址", "--")
        grid.addView(row2)

        card.addView(grid)

        // Action buttons
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 3f
            layoutParams = marginLp(top = 18)
        }
        btnStart = makeButton("启动服务", primary = true).apply {
            setOnClickListener { startService() }
        }
        btnRestart = makeButton("重启服务", primary = false).apply {
            setOnClickListener { graph.runtimeRepository.restartService() }
        }
        btnStop = makeButton("停止服务", primary = false).apply {
            setOnClickListener { graph.runtimeRepository.stopService() }
        }
        btnRow.addView(btnStart, weightLp(1f, end = 6))
        btnRow.addView(btnRestart, weightLp(1f, end = 6))
        btnRow.addView(btnStop, weightLp(1f))
        card.addView(btnRow)

        return card
    }

    private fun addTile(parent: LinearLayout, label: String, defaultValue: String): TextView {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = ContextCompat.getDrawable(this@CompatModeActivity, R.drawable.compat_tile_background)
        }
        tile.addView(TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
        })
        val valueView = TextView(this).apply {
            text = defaultValue
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurface))
            setPadding(0, dp(3), 0, 0)
            maxLines = 1
        }
        tile.addView(valueView)
        parent.addView(tile, weightLp(1f, end = 8))
        return valueView
    }

    // ── Progress Card ──

    private fun buildProgressCard(): View {
        progressCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = ContextCompat.getDrawable(this@CompatModeActivity, R.drawable.compat_card_panel)
            visibility = View.GONE
            layoutParams = marginLp(bottom = 14)
        }
        progressTitle = TextView(this).apply {
            text = "处理中"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurface))
        }
        progressCard.addView(progressTitle)
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            layoutParams = marginLp(top = 10)
        }
        progressCard.addView(progressBar)
        progressDetail = TextView(this).apply {
            textSize = 13f
            setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            layoutParams = marginLp(top = 8)
        }
        progressCard.addView(progressDetail)
        return progressCard
    }

    // ── App Update Card ──

    private fun buildAppUpdateCard(): View {
        appUpdateCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = ContextCompat.getDrawable(this@CompatModeActivity, R.drawable.compat_card_panel)
            layoutParams = marginLp(bottom = 14)
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        headerRow.addView(TextView(this).apply {
            text = "应用版本"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurface))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        val checkBtn = makeButton("检查更新", primary = false).apply {
            textSize = 13f
            minHeight = dp(38)
            minimumHeight = dp(38)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener { checkAppUpdate() }
        }
        headerRow.addView(checkBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        appUpdateCard.addView(headerRow)

        appUpdateTitle = TextView(this).apply {
            text = "当前版本: v${graph.appUpdateService.currentVersionName()}"
            textSize = 14f
            setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            layoutParams = marginLp(top = 8)
        }
        appUpdateCard.addView(appUpdateTitle)

        appUpdateNotes = TextView(this).apply {
            textSize = 13f
            setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            maxLines = 6
            visibility = View.GONE
            layoutParams = marginLp(top = 6)
        }
        appUpdateCard.addView(appUpdateNotes)

        appUpdateProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
            layoutParams = marginLp(top = 10)
        }
        appUpdateCard.addView(appUpdateProgress)

        appUpdateProgressText = TextView(this).apply {
            textSize = 12f
            setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            visibility = View.GONE
            layoutParams = marginLp(top = 4)
        }
        appUpdateCard.addView(appUpdateProgressText)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            layoutParams = marginLp(top = 14)
        }
        btnAppUpdate = makeButton("下载更新", primary = true).apply {
            setOnClickListener { downloadAppUpdate() }
        }
        btnAppInstall = makeButton("安装更新", primary = true).apply {
            visibility = View.GONE
            setOnClickListener { installAppUpdate() }
        }
        btnRow.addView(btnAppUpdate, weightLp(1f, end = 8))
        btnRow.addView(btnAppInstall, weightLp(1f))
        appUpdateCard.addView(btnRow)

        return appUpdateCard
    }

    // ── Core Section ──

    private fun buildCoreSectionHeader(): View {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = marginLp(top = 10)
        }
        header.addView(TextView(this).apply {
            text = "核心管理"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurface))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        val refreshBtn = makeButton("刷新", primary = false).apply {
            setOnClickListener {
                graph.coreRepository.refreshCoreInfo()
                toast("正在刷新核心信息")
            }
        }
        header.addView(refreshBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        return header
    }

    // ── Sync Card ──

    private fun buildSyncCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = ContextCompat.getDrawable(this@CompatModeActivity, R.drawable.compat_card_panel)
            layoutParams = marginLp(top = 14)
        }
        card.addView(TextView(this).apply {
            text = "手机同步"
            textSize = 19f
            setTypeface(null, Typeface.BOLD)
            setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurface))
        })

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = marginLp(top = 14)
        }

        syncQrView = ImageView(this).apply {
            adjustViewBounds = true
            background = ContextCompat.getDrawable(this@CompatModeActivity, R.drawable.compat_qr_surface)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        content.addView(syncQrView, LinearLayout.LayoutParams(dp(172), dp(172)))

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }
        syncHintView = TextView(this).apply {
            textSize = 14f
            setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
        }
        info.addView(syncHintView)
        syncStatusView = TextView(this).apply {
            textSize = 15f
            setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurface))
            layoutParams = marginLp(top = 10)
        }
        info.addView(syncStatusView)
        syncUrlView = TextView(this).apply {
            textSize = 13f
            setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            layoutParams = marginLp(top = 10)
        }
        info.addView(syncUrlView)
        content.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        card.addView(content)
        return card
    }

    // ── Core Card Builder ──

    private fun buildCoreCard(info: CoreInfo): View {
        val primaryColor = themeColor(androidx.appcompat.R.attr.colorPrimary)
        val surfaceColor = themeColor(com.google.android.material.R.attr.colorSurfaceContainerHigh)
        val onSurface = themeColor(com.google.android.material.R.attr.colorOnSurface)
        val secondary = themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        val isActive = runtimeState.variant == info.variant
        val highlightColor = ColorUtils.blendARGB(surfaceColor, primaryColor, 0.10f)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(if (isActive) highlightColor else surfaceColor)
                setStroke(dp(1), themeColor(com.google.android.material.R.attr.colorOutlineVariant))
            }
            layoutParams = marginLp(bottom = 12)
        }

        // Header row: name + badge
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val nameCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        nameCol.addView(TextView(this).apply {
            text = resolveVariantLabel(info.variant)
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(onSurface)
        })
        nameCol.addView(TextView(this).apply {
            text = coreVersionText(info)
            textSize = 13f
            setTextColor(if (info.hasUpdate) primaryColor else secondary)
            setPadding(0, dp(2), 0, 0)
        })
        val repoText = resolveVariantRepo(info.variant).ifBlank {
            if (info.variant == ApiVariant.Custom) "未配置仓库" else ""
        }
        if (repoText.isNotBlank()) {
            nameCol.addView(TextView(this).apply {
                text = repoText
                textSize = 12f
                setTextColor(secondary)
                setPadding(0, dp(2), 0, 0)
            })
        }
        header.addView(nameCol)

        // Status badge
        val badgeText = when {
            isActive -> "使用中"
            info.hasUpdate -> "可更新"
            info.isInstalled -> "已安装"
            else -> "未安装"
        }
        val badgeColor = when {
            isActive -> primaryColor
            info.hasUpdate -> themeColor(com.google.android.material.R.attr.colorTertiary)
            else -> secondary
        }
        header.addView(TextView(this).apply {
            text = badgeText
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(4), dp(10), dp(4))
            setTextColor(badgeColor)
            background = GradientDrawable().apply {
                cornerRadius = dp(999).toFloat()
                setColor(ColorUtils.setAlphaComponent(badgeColor, 30))
            }
        })
        card.addView(header)

        // Custom repo input
        if (info.variant == ApiVariant.Custom) {
            val inputRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = marginLp(top = 12)
            }
            val editText = EditText(this).apply {
                hint = "owner/repo"
                textSize = 14f
                inputType = InputType.TYPE_CLASS_TEXT
                setSingleLine(true)
                setPadding(dp(14), dp(10), dp(14), dp(10))
                background = ContextCompat.getDrawable(this@CompatModeActivity, R.drawable.compat_edittext_background)
                setText(graph.settingsRepository.customRepo.value)
                setTextColor(onSurface)
                setHintTextColor(ColorUtils.setAlphaComponent(secondary, 120))
            }
            inputRow.addView(editText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val saveBtn = makeButton("确认", primary = false).apply {
                setOnClickListener {
                    val repo = editText.text.toString().trim()
                    graph.settingsRepository.setCustomRepo(repo)
                    graph.coreRepository.refreshCoreInfo()
                    toast(if (repo.isNotBlank()) "已保存仓库: $repo" else "已清除自定义仓库")
                }
            }
            inputRow.addView(saveBtn, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(8) })
            card.addView(inputRow)
        }

        // Action buttons
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = marginLp(top = 14)
        }

        // Switch button (not for active variant)
        if (!isActive) {
            btnRow.addView(makeButton("切换使用", primary = false).apply {
                isEnabled = info.isInstalled && !isOperating
                alpha = if (isEnabled) 1f else 0.48f
                setOnClickListener { switchVariant(info.variant) }
            }, weightLp(1f, end = 6))
        }

        // Main action: install / update / check
        val mainText = when {
            !info.isInstalled -> "下载核心"
            info.hasUpdate -> "立即更新"
            else -> "检查更新"
        }
        val mainPrimary = !info.isInstalled || info.hasUpdate
        btnRow.addView(makeButton(mainText, primary = mainPrimary).apply {
            isEnabled = !isOperating
            alpha = if (isEnabled) 1f else 0.48f
            setOnClickListener {
                when {
                    !info.isInstalled -> installCore(info.variant)
                    info.hasUpdate -> updateCore(info.variant)
                    else -> checkUpdate(info.variant)
                }
            }
        }, weightLp(1f, end = if (info.isInstalled) 6 else 0))

        // Delete button (only for installed cores)
        if (info.isInstalled) {
            btnRow.addView(makeDangerButton("删除").apply {
                isEnabled = !isOperating && !isActive
                alpha = if (isEnabled) 1f else 0.48f
                setOnClickListener { deleteCore(info.variant) }
            }, weightLp(1f))
        }

        card.addView(btnRow)
        return card
    }

    // ── Observe State ──

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    graph.runtimeRepository.runtimeState.collectLatest {
                        runtimeState = it
                        syncServer.updateHost(resolveSyncHost(it))
                        renderDashboard()
                    }
                }
                launch {
                    graph.coreRepository.coreInfoList.collectLatest {
                        coreInfos = it
                        renderDashboard()
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
                    graph.settingsRepository.customRepo.collectLatest { renderCoreList() }
                }
                launch {
                    graph.settingsRepository.customRepoDisplayName.collectLatest {
                        renderDashboard()
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

    // ── Render Methods ──

    private fun renderDashboard() {
        val status = runtimeState.status
        statusPill.text = statusLabel(status)
        (statusPill.background as? GradientDrawable)?.setColor(statusColor(status))

        tileCoreValue.text = resolveVariantLabel(runtimeState.variant)
        tileVersionValue.text = currentCoreVersionText()
        tileModeValue.text = "兼容模式"
        tilePortValue.text = runtimeState.port.toString()
        tileLocalValue.text = runtimeState.localUrl.ifBlank { "--" }
        tileLanValue.text = runtimeState.lanUrl.ifBlank { "--" }

        val running = status == ServiceStatus.Running
        setButtonEnabled(btnStart, !running && !isOperating)
        setButtonEnabled(btnRestart, running && !isOperating)
        setButtonEnabled(btnStop, running && !isOperating)
    }

    private fun renderProgressCard() {
        val visible = downloadProgress.inProgress || isOperating
        progressCard.isVisible = visible
        if (!visible) return

        val label = downloadProgress.actionLabel.ifBlank {
            operationProgressTitle.ifBlank { if (isOperating) "处理中" else "下载中" }
        }
        progressTitle.text = label
        progressDetail.text = buildString {
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

    private fun renderAppUpdateCard() {
        val result = appCheckResult
        val hasUpdate = result != null && result.hasUpdate
        val hasApk = downloadedApk != null

        if (hasUpdate) {
            appUpdateTitle.text = "发现新版本 v${result.latestVersion}（当前 v${result.currentVersion}）"
            appUpdateTitle.setTextColor(themeColor(androidx.appcompat.R.attr.colorPrimary))
            val notes = result.releaseNotes.ifBlank { "（无更新说明）" }
            appUpdateNotes.text = notes
            appUpdateNotes.isVisible = true
        } else {
            val currentVer = result?.currentVersion ?: graph.appUpdateService.currentVersionName()
            appUpdateTitle.text = "当前版本: v$currentVer"
            appUpdateTitle.setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            appUpdateNotes.isVisible = false
        }

        appUpdateProgress.isVisible = isAppDownloading
        appUpdateProgressText.isVisible = isAppDownloading
        if (isAppDownloading) {
            if (appDownloadPercent in 0..100) {
                appUpdateProgress.isIndeterminate = false
                appUpdateProgress.progress = appDownloadPercent
            } else {
                appUpdateProgress.isIndeterminate = true
            }
            appUpdateProgressText.text = appDownloadDetail
        }

        // 按钮行：有更新时显示
        val btnRow = btnAppUpdate.parent as? ViewGroup
        btnRow?.isVisible = hasUpdate || hasApk || isAppDownloading
        btnAppUpdate.isVisible = hasUpdate && !hasApk
        setButtonEnabled(btnAppUpdate, !isAppDownloading)
        btnAppInstall.isVisible = hasApk
    }

    private fun renderCoreList() {
        coreLoadingView.isVisible = isCoreInfoLoading
        coreContainer.removeAllViews()
        coreInfos.forEach { info ->
            coreContainer.addView(buildCoreCard(info))
        }
    }

    private fun renderSyncCard() {
        val inviteUrl = syncUiState.inviteUrl
        val ready = inviteUrl.isNotBlank()
        syncHintView.text = if (ready) {
            "手机端进入「设置 > 备份与恢复」，点击「扫码同步到电视」即可推送当前配置。"
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

    // ── Actions ──

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

    private fun deleteCore(variant: ApiVariant) {
        if (isOperating) return
        if (runtimeState.variant == variant) {
            toast("当前正在使用此核心，请先切换到其他核心再删除")
            return
        }
        performCoreOperation("正在删除 ${resolveVariantLabel(variant)}") {
            graph.coreRepository.deleteCore(variant).fold(
                onSuccess = {
                    graph.coreRepository.refreshCoreInfo()
                    toast("${resolveVariantLabel(variant)} 已删除")
                },
                onFailure = {
                    toast("${resolveVariantLabel(variant)} 删除失败：${it.message ?: "未知错误"}")
                }
            )
        }
    }

    private fun performCoreOperation(title: String, block: suspend () -> Unit) {
        if (isOperating) return
        isOperating = true
        operationProgressTitle = title
        renderDashboard()
        renderProgressCard()
        renderCoreList()
        lifecycleScope.launch {
            try {
                block()
            } finally {
                isOperating = false
                operationProgressTitle = ""
                renderDashboard()
                renderProgressCard()
                renderCoreList()
            }
        }
    }

    private fun canOperateVariant(variant: ApiVariant): Boolean {
        if (variant != ApiVariant.Custom) return true
        if (graph.settingsRepository.customRepo.value.trim().isNotBlank()) return true
        toast("自定义版未配置仓库，请先输入仓库地址")
        return false
    }

    // ── App Update Actions ──

    private fun checkAppUpdate() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                graph.appUpdateService.checkLatestRelease()
            }
            result.onSuccess { info ->
                appCheckResult = info
                renderAppUpdateCard()
            }
        }
    }

    private fun downloadAppUpdate() {
        val result = appCheckResult ?: return
        if (isAppDownloading) return
        if (result.downloadUrls.isEmpty()) {
            toast("未找到适合当前设备架构的安装包")
            return
        }
        isAppDownloading = true
        appDownloadPercent = -1
        appDownloadDetail = "准备下载..."
        downloadedApk = null
        renderAppUpdateCard()

        lifecycleScope.launch {
            val downloadResult = withContext(Dispatchers.IO) {
                graph.appUpdateService.downloadApk(
                    urls = result.downloadUrls,
                    version = result.latestVersion
                ) { soFar, total ->
                    runOnUiThread {
                        if (total > 0) {
                            appDownloadPercent = ((soFar * 100f) / total).toInt().coerceIn(0, 100)
                            appDownloadDetail = "${formatBytes(soFar)} / ${formatBytes(total)}"
                        } else {
                            appDownloadPercent = -1
                            appDownloadDetail = "已下载 ${formatBytes(soFar)}"
                        }
                        renderAppUpdateCard()
                    }
                }
            }
            isAppDownloading = false
            downloadResult.fold(
                onSuccess = { apk ->
                    downloadedApk = apk
                    toast("下载完成：${apk.displayName}")
                },
                onFailure = {
                    toast("下载失败：${it.message ?: "请稍后重试"}")
                }
            )
            renderAppUpdateCard()
        }
    }

    private fun installAppUpdate() {
        val apk = downloadedApk ?: return
        when (val result = graph.appUpdateService.installApk(this, apk)) {
            is AppUpdateService.InstallResult.Launched -> {
                toast("已打开安装器，请按系统提示完成安装")
            }
            is AppUpdateService.InstallResult.NeedUnknownSourcePermission -> {
                toast("请完成「安装未知应用」授权，返回后将自动续装")
            }
            is AppUpdateService.InstallResult.Failed -> {
                toast(result.message)
            }
        }
    }

    // ── Night Mode ──

    private fun nightModeLabel(): String {
        return when (graph.settingsRepository.nightMode.value) {
            NightModePreference.Dark -> "亮色"
            NightModePreference.Light -> "暗色"
            NightModePreference.FollowSystem -> "暗色"
        }
    }

    private fun toggleNightMode() {
        val current = graph.settingsRepository.nightMode.value
        val next = when (current) {
            NightModePreference.Dark -> NightModePreference.Light
            NightModePreference.Light -> NightModePreference.Dark
            NightModePreference.FollowSystem -> NightModePreference.Dark
        }
        graph.settingsRepository.setNightMode(next)
        themeModeButton.text = when (next) {
            NightModePreference.Dark -> "亮色"
            NightModePreference.Light -> "暗色"
            NightModePreference.FollowSystem -> "暗色"
        }
    }

    // ── Helpers ──

    private fun currentCoreVersionText(): String {
        val info = coreInfos.find { it.variant == runtimeState.variant }
        return coreVersionText(info)
    }

    private fun coreVersionText(info: CoreInfo?): String {
        return when {
            info == null -> if (isCoreInfoLoading) "读取中" else "未知"
            !info.isInstalled -> "未安装"
            info.hasUpdate && !info.version.isNullOrBlank() && !info.latestVersion.isNullOrBlank() ->
                "v${info.version} → v${info.latestVersion}"
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

    private fun statusLabel(status: ServiceStatus): String = when (status) {
        ServiceStatus.Stopped -> "已停止"
        ServiceStatus.Starting -> "启动中"
        ServiceStatus.Running -> "运行中"
        ServiceStatus.Stopping -> "停止中"
        ServiceStatus.Error -> "异常"
    }

    private fun statusColor(status: ServiceStatus): Int = when (status) {
        ServiceStatus.Stopped -> 0xFF6B7280.toInt()
        ServiceStatus.Starting -> 0xFF2563EB.toInt()
        ServiceStatus.Running -> 0xFF16A34A.toInt()
        ServiceStatus.Stopping -> 0xFFF59E0B.toInt()
        ServiceStatus.Error -> 0xFFDC2626.toInt()
    }

    // ── View Factories ──

    private fun makeButton(text: String, primary: Boolean): Button {
        return Button(this).apply {
            this.text = text
            isAllCaps = false
            minHeight = dp(46)
            minimumHeight = dp(46)
            maxLines = 1
            textSize = 14f
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = ContextCompat.getDrawable(
                this@CompatModeActivity,
                if (primary) R.drawable.compat_button_primary else R.drawable.compat_button_secondary
            )
            setTextColor(
                if (primary) 0xFFFFFFFF.toInt()
                else themeColor(com.google.android.material.R.attr.colorOnSurface)
            )
            stateListAnimator = null
            setOnFocusChangeListener { view, hasFocus ->
                view.animate()
                    .scaleX(if (hasFocus) 1.04f else 1f)
                    .scaleY(if (hasFocus) 1.04f else 1f)
                    .translationZ(if (hasFocus) dp(8).toFloat() else 0f)
                    .setDuration(120L)
                    .start()
            }
        }
    }

    private fun makeDangerButton(text: String): Button {
        return Button(this).apply {
            this.text = text
            isAllCaps = false
            minHeight = dp(46)
            minimumHeight = dp(46)
            maxLines = 1
            textSize = 14f
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = ContextCompat.getDrawable(this@CompatModeActivity, R.drawable.compat_button_danger)
            setTextColor(0xFFDC2626.toInt())
            stateListAnimator = null
            setOnFocusChangeListener { view, hasFocus ->
                view.animate()
                    .scaleX(if (hasFocus) 1.04f else 1f)
                    .scaleY(if (hasFocus) 1.04f else 1f)
                    .translationZ(if (hasFocus) dp(8).toFloat() else 0f)
                    .setDuration(120L)
                    .start()
            }
        }
    }

    private fun setButtonEnabled(button: Button, enabled: Boolean) {
        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else 0.48f
    }

    // ── Layout Helpers ──

    private fun marginLp(
        top: Int = 0, bottom: Int = 0, start: Int = 0, end: Int = 0
    ): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(top)
            bottomMargin = dp(bottom)
            marginStart = dp(start)
            marginEnd = dp(end)
        }
    }

    private fun weightLp(weight: Float, end: Int = 0): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight).apply {
            marginEnd = dp(end)
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

    private fun themeColor(attr: Int): Int {
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
