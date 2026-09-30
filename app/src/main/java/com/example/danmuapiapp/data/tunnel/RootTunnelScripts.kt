package com.example.danmuapiapp.data.tunnel

import com.example.danmuapiapp.data.util.ShellUtils.shellQuote

/** App 与开机脚本共享身份检查、互斥和残留进程回收规则。 */
internal object RootTunnelScripts {
    fun identityFunctions(procDir: String = "/proc"): String = """
        FRP_PROC=${shellQuote(procDir)}
        FRP_REAL_DIR=$(readlink -f "${'$'}FRP_DIR")
        # Adopt/stop pre-migration processes without broadening ownership beyond this package.
        FRP_LEGACY_DIR=$(printf '%s' "${'$'}FRP_DIR" | sed 's,/user_de/,/user/,')
        FRP_LEGACY_REAL=$(readlink -f "${'$'}FRP_LEGACY_DIR")
        FRP_PACKAGE=$(basename "$(dirname "$(dirname "${'$'}FRP_DIR")")")
        frpc_ticks() {
          sed 's/.*) //' "${'$'}FRP_PROC/${'$'}1/stat" 2>/dev/null | awk '{print ${'$'}20}'
        }
        owns_frpc() {
          case "${'$'}1" in ''|*[!0-9]*) return 1 ;; esac
          [ "${'$'}1" -gt 1 ] && [ "${'$'}1" != "${'$'}${'$'}" ] || return 1
          EXE=$(readlink "${'$'}FRP_PROC/${'$'}1/exe" 2>/dev/null)
          EXE=${'$'}{EXE%" (deleted)"}
          # APK 升级后 exe 会变成随机的 ==deleted== 路径；cmdline 仍保留原包名。
          if [ "${'$'}EXE" != "${'$'}FRP_LIB" ] && [ "${'$'}EXE" != "${'$'}FRP_DIR/kernel/libfrpc.so" ] && [ "${'$'}EXE" != "${'$'}FRP_LEGACY_DIR/kernel/libfrpc.so" ]; then
            case "${'$'}EXE" in /data/app/*/lib/*/libfrpc.so) ;; *) return 1 ;; esac
            ARG0=$(tr '\0' '\n' < "${'$'}FRP_PROC/${'$'}1/cmdline" 2>/dev/null | head -n 1)
            case "${'$'}ARG0" in
              /data/app/"${'$'}FRP_PACKAGE"-*/lib/*/libfrpc.so|/data/app/*/"${'$'}FRP_PACKAGE"-*/lib/*/libfrpc.so) ;;
              *) return 1 ;;
            esac
          fi
          CFG=$(tr '\0' '\n' < "${'$'}FRP_PROC/${'$'}1/cmdline" 2>/dev/null | awk 'p == "-c" {print; exit} {p=${'$'}0}')
          case "${'$'}{CFG##*/}" in frpc.conf|frpc.toml) ;; *) return 1 ;; esac
          CFG_REAL=$(readlink -f "${'$'}{CFG%/*}")
          [ "${'$'}CFG_REAL" = "${'$'}FRP_REAL_DIR" ] || { [ -n "${'$'}FRP_LEGACY_REAL" ] && [ "${'$'}CFG_REAL" = "${'$'}FRP_LEGACY_REAL" ]; } || return 1
          TICKS=$(frpc_ticks "${'$'}1")
          [ -n "${'$'}TICKS" ] || return 1
          if [ "$(cat "${'$'}FRP_PIDFILE" 2>/dev/null)" = "${'$'}1" ]; then
            SAVED=$(cat "${'$'}FRP_PIDFILE.start" 2>/dev/null)
            [ -z "${'$'}SAVED" ] || [ "${'$'}SAVED" = "${'$'}TICKS" ] || return 1
          fi
          return 0
        }
        frpc_pids() {
          for ENTRY in "${'$'}FRP_PROC"/[0-9]*; do
            # read 是 shell 内建，先过滤名称，避免对每个 Android 进程启动 readlink。
            if [ -e "${'$'}ENTRY/comm" ]; then
              IFS= read -r COMM 2>/dev/null < "${'$'}ENTRY/comm" || continue
              case "${'$'}COMM" in libfrpc.so|frpc) ;; *) continue ;; esac
            fi
            CANDIDATE=${'$'}{ENTRY##*/}
            owns_frpc "${'$'}CANDIDATE" && echo "${'$'}CANDIDATE"
          done
          return 0
        }
        remember_frpc() {
          echo "${'$'}1" > "${'$'}FRP_PIDFILE"
          frpc_ticks "${'$'}1" > "${'$'}FRP_PIDFILE.start"
          "${'$'}FRP_PROC/${'$'}1/exe" --version > "${'$'}FRP_PIDFILE.version" 2>/dev/null || true
          chmod 0644 "${'$'}FRP_PIDFILE" "${'$'}FRP_PIDFILE.start" "${'$'}FRP_PIDFILE.version" "${'$'}FRP_DIR/frpc.log" 2>/dev/null || true
          OWNER=$(stat -c '%u' "${'$'}FRP_DIR" 2>/dev/null)
          case "${'$'}OWNER" in ''|*[!0-9]*) ;; *)
            chown "${'$'}OWNER:${'$'}OWNER" "${'$'}FRP_PIDFILE" "${'$'}FRP_PIDFILE.start" "${'$'}FRP_PIDFILE.version" "${'$'}FRP_DIR/frpc.log" 2>/dev/null || true ;;
          esac
        }
    """.trimIndent()

