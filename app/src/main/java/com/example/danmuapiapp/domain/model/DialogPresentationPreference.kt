package com.example.danmuapiapp.domain.model

enum class DialogPresentationPreference(
    val storageValue: String,
    val label: String,
    val description: String
) {
    Popup(
        storageValue = "popup",
        label = "弹出式弹窗",
        description = "默认居中显示，适合表单和长内容，避免底部弹窗滚动抖动"
    ),
    BottomSheet(
        storageValue = "bottom_sheet",
        label = "底部弹窗",
        description = "从屏幕底部展开，保留传统底部面板体验"
    );

    companion object {
        fun fromStorageValue(value: String?): DialogPresentationPreference {
            return entries.firstOrNull { it.storageValue == value?.trim()?.lowercase() } ?: Popup
        }
    }
}
