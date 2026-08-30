package com.example.danmuapiapp.desktop.app.settings

import java.net.InetAddress
import java.nio.file.InvalidPathException
import java.nio.file.Paths
import java.util.Locale

/** A stable top-level section in the desktop settings page. */
enum class SettingsCategoryId(val key: String) {
    GeneralStartup("general-startup"),
    Service("service"),
    PathsRuntime("paths-runtime"),
    NetworkDownload("network-download"),
    SecurityAdmin("security-admin"),
    Diagnostics("diagnostics"),
    UpdatesAbout("updates-about"),
}

/** Display metadata for one settings section. */
data class SettingsCategorySpec(
    val id: SettingsCategoryId,
    val title: String,
    val description: String,
    /** A platform-neutral key resolved to an icon by the UI layer. */
    val iconKey: String,
)

/**
 * The single source of truth for settings navigation order and section metadata.
 * Keep [categories] in product order; callers should not sort it alphabetically.
 */
object SettingsCategoryRegistry {
    val categories: List<SettingsCategorySpec> = listOf(
        SettingsCategorySpec(
            id = SettingsCategoryId.GeneralStartup,
            title = "通用与启动",
            description = "主题、开机自启和关闭窗口行为",
            iconKey = "general-startup",
        ),
        SettingsCategorySpec(
            id = SettingsCategoryId.Service,
            title = "服务",
            description = "监听地址、端口和核心变体",
            iconKey = "service",
        ),
        SettingsCategorySpec(
            id = SettingsCategoryId.PathsRuntime,
            title = "路径与运行时",
            description = "运行目录和运行时文件位置",
            iconKey = "paths-runtime",
        ),
        SettingsCategorySpec(
            id = SettingsCategoryId.NetworkDownload,
            title = "网络与下载",
            description = "GitHub 线路、代理和下载偏好",
            iconKey = "network-download",
        ),
        SettingsCategorySpec(
            id = SettingsCategoryId.SecurityAdmin,
            title = "管理员权限",
            description = "配置核心 ADMIN_TOKEN 与高级管理能力",
            iconKey = "security-admin",
        ),
        SettingsCategorySpec(
            id = SettingsCategoryId.Diagnostics,
            title = "诊断",
            description = "日志、健康检查和故障排查",
            iconKey = "diagnostics",
        ),
        SettingsCategorySpec(
            id = SettingsCategoryId.UpdatesAbout,
            title = "更新与关于",
            description = "核心更新、应用版本和许可信息",
            iconKey = "updates-about",
        ),
    )

    /** Alias intended for navigation code that emphasizes ordering. */
    val ordered: List<SettingsCategorySpec>
        get() = categories

    val ids: List<SettingsCategoryId>
        get() = categories.map(SettingsCategorySpec::id)

    fun find(id: SettingsCategoryId): SettingsCategorySpec? =
        categories.firstOrNull { it.id == id }

    operator fun get(id: SettingsCategoryId): SettingsCategorySpec =
        find(id) ?: error("未注册的设置分类: $id")
}

/** Persisted desktop settings exposed to the settings UI without exposing the token itself. */
data class DesktopSettingsSnapshot(
    val runtimeRootOverride: String? = null,
    val githubProxyId: String = DEFAULT_GITHUB_PROXY_ID,
    val theme: String = DEFAULT_THEME,
    val githubTokenConfigured: Boolean = false,
    /** Only whether an ADMIN_TOKEN override exists; the secret is never part of the snapshot. */
    val adminTokenConfigured: Boolean = false,
    val portOverride: Int? = null,
    val listenHostOverride: String? = null,
    /** Whether the service binds :: for the core's IPv4 + IPv6 dual-stack listener. */
    val ipv6Enabled: Boolean = false,
    val variantOverride: String? = null,
    val closeAction: String = DEFAULT_CLOSE_ACTION,
) {
    companion object {
        const val DEFAULT_GITHUB_PROXY_ID = "original"
        const val DEFAULT_THEME = "system"
        const val DEFAULT_CLOSE_ACTION = "ask"
    }
}

