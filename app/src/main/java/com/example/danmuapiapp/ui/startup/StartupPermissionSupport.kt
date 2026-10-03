package com.example.danmuapiapp.ui.startup

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri
import com.example.danmuapiapp.ui.screen.home.NormalModeKeepAliveGuideNavigator
import com.example.danmuapiapp.ui.screen.home.VendorSettingsPolicy
import com.example.danmuapiapp.data.util.DeviceCompatMode

internal enum class StartupPermissionItem(val key: String, val label: String) {
    Notification("notification", "通知权限"),
    Battery("battery", "电池优化设置"),
    LocalNetwork("local_network", "局域网访问权限")
}

/** Completion of an optional setup step never changes the real permission grant. */
internal fun startupPermissionStepReady(required: Boolean, supported: Boolean, granted: Boolean, skipped: Boolean): Boolean =
    !required || !supported || granted || skipped

/** Query errors are unknown, never evidence that an OEM removed a permission. */
internal fun startupRuntimePermissionSupported(definition: Boolean?, controller: Boolean?, settings: Boolean?): Boolean =
    definition != false && (definition == null || controller != false || settings != false)

/** An App info page alone does not establish that a stripped TV exposes battery controls. */
internal fun startupBatterySettingsSupported(
    powerService: Boolean?, dedicatedEntry: Boolean?, appDetails: Boolean?,
    vendorManager: Boolean?, television: Boolean
): Boolean = powerService != false && (
    dedicatedEntry != false || (appDetails != false && (!television || vendorManager != false))
)

internal object StartupPermissionSupport {
    fun runtimePermissionSupported(context: Context, permission: String): Boolean {
        val definition = try {
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPermissionInfo(permission, 0)
            (info.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE) == PermissionInfo.PROTECTION_DANGEROUS
        } catch (_: PackageManager.NameNotFoundException) { false }
        catch (_: Exception) { null }
        val controller = probeActivity(context, Intent("android.content.pm.action.REQUEST_PERMISSIONS"), frameworkRequest = true)
        val settingsIntents = if (permission == Manifest.permission.POST_NOTIFICATIONS) {
            notificationIntents(context)
        } else listOf(appDetails(context))
        val settings = combinedProbe(settingsIntents.map { probeActivity(context, it) })
        return startupRuntimePermissionSupported(definition, controller, settings)
    }

    private fun probeActivity(context: Context, intent: Intent, frameworkRequest: Boolean = false): Boolean? = try {
        @Suppress("DEPRECATION")
        val info = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
        if (info == null) false
        else if (!info.enabled || !info.applicationInfo.enabled) false
        else if (frameworkRequest) true // framework can reach OEM controllers unavailable to ordinary launches
        else if (!info.exported) false
        else {
            val guard = info.permission
            guard.isNullOrBlank() || context.checkSelfPermission(guard) == PackageManager.PERMISSION_GRANTED
        }
    } catch (_: Exception) { null }

    fun batterySupported(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val powerService = runCatching { context.getSystemService(Context.POWER_SERVICE) is PowerManager }.getOrNull()
        val dedicated = combinedProbe(dedicatedBatteryIntents(context).map { probeActivity(context, it) })
        val vendorPackages = VendorSettingsPolicy.batteryManagerPackages(VendorSettingsPolicy.family(Build.BRAND, Build.MANUFACTURER))
        val vendorManager = combinedProbe(vendorPackages.map { pkg ->
            try {
                @Suppress("DEPRECATION")
                context.packageManager.getApplicationInfo(pkg, 0).enabled
            } catch (_: PackageManager.NameNotFoundException) { false }
            catch (_: Exception) { null }
        })
        // Unknown device-feature queries keep the optional setup available.
        val television = runCatching { DeviceCompatMode.isCompatModeDevice(context) }.getOrDefault(false)
        return startupBatterySettingsSupported(powerService, dedicated, probeActivity(context, appDetails(context)), vendorManager, television)
    }

    private fun combinedProbe(results: List<Boolean?>): Boolean? = when {
        results.any { it == true } -> true
        results.any { it == null } -> null
        else -> false
    }

    private fun dedicatedBatteryIntents(context: Context): List<Intent> = listOf(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData("package:${context.packageName}".toUri()),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    ) + NormalModeKeepAliveGuideNavigator.batterySettingsIntents(context).filter { it.action != Settings.ACTION_APPLICATION_DETAILS_SETTINGS }

    fun batteryIntents(context: Context): List<Intent> = dedicatedBatteryIntents(context) + appDetails(context)

    fun notificationIntents(context: Context): List<Intent> = listOf(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        appDetails(context)
    )

    fun appDetails(context: Context): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData("package:${context.packageName}".toUri())
}
