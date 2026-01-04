package com.example.danmuapiapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.content.res.ColorStateList
import androidx.core.graphics.ColorUtils
import androidx.appcompat.app.AlertDialog
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.net.Inet4Address
import java.net.HttpURLConnection
import java.net.NetworkInterface
import java.net.URL

class MainActivity : AppCompatActivity() {

    companion object {
        // danmu_api upstream default token.
        private const val DEFAULT_TOKEN = "87654321"

        private const val PROJECT_URL = "https://github.com/lilixu3/danmu-api-android"
        private const val UPSTREAM_URL = "https://github.com/huangxd-/danmu_api"
    }

    private lateinit var toolbar: MaterialToolbar

    private lateinit var chipStatus: Chip
    private lateinit var tvStatus: TextView
    private lateinit var btnStart: MaterialButton
    private lateinit var btnStop: MaterialButton

    private lateinit var groupUrls: View
    private lateinit var tvUrlsHint: TextView
    private lateinit var tvLanUrl: TextView
    private lateinit var tvLocalUrl: TextView
    private lateinit var btnOpenUrl: MaterialButton
    private lateinit var btnCopyUrl: MaterialButton

    private var preferredUrl: String? = null

    private val requestNotifPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Start service anyway; on some Android versions the notification may be blocked without permission.
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
        // Apply persisted theme before view inflation (default is light/white theme).
        UiPrefs.applyTheme(this)

        super.onCreate(savedInstanceState)

        // Immersive status bar
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        updateSystemBarsAppearance()

        setContentView(R.layout.activity_main)

        // Apply "hide from recents" preference for current task.
        applyExcludeFromRecents(UiPrefs.isHideFromRecents(this))