/**
 * UI-editable settings values. Textual values intentionally remain strings while editing so
 * invalid input can be shown by [SettingsValidation] instead of being silently discarded.
 * Blank optional values mean "remove the override" when converted to a snapshot.
 */
data class SettingsDraft(
    val runtimeRootOverride: String = "",
    val githubProxyId: String = DesktopSettingsSnapshot.DEFAULT_GITHUB_PROXY_ID,
    val theme: String = DesktopSettingsSnapshot.DEFAULT_THEME,
    val githubTokenConfigured: Boolean = false,
    val adminTokenConfigured: Boolean = false,
    val portOverride: String = "",
    val listenHostOverride: String = "",
    val ipv6Enabled: Boolean = false,
    val variantOverride: String = "",
    val closeAction: String = DesktopSettingsSnapshot.DEFAULT_CLOSE_ACTION,
) {
    /** Convert only a valid draft; invalid values fail with every explicit validation message. */
    fun toSnapshot(): DesktopSettingsSnapshot {
        val validation = SettingsValidation.validate(this)
        require(validation.isValid) {
            validation.messages.joinToString(separator = "；")
        }
        return DesktopSettingsSnapshot(
            runtimeRootOverride = runtimeRootOverride.trim().takeIf(String::isNotBlank),
            githubProxyId = githubProxyId.trim(),
            theme = theme.trim().lowercase(Locale.ROOT),
            githubTokenConfigured = githubTokenConfigured,
            adminTokenConfigured = adminTokenConfigured,
            portOverride = portOverride.trim().takeIf(String::isNotBlank)?.toInt(),
            listenHostOverride = listenHostOverride.trim().takeIf(String::isNotBlank),
            ipv6Enabled = ipv6Enabled,
            variantOverride = variantOverride.trim().lowercase(Locale.ROOT)
                .takeIf(String::isNotBlank),
            closeAction = closeAction.trim().lowercase(Locale.ROOT),
        )
    }

    companion object {
        fun from(snapshot: DesktopSettingsSnapshot): SettingsDraft = SettingsDraft(
            runtimeRootOverride = snapshot.runtimeRootOverride.orEmpty(),
            githubProxyId = snapshot.githubProxyId,
            theme = snapshot.theme,
            githubTokenConfigured = snapshot.githubTokenConfigured,
            adminTokenConfigured = snapshot.adminTokenConfigured,
            portOverride = snapshot.portOverride?.toString().orEmpty(),
            listenHostOverride = snapshot.listenHostOverride.orEmpty(),
            ipv6Enabled = snapshot.ipv6Enabled,
            variantOverride = snapshot.variantOverride.orEmpty(),
            closeAction = snapshot.closeAction,
        )
    }
}

/** Identifies the editable value changed by a settings form control. */
enum class SettingsDraftField {
    RuntimeRootOverride,
    GithubProxyId,
    Theme,
    GithubTokenConfigured,
    AdminTokenConfigured,
    PortOverride,
    ListenHostOverride,
    Ipv6Enabled,
    VariantOverride,
    CloseAction,
}

/** Type-safe edits suitable for passing from SettingsPage callbacks to the reducer. */
sealed interface SettingsDraftEdit {
    data class RuntimeRootOverride(val value: String) : SettingsDraftEdit
    data class GithubProxyId(val value: String) : SettingsDraftEdit
    data class Theme(val value: String) : SettingsDraftEdit
    data class GithubTokenConfigured(val value: Boolean) : SettingsDraftEdit
    data class AdminTokenConfigured(val value: Boolean) : SettingsDraftEdit
    data class PortOverride(val value: String) : SettingsDraftEdit
    data class ListenHostOverride(val value: String) : SettingsDraftEdit
    data class Ipv6Enabled(val value: Boolean) : SettingsDraftEdit
    data class VariantOverride(val value: String) : SettingsDraftEdit
    data class CloseAction(val value: String) : SettingsDraftEdit
}

