package com.example.danmuapiapp.ui.screen.home

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri
import com.example.danmuapiapp.data.util.DeviceCompatMode

object NormalModeKeepAliveGuideNavigator {

    fun manufacturerName(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        return manufacturer.ifBlank { "Android" }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        return runCatching {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return@runCatching false
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }.getOrDefault(false)
    }

    fun batterySettingsHint(): String = VendorSettingsPolicy.batteryHint(normalizedBrand())

    fun batterySettingsIntents(context: Context): List<Intent> {
        val packageUri = "package:${context.packageName}".toUri()
        val candidates = listOf(
            Intent("android.settings.APP_BATTERY_SETTINGS").apply {
                data = packageUri
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            },
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = packageUri
            }
        )
        // OEM battery controls can live inside App info; a missing AOSP screen
        // is not evidence that MIUI/OriginOS/ColorOS lacks battery controls.
        return candidates
    }

    fun openAppBatterySettings(context: Context): Boolean =
        batterySettingsIntents(context).any { launchIntent(context, it) }

    fun requestIgnoreBatteryOptimization(context: Context): Boolean {
        if (isIgnoringBatteryOptimizations(context)) return false
        val packageUri = "package:${context.packageName}".toUri()
        val candidates = listOf(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = packageUri },
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent("android.settings.APP_BATTERY_SETTINGS").apply { data = packageUri },
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = packageUri }
        )
        return candidates.any { launchIntent(context, it) }
    }

    fun openAutoStartSettings(context: Context): Boolean {
        if (DeviceCompatMode.isCompatModeDevice(context)) {
            return launchIntent(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = "package:${context.packageName}".toUri()
            })
        }
        val brand = normalizedBrand()
        val candidates = VendorSettingsPolicy.autoStartComponents(brand).map { (pkg, clazz) ->
            componentIntent(pkg, clazz)
        } + if (brand in setOf("xiaomi", "redmi")) {
            // Xiaomi's documented permission editor is a fallback, not a grant check.
            listOf(Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setPackage("com.miui.securitycenter")
                addCategory(Intent.CATEGORY_DEFAULT)
                putExtra("extra_pkgname", context.packageName)
            })
        } else emptyList()

        if (candidates.any { launchIntent(context, it) }) return true

        val appInfoIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = "package:${context.packageName}".toUri()
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
        return launchIntent(context, Intent(Intent.ACTION_VIEW, url.toUri()))
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
            "xiaomi", "redmi" -> "MIUI/澎湃 OS：设置 > 应用 > 权限管理 > 后台自启动；不同版本也可能位于应用详情。"
            "oppo", "oneplus", "realme" -> "ColorOS/realme UI：设置 > 应用 > 自启动；旧版也可能在手机管家或电池设置。部分版本没有独立开关，请以系统显示为准。"
            "vivo", "iqoo" -> "OriginOS/Funtouch OS：设置 > 应用与权限 > 权限管理 > 自启动；旧版也可能位于 i 管家。"
            "huawei", "honor" -> "建议路径：设置 > 应用启动管理，关闭“自动管理”并开启后台运行。"
            else -> "如系统有“自启动管理”，请将本应用设为允许。"
        }
    }

    private fun normalizedBrand(): String = VendorSettingsPolicy.family(Build.BRAND, Build.MANUFACTURER)

    private fun componentIntent(pkg: String, clazz: String): Intent {
        return Intent().apply { component = ComponentName(pkg, clazz) }
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
