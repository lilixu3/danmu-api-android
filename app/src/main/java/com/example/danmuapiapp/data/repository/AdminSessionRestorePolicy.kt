package com.example.danmuapiapp.data.repository

/** 未得到成功读取的配置前保留会话；只有确认密钥移除或变更后才撤销。 */
internal fun restoreAdminSessionToken(
    sessionToken: String,
    loadedEnvVars: Map<String, String>?
): String {
    if (loadedEnvVars == null) return sessionToken
    val configuredToken = loadedEnvVars["ADMIN_TOKEN"]?.trim().orEmpty()
    return if (configuredToken.isNotBlank() && sessionToken == configuredToken) sessionToken else ""
}