/** Short alias for UI code that calls edits simply SettingsEdit. */
typealias SettingsEdit = SettingsDraftEdit

/** Pure operations for editing and validating a settings draft. */
object SettingsDraftReducer {
    fun edit(draft: SettingsDraft, change: SettingsDraftEdit): SettingsDraft = when (change) {
        is SettingsDraftEdit.RuntimeRootOverride -> draft.copy(runtimeRootOverride = change.value)
        is SettingsDraftEdit.GithubProxyId -> draft.copy(githubProxyId = change.value)
        is SettingsDraftEdit.Theme -> draft.copy(theme = change.value)
        is SettingsDraftEdit.GithubTokenConfigured ->
            draft.copy(githubTokenConfigured = change.value)
        is SettingsDraftEdit.AdminTokenConfigured ->
            draft.copy(adminTokenConfigured = change.value)
        is SettingsDraftEdit.PortOverride -> draft.copy(portOverride = change.value)
        is SettingsDraftEdit.ListenHostOverride ->
            draft.copy(listenHostOverride = change.value)
        is SettingsDraftEdit.Ipv6Enabled -> draft.copy(ipv6Enabled = change.value)
        is SettingsDraftEdit.VariantOverride -> draft.copy(variantOverride = change.value)
        is SettingsDraftEdit.CloseAction -> draft.copy(closeAction = change.value)
    }

    fun reduce(draft: SettingsDraft, change: SettingsDraftEdit): SettingsDraft = edit(draft, change)

    /** String-valued adapter for text controls; boolean edits must use the Boolean overload. */
    fun edit(
        draft: SettingsDraft,
        field: SettingsDraftField,
        value: String,
    ): SettingsDraft = when (field) {
        SettingsDraftField.RuntimeRootOverride -> draft.copy(runtimeRootOverride = value)
        SettingsDraftField.GithubProxyId -> draft.copy(githubProxyId = value)
        SettingsDraftField.Theme -> draft.copy(theme = value)
        SettingsDraftField.GithubTokenConfigured -> when (value.trim().lowercase(Locale.ROOT)) {
            "true" -> draft.copy(githubTokenConfigured = true)
            "false" -> draft.copy(githubTokenConfigured = false)
            else -> throw IllegalArgumentException(
                "githubTokenConfigured 必须是 true 或 false，实际为: $value",
            )
        }
        SettingsDraftField.AdminTokenConfigured -> when (value.trim().lowercase(Locale.ROOT)) {
            "true" -> draft.copy(adminTokenConfigured = true)
            "false" -> draft.copy(adminTokenConfigured = false)
            else -> throw IllegalArgumentException(
                "adminTokenConfigured 必须是 true 或 false，实际为: $value",
            )
        }
        SettingsDraftField.PortOverride -> draft.copy(portOverride = value)
        SettingsDraftField.ListenHostOverride -> draft.copy(listenHostOverride = value)
        SettingsDraftField.Ipv6Enabled -> when (value.trim().lowercase(Locale.ROOT)) {
            "true" -> draft.copy(ipv6Enabled = true)
            "false" -> draft.copy(ipv6Enabled = false)
            else -> throw IllegalArgumentException(
                "ipv6Enabled 必须是 true 或 false，实际为: $value",
            )
        }
        SettingsDraftField.VariantOverride -> draft.copy(variantOverride = value)
        SettingsDraftField.CloseAction -> draft.copy(closeAction = value)
    }

    fun edit(
        draft: SettingsDraft,
        field: SettingsDraftField,
        value: Boolean,
    ): SettingsDraft {
        require(
            field == SettingsDraftField.GithubTokenConfigured ||
                field == SettingsDraftField.AdminTokenConfigured ||
                field == SettingsDraftField.Ipv6Enabled,
        ) {
            "$field 不是布尔设置字段，不能使用 Boolean 编辑值"
        }
        return when (field) {
            SettingsDraftField.AdminTokenConfigured -> draft.copy(adminTokenConfigured = value)
            SettingsDraftField.Ipv6Enabled -> draft.copy(ipv6Enabled = value)
            SettingsDraftField.GithubTokenConfigured -> draft.copy(githubTokenConfigured = value)
            else -> error("$field 不是布尔设置字段")
        }
    }