    /** FIFO keeps the actual frpc PID, while the sink exits on writer EOF. */
    fun logSink(): String = """
        [ -x "${'$'}FRP_LOG_HELPER" ] || { echo '缺少日志组件，请更新完整 APK' >&2; exit 7; }
        FRP_PIPE="${'$'}FRP_DIR/frpc-log.pipe"
        rm -f "${'$'}FRP_PIPE"
        mkfifo -m 600 "${'$'}FRP_PIPE" || exit 7
        (
          if command -v setsid >/dev/null 2>&1; then
            exec setsid "${'$'}FRP_LOG_HELPER" --bounded-log "${'$'}FRP_LOG" < "${'$'}FRP_PIPE"
          else
            exec "${'$'}FRP_LOG_HELPER" --bounded-log "${'$'}FRP_LOG" < "${'$'}FRP_PIPE"
          fi
        ) 9>&- > /dev/null 2>&1 &
        FRP_LOG_PID=${'$'}!
    """.trimIndent()

    /** FD 9 必须在启动的长期子进程里关闭，否则它会一直持有控制锁。 */
    fun lock(): String = """
        exec 9>"${'$'}FRP_DIR/control.lock" || exit 8
        # Android mksh 默认对额外 FD 设置 close-on-exec，显式重定向才能传给 toybox。
        flock -nx 9 9>&9 || { echo '穿透正在执行其他操作，请稍后重试' >&2; exit 8; }
    """.trimIndent()

    fun selectKernel(): String = """
        if [ -s "${'$'}FRP_DIR/kernel/libfrpc.so" ] && [ -x "${'$'}FRP_DIR/kernel/libfrpc.so" ]; then
          FRP_LIB="${'$'}FRP_DIR/kernel/libfrpc.so"
        fi
    """.trimIndent()

    /** 即使 PID 文件丢失，也只回收可验证为本应用配置的 frpc。 */
    fun stop(dir: String, library: String, procDir: String = "/proc"): String = """
        FRP_DIR=${shellQuote(dir)}
        FRP_LIB=${shellQuote(library)}
        FRP_PIDFILE="${'$'}FRP_DIR/frpc-root.pid"
        ${lock()}
        ${identityFunctions(procDir)}
        PIDS=$(frpc_pids)
        for PID in ${'$'}PIDS; do
          owns_frpc "${'$'}PID" || continue
          ORIGINAL=$(frpc_ticks "${'$'}PID")
          kill "${'$'}PID" 2>/dev/null || true
          I=0
          while [ "${'$'}I" -lt 10 ] && owns_frpc "${'$'}PID" && [ "$(frpc_ticks "${'$'}PID")" = "${'$'}ORIGINAL" ]; do
            I=${'$'}((I + 1)); sleep 0.3
          done
          if owns_frpc "${'$'}PID" && [ "$(frpc_ticks "${'$'}PID")" = "${'$'}ORIGINAL" ]; then
            kill -9 "${'$'}PID" 2>/dev/null || exit 1
            I=0
            while [ "${'$'}I" -lt 10 ] && owns_frpc "${'$'}PID" && [ "$(frpc_ticks "${'$'}PID")" = "${'$'}ORIGINAL" ]; do
              I=${'$'}((I + 1)); sleep 0.1
            done
          fi
        done
        if [ -n "$(frpc_pids)" ]; then
          echo '仍有本应用的 frpc 进程未退出，停止失败' >&2; exit 1
        fi
        rm -f "${'$'}FRP_PIDFILE" "${'$'}FRP_PIDFILE.start" "${'$'}FRP_PIDFILE.version"
    """.trimIndent()
}
