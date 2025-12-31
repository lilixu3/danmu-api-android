package com.example.danmuapiapp

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.content.res.Configuration
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatDelegate
import android.widget.FrameLayout
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL

class MainActivity : AppCompatActivity() {

    companion object {
        private const val MAIN_PORT = 9321
        private const val PROJECT_URL = "https://github.com/lilixu3/danmu-api-android"
        private const val UPSTREAM_URL = "https://github.com/huangxd-/danmu_api"

        const val PREFS_UI = "ui_prefs"
        const val KEY_DARK_MODE = "dark_mode"

        @Volatile
        private var pendingThemeFade = false
    }

    private lateinit var toolbar: MaterialToolbar
    private lateinit var statusIndicator: View
    private lateinit var chipStatus: Chip
    private lateinit var tvStatusTitle: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnStart: MaterialButton
    private lateinit var btnStop: MaterialButton
    private lateinit var btnBattery: View
    private lateinit var cardQuickSettings: View

    private lateinit var rowTheme: View
    private lateinit var switchTheme: MaterialSwitch
    private lateinit var tvThemeHint: TextView

    private lateinit var cardUrls: View
    private lateinit var layoutLanUrl: View
    private lateinit var layoutLocalUrl: View
    private lateinit var tvLanUrl: TextView
    private lateinit var tvLocalUrl: TextView
    private lateinit var btnOpenUrl: MaterialButton
    private lateinit var btnCopyUrl: MaterialButton

    private var preferredUrl: String? = null
    private var statusAnimator: ValueAnimator? = null

