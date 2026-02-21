package com.example.danmuapiapp.data.service

import android.content.Context
import com.example.danmuapiapp.domain.model.RunMode

/**
 * Root 模式开机自启模块（Magisk/KernelSU）。
 */
object RootAutoStartModule {

    private const val MODULE_ID = "danmuapi_boot_autostart"
    private const val FLAG_DIR = BootFlagPaths.FLAG_DIR
    private const val FLAG_FILE = BootFlagPaths.ROOT_MODULE_FLAG_FILE
    private const val MODE_FILE = BootFlagPaths.MODE_FILE
    private const val MODULE_DIR = "/data/adb/modules/$MODULE_ID"

    data class OpResult(val ok: Boolean, val message: String = "")

    fun installAndEnable(context: Context): OpResult {
        if (!RootShell.hasRoot(3000L)) {
            return OpResult(false, "未获得 Root 权限")
        }

        val sync = RootRuntimeController.syncWorkDirToRoot(context)
        if (!sync.ok) {
            return OpResult(false, sync.detail.ifBlank { sync.message })
        }

        val moduleProp = ensureTrailingNewline(
            """
                id=$MODULE_ID
                name=Danmu API 开机自启
                version=v1
                versionCode=1
                author=Danmu API
                description=开机触发 Root 模式启动（失败最多重试 3 次）
            """.trimIndent()
        )
        val configSh = ensureTrailingNewline("PKG='${context.packageName}'")

        val postFsDataSh = ensureTrailingNewline(
            RootAutoStartScriptBuilders.buildPostFsDataSh(
                moduleDir = MODULE_DIR,
                flagDir = FLAG_DIR
            )
        )
        val serviceSh = ensureTrailingNewline(
            RootAutoStartScriptBuilders.buildServiceSh(
                moduleId = MODULE_ID,
                moduleDir = MODULE_DIR,
                flagDir = FLAG_DIR,
                flagFile = FLAG_FILE,
                modeFile = MODE_FILE,
                mainClass = RootNodeEntry::class.java.name,
            )
        )

        val script = StringBuilder().apply {
            append("set -e\n")
            append("MODDIR='").append(MODULE_DIR).append("'\n")
            append("FLAGDIR='").append(FLAG_DIR).append("'\n")
            append("[ -d /data/adb/modules ] || { echo '未检测到 /data/adb/modules（需要 Magisk/KernelSU）' >&2; exit 2; }\n")
            append("mkdir -p \"${'$'}MODDIR\" \"${'$'}FLAGDIR\"\n")

            append("cat > \"${'$'}MODDIR/module.prop\" <<'DANMUAPI_PROP_EOF'\n")
            append(moduleProp)
            append("DANMUAPI_PROP_EOF\n")

            append("cat > \"${'$'}MODDIR/config.sh\" <<'DANMUAPI_CFG_EOF'\n")
            append(configSh)
            append("DANMUAPI_CFG_EOF\n")

            append("cat > \"${'$'}MODDIR/post-fs-data.sh\" <<'DANMUAPI_POSTFS_EOF'\n")
            append(postFsDataSh)
            append("DANMUAPI_POSTFS_EOF\n")

            append("cat > \"${'$'}MODDIR/service.sh\" <<'DANMUAPI_SERVICE_EOF'\n")
            append(serviceSh)
            append("DANMUAPI_SERVICE_EOF\n")

            append("chmod 0644 \"${'$'}MODDIR/module.prop\" \"${'$'}MODDIR/config.sh\"\n")
            append("chmod 0755 \"${'$'}MODDIR/post-fs-data.sh\" \"${'$'}MODDIR/service.sh\"\n")

            // 部分系统需要修正上下文，否则模块脚本可能不会执行。
            append("if command -v chcon >/dev/null 2>&1; then\n")
            append("  chcon -R u:object_r:magisk_file:s0 \"${'$'}MODDIR\" 2>/dev/null || chcon -R u:object_r:system_file:s0 \"${'$'}MODDIR\" 2>/dev/null || true\n")
            append("  chcon -R u:object_r:magisk_file:s0 \"${'$'}FLAGDIR\" 2>/dev/null || chcon -R u:object_r:system_file:s0 \"${'$'}FLAGDIR\" 2>/dev/null || true\n")
            append("fi\n")

            append("rm -f \"${'$'}MODDIR/disable\" \"${'$'}MODDIR/remove\" 2>/dev/null || true\n")
            append("mkdir -p \"${'$'}FLAGDIR\"\n")
            append("rm -f \"${'$'}FLAGDIR/apk_path\" \"${'$'}FLAGDIR/lib_dir\" 2>/dev/null || true\n")
            append("touch '").append(FLAG_FILE).append("'\n")
            append("[ -s \"${'$'}MODDIR/module.prop\" ] && [ -s \"${'$'}MODDIR/config.sh\" ] && [ -s \"${'$'}MODDIR/post-fs-data.sh\" ] && [ -s \"${'$'}MODDIR/service.sh\" ]\n")
        }.toString()

        val r = RootShell.exec(script, timeoutMs = 20_000L)
        if (!r.ok) {
            return OpResult(false, (r.stderr.ifBlank { r.stdout }).trim().ifBlank { "安装模块失败" })
        }

        val modeResult = writeRunModeFlag(RunMode.Root)
        if (!modeResult.ok) return modeResult
        return OpResult(true, "已安装模块并开启开机自启")
    }

    fun disableOnly(): OpResult {
        if (!RootShell.hasRoot(3000L)) {
            return OpResult(false, "未获得 Root 权限")
        }
        val r = RootShell.exec("rm -f '$FLAG_FILE' 2>/dev/null || true", timeoutMs = 8000L)
        return if (r.ok) OpResult(true, "已关闭开机自启") else {
            OpResult(false, (r.stderr.ifBlank { r.stdout }).trim().ifBlank { "关闭失败" })
        }
    }

    fun uninstall(): OpResult {
        if (!RootShell.hasRoot(3000L)) {
            return OpResult(false, "未获得 Root 权限")
        }
        val script = """
            rm -f '$FLAG_FILE' 2>/dev/null || true
            rm -rf '$FLAG_DIR' 2>/dev/null || true
            rm -rf '$MODULE_DIR' 2>/dev/null || true
        """.trimIndent()
        val r = RootShell.exec(script, timeoutMs = 12_000L)
        return if (r.ok) OpResult(true, "已卸载开机自启模块") else {
            OpResult(false, (r.stderr.ifBlank { r.stdout }).trim().ifBlank { "卸载失败" })
        }
    }

    fun writeRunModeFlag(mode: RunMode): OpResult {
        if (!RootShell.hasRoot(3000L)) {
            return OpResult(false, "未获得 Root 权限")
        }
        val value = when (mode) {
            RunMode.Normal -> "normal"
            RunMode.Root -> "root"
        }
        val cmd = """
            mkdir -p '$FLAG_DIR' 2>/dev/null || true
            cat > '$MODE_FILE' <<'DANMUAPI_MODE_EOF'
            $value
            DANMUAPI_MODE_EOF
        """.trimIndent()
        val r = RootShell.exec(cmd, timeoutMs = 8000L)
        return if (r.ok) OpResult(true, "模式标记已更新") else {
            OpResult(false, (r.stderr.ifBlank { r.stdout }).trim().ifBlank { "模式标记写入失败" })
        }
    }

    private fun ensureTrailingNewline(input: String): String {
        return if (input.endsWith("\n")) input else "$input\n"
    }
}
