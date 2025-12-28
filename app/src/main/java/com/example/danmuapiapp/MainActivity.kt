package com.example.danmuapiapp

import android.Manifest
import android.annotation.SuppressLint
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
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.button.MaterialButton
import java.net.Inet4Address
import java.net.HttpURLConnection
import java.net.NetworkInterface
import java.net.URL

class MainActivity : AppCompatActivity() {

    companion object {
        // Keep in sync with assets/nodejs-project/android-server.mjs defaults.
        private const val MAIN_PORT = 9321
    }

    private lateinit var tvStatus: TextView
    private lateinit var btnStart: MaterialButton
    private lateinit var btnStop: MaterialButton
    private lateinit var btnBattery: MaterialButton
    private lateinit var btnCheckUpdate: MaterialButton

    private lateinit var groupUrls: View
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
        super.onCreate(savedInstanceState)

        // Immersive status bar
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        setContentView(R.layout.activity_main)

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

        tvStatus = findViewById(R.id.tvStatus)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        btnBattery = findViewById(R.id.btnBattery)
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate)

        groupUrls = findViewById(R.id.groupUrls)
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

        btnBattery.setOnClickListener {
            openBatteryOptimization()
        }

        btnCheckUpdate.setOnClickListener {
            UpdateChecker.check(this, userInitiated = true)
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
        registerStatusReceiver()
        refreshUiFromServiceState()
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

    private fun startNodeServiceWithUiHint() {
        setUiStarting("启动中…请稍候（可在通知栏看到运行状态）")
        startNodeService()
        Toast.makeText(this, "已启动：后台服务运行中", Toast.LENGTH_SHORT).show()
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

    /**
     * 关闭按钮：
     * 1) 尝试请求 Node 在本机 127.0.0.1 上优雅退出（/\_\_shutdown）
     * 2) 停止前台服务
     * 3) 自动退出 App（必要时兜底 killProcess，确保 Node 线程不会残留）
     */
    private fun stopAndExitApp() {
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

            // 再停止 Service（如果 Node 已退出，会自动 stopSelf；这里是兜底）
            try {
                stopService(Intent(this, NodeService::class.java))
            } catch (_: Throwable) {
            }

            runOnUiThread {
                Toast.makeText(this, "已关闭服务，正在退出 App…", Toast.LENGTH_SHORT).show()
                // 让任务从最近任务列表移除，更像“真正退出”
                finishAndRemoveTask()
            }

            // 给一点时间让 Node 退出 / 资源释放；如果仍未退出，兜底杀进程
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
            // 读取一下响应，确保请求真正发出去
            runCatching { conn.inputStream.use { it.readBytes() } }
        } finally {
            conn.disconnect()
        }
    }

    private fun updateUrls(visible: Boolean) {
        if (!visible) {
            groupUrls.visibility = View.GONE
            preferredUrl = null
            return
        }

        groupUrls.visibility = View.VISIBLE

        val lanIp = getLanIpv4()
        val lanUrl = if (lanIp != null) "http://$lanIp:$MAIN_PORT/" else null
        val localUrl = "http://127.0.0.1:$MAIN_PORT/"

        preferredUrl = lanUrl ?: localUrl

        tvLanUrl.text = lanUrl ?: "未获取到局域网 IP（可检查是否已连接 Wi‑Fi/局域网）"
        tvLocalUrl.text = localUrl

        // Disable click when LAN URL not available
        tvLanUrl.isEnabled = lanUrl != null
        tvLanUrl.alpha = if (lanUrl != null) 1f else 0.55f
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
        tvStatus.text = message
        btnStart.isEnabled = false
        btnStop.isEnabled = true
        updateUrls(false)
    }

    private fun setUiRunning(message: String) {
        tvStatus.text = message
        btnStart.isEnabled = false
        btnStop.isEnabled = true
        updateUrls(true)
    }

    private fun setUiStopped(message: String) {
        tvStatus.text = message
        btnStart.isEnabled = true
        btnStop.isEnabled = false
        updateUrls(false)
    }

    private fun setUiError(message: String) {
        tvStatus.text = "启动失败：\n$message"
        btnStart.isEnabled = true
        btnStop.isEnabled = false
        updateUrls(false)
        Toast.makeText(this, "启动失败（详情见页面）", Toast.LENGTH_LONG).show()
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun isIgnoringBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = getSystemService(PowerManager::class.java)
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun maybePromptBatteryOptimization() {
        if (isIgnoringBatteryOptimizations()) return

        AlertDialog.Builder(this)
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
