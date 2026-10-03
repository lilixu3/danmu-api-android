package com.example.danmuapiapp.data.service

/** Root process discovery works after app data is removed and app_process rewrites argv. */
internal object RootRuntimeRecovery {
    data class Process(val pid: Int, val startTicks: Long)

    fun parseProcesses(output: String): List<Process> = output.lineSequence().mapNotNull { line ->
        val parts = line.trim().split(' ')
        if (parts.size != 3 || parts[0] != "OWNED_ROOT") return@mapNotNull null
        val pid = parts[1].toIntOrNull()?.takeIf { it > 1 } ?: return@mapNotNull null
        val ticks = parts[2].toLongOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
        Process(pid, ticks)
    }.distinctBy { it.pid }.toList()

    fun shouldTryAfterDataLoss(noExplicitMode: Boolean, normalProcessRunning: Boolean, rootHealthMatches: Boolean, portOpen: Boolean): Boolean =
        noExplicitMode && !normalProcessRunning && (rootHealthMatches || portOpen)

    fun buildScanShell(projectPath: String, mainClass: String, procRoot: String = "/proc"): String =
        identityShell(projectPath, mainClass, procRoot) + "\n" + """
            if [ "${'$'}PROC_ROOT" = '/proc' ]; then
              if PS_OUTPUT=${'$'}(ps -A -o PID,UID,ARGS 2>/dev/null); then
                CANDIDATES=${'$'}(printf '%s\n' "${'$'}PS_OUTPUT" | awk -v cls="${'$'}MAIN_CLASS" '${'$'}2 == "0" && (${'$'}3 == "danmuapi_rootnode" || index(${'$'}0, cls) > 0) {print ${'$'}1}')
              else
                CANDIDATES=''
                for DIR in "${'$'}PROC_ROOT"/[0-9]*; do CANDIDATES="${'$'}CANDIDATES ${'$'}{DIR##*/}"; done
              fi
            else
              CANDIDATES=''
              for DIR in "${'$'}PROC_ROOT"/[0-9]*; do CANDIDATES="${'$'}CANDIDATES ${'$'}{DIR##*/}"; done
            fi
            for PID in ${'$'}CANDIDATES; do
              if owned_root "${'$'}PID"; then
                printf 'OWNED_ROOT %s %s\n' "${'$'}PID" "${'$'}START_TICKS"
              fi
            done
        """.trimIndent()

    fun buildSignalShell(projectPath: String, mainClass: String, processes: List<Process>, force: Boolean, procRoot: String = "/proc"): String {
        val checks = processes.joinToString("\n") { process ->
            // The birth tick check prevents signaling a reused PID between discovery and stop.
            "if owned_root ${process.pid} && [ \"\${START_TICKS}\" = ${quote(process.startTicks.toString())} ]; then kill -${if (force) "KILL" else "TERM"} ${process.pid} || exit 2; fi"
        }
        return identityShell(projectPath, mainClass, procRoot) + "\n" + checks
    }

    private fun identityShell(projectPath: String, mainClass: String, procRoot: String): String = """
        PROJECT=${quote(projectPath.trimEnd('/'))}
        MAIN_CLASS=${quote(mainClass)}
        PROC_ROOT=${quote(procRoot)}
    """.trimIndent() + "\n" + identityFunctionShell()

    fun identityFunctionShell(): String = """
        owned_root() {
          PID="${'$'}1"
          case "${'$'}PID" in ''|*[!0-9]*|0|1) return 1 ;; esac
          DIR="${'$'}PROC_ROOT/${'$'}PID"
          [ -d "${'$'}DIR" ] || return 1
          OWNER=${'$'}(awk '/^Uid:/ {print ${'$'}2; exit}' "${'$'}DIR/status" 2>/dev/null)
          [ "${'$'}OWNER" = '0' ] || return 1
          CWD=${'$'}(readlink "${'$'}DIR/cwd" 2>/dev/null) || return 1
          [ "${'$'}CWD" = "${'$'}PROJECT" ] || return 1
          EXE=${'$'}(readlink "${'$'}DIR/exe" 2>/dev/null) || return 1
          case "${'$'}EXE" in /system/bin/app_process|/system/bin/app_process32|/system/bin/app_process64) ;; *) return 1 ;; esac
          CMD=${'$'}(tr '\000' '\n' < "${'$'}DIR/cmdline" 2>/dev/null | head -n 1 | sed 's/[[:space:]]*${'$'}//')
          if [ "${'$'}CMD" != 'danmuapi_rootnode' ] && [ "${'$'}CMD" != "${'$'}MAIN_CLASS" ]; then return 1; fi
          STAT=${'$'}(sed 's/^.*) //' "${'$'}DIR/stat" 2>/dev/null) || return 1
          STATE=${'$'}(printf '%s\n' "${'$'}STAT" | awk '{print ${'$'}1}')
          case "${'$'}STATE" in ''|Z|X) return 1 ;; esac
          START_TICKS=${'$'}(printf '%s\n' "${'$'}STAT" | awk '{print ${'$'}20}')
          case "${'$'}START_TICKS" in ''|*[!0-9]*|0) return 1 ;; esac
          return 0
        }
    """.trimIndent()

    private fun quote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"
}