    private val requestNotifPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        startNodeServiceWithUiHint()
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            if (intent.action != NodeService.ACTION_NODE_STATUS) return
            val status = intent.getStringExtra(NodeService.EXTRA_STATUS) ?: return
            val msg = intent.getStringExtra(NodeService.EXTRA_MESSAGE) ?: ""
            when (status) {
                NodeService.STATUS_STARTING -> setUiStarting(msg)
                NodeService.STATUS_RUNNING -> setUiRunning(msg)
                NodeService.STATUS_STOPPED -> setUiStopped(msg)
                NodeService.STATUS_ERROR -> setUiError(msg)
                NodeService.STATUS_ALREADY_RUNNING -> setUiRunning(msg)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Smooth theme switching: fade-in on recreate to avoid an "abrupt" flash
        if (pendingThemeFade) {
            window.decorView.alpha = 0f
        }

        // Immersive system bars + correct icon color for light/dark
        applySystemBars()

        setContentView(R.layout.activity_main)

        // 处理系统栏 insets
        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, sys.top, 0, sys.bottom)
            insets
        }

        initViews()
        setupListeners()
        setupToolbarActionViews()
        setupThemeControls()
        refreshUiFromServiceState()

        // 检查更新（后台静默）
        UpdateChecker.check(this)

        if (pendingThemeFade) {
            pendingThemeFade = false
            window.decorView.animate().alpha(1f).setDuration(180).start()
        }
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        statusIndicator = findViewById(R.id.statusIndicator)
        chipStatus = findViewById(R.id.chipStatus)
        tvStatusTitle = findViewById(R.id.tvStatusTitle)
        tvStatus = findViewById(R.id.tvStatus)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        btnBattery = findViewById(R.id.btnBattery)
        cardQuickSettings = findViewById(R.id.cardQuickSettings)

        rowTheme = findViewById(R.id.rowTheme)
        switchTheme = findViewById(R.id.switchTheme)
        tvThemeHint = findViewById(R.id.tvThemeHint)

        cardUrls = findViewById(R.id.cardUrls)
        layoutLanUrl = findViewById(R.id.layoutLanUrl)
        layoutLocalUrl = findViewById(R.id.layoutLocalUrl)
        tvLanUrl = findViewById(R.id.tvLanUrl)
        tvLocalUrl = findViewById(R.id.tvLocalUrl)
        btnOpenUrl = findViewById(R.id.btnOpenUrl)
        btnCopyUrl = findViewById(R.id.btnCopyUrl)
    }

    private fun setupListeners() {
        btnStart.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33) {
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestNotifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                    return@setOnClickListener
                }
            }
            startNodeServiceWithUiHint()
        }

        btnStop.setOnClickListener {
            showStopConfirmDialog()
        }

        btnBattery.setOnClickListener {
            openBatteryOptimization()
        }

        // Menu click fallback (e.g. overflow / non-action-view situations)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_settings -> {
                    showSettingsSheet()
                    true
                }
                R.id.action_check_update -> {
                    UpdateChecker.check(this, userInitiated = true)
                    true
                }
                R.id.action_project -> {
                    showProjectJump()
                    true
                }
                else -> false
            }
        }

        // URL 点击事件
        layoutLanUrl.setOnClickListener { openUrlSafely(tvLanUrl.text.toString()) }
        layoutLocalUrl.setOnClickListener { openUrlSafely(tvLocalUrl.text.toString()) }
        layoutLanUrl.setOnLongClickListener { copyUrl(tvLanUrl.text.toString()); true }
        layoutLocalUrl.setOnLongClickListener { copyUrl(tvLocalUrl.text.toString()); true }

        btnOpenUrl.setOnClickListener { preferredUrl?.let { openUrlSafely(it) } }
        btnCopyUrl.setOnClickListener { preferredUrl?.let { copyUrl(it) } }
    }

    override fun onStart() {
        super.onStart()
        registerStatusReceiver()
        refreshUiFromServiceState()
    }

    override fun onStop() {
        super.onStop()
        statusAnimator?.cancel()
        try {
            unregisterReceiver(statusReceiver)
        } catch (_: Throwable) {
        }
    }

    private fun refreshUiFromServiceState() {
        updateBatteryActionVisibility()
        if (NodeService.isRunning()) {
            setUiRunning("服务正在后台运行，可通过下方地址访问")
        } else {
            setUiStopped("点击启动按钮，开始运行弹幕 API 服务")
        }
    }

    private fun setupToolbarActionViews() {
        // Make toolbar actions look like subtle, modern icon buttons.
        runCatching {
            (toolbar.menu.findItem(R.id.action_settings)?.actionView as? MaterialButton)?.setOnClickListener {
                showSettingsSheet()
            }
        }
    }

    private fun showSettingsSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_settings, null)
        dialog.setContentView(view)

        dialog.setOnShowListener {
            // Apply custom rounded glass background to the bottom sheet container.
            val sheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            sheet?.setBackgroundResource(R.drawable.bg_bottom_sheet)
        }

        // Theme toggle
        val prefs = getSharedPreferences(PREFS_UI, MODE_PRIVATE)
        val storedDark = prefs.getBoolean(KEY_DARK_MODE, isNightMode())

        val rowThemeSheet = view.findViewById<View>(R.id.rowThemeSheet)
        val switchThemeSheet = view.findViewById<MaterialSwitch>(R.id.switchThemeSheet)
        val tvThemeHintSheet = view.findViewById<TextView>(R.id.tvThemeHintSheet)

        switchThemeSheet.setOnCheckedChangeListener(null)
        switchThemeSheet.isChecked = storedDark
        tvThemeHintSheet.text = if (storedDark) "已开启" else "已关闭"

        rowThemeSheet.setOnClickListener { switchThemeSheet.toggle() }
        switchThemeSheet.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_DARK_MODE, isChecked).apply()
            tvThemeHintSheet.text = if (isChecked) "已开启" else "已关闭"

            // Keep main-page quick switch in sync (if visible)
            runCatching {
                switchTheme.setOnCheckedChangeListener(null)
                switchTheme.isChecked = isChecked
                updateThemeHint(isChecked)
                switchTheme.setOnCheckedChangeListener { _, checked ->
                    prefs.edit().putBoolean(KEY_DARK_MODE, checked).apply()
                    updateThemeHint(checked)

                    val nowNight = isNightMode()
                    if (checked != nowNight) {
                        pendingThemeFade = true
                        AppCompatDelegate.setDefaultNightMode(
                            if (checked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                        )
                        recreate()
                    }
                }
            }

            val nowNight = isNightMode()
            if (isChecked != nowNight) {
                pendingThemeFade = true
                AppCompatDelegate.setDefaultNightMode(
                    if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                )
                dialog.dismiss()
                recreate()
            }
        }

        // About
        view.findViewById<View>(R.id.rowAbout).setOnClickListener {
            dialog.dismiss()
            showAboutDialog()
        }

        // Update
        view.findViewById<View>(R.id.rowCheckUpdate).setOnClickListener {
            UpdateChecker.check(this, userInitiated = true)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun setupThemeControls() {
        val prefs = getSharedPreferences(PREFS_UI, MODE_PRIVATE)
        val storedDark = prefs.getBoolean(KEY_DARK_MODE, isNightMode())

        // Avoid triggering listener when restoring
        switchTheme.setOnCheckedChangeListener(null)
        switchTheme.isChecked = storedDark
        updateThemeHint(storedDark)

        rowTheme.setOnClickListener {
            switchTheme.toggle()
        }

        switchTheme.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_DARK_MODE, isChecked).apply()
            updateThemeHint(isChecked)

            val nowNight = isNightMode()
            if (isChecked != nowNight) {
                pendingThemeFade = true
                AppCompatDelegate.setDefaultNightMode(
                    if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                )
                recreate()
            }
        }
    }

    private fun updateThemeHint(dark: Boolean) {
        tvThemeHint.text = if (dark) "深色模式" else "浅色模式"
    }

    private fun isNightMode(): Boolean {
        val mask = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mask == Configuration.UI_MODE_NIGHT_YES
    }

    private fun applySystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        val isLight = !isNightMode()
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = isLight
        controller.isAppearanceLightNavigationBars = isLight
    }

    private fun startNodeServiceWithUiHint() {
        setUiStarting("正在启动服务，请稍候...")
        startNodeService()
        Toast.makeText(this, "服务启动中...", Toast.LENGTH_SHORT).show()
        maybePromptBatteryOptimization()
    }

    private fun startNodeService() {
        val intent = Intent(this, NodeService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun showStopConfirmDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("停止服务")
            .setMessage("确定要停止弹幕 API 服务吗？应用将会退出。")
            .setPositiveButton("确定") { _, _ ->
                stopAndExitApp()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun stopAndExitApp() {
        btnStart.isEnabled = false
        btnStop.isEnabled = false
        tvStatus.text = "正在关闭服务..."

        Thread {
            try {
                requestNodeShutdown()
            } catch (_: Throwable) {
            }

            try {
                stopService(Intent(this, NodeService::class.java))
            } catch (_: Throwable) {
            }

            runOnUiThread {
                Toast.makeText(this, "服务已关闭，应用即将退出", Toast.LENGTH_SHORT).show()
                finishAndRemoveTask()
            }

            try {
                Thread.sleep(900)
            } catch (_: Throwable) {
            }
            android.os.Process.killProcess(android.os.Process.myPid())
        }.start()
    }

    private fun requestNodeShutdown() {
        val url = URL("http://127.0.0.1:$MAIN_PORT/__shutdown")
        val conn = (url.openConnection() as HttpURLConnection)
        conn.requestMethod = "GET"
        conn.instanceFollowRedirects = false
        conn.connectTimeout = 800
        conn.readTimeout = 800
        conn.doInput = true
        try {
            conn.connect()
            runCatching { conn.inputStream.use { it.readBytes() } }
        } finally {
            conn.disconnect()
        }
    }

    private fun updateUrls(visible: Boolean) {
        if (!visible) {
            preferredUrl = null
            if (cardUrls.visibility == View.VISIBLE) {
                cardUrls.animate().cancel()
                cardUrls.animate()
                    .alpha(0f)
                    .translationY(dp(12).toFloat())
                    .setDuration(180)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .withEndAction {
                        cardUrls.visibility = View.GONE
                        cardUrls.alpha = 1f
                        cardUrls.translationY = 0f
                    }
                    .start()
            } else {
                cardUrls.visibility = View.GONE
                cardUrls.alpha = 1f
                cardUrls.translationY = 0f
            }
            return
        }

        if (cardUrls.visibility != View.VISIBLE) {
            cardUrls.animate().cancel()
            cardUrls.alpha = 0f
            cardUrls.translationY = dp(12).toFloat()
            cardUrls.visibility = View.VISIBLE
            cardUrls.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }

        val lanIp = getLanIpv4()
        val lanUrl = if (lanIp != null) "http://$lanIp:$MAIN_PORT/" else null
        val localUrl = "http://127.0.0.1:$MAIN_PORT/"

        preferredUrl = lanUrl ?: localUrl

        if (lanUrl != null) {
            tvLanUrl.text = lanUrl
            layoutLanUrl.isEnabled = true
            layoutLanUrl.alpha = 1f
        } else {
            tvLanUrl.text = "未检测到局域网连接"
            layoutLanUrl.isEnabled = false
            layoutLanUrl.alpha = 0.5f
        }

        tvLocalUrl.text = localUrl
    }

    private fun openUrlSafely(url: String) {
        val u = url.trim()
        if (!u.startsWith("http://") && !u.startsWith("https://")) return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
        } catch (t: Throwable) {
            Toast.makeText(this, "无法打开浏览器", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyUrl(url: String) {
        val u = url.trim()
        if (!u.startsWith("http://") && !u.startsWith("https://")) return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("url", u))
        Toast.makeText(this, "地址已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }

    private fun getLanIpv4(): String? {
        try {
            val itf = NetworkInterface.getNetworkInterfaces() ?: return null
            for (ni in itf) {
                if (!ni.isUp || ni.isLoopback) continue
                val name = (ni.name ?: "").lowercase()
                val prefer = name.startsWith("wlan") || name.startsWith("eth") || name.startsWith("en")
                val addrs = ni.inetAddresses ?: continue
                for (addr in addrs) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        if (ip.startsWith("169.254.")) continue
                        if (prefer) return ip
                    }
                }
            }
            val itf2 = NetworkInterface.getNetworkInterfaces() ?: return null
            for (ni in itf2) {
                if (!ni.isUp || ni.isLoopback) continue
                val addrs = ni.inetAddresses ?: continue
                for (addr in addrs) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        if (ip.startsWith("169.254.")) continue
                        return ip
                    }
                }
            }
        } catch (_: Throwable) {
        }
        return null
    }

    private fun updateBatteryActionVisibility() {
        // 电池“不受限制/忽略优化”已设置时，不再展示该入口（让界面更干净）
        val done = isIgnoringBatteryOptimizations()
        btnBattery.visibility = if (done) View.GONE else View.VISIBLE

        // Quick settings card stays, because it also hosts the appearance switch
        cardQuickSettings.visibility = View.VISIBLE
    }

    private fun dp(value: Int): Float = value * resources.displayMetrics.density

    private fun setStartButtonState(text: String, enabled: Boolean, iconRes: Int) {
        // Keep the "running => 已启用且不可点" small detail, while letting the new
        // modern disabled color-states handle the look.
        btnStart.text = text
        btnStart.isEnabled = enabled
        btnStart.alpha = 1f
        btnStart.setIconResource(iconRes)
    }

    private fun setStopButtonEnabled(enabled: Boolean) {
        btnStop.isEnabled = enabled
        btnStop.alpha = 1f
    }

    private fun setUiStarting(message: String) {
        updateStatusIndicator(R.color.status_warning, true)
        updateStatusChip("启动中", R.color.status_warning)
        tvStatusTitle.text = "正在启动"
        tvStatus.text = message
        setStartButtonState("启动中…", false, R.drawable.ic_hourglass_24)
        setStopButtonEnabled(true)
        updateUrls(false)
    }

    private fun setUiRunning(message: String) {
        updateStatusIndicator(R.color.status_success, true)
        updateStatusChip("运行中", R.color.status_success)
        tvStatusTitle.text = "服务运行中"
        tvStatus.text = message
        // 服务运行中：按钮改为“已启用”并禁止点击，避免重复触发
        setStartButtonState("已启用", false, R.drawable.ic_check_24)
        setStopButtonEnabled(true)
        updateUrls(true)
    }

    private fun setUiStopped(message: String) {
        updateStatusIndicator(R.color.text_tertiary, false)
        updateStatusChip("未运行", R.color.text_tertiary)
        tvStatusTitle.text = "准备就绪"
        tvStatus.text = message
        setStartButtonState("启动服务", true, R.drawable.ic_play_24)
        setStopButtonEnabled(false)
        updateUrls(false)
    }

    private fun setUiError(message: String) {
        updateStatusIndicator(R.color.status_error, false)
        updateStatusChip("错误", R.color.status_error)
        tvStatusTitle.text = "启动失败"
        tvStatus.text = message
        setStartButtonState("启动服务", true, R.drawable.ic_play_24)
        setStopButtonEnabled(false)
        updateUrls(false)
        Toast.makeText(this, "服务启动失败", Toast.LENGTH_LONG).show()
    }

    private fun updateStatusIndicator(colorRes: Int, animate: Boolean) {
        val color = ContextCompat.getColor(this, colorRes)
        statusIndicator.backgroundTintList = ColorStateList.valueOf(color)

        statusAnimator?.cancel()
        if (animate) {
            statusAnimator = ValueAnimator.ofFloat(0.3f, 1f).apply {
                duration = 1000
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { animation ->
                    statusIndicator.alpha = animation.animatedValue as Float
                }
                start()
            }
        } else {
            statusIndicator.alpha = 1f
        }
    }

    private fun updateStatusChip(text: String, colorRes: Int) {
        chipStatus.text = text
        val color = ContextCompat.getColor(this, colorRes)
        val alpha = if (isNightMode()) 0x30 else 0x1A
        val bgColor = (color and 0x00FFFFFF) or (alpha shl 24)
        chipStatus.chipBackgroundColor = ColorStateList.valueOf(bgColor)
        chipStatus.setTextColor(color)
        chipStatus.chipIconTint = ColorStateList.valueOf(color)
    }

    private fun showProjectJump() {
        val items = arrayOf(
            "查看应用项目主页",
            "查看上游项目（danmu_api）"
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("项目信息")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> openUrlSafely(PROJECT_URL)
                    1 -> openUrlSafely(UPSTREAM_URL)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showAboutDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_about, null)

        view.findViewById<TextView>(R.id.tvAboutTitle).text = getString(R.string.app_name)
        val pkgInfo = runCatching { packageManager.getPackageInfo(packageName, 0) }.getOrNull()
        // 一些工程可能会关闭 BuildConfig 生成（例如在 gradle.properties 中设置相关开关），
        // 因此这里不要依赖 BuildConfig 以免在 CI/不同构建环境下编译失败。
        val versionName = pkgInfo?.versionName ?: "unknown"
        val versionCode = if (pkgInfo == null) {
            0L
        } else {
            if (Build.VERSION.SDK_INT >= 28) pkgInfo.longVersionCode else pkgInfo.versionCode.toLong()
        }
        view.findViewById<TextView>(R.id.tvAboutVersion).text = "v$versionName (${versionCode})"
        view.findViewById<TextView>(R.id.tvAboutProjectUrl).text = PROJECT_URL
        view.findViewById<TextView>(R.id.tvAboutUpstreamUrl).text = UPSTREAM_URL

        val rowProject = view.findViewById<View>(R.id.rowAboutProject)
        val rowUpstream = view.findViewById<View>(R.id.rowAboutUpstream)

        rowProject.setOnClickListener { openUrlSafely(PROJECT_URL) }
        rowProject.setOnLongClickListener { copyUrl(PROJECT_URL); true }
        rowUpstream.setOnClickListener { openUrlSafely(UPSTREAM_URL) }
        rowUpstream.setOnLongClickListener { copyUrl(UPSTREAM_URL); true }

        MaterialAlertDialogBuilder(this)
            .setTitle("关于")
            .setView(view)
            .setPositiveButton("打开项目主页") { _, _ -> openUrlSafely(PROJECT_URL) }
            .setNeutralButton("复制项目地址") { _, _ -> copyUrl(PROJECT_URL) }
            .setNegativeButton("关闭", null)
            .show()
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun isIgnoringBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = getSystemService(PowerManager::class.java)
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun maybePromptBatteryOptimization() {
        if (isIgnoringBatteryOptimizations()) return

        MaterialAlertDialogBuilder(this)
            .setTitle("后台运行优化")
            .setMessage(
                "为确保服务稳定运行，建议将本应用添加到电池优化白名单，并设置为“ 不受限制 ”模式。\n\n" +
                        "点击“前往设置”将打开系统电池优化页面。"
            )
            .setNegativeButton("稍后提醒") { d, _ -> d.dismiss() }
            .setPositiveButton("前往设置") { d, _ ->
                d.dismiss()
                openBatteryOptimization()
            }
            .show()
    }

    private fun openBatteryOptimization() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isIgnoringBatteryOptimizations()) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
                return
            }
        } catch (_: Throwable) {
        }

        try {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (_: Throwable) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }
    }

    private fun registerStatusReceiver() {
        val filter = IntentFilter(NodeService.ACTION_NODE_STATUS)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(statusReceiver, filter)
        }
    }
}