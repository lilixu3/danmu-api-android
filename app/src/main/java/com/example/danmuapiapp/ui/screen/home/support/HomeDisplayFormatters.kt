package com.example.danmuapiapp.ui.screen.home.support

import java.util.Locale

/**
 * 首页展示格式化工具。
 *
 * 从 `HomeViewModel.formatUptime` 原样抽出，行为保持不变。
 */
internal fun formatHomeUptime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
    else String.format(Locale.getDefault(), "%02d:%02d", m, s)
}
