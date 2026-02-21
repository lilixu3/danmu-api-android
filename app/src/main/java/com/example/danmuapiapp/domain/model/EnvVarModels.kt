package com.example.danmuapiapp.domain.model

data class EnvVarDef(
    val key: String,
    val category: String,
    val type: EnvType,
    val description: String,
    val options: List<String> = emptyList(),
    val min: Int? = null,
    val max: Int? = null,
    val sensitive: Boolean = false,
)

enum class EnvType {
    TEXT, NUMBER, BOOLEAN, SELECT, MULTI_SELECT, MAP, COLOR_LIST
}
