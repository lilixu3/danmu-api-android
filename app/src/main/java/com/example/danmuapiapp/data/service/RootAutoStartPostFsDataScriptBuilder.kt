package com.example.danmuapiapp.data.service

object RootAutoStartPostFsDataScriptBuilder {

    fun build(moduleDir: String, flagDir: String): String {
        return """
            #!/system/bin/sh
            MODDIR='$moduleDir'
            FLAGDIR='$flagDir'

            # 读取包名
            PKG=""
            if [ -f "${'$'}MODDIR/config.sh" ]; then
              . "${'$'}MODDIR/config.sh"
            fi
            [ -n "${'$'}PKG" ] || exit 0

            RUNTIME_BASE='/data/adb/danmuapi_runtime'
            RUNTIME="${'$'}RUNTIME_BASE/${'$'}PKG"
            PROJ="${'$'}RUNTIME/nodejs-project"
            CFG="${'$'}PROJ/config"
            LOGS="${'$'}PROJ/logs"

            mkdir -p "${'$'}FLAGDIR" "${'$'}RUNTIME" "${'$'}PROJ" "${'$'}CFG" "${'$'}LOGS" 2>/dev/null || true

            # 收敛权限，避免异常继承
            chmod 755 "${'$'}RUNTIME_BASE" "${'$'}RUNTIME" "${'$'}PROJ" "${'$'}CFG" "${'$'}LOGS" 2>/dev/null || true
            [ -f "${'$'}CFG/.env" ] && chmod 600 "${'$'}CFG/.env" 2>/dev/null || true
        """.trimIndent()
    }
}