        // Apply system bar insets to root padding (so UI won't be covered by status bar)
        val root = findViewById<View>(R.id.root)
        val basePadL = root.paddingLeft
        val basePadT = root.paddingTop
        val basePadR = root.paddingRight
        val basePadB = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                basePadL + sys.left,
                basePadT + sys.top,
                basePadR + sys.right,
                basePadB + sys.bottom
            )
            insets
        }

        toolbar = findViewById(R.id.toolbar)
        chipStatus = findViewById(R.id.chipStatus)
        tvStatus = findViewById(R.id.tvStatus)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)

        groupUrls = findViewById(R.id.groupUrls)
        tvUrlsHint = findViewById(R.id.tvUrlsHint)
        tvLanUrl = findViewById(R.id.tvLanUrl)
        tvLocalUrl = findViewById(R.id.tvLocalUrl)
        btnOpenUrl = findViewById(R.id.btnOpenUrl)
        btnCopyUrl = findViewById(R.id.btnCopyUrl)

        btnStart.setOnClickListener {
            // Android 13+: request notification permission so foreground service notif can be shown.
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
            stopAndExitApp()
        }

        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_settings -> { showSettingsDialog(); true }
                else -> false
            }
        }


        // URL interactions
        tvLanUrl.setOnClickListener { openUrlSafely(tvLanUrl.text.toString()) }
        tvLocalUrl.setOnClickListener { openUrlSafely(tvLocalUrl.text.toString()) }
        tvLanUrl.setOnLongClickListener { copyUrl(tvLanUrl.text.toString()); true }
        tvLocalUrl.setOnLongClickListener { copyUrl(tvLocalUrl.text.toString()); true }

        btnOpenUrl.setOnClickListener { preferredUrl?.let { openUrlSafely(it) } }
        btnCopyUrl.setOnClickListener { preferredUrl?.let { copyUrl(it) } }

        // Initial state
        refreshUiFromServiceState()

        // 检查 GitHub 发行版最新版本（不会阻塞 UI）
        UpdateChecker.check(this)
    }

    override fun onStart() {
        super.onStart()
        // Ensure preference is applied when returning to foreground.
        applyExcludeFromRecents(UiPrefs.isHideFromRecents(this))
        registerStatusReceiver()
        refreshUiFromServiceState()
    }

    /**
     * Hide/show this App's task card in the system "Recents" (swipe-up task switcher).
     *
     * Note: this only affects our own task, and takes effect immediately on most launchers.
     */
    private fun applyExcludeFromRecents(exclude: Boolean) {
        if (Build.VERSION.SDK_INT < 21) return
        val am = (getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager) ?: return
        val currentId = taskId

        var applied = false
        for (t in am.appTasks) {
            val info = runCatching { t.taskInfo }.getOrNull() ?: continue
            if (info.id == currentId) {
                runCatching { t.setExcludeFromRecents(exclude) }
                applied = true
                break
            }
        }

        // Fallback: some ROMs may not match taskId reliably.
        if (!applied) {
            am.appTasks.firstOrNull()?.let { runCatching { it.setExcludeFromRecents(exclude) } }
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(statusReceiver)
        } catch (_: Throwable) {
        }
    }

    private fun refreshUiFromServiceState() {
        if (NodeService.isRunning()) {
            setUiRunning("Node 正在运行（前台服务）")
        } else {
            setUiStopped("未运行。点击“启动服务”后，Node 会在后台跑起来。")
        }
    }

    private fun updateSystemBarsAppearance() {
        val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !night
            // Some devices also tint nav icons; keep consistent.
            isAppearanceLightNavigationBars = !night
        }
    }

    private fun startNodeServiceWithUiHint() {
        setUiStarting("启动中…请稍候（可在通知栏看到运行状态）")
        startNodeService()
        Toast.makeText(this, "已启动：后台服务运行中", Toast.LENGTH_SHORT).show()
        maybePromptBatteryOptimization()
    }

    private fun startNodeService() {
        // Mark as "desired running" so keep-alive can restart if it crashes.
        NodeKeepAlive.setDesiredRunning(this, true)
        val intent = Intent(this, NodeService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    /**
     * 关闭按钮：
     * 1) 尝试请求 Node 在本机 127.0.0.1 上优雅退出（/\_\_shutdown）
     * 2) 停止前台服务
     * 3) 自动退出 App（必要时兜底 killProcess，确保 Node 线程不会残留）
     */
    private fun stopAndExitApp() {
        // 手动停止：不要自动重启
        NodeKeepAlive.setDesiredRunning(this, false)
        // 防止重复点击
        btnStart.isEnabled = false
        btnStop.isEnabled = false
        tvStatus.text = "正在关闭…"

        Thread {
            // 先尝试让 Node 自己优雅退出
            try {
                requestNodeShutdown()
            } catch (_: Throwable) {
            }

            // 再向前台服务发送 STOP（会确保 desiredRunning=false，并避免“杀进程后被系统拉起”）
            try {
                startService(Intent(this, NodeService::class.java).setAction(NodeService.ACTION_STOP))
            } catch (_: Throwable) {
            }

            runOnUiThread {
                Toast.makeText(this, "已关闭服务，正在退出 App…", Toast.LENGTH_SHORT).show()
                // 让任务从最近任务列表移除，更像“真正退出”
                finishAndRemoveTask()
            }

            // 给一点时间让 Node 退出 / 资源释放；如果仍未退出，兜底杀进程
            val deadline = System.currentTimeMillis() + 2000
            while (NodeService.isRunning() && System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(120)
                } catch (_: Throwable) {
                }
            }
            if (NodeService.isRunning()) {
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }.start()
    }

    private fun requestNodeShutdown() {
        val port = RuntimeConfig.getMainPort(this)
        val url = URL("http://127.0.0.1:$port/__shutdown")
        val conn = (url.openConnection() as HttpURLConnection)
        conn.requestMethod = "GET"
        conn.instanceFollowRedirects = false
        conn.connectTimeout = 800
        conn.readTimeout = 800
        conn.doInput = true
        try {
            conn.connect()
            // 读取一下响应，确保请求真正发出去
            runCatching { conn.inputStream.use { it.readBytes() } }
        } finally {
            conn.disconnect()
        }
    }

    private fun updateUrls(visible: Boolean) {
        if (!visible) {
            groupUrls.visibility = View.GONE
            tvUrlsHint.visibility = View.VISIBLE
            preferredUrl = null
            return
        }

        groupUrls.visibility = View.VISIBLE
        tvUrlsHint.visibility = View.GONE

        val token = readApiTokenForDisplay()

        val port = RuntimeConfig.getMainPort(this)

        fun buildEndpoint(host: String): String {
            val base = "http://$host:$port"
            // Always show full endpoint with TOKEN (even if it equals the upstream default),
            // so it matches the web UI and is easier to copy/paste.
            return if (!token.isNullOrBlank()) "$base/$token" else base
        }

        val lanIp = getLanIpv4()
        val lanUrl = if (lanIp != null) buildEndpoint(lanIp) else null
        val localUrl = buildEndpoint("127.0.0.1")

        preferredUrl = lanUrl ?: localUrl

        tvLanUrl.text = lanUrl ?: "未获取到局域网 IP（可检查是否已连接 Wi‑Fi/局域网）"
        tvLocalUrl.text = localUrl

        // Disable click when LAN URL not available
        tvLanUrl.isEnabled = lanUrl != null
        tvLanUrl.alpha = if (lanUrl != null) 1f else 0.55f
    }

    /**
     * 读取运行时配置里的 TOKEN（用于拼接可直接粘贴到弹幕客户端的 API 地址）。
     *
     * - 优先读取 filesDir/nodejs-project/config/.env（网页端修改会落盘到这里）
     * - 兜底读取 config/config.yaml
     *
     * 注意：这里返回的是 API token（TOKEN），不是 ADMIN_TOKEN。
     */
    private fun readApiTokenForDisplay(): String? {
        // 1) .env
        runCatching {
            val envFile = java.io.File(filesDir, "nodejs-project/config/.env")
            if (envFile.exists()) {
                val lines = envFile.readLines(Charsets.UTF_8)
                for (raw in lines) {
                    val line = raw.trim()
                    if (line.isEmpty() || line.startsWith("#")) continue
                    val idx = line.indexOf('=')
                    if (idx <= 0) continue
                    val key = line.substring(0, idx).trim()
                    if (key != "TOKEN") continue
                    var value = line.substring(idx + 1).trim()
                    // strip quotes if any
                    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith('\'') && value.endsWith('\''))) {
                        if (value.length >= 2) value = value.substring(1, value.length - 1)
                    }
                    return value
                }
            }
        }

        // 2) config.yaml (very small parser; enough for TOKEN: "..." or TOKEN: ...)
        runCatching {
            val yamlFile = java.io.File(filesDir, "nodejs-project/config/config.yaml")
            if (yamlFile.exists()) {
                val lines = yamlFile.readLines(Charsets.UTF_8)
                for (raw in lines) {
                    val line = raw.trim()
                    if (line.isEmpty() || line.startsWith("#")) continue
                    if (!line.startsWith("TOKEN")) continue
                    val idx = line.indexOf(':')
                    if (idx <= 0) continue
                    val key = line.substring(0, idx).trim()
                    if (key != "TOKEN") continue
                    var value = line.substring(idx + 1).trim()
                    // remove surrounding quotes
                    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith('\'') && value.endsWith('\''))) {
                        if (value.length >= 2) value = value.substring(1, value.length - 1)
                    }
                    return value
                }
            }
        }

        return null
    }

    /**
     * Read value from runtime config (filesDir/nodejs-project/config/.env first, then config.yaml).
     * This is used for displaying and editing settings inside the App.
     */
    private fun readConfigValue(keyName: String): String? {
        // Ensure the node project has been extracted so runtime config files exist.
        runCatching { AssetCopier.ensureNodeProjectExtracted(this) }

        // 1) .env
        runCatching {
            val envFile = java.io.File(filesDir, "nodejs-project/config/.env")
            if (envFile.exists()) {
                val lines = envFile.readLines(Charsets.UTF_8)
                for (raw in lines) {
                    val line = raw.trim()
                    if (line.isEmpty() || line.startsWith("#")) continue
                    val idx = line.indexOf('=')
                    if (idx <= 0) continue
                    val key = line.substring(0, idx).trim()
                    if (key != keyName) continue
                    var value = line.substring(idx + 1).trim()
                    // strip quotes if any
                    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith('\'') && value.endsWith('\''))) {
                        if (value.length >= 2) value = value.substring(1, value.length - 1)
                    }
                    return value
                }
            }
        }

        // 2) config.yaml (very small parser; enough for KEY: "..." or KEY: ...)
        runCatching {
            val yamlFile = java.io.File(filesDir, "nodejs-project/config/config.yaml")
            if (yamlFile.exists()) {
                val lines = yamlFile.readLines(Charsets.UTF_8)
                for (raw in lines) {
                    val line = raw.trim()
                    if (line.isEmpty() || line.startsWith("#")) continue
                    if (!line.startsWith(keyName)) continue
                    val idx = line.indexOf(':')
                    if (idx <= 0) continue
                    val key = line.substring(0, idx).trim()
                    if (key != keyName) continue
                    var value = line.substring(idx + 1).trim()
                    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith('\'') && value.endsWith('\''))) {
                        if (value.length >= 2) value = value.substring(1, value.length - 1)
                    }
                    return value
                }
            }
        }

        return null
    }

    private fun setConfigValue(keyName: String, valueRaw: String): Boolean {
        return runCatching {
            AssetCopier.ensureNodeProjectExtracted(this)

            val configDir = java.io.File(filesDir, "nodejs-project/config")
            if (!configDir.exists()) configDir.mkdirs()

            val envFile = java.io.File(configDir, ".env")
            val yamlFile = java.io.File(configDir, "config.yaml")

            val value = valueRaw.trim()

            fun formatEnvValue(v: String): String {
                if (v.isEmpty()) return ""
                // Quote only if needed to keep .env parse stable
                val needsQuote = v.any { it.isWhitespace() || it == '#' || it == '"' || it == '\'' }
                return if (needsQuote) "\"" + v.replace("\"", "\\\"") + "\"" else v
            }

            fun formatYamlValue(v: String): String {
                val t = v.trim()
                if (t.isEmpty()) return "\"\""
                if (t.equals("true", ignoreCase = true)) return "true"
                if (t.equals("false", ignoreCase = true)) return "false"
                // keep pure numbers unquoted (match web端逻辑：Number(value))
                val isPureNumber = t.matches(Regex("-?\\d+(\\.\\d+)?"))
                if (isPureNumber) return t
                val escaped = t.replace("\\", "\\\\").replace("\"", "\\\"")
                return "\"$escaped\""
            }

            var updated = false

            // ============ 更新 .env（与网页端一致：若存在则更新；不存在则创建） ============
            runCatching {
                val outValue = formatEnvValue(value)
                val lines = if (envFile.exists()) envFile.readLines(Charsets.UTF_8).toMutableList() else mutableListOf()
                var replaced = false
                for (i in lines.indices) {
                    val raw = lines[i]
                    val line = raw.trim()
                    if (line.isEmpty() || line.startsWith("#")) continue
                    val idx = line.indexOf('=')
                    if (idx <= 0) continue
                    val key = line.substring(0, idx).trim()
                    if (key != keyName) continue
                    lines[i] = "$keyName=$outValue"
                    replaced = true
                    break
                }
                if (!replaced) {
                    if (lines.isNotEmpty() && lines.last().isNotBlank()) lines.add("")
                    lines.add("$keyName=$outValue")
                }
                envFile.writeText(lines.joinToString("\n") + "\n", Charsets.UTF_8)
                updated = true
            }

            // ============ 更新 config.yaml（网页端“修改环境变量”也会落盘到这里；同时该文件更易被用户直接查看） ============
            runCatching {
                val outValue = formatYamlValue(value)
                val lines = if (yamlFile.exists()) yamlFile.readLines(Charsets.UTF_8).toMutableList() else mutableListOf()

                var keyFound = false
                for (i in lines.indices) {
                    val trimmed = lines[i].trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                    val idx = trimmed.indexOf(':')
                    if (idx <= 0) continue
                    val key = trimmed.substring(0, idx).trim()
                    if (key != keyName) continue
                    lines[i] = "$keyName: $outValue"
                    keyFound = true
                    break
                }

                if (!keyFound) {
                    if (lines.isNotEmpty() && lines.last().isNotBlank()) lines.add("")
                    lines.add("$keyName: $outValue")
                }

                yamlFile.writeText(lines.joinToString("\n") + "\n", Charsets.UTF_8)
                updated = true
            }

            updated
        }.getOrElse { false }
    }

    private fun showAdminTokenDialog(onUpdated: (() -> Unit)? = null) {
        val current = readConfigValue("ADMIN_TOKEN") ?: ""

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (resources.displayMetrics.density * 18).toInt()
            setPadding(pad, (resources.displayMetrics.density * 6).toInt(), pad, 0)
        }

        val oldTil = TextInputLayout(this).apply {
            hint = "旧管理员密码"
            endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        }
        val oldEt = TextInputEditText(oldTil.context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        oldTil.addView(oldEt)
        container.addView(oldTil)

        val newTil = TextInputLayout(this).apply {
            hint = "新管理员密码"
            endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        }
        val newEt = TextInputEditText(newTil.context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        newTil.addView(newEt)
        container.addView(newTil)

        val dlg = MaterialAlertDialogBuilder(this)
            .setTitle("修改管理员密码")
            .setMessage("此密码对应环境变量 ADMIN_TOKEN。\n\n未设置过旧密码无需填写，直接输入新密码即可")
            .setView(container)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存", null)
            .show()

        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            oldTil.error = null
            newTil.error = null

            val oldVal = oldEt.text?.toString()?.trim() ?: ""
            val newVal = newEt.text?.toString()?.trim() ?: ""

            if (oldVal != current) {
                oldTil.error = "旧密码不正确"
                return@setOnClickListener
            }
            if (newVal.isBlank()) {
                newTil.error = "新密码不能为空"
                return@setOnClickListener
            }

            val ok = setConfigValue("ADMIN_TOKEN", newVal)
            if (!ok) {
                Toast.makeText(this, "保存失败：无法写入配置文件", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            Toast.makeText(this, "已更新管理员密码", Toast.LENGTH_SHORT).show()
            dlg.dismiss()
            onUpdated?.invoke()
        }
    }

    private fun openUrlSafely(url: String) {
        val u = url.trim()
        if (!u.startsWith("http://") && !u.startsWith("https://")) return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
        } catch (t: Throwable) {
            Toast.makeText(this, "无法打开浏览器：${t.message ?: t}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyUrl(url: String) {
        val u = url.trim()
        if (!u.startsWith("http://") && !u.startsWith("https://")) return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("url", u))
        Toast.makeText(this, "已复制：$u", Toast.LENGTH_SHORT).show()
    }

    private fun getLanIpv4(): String? {
        try {
            val itf = NetworkInterface.getNetworkInterfaces() ?: return null
            for (ni in itf) {
                if (!ni.isUp || ni.isLoopback) continue
                val name = (ni.name ?: "").lowercase()
                // Prefer common WLAN/ETH interfaces first
                val prefer = name.startsWith("wlan") || name.startsWith("eth") || name.startsWith("en")
                val addrs = ni.inetAddresses ?: continue
                for (addr in addrs) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        if (ip.startsWith("169.254.")) continue // ignore link-local
                        if (prefer) return ip
                    }
                }
            }
            // Fallback: any non-loopback IPv4
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

    private fun setUiStarting(message: String) {
        updateStatusChip("启动中", R.color.status_warn)
        tvStatus.text = message
        btnStart.text = "启动中…"
        btnStart.setIconResource(R.drawable.ic_play_24)
        btnStart.isEnabled = false
        btnStop.isEnabled = true
        updateUrls(false)
    }

    private fun setUiRunning(message: String) {
        updateStatusChip("运行中", R.color.status_ok)
        tvStatus.text = message
        btnStart.text = "已启动"
        btnStart.setIconResource(R.drawable.ic_check_24)
        btnStart.isEnabled = false
        btnStop.isEnabled = true
        updateUrls(true)
    }

    private fun setUiStopped(message: String) {
        updateStatusChip("未运行", R.color.status_neutral)
        tvStatus.text = message
        btnStart.text = "启动服务"
        btnStart.setIconResource(R.drawable.ic_play_24)
        btnStart.isEnabled = true
        btnStop.isEnabled = false
        updateUrls(false)
    }

    private fun setUiError(message: String) {
        updateStatusChip("出错", R.color.status_error)
        tvStatus.text = "启动失败：\n$message"
        btnStart.text = "重新启动"
        btnStart.setIconResource(R.drawable.ic_play_24)
        btnStart.isEnabled = true
        btnStop.isEnabled = false
        updateUrls(false)
        Toast.makeText(this, "启动失败（详情见页面）", Toast.LENGTH_LONG).show()
    }

    private fun updateStatusChip(text: String, colorRes: Int) {
        chipStatus.text = text
        val color = ContextCompat.getColor(this, colorRes)
        // 背景使用低透明度：浅色/深色主题都更舒适
        val bg = ColorUtils.setAlphaComponent(color, 40)
        chipStatus.chipBackgroundColor = ColorStateList.valueOf(bg)
        chipStatus.setTextColor(color)
        chipStatus.chipIconTint = ColorStateList.valueOf(color)
    }

    private fun showProjectJump() {
        val items = arrayOf(
            "打开项目主页（App）",
            "打开上游项目（danmu_api）"
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("项目跳转")
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
        val msg = (
            "简单用法：\n" +
                "1) 点击“启动服务”后，服务会以前台方式在后台运行。\n" +
                "2) 在同一局域网设备上，用浏览器打开页面显示的“局域网地址”。\n" +
                "3) 若经常被杀后台，请在“设置 → 电池优化”里将电池用量设置为“不受限制/无限制”。\n\n" +
                "项目地址：\n$PROJECT_URL"
        )

        MaterialAlertDialogBuilder(this)
            .setTitle("关于")
            .setMessage(msg)
            .setPositiveButton("打开项目") { _, _ -> openUrlSafely(PROJECT_URL) }
            .setNeutralButton("复制地址") { _, _ -> copyUrl(PROJECT_URL) }
            .setNegativeButton("更多…") { _, _ -> showProjectJump() }
            .show()
    }

    private fun showSettingsDialog() {
        val v = layoutInflater.inflate(R.layout.sheet_settings, null)

        val sheetRoot = v.findViewById<View>(R.id.sheetRoot)
        val btnClose = v.findViewById<View>(R.id.btnClose)

        val rowTheme = v.findViewById<View>(R.id.rowTheme)
        val tvThemeSub = v.findViewById<TextView>(R.id.tvThemeSub)
        val sw = v.findViewById<MaterialSwitch>(R.id.switchTheme)

        val rowBattery = v.findViewById<View>(R.id.rowBattery)
        val tvBatterySub = v.findViewById<TextView>(R.id.tvBatterySub)
        val ivBatteryArrow = v.findViewById<ImageView>(R.id.ivBatteryArrow)

        val rowHideRecents = v.findViewById<View>(R.id.rowHideRecents)
        val tvHideRecentsSub = v.findViewById<TextView>(R.id.tvHideRecentsSub)
        val swHideRecents = v.findViewById<MaterialSwitch>(R.id.switchHideRecents)

        val rowA11yKeepAlive = v.findViewById<View>(R.id.rowA11yKeepAlive)
        val tvA11yKeepAliveSub = v.findViewById<TextView>(R.id.tvA11yKeepAliveSub)
        val swA11yKeepAlive = v.findViewById<MaterialSwitch>(R.id.switchA11yKeepAlive)

        val rowUpdate = v.findViewById<View>(R.id.rowUpdate)
        val rowAdminToken = v.findViewById<View>(R.id.rowAdminToken)
        val tvAdminTokenSub = v.findViewById<TextView>(R.id.tvAdminTokenSub)
        val rowAbout = v.findViewById<View>(R.id.rowAbout)

        val dialog = BottomSheetDialog(this, R.style.ThemeOverlay_DanmuApiApp_BottomSheet)
        dialog.setContentView(v)
        dialog.window?.setDimAmount(0.35f)

        // Avoid being covered by gesture navigation bar
        val basePadL = sheetRoot.paddingLeft
        val basePadT = sheetRoot.paddingTop
        val basePadR = sheetRoot.paddingRight
        val basePadB = sheetRoot.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(sheetRoot) { view, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.setPadding(basePadL, basePadT, basePadR, basePadB + nav.bottom)
            insets
        }

        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.setBackgroundResource(R.drawable.bg_sheet)
            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                // iOS-like bottom panel: attached to bottom, not draggable.
                behavior.isFitToContents = true
                behavior.skipCollapsed = true
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.isDraggable = false
            }
        }

        btnClose.setOnClickListener { dialog.dismiss() }

        // Theme toggle
        val dark = UiPrefs.isDarkTheme(this)
        sw.isChecked = dark
        tvThemeSub.text = if (dark) "当前：黑色主题" else "当前：白色主题"

        rowTheme.setOnClickListener { sw.isChecked = !sw.isChecked }
        sw.setOnCheckedChangeListener { _, isChecked ->
            UiPrefs.setDarkTheme(this, isChecked)
            dialog.dismiss()
            // Ensure immediate visual update
            recreate()
        }

        // Battery optimization
        fun refreshBatteryRow() {
            val done = isIgnoringBatteryOptimizations()
            if (done) {
                tvBatterySub.text = "已设置（无限制）"
                rowBattery.isEnabled = false
                rowBattery.alpha = 0.55f
                ivBatteryArrow.visibility = View.GONE
            } else {
                tvBatterySub.text = "建议设置为“不受限制/无限制”，更稳定"
                rowBattery.isEnabled = true
                rowBattery.alpha = 1f
                ivBatteryArrow.visibility = View.VISIBLE
            }
        }
        refreshBatteryRow()

        // Hide from recents (task switcher card)
        var ignoreHideRecentsChange = false
        fun refreshHideRecentsRow() {
            val hide = UiPrefs.isHideFromRecents(this)
            ignoreHideRecentsChange = true
            swHideRecents.isChecked = hide
            ignoreHideRecentsChange = false
            tvHideRecentsSub.text = if (hide) {
                "已隐藏（上滑最近任务中不显示）"
            } else {
                "显示在上滑任务栏/最近任务中"
            }
        }
        refreshHideRecentsRow()

        rowHideRecents.setOnClickListener { swHideRecents.toggle() }

        swHideRecents.setOnCheckedChangeListener { _, isChecked ->
            if (ignoreHideRecentsChange) return@setOnCheckedChangeListener

            if (isChecked) {
                // Only enable after explicit confirmation.
                MaterialAlertDialogBuilder(this)
                    .setTitle("隐藏最近任务卡片")
                    .setMessage(
                        "开启后，上滑任务栏/最近任务中将看不到本 App 的任务卡片。\n\n" +
                            "请先在最近任务界面，将本 App 任务卡片“上锁/锁定”（不同系统叫法可能不同）。\n\n" +
                            "完成上锁后，再点击下方“确认隐藏”。"
                    )
                    .setNegativeButton("取消") { d, _ ->
                        d.dismiss()
                        ignoreHideRecentsChange = true
                        swHideRecents.isChecked = false
                        ignoreHideRecentsChange = false
                    }
                    .setPositiveButton("确认隐藏") { d, _ ->
                        d.dismiss()
                        UiPrefs.setHideFromRecents(this, true)
                        applyExcludeFromRecents(true)
                        refreshHideRecentsRow()
                        Toast.makeText(this, "已隐藏：最近任务中不再显示", Toast.LENGTH_SHORT).show()
                    }
                    .show()
            } else {
                UiPrefs.setHideFromRecents(this, false)
                applyExcludeFromRecents(false)
                refreshHideRecentsRow()
                Toast.makeText(this, "已恢复显示：最近任务可见", Toast.LENGTH_SHORT).show()
            }
        }

        // Accessibility keep-alive
        fun refreshA11yKeepAliveRow() {
            val keepAliveOn = NodeKeepAlive.isKeepAliveEnabled(this)
            val a11yEnabled = NodeKeepAlive.isAccessibilityServiceEnabled(this)
            val notifOk = NodeKeepAlive.hasPostNotificationsPermission(this)

            swA11yKeepAlive.isChecked = keepAliveOn

            tvA11yKeepAliveSub.text = when {
                !keepAliveOn -> "关闭（不自动重启）"
                !notifOk -> "已开启：需要通知权限才能自动重启"
                a11yEnabled -> "已开启：异常退出会自动拉起服务"
                else -> "已开启：请在系统无障碍中启用“保活服务”"
            }
        }

        refreshA11yKeepAliveRow()

        rowA11yKeepAlive.setOnClickListener {
            // Open system accessibility settings; user must enable the service manually.
            dialog.dismiss()
            openAccessibilitySettings()
        }

        swA11yKeepAlive.setOnCheckedChangeListener { _, isChecked ->
            NodeKeepAlive.setKeepAliveEnabled(this, isChecked)
            // Sync: when user turns OFF keep-alive in App settings, also turn OFF the
            // system Accessibility service (it can only disable itself).
            if (!isChecked) {
                NodeKeepAlive.requestDisableAccessibilityService(this)
            }

            // If Node foreground service is currently running, refresh its restart policy
            // so "关闭保活" takes effect immediately (avoid START_STICKY lingering).
            if (NodeService.isRunning()) {
                runCatching {
                    startService(
                        Intent(this, NodeService::class.java)
                            .setAction(NodeService.ACTION_REFRESH_POLICY)
                    )
                }
            }
            refreshA11yKeepAliveRow()
            if (isChecked && !NodeKeepAlive.isAccessibilityServiceEnabled(this)) {
                // Guide user to enable accessibility service.
                dialog.dismiss()
                Toast.makeText(
                    this,
                    "请在系统无障碍中启用“弹幕API 保活服务”后生效",
                    Toast.LENGTH_LONG
                ).show()
                openAccessibilitySettings()
            }
        }

        rowBattery.setOnClickListener {
            if (!isIgnoringBatteryOptimizations()) {
                dialog.dismiss()
                openBatteryOptimization()
            }
        }

        rowUpdate.setOnClickListener {
            dialog.dismiss()
            UpdateChecker.check(this, userInitiated = true)
        }

        // Admin token (ADMIN_TOKEN)
        fun refreshAdminTokenSub() {
            val current = readConfigValue("ADMIN_TOKEN")
            tvAdminTokenSub.text = if (current.isNullOrBlank()) {
                "未设置（系统管理不可用）"
            } else {
                "已设置（需旧密码才能修改）"
            }
        }
        refreshAdminTokenSub()

        rowAdminToken.setOnClickListener {
            // Keep sheet open; show dialog on top.
            showAdminTokenDialog(onUpdated = { refreshAdminTokenSub() })
        }

        rowAbout.setOnClickListener {
            dialog.dismiss()
            showAboutDialog()
        }

        dialog.show()
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
            .setTitle("后台常驻建议")
            .setMessage(
                "为了减少被系统省电策略限制，建议把本 App 的电池用量设置为“不受限制/无限制”，并允许忽略电池优化。\n\n" +
                        "点击“去设置”将打开系统页面（不同品牌入口可能略有差异）。"
            )
            .setNegativeButton("稍后") { d, _ -> d.dismiss() }
            .setPositiveButton("去设置") { d, _ ->
                d.dismiss()
                openBatteryOptimization()
            }
            .show()
    }

    private fun openBatteryOptimization() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isIgnoringBatteryOptimizations()) {
                // 直接请求加入白名单
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
                return
            }
        } catch (_: Throwable) {
        }

        // 兜底：打开电池优化设置或 App 信息页（用户手动设置为“不受限制”）
        try {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (_: Throwable) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }
    }

    /**
     * Open system Accessibility settings so user can enable the keep-alive AccessibilityService.
     * Accessibility services must be enabled manually by the user.
     */
    private fun openAccessibilitySettings() {
        // Primary: open Accessibility settings directly
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        } catch (_: Throwable) {
        }

        // Fallbacks: Settings app / App details
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS))
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
