package com.example.danmuapiapp.domain.model

enum class NormalModeStabilityMode(
    val storageValue: String,
    val label: String,
    val description: String
) {
    Auto(
        storageValue = "auto",
        label = "自动",
        description = "按设备内存和普通模式工作目录自动选择更稳或更快的策略"
    ),
    PreferStability(
        storageValue = "stability",
        label = "稳定优先",
        description = "关闭 worker 与热更新，适合低端机或普通模式偶发异常停止时"
    ),
    PreferPerformance(
        storageValue = "performance",
        label = "性能优先",
        description = "始终保持 worker 与热更新，适合性能足够且目录较快的设备"
    );

    companion object {
        fun fromStorageValue(value: String?): NormalModeStabilityMode {
            return entries.firstOrNull { it.storageValue == value?.trim()?.lowercase() } ?: Auto
        }
    }
}
