package com.example.danmuapiapp.domain.model

data class CoreVariantDisplayNames(
    val stable: String = "",
    val dev: String = "",
    val custom: String = ""
) {
    fun resolve(variant: ApiVariant): String {
        val configured = when (variant) {
            ApiVariant.Stable -> stable
            ApiVariant.Dev -> dev
            ApiVariant.Custom -> custom
        }.trim()
        return configured.ifBlank { variant.label }
    }
}
