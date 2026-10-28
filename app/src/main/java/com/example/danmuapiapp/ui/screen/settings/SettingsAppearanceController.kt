package com.example.danmuapiapp.ui.screen.settings

import android.app.Activity
import android.content.Context
import com.example.danmuapiapp.data.util.AppAppearancePrefs
import com.example.danmuapiapp.domain.model.AccessEntryLayout
import com.example.danmuapiapp.domain.model.AccessEntryTab
import com.example.danmuapiapp.domain.model.AppBackgroundMode
import com.example.danmuapiapp.domain.model.AppBackgroundRefreshPolicy
import com.example.danmuapiapp.domain.model.GlassMaterialPreference
import com.example.danmuapiapp.domain.model.GlassTuningPreference
import com.example.danmuapiapp.domain.model.GlassTuningPreset
import com.example.danmuapiapp.domain.model.NightModePreference
import com.example.danmuapiapp.domain.model.isValidBackgroundImageUrl
import com.example.danmuapiapp.domain.repository.SettingsRepository

/**
 * 设置页「外观与主题」子控制器：主题、玻璃、背景、DPI、最近任务与访问入口偏好。
 *
 * 全部改动仍然只经过 [SettingsRepository]，行为与拆分前一致。
 */
internal class SettingsAppearanceController(
    private val context: Context,
    private val settingsRepo: SettingsRepository,
    private val postMessage: (String) -> Unit
) {
    fun setAccessEntryDefaultTab(tab: AccessEntryTab) {
        settingsRepo.setAccessEntryDefaultTab(tab)
        postMessage("默认访问标签已设为${tab.label}")
    }

    fun setAccessEntryLayout(layout: AccessEntryLayout) {
        settingsRepo.setAccessEntryLayout(layout)
        postMessage("访问入口已改为${layout.label}形式")
    }

    fun setNightMode(mode: NightModePreference) {
        settingsRepo.setNightMode(mode)
        postMessage(
            when (mode) {
                NightModePreference.FollowSystem -> "主题已改为跟随系统"
                NightModePreference.Light -> "已切换为浅色主题"
                NightModePreference.Dark -> "已切换为暗色主题"
            }
        )
    }

    fun setGlassMaterial(material: GlassMaterialPreference) {
        settingsRepo.setGlassMaterial(material)
        postMessage(
            when (material) {
                GlassMaterialPreference.LiquidGlass -> "已启用液态玻璃"
                GlassMaterialPreference.Off -> "已关闭液态玻璃"
            }
        )
    }

    fun setGlassBottomBar(enabled: Boolean) {
        settingsRepo.setGlassBottomBar(enabled)
        postMessage(if (enabled) "已启用液态玻璃底栏" else "已关闭液态玻璃底栏")
    }

    fun setGlassTuning(tuning: GlassTuningPreference) {
        settingsRepo.setGlassTuning(tuning)
    }

    fun setGlassPreset(preset: GlassTuningPreset) {
        settingsRepo.setGlassTuning(preset.tuning)
        postMessage("已切换为${preset.label}玻璃")
    }

    fun setAppBackgroundMode(mode: AppBackgroundMode) {
        val current = settingsRepo.appBackground.value
        if (current.mode == mode) return
        settingsRepo.setAppBackground(current.copy(mode = mode))
        postMessage(
            when (mode) {
                AppBackgroundMode.Solid -> "已使用纯色背景"
                AppBackgroundMode.LocalImage -> "已使用本地图片背景"
                AppBackgroundMode.OnlineImage -> "已切换到在线图片背景"
                AppBackgroundMode.RandomOnlineImage -> "已启用前台随机图片背景"
            }
        )
    }

    fun setLocalAppBackground(uri: String) {
        settingsRepo.setAppBackground(
            settingsRepo.appBackground.value.copy(
                mode = AppBackgroundMode.LocalImage,
                localImageUri = uri
            )
        )
        postMessage("已应用本地图片背景")
    }

    fun setOnlineAppBackground(url: String, random: Boolean) {
        val normalized = url.trim()
        if (!isValidBackgroundImageUrl(normalized)) {
            postMessage("请输入有效的 HTTP 或 HTTPS 图片链接")
            return
        }
        val current = settingsRepo.appBackground.value
        settingsRepo.setAppBackground(
            if (random) {
                current.copy(
                    mode = AppBackgroundMode.RandomOnlineImage,
                    randomImageUrl = normalized
                )
            } else {
                current.copy(
                    mode = AppBackgroundMode.OnlineImage,
                    onlineImageUrl = normalized
                )
            }
        )
        postMessage(
            if (random) {
                "已应用前台随机图片接口"
            } else {
                "已应用在线图片背景"
            }
        )
    }

    fun setRandomBackgroundRefreshPolicy(policy: AppBackgroundRefreshPolicy) {
        val current = settingsRepo.appBackground.value
        if (policy == AppBackgroundRefreshPolicy.Custom &&
            current.customRandomRefreshSeconds <= 0L
        ) {
            postMessage("请先输入并应用自定义刷新秒数")
            return
        }
        if (current.randomRefreshPolicy == policy) return
        settingsRepo.setAppBackground(current.copy(randomRefreshPolicy = policy))
        postMessage("随机背景前台刷新条件已设为 ${randomRefreshPolicyLabel(policy)}")
    }

    fun setCustomRandomBackgroundRefreshInterval(input: String): Boolean {
        val seconds = input.trim().toLongOrNull()
        if (seconds == null || seconds <= 0L || seconds > Long.MAX_VALUE / 1_000L) {
            postMessage("请输入大于 0 的有效秒数")
            return false
        }
        val current = settingsRepo.appBackground.value
        settingsRepo.setAppBackground(
            current.copy(
                randomRefreshPolicy = AppBackgroundRefreshPolicy.Custom,
                customRandomRefreshSeconds = seconds
            )
        )
        postMessage("随机背景前台刷新条件已设为 ${seconds} 秒")
        return true
    }

    private fun randomRefreshPolicyLabel(policy: AppBackgroundRefreshPolicy): String {
        return when (policy) {
            AppBackgroundRefreshPolicy.OnForeground -> "每次进入前台"
            AppBackgroundRefreshPolicy.Seconds30 -> "30 秒"
            AppBackgroundRefreshPolicy.Minute1 -> "1 分钟"
            AppBackgroundRefreshPolicy.Minutes3 -> "3 分钟"
            AppBackgroundRefreshPolicy.Minutes5 -> "5 分钟"
            AppBackgroundRefreshPolicy.Minutes10 -> "10 分钟"
            AppBackgroundRefreshPolicy.Custom -> "自定义"
        }
    }

    fun setAppDpiOverride(activity: Activity?, dpi: Int) {
        val normalized = AppAppearancePrefs.normalizeAppDpiOverride(dpi)
        val actualDpi = activity?.resources?.displayMetrics?.densityDpi ?: currentSystemDensityDpi()
        val refreshNeeded = AppAppearancePrefs.shouldRecreateForDpi(
            settingsRepo.appDpiOverride.value, normalized, actualDpi, currentSystemDensityDpi()
        )
        settingsRepo.setAppDpiOverride(normalized)
        if (!refreshNeeded) {
            postMessage(
                if (normalized == AppAppearancePrefs.APP_DPI_SYSTEM) {
                    "当前已是跟随系统 DPI"
                } else {
                    "当前已是 ${normalized} DPI"
                }
            )
            return
        }
        postMessage(
            if (normalized == AppAppearancePrefs.APP_DPI_SYSTEM) {
                "已恢复跟随系统 DPI，正在刷新界面"
            } else {
                "已应用 ${normalized} DPI，正在刷新界面"
            }
        )
        activity?.recreate()
    }

    fun currentSystemDensityDpi(): Int = AppAppearancePrefs.systemDensityDpi(context)

    fun setHideFromRecents(enabled: Boolean) {
        settingsRepo.setHideFromRecents(enabled)
        postMessage(
            if (enabled) {
                "已隐藏最近任务卡片"
            } else {
                "已恢复显示最近任务卡片"
            }
        )
    }

    fun setFileLogEnabled(enabled: Boolean) {
        settingsRepo.setFileLogEnabled(false)
        postMessage("已固定为 API 日志模式，不再写入本地日志文件")
    }
}
