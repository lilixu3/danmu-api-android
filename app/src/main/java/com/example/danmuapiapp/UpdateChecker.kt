package com.example.danmuapiapp

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object UpdateChecker {

    private const val LATEST_RELEASE_API = "https://api.github.com/repos/lilixu3/Private/releases/latest"
    private const val FALLBACK_LATEST_PAGE = "https://github.com/lilixu3/Private/releases/latest"

    private val executor = Executors.newSingleThreadExecutor()

    fun check(activity: Activity, userInitiated: Boolean = false) {
        val currentVersion = getCurrentVersionName(activity)
        if (currentVersion.isEmpty()) {
            if (userInitiated) {
                Toast.makeText(activity, "无法获取当前版本信息", Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (userInitiated) {
            Toast.makeText(activity, "正在检查更新…", Toast.LENGTH_SHORT).show()
        }

        executor.execute {
            try {
                val url = URL(LATEST_RELEASE_API)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/vnd.github+json")
                    // GitHub API 建议带 User-Agent
                    setRequestProperty("User-Agent", "DanmuApiApp")
                }

                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)

                val tagName = json.optString("tag_name").orEmpty()
                if (tagName.isEmpty()) {
                    if (userInitiated) {
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(activity, "检查更新失败：无有效版本信息", Toast.LENGTH_SHORT).show()
                        }
                    }
                    return@execute
                }

                val latestVersion = tagName.removePrefix("v").removePrefix("V").trim()
                val hasUpdate = isNewerVersion(latestVersion, currentVersion)

                val htmlUrl = json.optString("html_url", FALLBACK_LATEST_PAGE).ifBlank { FALLBACK_LATEST_PAGE }

                Handler(Looper.getMainLooper()).post {
                    if (activity.isFinishing) return@post

                    if (hasUpdate) {
                        AlertDialog.Builder(activity)
                            .setTitle("发现新版本")
                            .setMessage("当前版本：$currentVersion\n最新版本：$latestVersion\n\n前往下载更新？")
                            .setPositiveButton("去更新") { _, _ ->
                                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(htmlUrl)))
                            }
                            .setNegativeButton("取消", null)
                            .show()
                    } else if (userInitiated) {
                        Toast.makeText(activity, "已是最新版本（$currentVersion）", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (_: Exception) {
                if (userInitiated) {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(activity, "检查更新失败，请稍后重试", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }



    private fun getCurrentVersionName(activity: Activity): String {
        return try {
            val pm = activity.packageManager
            @Suppress("DEPRECATION")
            val pi = if (android.os.Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(activity.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                pm.getPackageInfo(activity.packageName, 0)
            }
            (pi.versionName ?: "").trim()
        } catch (_: Exception) {
            ""
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val l = normalize(latest)
        val c = normalize(current)
        if (l.isEmpty() || c.isEmpty()) return false

        val max = maxOf(l.size, c.size)
        for (i in 0 until max) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv != cv) return lv > cv
        }
        return false
    }

    private fun normalize(v: String): List<Int> {
        // 只取数字段：比如 1.2.3-beta -> [1,2,3]
        val main = v.trim().split("-", limit = 2).firstOrNull().orEmpty()
        if (main.isBlank()) return emptyList()
        return main.split(".")
            .mapNotNull { part -> part.toIntOrNull() ?: part.filter { it.isDigit() }.toIntOrNull() }
    }
}
