package com.example.danmuapiapp.ui.screen.home

import java.util.Locale

internal object VendorSettingsPolicy {
    /** Brand wins over the parent manufacturer (realme/OnePlus often report OPPO). */
    fun family(brand: String?, manufacturer: String?): String {
        fun classify(value: String): String? = when {
            "iqoo" in value -> "iqoo"
            "vivo" in value -> "vivo"
            "realme" in value -> "realme"
            "oneplus" in value -> "oneplus"
            "oppo" in value -> "oppo"
            "redmi" in value -> "redmi"
            "xiaomi" in value || "poco" in value || "miui" in value -> "xiaomi"
            "honor" in value -> "honor"
            "huawei" in value -> "huawei"
            "asus" in value -> "asus"
            "samsung" in value -> "samsung"
            else -> null
        }
        val normalizedBrand = brand.orEmpty().trim().lowercase(Locale.ROOT)
        return classify(normalizedBrand)
            ?: classify(manufacturer.orEmpty().trim().lowercase(Locale.ROOT))
            ?: normalizedBrand
    }

    // Known entry points are candidates, never proof that a ROM exposes them.
    // Sources: Xiaomi developer adaptation FAQ and AutoStarter's original source.
    fun autoStartComponents(family: String): List<Pair<String, String>> = when (family) {
        "xiaomi", "redmi" -> listOf(
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity"
        )
        "huawei", "honor" -> listOf(
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity"
        )
        "oppo", "realme", "oneplus" -> listOf(
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity"
        ) + if (family == "oneplus") listOf(
            "com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
        ) else emptyList()
        "vivo", "iqoo" -> listOf(
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager",
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
        )
        "asus" -> listOf(
            "com.asus.mobilemanager" to "com.asus.mobilemanager.powersaver.PowerSaverSettings"
        )
        else -> emptyList()
    }

    fun batteryManagerPackages(family: String): List<String> = when (family) {
        "xiaomi", "redmi" -> listOf("com.miui.powerkeeper", "com.miui.securitycenter")
        "vivo", "iqoo" -> listOf("com.iqoo.secure", "com.vivo.permissionmanager")
        "oppo", "realme" -> listOf("com.coloros.safecenter", "com.oppo.safe")
        "oneplus" -> listOf("com.coloros.safecenter", "com.oppo.safe", "com.oneplus.security")
        "huawei", "honor" -> listOf("com.huawei.systemmanager")
        "asus" -> listOf("com.asus.mobilemanager")
        else -> emptyList()
    }

    fun batteryHint(family: String): String = when (family) {
        "xiaomi", "redmi" -> "MIUI/澎湃 OS：在应用详情中找到“省电策略”，选择“无限制”；后台自启动需在权限管理中单独开启。"
        "vivo", "iqoo" -> "OriginOS/Funtouch OS：在设置的“电池 → 后台耗电管理”中允许后台耗电；自启动需在应用权限管理中单独开启。"
        "oppo", "realme", "oneplus" -> "ColorOS/realme UI（部分一加机型为 OxygenOS）：在“应用电池管理”中允许后台活动；自启动是独立选项。具体名称随系统版本不同。"
        else -> "如系统提供此选项，请将本应用的后台电池使用设为不受限制。"
    }
}
