package com.example.danmuapiapp.ui.screen.home

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import java.util.Locale

object NormalModeKeepAliveGuideNavigator {

    fun manufacturerName(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        return manufacturer.ifBlank { "Android" }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun openAppBatterySettings(context: Context): Boolean {
        val packageUri = Uri.parse("package:${context.packageName}")
        val candidates = listOf(
            Intent("android.settings.APP_BATTERY_SETTINGS").apply {
                data = packageUri
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            },
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = packageUri
            }
        )
        return candidates.any { launchIntent(context, it) }
    }

    fun requestIgnoreBatteryOptimization(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        if (isIgnoringBatteryOptimizations(context)) return false
        val packageUri = Uri.parse("package:${context.packageName}")
        val candidates = listOf(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = packageUri
            },
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        )
        return candidates.any { launchIntent(context, it) }
    }

    fun openAutoStartSettings(context: Context): Boolean {
        val brand = normalizedBrand()
        val candidates = when {
            brand in setOf("xiaomi", "redmi") -> listOf(
                componentIntent("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
            )

            brand in setOf("huawei", "honor") -> listOf(
                componentIntent("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                componentIntent("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
            )

            brand in setOf("oppo", "oneplus", "realme") -> listOf(
                componentIntent("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                componentIntent("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
                componentIntent("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")
            )

            brand in setOf("vivo", "iqoo") -> listOf(
                componentIntent("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
                componentIntent("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                componentIntent("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")
            )

            brand == "asus" -> listOf(
                componentIntent("com.asus.mobilemanager", "com.asus.mobilemanager.powersaver.PowerSaverSettings")
            )

            else -> emptyList()
        }

        if (candidates.any { launchIntent(context, it) }) return true

        val appInfoIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        return launchIntent(context, appInfoIntent)
    }

    fun openVendorGuide(context: Context): Boolean {
        val brand = normalizedBrand()
        val slug = when {
            brand in setOf("xiaomi", "redmi") -> "xiaomi"
            brand in setOf("huawei", "honor") -> "huawei"
            brand in setOf("oppo", "realme") -> "oppo"
            brand == "oneplus" -> "oneplus"
            brand in setOf("vivo", "iqoo") -> "vivo"
            brand == "samsung" -> "samsung"
            brand == "asus" -> "asus"
            else -> ""
        }
        val url = if (slug.isBlank()) "https://dontkillmyapp.com/" else "https://dontkillmyapp.com/$slug"
        return launchIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    fun recentsLockHint(): String {
        return when (normalizedBrand()) {
            "xiaomi", "redmi" -> "最近任务页下拉应用卡片或长按卡片，开启小锁。"
            "oppo", "oneplus", "realme" -> "最近任务页长按应用卡片，选择“锁定/保留”。"
            "vivo", "iqoo" -> "最近任务页打开应用菜单，选择“锁定/锁后台”。"
            "huawei", "honor" -> "最近任务页长按应用卡片，开启“锁定”。"
            else -> "最近任务页长按本应用卡片，开启“锁定/保留”即可。"
        }
    }

    fun autoStartHint(): String {
        return when (normalizedBrand()) {
            "xiaomi", "redmi" -> "建议路径：设置 > 应用 > 本应用 > 应用权限 > 后台自启动。"
            "oppo", "oneplus", "realme" -> "建议路径：设置 > 应用管理 > 自启动管理。"
            "vivo", "iqoo" -> "建议路径：设置 > 更多设置 > 应用 > 自启动。"
            "huawei", "honor" -> "建议路径：设置 > 应用启动管理，关闭“自动管理”并开启后台运行。"
            else -> "如系统有“自启动管理”，请将本应用设为允许。"
        }
    }

    private fun normalizedBrand(): String {
        val brand = Build.BRAND.orEmpty().lowercase(Locale.ROOT)
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase(Locale.ROOT)
        return when {
            "iqoo" in brand || "iqoo" in manufacturer -> "iqoo"
            "redmi" in brand || "redmi" in manufacturer -> "redmi"
            "oneplus" in brand || "oneplus" in manufacturer -> "oneplus"
            "realme" in brand || "realme" in manufacturer -> "realme"
            "honor" in brand || "hihonor" in manufacturer || "honor" in manufacturer -> "honor"
            brand.isNotBlank() -> brand
            else -> manufacturer
        }
    }

    private fun componentIntent(pkg: String, clazz: String): Intent {
        return Intent().apply {
            component = ComponentName(pkg, clazz)
        }
    }

    private fun launchIntent(context: Context, intent: Intent): Boolean {
        val finalIntent = Intent(intent).apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        return runCatching {
            context.startActivity(finalIntent)
            true
        }.getOrDefault(false)
    }
}