    fun dirty(draft: SettingsDraft, baseline: SettingsDraft): Boolean = draft != baseline

    fun dirty(draft: SettingsDraft, snapshot: DesktopSettingsSnapshot): Boolean =
        dirty(draft, SettingsDraft.from(snapshot))

    fun reset(baseline: SettingsDraft): SettingsDraft = baseline

    fun reset(snapshot: DesktopSettingsSnapshot): SettingsDraft = SettingsDraft.from(snapshot)

    fun reset(
        current: SettingsDraft,
        baseline: SettingsDraft,
    ): SettingsDraft = baseline

    fun reset(
        current: SettingsDraft,
        snapshot: DesktopSettingsSnapshot,
    ): SettingsDraft = SettingsDraft.from(snapshot)

    fun validate(draft: SettingsDraft): SettingsValidation = SettingsValidation.validate(draft)
}

/**
 * Validation result for a draft. The map contains an explicit message for every invalid field;
 * no invalid value is converted to null. Nullable field accessors return null only when that
 * field has no error, which keeps normal form rendering convenient.
 */
data class SettingsValidation(
    val errors: Map<SettingsDraftField, String>,
) {
    val isValid: Boolean
        get() = errors.isEmpty()

    val messages: List<String>
        get() = errors.values.toList()

    val portError: String?
        get() = errors[SettingsDraftField.PortOverride]

    val hostError: String?
        get() = errors[SettingsDraftField.ListenHostOverride]

    val variantError: String?
        get() = errors[SettingsDraftField.VariantOverride]

    val pathError: String?
        get() = errors[SettingsDraftField.RuntimeRootOverride]

    fun errorFor(field: SettingsDraftField): String? = errors[field]

    companion object {
        private val VALID_THEMES = setOf("system", "light", "dark")
        private val VALID_CLOSE_ACTIONS = setOf("ask", "tray", "exit")
        private val HOSTNAME_PATTERN = Regex(
            "(?i)^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)*\\.?$",
        )
        private val WINDOWS_DRIVE_PATH = Regex("^[a-zA-Z]:[\\\\/].*")

        fun validate(draft: SettingsDraft): SettingsValidation {
            val errors = linkedMapOf<SettingsDraftField, String>()
            validatePath(draft.runtimeRootOverride)?.let {
                errors[SettingsDraftField.RuntimeRootOverride] = it
            }
            validatePort(draft.portOverride)?.let {
                errors[SettingsDraftField.PortOverride] = it
            }
            validateHost(draft.listenHostOverride)?.let {
                errors[SettingsDraftField.ListenHostOverride] = it
            }
            validateVariant(draft.variantOverride)?.let {
                errors[SettingsDraftField.VariantOverride] = it
            }

            val proxy = draft.githubProxyId.trim()
            if (proxy.isBlank()) {
                errors[SettingsDraftField.GithubProxyId] = "GitHub 线路不能为空"
            }
            val theme = draft.theme.trim().lowercase(Locale.ROOT)
            if (theme !in VALID_THEMES) {
                errors[SettingsDraftField.Theme] = "主题只能是 system、light 或 dark"
            }
            val closeAction = draft.closeAction.trim().lowercase(Locale.ROOT)
            if (closeAction !in VALID_CLOSE_ACTIONS) {
                errors[SettingsDraftField.CloseAction] = "关闭窗口行为只能是 ask、tray 或 exit"
            }
            return SettingsValidation(errors)
        }

        /** Returns null only for a valid blank override or a valid port. */
        fun validatePort(raw: String): String? {
            val value = raw.trim()
            if (value.isBlank()) return null
            if (!value.all(Char::isDigit)) {
                return "端口必须是 1–65535 的整数"
            }
            val port = value.toIntOrNull()
                ?: return "端口必须是 1–65535 的整数"
            return if (port in 1..65535) null else "端口必须在 1–65535 范围内"
        }

        /** Returns null only for a valid blank override or a valid host. */
        fun validateHost(raw: String): String? {
            val value = raw.trim()
            if (value.isBlank()) return null
            if (value.length > 253 || value.any(Char::isWhitespace)) {
                return "监听地址不能包含空白，且长度不能超过 253 个字符"
            }
            if (value.startsWith("[") || value.endsWith("]")) {
                if (!(value.startsWith("[") && value.endsWith("]"))) {
                    return "监听地址的 IPv6 方括号必须成对出现"
                }
                return validateIpv6(value.substring(1, value.length - 1))
            }
            if (value.contains(':')) return validateIpv6(value)
            if (value.all { it.isDigit() || it == '.' }) {
                return if (isValidIpv4(value)) null else "监听地址不是有效的 IPv4 地址"
            }
            return if (HOSTNAME_PATTERN.matches(value)) {
                null
            } else {
                "监听地址必须是有效的 IPv4、IPv6 或主机名"
            }
        }

        /** Returns null only for a valid blank override or one of stable/dev/custom. */
        fun validateVariant(raw: String): String? {
            val value = raw.trim()
            if (value.isBlank()) return null
            return if (value.lowercase(Locale.ROOT) in setOf("stable", "dev", "custom")) {
                null
            } else {
                "核心变体只能是 stable、dev 或 custom"
            }
        }

        /**
         * Validates syntax and absolute- path semantics without requiring the directory to exist.
         * A missing directory is valid because the runtime creates its data directories on start.
         */
        fun validatePath(raw: String): String? {
            val value = raw.trim()
            if (value.isBlank()) return null
            if (value.any { it.code < 0x20 || it in "<>\"|?*" }) {
                return "运行目录包含 Windows 不允许的字符"
            }
            if (value.length > 32_767) return "运行目录路径过长"
            if (WINDOWS_DRIVE_PATH.matches(value) && value.drop(2).contains(':')) {
                return "运行目录路径中的冒号位置无效"
            }
            val windowsStyle = WINDOWS_DRIVE_PATH.matches(value) || value.startsWith("\\\\")
            if (windowsStyle) {
                val hasInvalidSegment = value.split('/', '\\').any {
                    it.endsWith(' ') || it.endsWith('.')
                }
                if (hasInvalidSegment) return "运行目录的路径段不能以空格或句点结尾"
            }
            val absolute = WINDOWS_DRIVE_PATH.matches(value) || value.startsWith("\\\\") ||
                try {
                    Paths.get(value).isAbsolute()
                } catch (_: InvalidPathException) {
                    false
                }
            if (!absolute) return "运行目录必须是绝对路径，留空可使用默认目录"
            return try {
                Paths.get(value)
                null
            } catch (_: InvalidPathException) {
                "运行目录不是有效的文件系统路径"
            }
        }

        fun port(raw: String): String? = validatePort(raw)
        fun host(raw: String): String? = validateHost(raw)
        fun variant(raw: String): String? = validateVariant(raw)
        fun path(raw: String): String? = validatePath(raw)

        private fun isValidIpv4(value: String): Boolean {
            val parts = value.split('.')
            return parts.size == 4 && parts.all { part ->
                part.isNotEmpty() && part.length <= 3 && part.all(Char::isDigit) &&
                    part.toIntOrNull()?.let { it in 0..255 } == true
            }
        }

        private fun validateIpv6(value: String): String? {
            if (value.isBlank() || value.contains('[') || value.contains(']')) {
                return "监听地址不是有效的 IPv6 地址"
            }
            val valid = try {
                // A colon forces the JDK parser down its literal IPv6 path; no DNS fallback is used.
                InetAddress.getByName(value).hostAddress?.contains(':') == true
            } catch (_: Exception) {
                false
            }
            return if (valid) null else "监听地址不是有效的 IPv6 地址"
        }
    }
}
