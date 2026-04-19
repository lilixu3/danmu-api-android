package com.example.danmuapiapp.data.service

object RootAutoStartServiceScriptPartA {

    fun build(
        moduleId: String,
        moduleDir: String,
        flagDir: String,
        flagFile: String,
        modeFile: String,
        mainClass: String
    ): String {
        return """
            #!/system/bin/sh
            MODULE_ID='$moduleId'
            # 优先使用固定模块目录，避免部分 shell 的 ${'$'}{0%/*} 不稳定
            MODDIR='$moduleDir'
            if [ ! -d "${'$'}MODDIR" ]; then
              MODDIR=${'$'}{0%/*}
            fi
            FLAGDIR='$flagDir'
            FLAGFILE='$flagFile'
            MODEFILE='$modeFile'
            MAIN_CLASS='$mainClass'
            LOGFILE="${'$'}FLAGDIR/boot.log"
            umask 022

            # 限制 boot.log 大小（保留最近 50KB）
            if [ -f "${'$'}LOGFILE" ]; then
              sz=$(wc -c < "${'$'}LOGFILE" 2>/dev/null)
              if [ -n "${'$'}sz" ] && [ "${'$'}sz" -gt 51200 ]; then
                tail -c 25600 "${'$'}LOGFILE" > "${'$'}LOGFILE.tmp" 2>/dev/null && mv "${'$'}LOGFILE.tmp" "${'$'}LOGFILE" 2>/dev/null || true
              fi
            fi

            log() {
              TS=$(date '+%F %T' 2>/dev/null || echo 'boot')
              echo "[danmuapi_boot] ${'$'}TS ${'$'}*" >> "${'$'}LOGFILE" 2>/dev/null || true
            }

            logd() {
              [ "${'$'}DEBUG" = "1" ] && log "${'$'}@"
            }

            mkdir -p "${'$'}FLAGDIR" 2>/dev/null || true
            DEBUG=0
            [ -f "${'$'}FLAGDIR/debug" ] && DEBUG=1
            log "service start"
            [ "${'$'}DEBUG" = "1" ] && log "debug=1"

            BOOT_ID=$(cat /proc/sys/kernel/random/boot_id 2>/dev/null | tr -d '\r' | tr -d '\n')
            [ -n "${'$'}BOOT_ID" ] && logd "boot_id=${'$'}BOOT_ID"

            # 开机早期补全常见可执行路径
            export PATH="/data/adb/magisk:/data/adb/ksu/bin:/sbin:/system/bin:/system/xbin:/vendor/bin:/product/bin:${'$'}PATH"
            chmod 666 /dev/null 2>/dev/null || true

            # 关闭开关则退出
            [ -f "${'$'}FLAGFILE" ] || { log "exit: disabled"; exit 0; }

            # 仅 Root 模式才执行
            if [ -f "${'$'}MODEFILE" ]; then
              MODE=$(cat "${'$'}MODEFILE" 2>/dev/null | tr -d '\r' | tr -d '\n')
              [ "${'$'}MODE" = "root" ] || { log "exit: mode=${'$'}MODE"; exit 0; }
            else
              log "exit: mode missing"
              exit 0
            fi

            wait_boot() {
              # 避免直接使用 resetprop -w 导致某些系统卡死，改为有上限轮询
              check_boot() {
                B1=$(getprop sys.boot_completed 2>/dev/null)
                B2=$(getprop dev.bootcomplete 2>/dev/null)
                [ "${'$'}B1" = "1" ] || [ "${'$'}B2" = "1" ]
              }

              check_boot && return 0
              for S in 5 10 10 15 15 20 20 25; do
                sleep "${'$'}S"
                check_boot && return 0
              done
              [ "${'$'}DEBUG" = "1" ] && log "wait_boot timeout"
              return 0
            }

            wait_boot
            # boot_completed 后再等 8 秒，降低系统尚未稳定导致的失败率
            sleep 8
            if [ "${'$'}DEBUG" = "1" ]; then
              B1=$(getprop sys.boot_completed 2>/dev/null)
              B2=$(getprop dev.bootcomplete 2>/dev/null)
              logd "boot_completed sys=${'$'}B1 dev=${'$'}B2"
            fi

            start_try() {
              # 读取包名
              PKG=""
              if [ -f "${'$'}MODDIR/config.sh" ]; then
                . "${'$'}MODDIR/config.sh"
              fi
              [ -n "${'$'}PKG" ] || { log "exit: missing PKG"; return 1; }
              [ -n "${'$'}MAIN_CLASS" ] || { log "exit: missing MAIN_CLASS"; return 1; }

              log "trigger start: PKG=${'$'}PKG"

              CACHE_APK="${'$'}FLAGDIR/apk_path"
              CACHE_LIB="${'$'}FLAGDIR/lib_dir"

              valid_libdir() {
                [ -n "${'$'}1" ] || return 1
                [ -f "${'$'}1/libnode.so" ] && [ -f "${'$'}1/libnative-lib.so" ]
              }

              APK=""
              LIBDIR=""

              # 优先使用缓存，减少开机早期对 pm/dumpsys 的依赖
              if [ -f "${'$'}CACHE_APK" ]; then
                APK=$(cat "${'$'}CACHE_APK" 2>/dev/null | tr -d '\r' | tr -d '\n')
                [ -f "${'$'}APK" ] || APK=""
              fi
              if [ -f "${'$'}CACHE_LIB" ]; then
                LIBDIR=$(cat "${'$'}CACHE_LIB" 2>/dev/null | tr -d '\r' | tr -d '\n')
                valid_libdir "${'$'}LIBDIR" || LIBDIR=""
              fi
              [ -n "${'$'}APK" ] && logd "apk cache hit"
              [ -n "${'$'}LIBDIR" ] && logd "libdir cache hit"

              resolve_apk() {
                APK=$(pm path "${'$'}PKG" 2>/dev/null | head -n 1 | cut -d: -f2)
                [ -n "${'$'}APK" ] && return 0
                return 1
              }

              if [ -z "${'$'}APK" ]; then
                if ! resolve_apk; then
                  # PackageManager 可能在 boot_completed 后短时间仍不可用，做短轮询
                  OK=0
                  for I in 1 2 3 4 5; do
                    sleep 2
                    if resolve_apk; then
                      OK=1
                      break
                    fi
                  done
                  [ "${'$'}OK" = "1" ] || { log "pm path failed"; return 1; }
                fi
              fi
              logd "apk=${'$'}APK"

              APPDIR=$(dirname "${'$'}APK")
              if [ -z "${'$'}LIBDIR" ]; then
                LIBDIR=$(ls -d "${'$'}APPDIR"/lib/* 2>/dev/null | head -n 1)
              fi
              if [ -z "${'$'}LIBDIR" ] && command -v dumpsys >/dev/null 2>&1; then
                LIBDIR=$(dumpsys package "${'$'}PKG" 2>/dev/null | grep -m1 'nativeLibraryDir=' | cut -d= -f2 | cut -d' ' -f1)
              fi
              [ -n "${'$'}LIBDIR" ] || LIBDIR="${'$'}APPDIR/lib"
              logd "libdir=${'$'}LIBDIR"

              if [ -n "${'$'}APK" ] && [ -f "${'$'}APK" ]; then
                echo "${'$'}APK" > "${'$'}CACHE_APK" 2>/dev/null || true
              fi
              if valid_libdir "${'$'}LIBDIR"; then
                echo "${'$'}LIBDIR" > "${'$'}CACHE_LIB" 2>/dev/null || true
              fi

              APPPROC='/system/bin/app_process'
              if echo "${'$'}LIBDIR" | grep -q 'arm64'; then
                [ -x /system/bin/app_process64 ] && APPPROC='/system/bin/app_process64'
              else
                [ -x /system/bin/app_process32 ] && APPPROC='/system/bin/app_process32'
              fi
              [ -x "${'$'}APPPROC" ] || APPPROC='/system/bin/app_process'

              export CLASSPATH="${'$'}APK"
              if [ -n "${'$'}LD_LIBRARY_PATH" ]; then
                export LD_LIBRARY_PATH="${'$'}LIBDIR:${'$'}LD_LIBRARY_PATH"
              else
                export LD_LIBRARY_PATH="${'$'}LIBDIR"
              fi
              export DANMUAPI_LIBDIR="${'$'}LIBDIR"

              # 使用 DE 目录，避免依赖 CE 解锁后可见
              RUNTIME_BASE='/data/adb/danmuapi_runtime'
              RUNTIME="${'$'}RUNTIME_BASE/${'$'}PKG"
              PROJ="${'$'}RUNTIME/nodejs-project"
              ENTRY="${'$'}PROJ/main.js"
              PIDFILE="${'$'}RUNTIME/root_node.pid"
              STAMPFILE="${'$'}RUNTIME/apk_stamp"

              ensure_runtime() {
                mkdir -p "${'$'}RUNTIME" "${'$'}PROJ"

                wrapper_ready() {
                  [ -f "${'$'}PROJ/main.js" ] &&
                  [ -f "${'$'}PROJ/android-server.mjs" ] &&
                  [ -f "${'$'}PROJ/worker-proxy.mjs" ] &&
                  [ -f "${'$'}PROJ/package.json" ]
                }

                get_apk_stamp() {
                  [ -n "${'$'}APK" ] || { echo ""; return 0; }
                  if command -v stat >/dev/null 2>&1; then
                    stat -c %Y "${'$'}APK" 2>/dev/null && return 0
                  fi
                  if command -v busybox >/dev/null 2>&1; then
                    busybox stat -c %Y "${'$'}APK" 2>/dev/null && return 0
                  fi
                  if command -v toybox >/dev/null 2>&1; then
                    toybox stat -c %Y "${'$'}APK" 2>/dev/null && return 0
                  fi
                  echo ""
                  return 0
                }

                CUR_STAMP=$(get_apk_stamp | tr -d '\r' | tr -d '\n')
                LAST_STAMP=$(cat "${'$'}STAMPFILE" 2>/dev/null | tr -d '\r' | tr -d '\n')

                NEED_UPDATE=0
                if ! wrapper_ready; then
                  NEED_UPDATE=1
                fi
                if [ -n "${'$'}CUR_STAMP" ] && [ "${'$'}CUR_STAMP" != "${'$'}LAST_STAMP" ]; then
                  NEED_UPDATE=1
                fi

                if [ "${'$'}NEED_UPDATE" = "1" ]; then
                  TMP="${'$'}RUNTIME_BASE/.tmp_${'$'}{PKG}_${'$'}$"
                  rm -rf "${'$'}TMP" 2>/dev/null || true
                  mkdir -p "${'$'}TMP"

                  UNZ=""
                  if command -v unzip >/dev/null 2>&1; then
                    UNZ='unzip'
                  elif command -v busybox >/dev/null 2>&1; then
                    UNZ='busybox unzip'
                  elif command -v toybox >/dev/null 2>&1; then
                    UNZ='toybox unzip'
                  fi
                  [ -n "${'$'}UNZ" ] || { log "no unzip/busybox/toybox-unzip; cannot extract assets"; rm -rf "${'$'}TMP" 2>/dev/null || true; return 0; }

                  ${'$'}UNZ -oq "${'$'}APK" 'assets/nodejs-project/*' -d "${'$'}TMP" 2>/dev/null || { log "unzip assets failed"; rm -rf "${'$'}TMP" 2>/dev/null || true; return 0; }
                  SRC="${'$'}TMP/assets/nodejs-project"
                  [ -d "${'$'}SRC" ] || { log "assets/nodejs-project missing after unzip"; rm -rf "${'$'}TMP" 2>/dev/null || true; return 0; }

                  (cd "${'$'}SRC" && find . -mindepth 1 -print) | while read -r P; do
                    TOP=$(echo "${'$'}P" | sed 's#^\./##' | cut -d/ -f1)
                    case "${'$'}TOP" in
                      config) continue ;;
                      danmu_api_*) continue ;;
                    esac

                    if [ -d "${'$'}SRC/${'$'}P" ]; then
                      mkdir -p "${'$'}PROJ/${'$'}P" 2>/dev/null || true
                    elif [ -f "${'$'}SRC/${'$'}P" ] || [ -L "${'$'}SRC/${'$'}P" ]; then
                      mkdir -p "${'$'}PROJ/$(dirname "${'$'}P")" 2>/dev/null || true
                      cp -f "${'$'}SRC/${'$'}P" "${'$'}PROJ/${'$'}P" 2>/dev/null || true
                    fi
                  done

                  mkdir -p "${'$'}PROJ/config" 2>/dev/null || true
                  if [ ! -f "${'$'}PROJ/config/.env" ] && [ -f "${'$'}SRC/config/.env" ]; then
                    cp -f "${'$'}SRC/config/.env" "${'$'}PROJ/config/.env" 2>/dev/null || true
                  fi

                  if [ -n "${'$'}CUR_STAMP" ]; then
                    echo "${'$'}CUR_STAMP" > "${'$'}STAMPFILE" 2>/dev/null || true
                  fi
                  rm -rf "${'$'}TMP" 2>/dev/null || true
                fi
              }

              normalize_variant() {
                RAW=$(echo "${'$'}1" | tr '[:upper:]' '[:lower:]' | tr -d '\r' | tr -d '\n')
                case "${'$'}RAW" in
                  dev|develop|development) echo "dev" ;;
                  custom) echo "custom" ;;
                  stable) echo "stable" ;;
                  *) echo "stable" ;;
                esac
              }

              read_selected_variant() {
                ENV_FILE="${'$'}PROJ/config/.env"
                if [ -f "${'$'}ENV_FILE" ]; then
                  RAW=$(awk -F= '/^[[:space:]]*DANMU_API_VARIANT[[:space:]]*=/{gsub(/^[[:space:]]+|[[:space:]]+$/, "", $2); print $2; exit}' "${'$'}ENV_FILE" 2>/dev/null)
                  if [ -n "${'$'}RAW" ]; then
                    normalize_variant "${'$'}RAW"
                    return 0
                  fi
                fi
                echo "stable"
              }

              read_runtime_port() {
                ENV_FILE="${'$'}PROJ/config/.env"
                [ -f "${'$'}ENV_FILE" ] || { echo ""; return 0; }
                awk -F= '/^[[:space:]]*DANMU_API_PORT[[:space:]]*=/{gsub(/^[[:space:]]+|[[:space:]]+$/, "", $2); print $2; exit}' "${'$'}ENV_FILE" 2>/dev/null
              }

              is_runtime_port_ready() {
                PORT_RAW="${'$'}1"
                case "${'$'}PORT_RAW" in
                  ''|*[!0-9]*) return 1 ;;
                esac
                [ "${'$'}PORT_RAW" -ge 1 ] 2>/dev/null || return 1
                [ "${'$'}PORT_RAW" -le 65535 ] 2>/dev/null || return 1
                PORT_HEX=$(printf '%04X' "${'$'}PORT_RAW" 2>/dev/null | tr '[:lower:]' '[:upper:]')
                [ -n "${'$'}PORT_HEX" ] || return 1
                awk -v p="${'$'}PORT_HEX" '
                  NR > 1 {
                    split($2, a, ":")
                    if (toupper(a[2]) == p && $4 == "0A") {
                      found = 1
                      exit 0
                    }
                  }
                  END { exit(found ? 0 : 1) }
                ' /proc/net/tcp /proc/net/tcp6 2>/dev/null
              }

              core_has_worker() {
                DIR="${'$'}1"
                [ -f "${'$'}DIR/worker.js" ] || [ -f "${'$'}DIR/danmu_api/worker.js" ] || [ -f "${'$'}DIR/danmu-api/worker.js" ]
              }

              ensure_runtime

              [ -f "${'$'}ENTRY" ] || { log "entry missing: ${'$'}ENTRY"; return 1; }
              [ -f "${'$'}PROJ/android-server.mjs" ] || { log "wrapper missing: android-server.mjs"; return 1; }
              [ -f "${'$'}PROJ/worker-proxy.mjs" ] || { log "wrapper missing: worker-proxy.mjs"; return 1; }
              [ -f "${'$'}PROJ/package.json" ] || { log "wrapper missing: package.json"; return 1; }
              SELECTED_VARIANT=$(read_selected_variant)
              CORE_DIR="${'$'}PROJ/danmu_api_${'$'}SELECTED_VARIANT"
              core_has_worker "${'$'}CORE_DIR" || {
                log "selected core missing or incomplete: variant=${'$'}SELECTED_VARIANT dir=${'$'}CORE_DIR"
                return 1
              }
              logd "entry=${'$'}ENTRY"
              logd "selected_variant=${'$'}SELECTED_VARIANT"

              export DANMU_API_HOME="${'$'}PROJ"
              cd "${'$'}DANMU_API_HOME" >/dev/null 2>&1 || true
              RUNTIME_PORT=$(read_runtime_port | tr -d '\r' | tr -d '\n')

              if [ -f "${'$'}PIDFILE" ]; then
                OLD=$(cat "${'$'}PIDFILE" 2>/dev/null | tr -d '\r' | tr -d '\n')
                if [ -n "${'$'}OLD" ] && [ -d "/proc/${'$'}OLD" ]; then
                  CMDLINE=$(tr '\0' ' ' < "/proc/${'$'}OLD/cmdline" 2>/dev/null)
                  if echo "${'$'}CMDLINE" | grep -q "${'$'}MAIN_CLASS"; then
                    if is_runtime_port_ready "${'$'}RUNTIME_PORT"; then
                      log "skip: already running pid=${'$'}OLD port=${'$'}RUNTIME_PORT"
                      return 0
                    fi
                    log "existing process alive but port not ready pid=${'$'}OLD port=${'$'}RUNTIME_PORT"
                    return 1
                  fi
                fi
              fi

              if command -v setsid >/dev/null 2>&1; then
                setsid "${'$'}APPPROC" /system/bin --nice-name=danmuapi_rootnode "${'$'}MAIN_CLASS" --entry "${'$'}ENTRY" --pidfile "${'$'}PIDFILE" >/dev/null 2>&1 < /dev/null &
              elif command -v nohup >/dev/null 2>&1; then
                nohup "${'$'}APPPROC" /system/bin --nice-name=danmuapi_rootnode "${'$'}MAIN_CLASS" --entry "${'$'}ENTRY" --pidfile "${'$'}PIDFILE" >/dev/null 2>&1 < /dev/null &
              else
                "${'$'}APPPROC" /system/bin --nice-name=danmuapi_rootnode "${'$'}MAIN_CLASS" --entry "${'$'}ENTRY" --pidfile "${'$'}PIDFILE" >/dev/null 2>&1 < /dev/null &
              fi

              log "start dispatched"

              sleep 2
              if [ -f "${'$'}PIDFILE" ]; then
                NEW=$(cat "${'$'}PIDFILE" 2>/dev/null | tr -d '\r' | tr -d '\n')
                if [ -n "${'$'}NEW" ] && [ -d "/proc/${'$'}NEW" ]; then
                  CMDLINE=$(tr '\0' ' ' < "/proc/${'$'}NEW/cmdline" 2>/dev/null)
                  if echo "${'$'}CMDLINE" | grep -q "${'$'}MAIN_CLASS" && is_runtime_port_ready "${'$'}RUNTIME_PORT"; then
                    logd "start ok pid=${'$'}NEW port=${'$'}RUNTIME_PORT"
                    return 0
                  fi
                  log "process alive but port not ready pid=${'$'}NEW port=${'$'}RUNTIME_PORT"
                  return 1
                fi
              fi
              log "start failed"
              return 1
            }

            if start_try; then
              exit 0
            fi

            for D in 10 20 30; do
              log "retry in ${'$'}{D}s"
              sleep "${'$'}D"
              if start_try; then
                exit 0
              fi
            done
            log "exit: start failed after 3 retries"
            exit 1
        """.trimIndent()
    }
}
