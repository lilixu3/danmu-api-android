package com.example.danmuapiapp.data.service

import com.example.danmuapiapp.data.util.ShellUtils.shellQuote

/**
 * Root 运行时所需的 Shell 脚本构建。
 *
 * 只负责把参数拼装成可执行的 shell 文本，不触碰进程、文件系统与网络。
 */
internal object RootRuntimeScripts {
    internal fun buildAtomicCoreSyncShell(
        sourcePath: String,
        destinationPath: String,
        operationToken: String
    ): String {
        return """
            SRC=${shellQuote(sourcePath)}
            DST=${shellQuote(destinationPath)}
            NEW="${'$'}DST.new-${operationToken}"
            BACKUP="${'$'}DST.backup-${operationToken}"
            SRC_LIST="${'$'}DST.source-${operationToken}.cksum"
            DST_LIST="${'$'}DST.target-${operationToken}.cksum"
            HAD_DST=0
            SWAPPED=0

            cleanup_sync() {
              STATUS=${'$'}?
              trap - EXIT HUP INT TERM
              rm -rf "${'$'}NEW" 2>/dev/null || true
              rm -f "${'$'}SRC_LIST" "${'$'}DST_LIST" 2>/dev/null || true
              if [ "${'$'}STATUS" -ne 0 ] && [ "${'$'}SWAPPED" = "1" ]; then
                rm -rf "${'$'}DST" 2>/dev/null || true
                if [ "${'$'}HAD_DST" = "1" ] && [ -e "${'$'}BACKUP" ]; then
                  mv "${'$'}BACKUP" "${'$'}DST" 2>/dev/null || true
                fi
              fi
              exit "${'$'}STATUS"
            }
            trap cleanup_sync EXIT HUP INT TERM

            [ -d "${'$'}SRC" ] || exit 2
            mkdir -p "${'$'}(dirname "${'$'}DST")" || exit 3
            rm -rf "${'$'}NEW" "${'$'}BACKUP" || exit 4
            mkdir -p "${'$'}NEW" || exit 5
            if ! cp -a "${'$'}SRC/." "${'$'}NEW/" 2>/dev/null; then
              rm -rf "${'$'}NEW" || exit 6
              mkdir -p "${'$'}NEW" || exit 7
              cp -r "${'$'}SRC/." "${'$'}NEW/" || exit 8
            fi

            ([ -f "${'$'}NEW/worker.js" ] || [ -f "${'$'}NEW/danmu_api/worker.js" ] || [ -f "${'$'}NEW/danmu-api/worker.js" ]) || exit 9
            (cd "${'$'}SRC" && find . -type f -exec cksum {} + | LC_ALL=C sort) > "${'$'}SRC_LIST" || exit 10
            (cd "${'$'}NEW" && find . -type f -exec cksum {} + | LC_ALL=C sort) > "${'$'}DST_LIST" || exit 11
            cmp -s "${'$'}SRC_LIST" "${'$'}DST_LIST" || exit 12
            rm -f "${'$'}SRC_LIST" "${'$'}DST_LIST" || exit 13

            if [ -e "${'$'}DST" ]; then
              mv "${'$'}DST" "${'$'}BACKUP" || exit 14
              HAD_DST=1
            fi
            SWAPPED=1
            mv "${'$'}NEW" "${'$'}DST" || exit 15
            chmod -R u+rwX,go+rX "${'$'}DST" || exit 16
            SWAPPED=0
            rm -rf "${'$'}BACKUP" 2>/dev/null || true
            trap - EXIT HUP INT TERM
            exit 0
        """.trimIndent()
    }

    internal fun buildAtomicProjectBootstrapShell(
        sourcePath: String,
        destinationPath: String,
        operationToken: String
    ): String {
        return """
            SRC=${shellQuote(sourcePath)}
            DST=${shellQuote(destinationPath)}
            NEW="${'$'}DST.new-${operationToken}"
            BACKUP="${'$'}DST.backup-${operationToken}"
            SRC_LIST="${'$'}DST.source-${operationToken}.cksum"
            DST_LIST="${'$'}DST.target-${operationToken}.cksum"
            HAD_DST=0
            SWAPPED=0
            cleanup_sync() {
              STATUS=${'$'}?
              trap - EXIT HUP INT TERM
              rm -rf "${'$'}NEW" 2>/dev/null || true
              rm -f "${'$'}SRC_LIST" "${'$'}DST_LIST" 2>/dev/null || true
              if [ "${'$'}STATUS" -ne 0 ] && [ "${'$'}SWAPPED" = "1" ]; then
                rm -rf "${'$'}DST" 2>/dev/null || true
                if [ "${'$'}HAD_DST" = "1" ] && [ -e "${'$'}BACKUP" ]; then
                  mv "${'$'}BACKUP" "${'$'}DST" 2>/dev/null || true
                fi
              fi
              exit "${'$'}STATUS"
            }
            trap cleanup_sync EXIT HUP INT TERM

            [ -f "${'$'}SRC/main.js" ] || exit 2
            mkdir -p "${'$'}(dirname "${'$'}DST")" || exit 3
            rm -rf "${'$'}NEW" "${'$'}BACKUP" || exit 4
            mkdir -p "${'$'}NEW" || exit 5
            if ! cp -a "${'$'}SRC/." "${'$'}NEW/" 2>/dev/null; then
              rm -rf "${'$'}NEW" || exit 6
              mkdir -p "${'$'}NEW" || exit 7
              cp -r "${'$'}SRC/." "${'$'}NEW/" || exit 8
            fi
            rm -rf "${'$'}NEW/.cache" || exit 9
            mkdir -p "${'$'}NEW/.cache" || exit 10
            if [ -f "${'$'}SRC/.cache/${FavoriteCacheStore.FILE_NAME}" ]; then
              cp -f "${'$'}SRC/.cache/${FavoriteCacheStore.FILE_NAME}" "${'$'}NEW/.cache/${FavoriteCacheStore.FILE_NAME}" 2>/dev/null || \
                cat "${'$'}SRC/.cache/${FavoriteCacheStore.FILE_NAME}" > "${'$'}NEW/.cache/${FavoriteCacheStore.FILE_NAME}" || exit 11
            fi
            [ -f "${'$'}NEW/main.js" ] || exit 12

            (cd "${'$'}SRC" && find . -path './.cache' -prune -o -type f -exec cksum {} + | LC_ALL=C sort) > "${'$'}SRC_LIST" || exit 13
            (cd "${'$'}NEW" && find . -path './.cache' -prune -o -type f -exec cksum {} + | LC_ALL=C sort) > "${'$'}DST_LIST" || exit 14
            cmp -s "${'$'}SRC_LIST" "${'$'}DST_LIST" || exit 15
            rm -f "${'$'}SRC_LIST" "${'$'}DST_LIST" || exit 16

            if [ -e "${'$'}DST" ]; then
              mv "${'$'}DST" "${'$'}BACKUP" || exit 17
              HAD_DST=1
            fi
            SWAPPED=1
            mv "${'$'}NEW" "${'$'}DST" || exit 18
            SWAPPED=0
            rm -rf "${'$'}BACKUP" 2>/dev/null || true
            trap - EXIT HUP INT TERM
            exit 0
        """.trimIndent()
    }

    internal fun buildAtomicNodeModulesSyncShell(
        sourcePath: String,
        destinationPath: String,
        fingerprint: String,
        operationToken: String
    ): String {
        return """
            SRC=${shellQuote(sourcePath)}
            DST=${shellQuote(destinationPath)}
            EXPECTED=${shellQuote(fingerprint)}
            MARKER="${'$'}DST/.danmuapiapp-sync-fingerprint"
            [ -d "${'$'}SRC" ] || exit 0
            if [ -f "${'$'}MARKER" ] && [ -d "${'$'}DST" ]; then
              MARKER_FINGERPRINT=${'$'}(sed -n '1p' "${'$'}MARKER" 2>/dev/null)
              MARKER_FILE_COUNT=${'$'}(sed -n '2p' "${'$'}MARKER" 2>/dev/null)
              CURRENT_FILE_COUNT=${'$'}(find "${'$'}DST" -type f ! -name .danmuapiapp-sync-fingerprint 2>/dev/null | wc -l | tr -d '[:space:]')
              if [ "${'$'}MARKER_FINGERPRINT" = "${'$'}EXPECTED" ] &&
                 [ -n "${'$'}MARKER_FILE_COUNT" ] &&
                 [ "${'$'}MARKER_FILE_COUNT" = "${'$'}CURRENT_FILE_COUNT" ]; then
                exit 0
              fi
            fi

            NEW="${'$'}DST.new-${operationToken}"
            BACKUP="${'$'}DST.backup-${operationToken}"
            SRC_LIST="${'$'}DST.source-${operationToken}.cksum"
            DST_LIST="${'$'}DST.target-${operationToken}.cksum"
            HAD_DST=0
            SWAPPED=0
            cleanup_sync() {
              STATUS=${'$'}?
              trap - EXIT HUP INT TERM
              rm -rf "${'$'}NEW" 2>/dev/null || true
              rm -f "${'$'}SRC_LIST" "${'$'}DST_LIST" 2>/dev/null || true
              if [ "${'$'}STATUS" -ne 0 ] && [ "${'$'}SWAPPED" = "1" ]; then
                rm -rf "${'$'}DST" 2>/dev/null || true
                if [ "${'$'}HAD_DST" = "1" ] && [ -e "${'$'}BACKUP" ]; then
                  mv "${'$'}BACKUP" "${'$'}DST" 2>/dev/null || true
                fi
              fi
              exit "${'$'}STATUS"
            }
            trap cleanup_sync EXIT HUP INT TERM

            (cd "${'$'}SRC" && find . -type f ! -name .danmuapiapp-sync-fingerprint -exec cksum {} + | LC_ALL=C sort) > "${'$'}SRC_LIST" || exit 2
            if [ -d "${'$'}DST" ]; then
              (cd "${'$'}DST" && find . -type f ! -name .danmuapiapp-sync-fingerprint -exec cksum {} + | LC_ALL=C sort) > "${'$'}DST_LIST" || exit 3
              if cmp -s "${'$'}SRC_LIST" "${'$'}DST_LIST"; then
                VERIFIED_FILE_COUNT=${'$'}(find "${'$'}DST" -type f ! -name .danmuapiapp-sync-fingerprint | wc -l | tr -d '[:space:]')
                printf '%s\n%s\n' "${'$'}EXPECTED" "${'$'}VERIFIED_FILE_COUNT" > "${'$'}MARKER" || exit 4
                rm -f "${'$'}SRC_LIST" "${'$'}DST_LIST" || exit 5
                trap - EXIT HUP INT TERM
                exit 0
              fi
              rm -f "${'$'}DST_LIST" || exit 6
            fi

            mkdir -p "${'$'}(dirname "${'$'}DST")" || exit 7
            rm -rf "${'$'}NEW" "${'$'}BACKUP" || exit 8
            mkdir -p "${'$'}NEW" || exit 9
            if ! cp -a "${'$'}SRC/." "${'$'}NEW/" 2>/dev/null; then
              rm -rf "${'$'}NEW" || exit 10
              mkdir -p "${'$'}NEW" || exit 11
              cp -r "${'$'}SRC/." "${'$'}NEW/" || exit 12
            fi
            rm -f "${'$'}NEW/.danmuapiapp-sync-fingerprint" 2>/dev/null || true

            (cd "${'$'}NEW" && find . -type f ! -name .danmuapiapp-sync-fingerprint -exec cksum {} + | LC_ALL=C sort) > "${'$'}DST_LIST" || exit 14
            cmp -s "${'$'}SRC_LIST" "${'$'}DST_LIST" || exit 15
            VERIFIED_FILE_COUNT=${'$'}(find "${'$'}NEW" -type f ! -name .danmuapiapp-sync-fingerprint | wc -l | tr -d '[:space:]')
            printf '%s\n%s\n' "${'$'}EXPECTED" "${'$'}VERIFIED_FILE_COUNT" > "${'$'}NEW/.danmuapiapp-sync-fingerprint" || exit 16
            rm -f "${'$'}SRC_LIST" "${'$'}DST_LIST" || exit 17

            if [ -e "${'$'}DST" ]; then
              mv "${'$'}DST" "${'$'}BACKUP" || exit 18
              HAD_DST=1
            fi
            SWAPPED=1
            mv "${'$'}NEW" "${'$'}DST" || exit 19
            chmod -R u+rwX,go+rX "${'$'}DST" || exit 20
            SWAPPED=0
            rm -rf "${'$'}BACKUP" 2>/dev/null || true
            trap - EXIT HUP INT TERM
            exit 0
        """.trimIndent()
    }

    internal fun buildClearRuntimeMarkersShell(pidPath: String, startedAtPath: String): String {
        return """
            PID_FILE=${shellQuote(pidPath)}
            STARTED_AT_FILE=${shellQuote(startedAtPath)}
            rm -f "${'$'}PID_FILE" "${'$'}STARTED_AT_FILE" 2>/dev/null || true
        """.trimIndent()
    }

    internal fun buildRootProjectIncrementalSyncShell(
        srcProjectPath: String,
        dstProjectPath: String,
        managedPaths: List<String> = listOf("main.js")
    ): String {
        val normalizedPaths = managedPaths.map(::normalizeRuntimeAssetPath).distinct()
        require("main.js" in normalizedPaths) { "managedPaths 必须包含 main.js" }
        val managedPathList = normalizedPaths.joinToString(" ") { shellQuote(it) }
        return """
            SRC=${shellQuote(srcProjectPath)}
            DST=${shellQuote(dstProjectPath)}
            mkdir -p "${'$'}DST" "${'$'}DST/config" "${'$'}DST/logs" || exit 2

            # app-runtime-assets.json 是唯一文件清单。只同步清单内文件，保留 Root 的
            # config、core、node_modules 与 .cache，并支持 server/ 等嵌套目录。
            for REL in $managedPathList; do
              FILE="${'$'}SRC/${'$'}REL"
              [ -f "${'$'}FILE" ] || exit 2
              REL_DIR="${'$'}{REL%/*}"
              if [ "${'$'}REL_DIR" != "${'$'}REL" ]; then mkdir -p "${'$'}DST/${'$'}REL_DIR" || exit 2; fi
              TMP="${'$'}DST/${'$'}REL.tmp-${'$'}$"
              rm -f "${'$'}TMP" 2>/dev/null || true
              if ! cp -f "${'$'}FILE" "${'$'}TMP" 2>/dev/null; then
                cat "${'$'}FILE" > "${'$'}TMP" || exit 3
              fi
              cmp -s "${'$'}FILE" "${'$'}TMP" || exit 4
              mv -f "${'$'}TMP" "${'$'}DST/${'$'}REL" || exit 5
            done

            if [ -f "${'$'}SRC/.app_version" ]; then
              cp -f "${'$'}SRC/.app_version" "${'$'}DST/.app_version" 2>/dev/null || \
                cat "${'$'}SRC/.app_version" > "${'$'}DST/.app_version" || exit 5
            fi
            test -f "${'$'}DST/main.js" || exit 6
        """.trimIndent()
    }

    internal fun buildRootProjectPermissionNormalizeShell(
        rootProjectPath: String,
        fullScan: Boolean,
        managedPaths: List<String> = listOf("main.js")
    ): String {
        val normalizedPaths = managedPaths.map(::normalizeRuntimeAssetPath).distinct()
        val commonTail = """
            [ -d "${'$'}DST/config" ] && chmod 0755 "${'$'}DST/config" 2>/dev/null || true
            [ -f "${'$'}DST/config/.env" ] && chmod 0640 "${'$'}DST/config/.env" 2>/dev/null || true
            [ -d "${'$'}DST/logs" ] && chmod 0775 "${'$'}DST/logs" 2>/dev/null || true
            exit 0
        """.trimIndent()

        return if (fullScan) {
            """
                DST=${shellQuote(rootProjectPath)}
                [ -d "${'$'}DST" ] || exit 0

                # 首次引导/大同步后才做递归权限归一，避免每次热启动扫描整个 core/node_modules。
                chown -R 0:0 "${'$'}DST" 2>/dev/null || true
                chmod -R u+rwX,go+rX "${'$'}DST" 2>/dev/null || true

                $commonTail
            """.trimIndent()
        } else {
            val managedPathList = normalizedPaths.joinToString(" ") { shellQuote(it) }
            """
                DST=${shellQuote(rootProjectPath)}
                [ -d "${'$'}DST" ] || exit 0
                mkdir -p "${'$'}DST/config" "${'$'}DST/logs" 2>/dev/null || true

                # 热启动只修正 manifest 管理的文件和配置/日志目录。
                for REL in $managedPathList; do
                  [ -f "${'$'}DST/${'$'}REL" ] && chmod 0644 "${'$'}DST/${'$'}REL" 2>/dev/null || true
                done
                [ -f "${'$'}DST/.app_version" ] && chmod 0644 "${'$'}DST/.app_version" 2>/dev/null || true

                $commonTail
            """.trimIndent()
        }
    }

    internal fun buildRootRuntimeDependencyProbeShell(
        rootProjectPath: String,
        variantKey: String
    ): String {
        return """
            PROJECT=${shellQuote(rootProjectPath)}
            CORE=${shellQuote("$rootProjectPath/danmu_api_$variantKey")}
            REQUIREMENTS="${'$'}CORE/${NodeProjectManager.CORE_RUNTIME_REQUIREMENTS_FILE}"
            [ -f "${'$'}REQUIREMENTS" ] || exit 0
            MISSING=0
            while IFS= read -r DEP || [ -n "${'$'}DEP" ]; do
              DEP="${'$'}(printf '%s' "${'$'}DEP" | tr -d '\r')"
              [ -n "${'$'}DEP" ] || continue
              if ! printf '%s' "${'$'}DEP" | grep -Eq '^(@[A-Za-z0-9._-]+/)?[A-Za-z0-9._-]+${'$'}'; then
                printf '%s\n' '__invalid_dependency_name__'
                MISSING=1
                continue
              fi
              if [ ! -f "${'$'}CORE/node_modules/${'$'}DEP/package.json" ] && \
                 [ ! -f "${'$'}PROJECT/node_modules/${'$'}DEP/package.json" ]; then
                printf '%s\n' "${'$'}DEP"
                MISSING=1
              fi
            done < "${'$'}REQUIREMENTS"
            [ "${'$'}MISSING" = "0" ]
        """.trimIndent()
    }

    internal fun buildNodeModuleIntegrityProbeShell(
        srcNodeModulesVar: String,
        dstNodeModulesVar: String
    ): String {
        return buildNodeModulePackageVisitShell(
            srcNodeModulesVar = srcNodeModulesVar,
            dstNodeModulesVar = dstNodeModulesVar,
            actionOnMismatch = "NEED_SYNC=1"
        )
    }

    internal fun buildNodeModulePackageRepairShell(
        srcNodeModulesVar: String,
        dstNodeModulesVar: String
    ): String {
        return buildNodeModulePackageVisitShell(
            srcNodeModulesVar = srcNodeModulesVar,
            dstNodeModulesVar = dstNodeModulesVar,
            actionOnMismatch = """
                DST_PACKAGE_DIR="${'$'}DST_ROOT/${'$'}PKG"
                rm -rf "${'$'}DST_PACKAGE_DIR" 2>/dev/null || true
                mkdir -p "${'$'}(dirname "${'$'}DST_PACKAGE_DIR")" 2>/dev/null || true
                cp -a "${'$'}SRC_ROOT/${'$'}PKG" "${'$'}DST_PACKAGE_DIR" 2>/dev/null || cp -r "${'$'}SRC_ROOT/${'$'}PKG" "${'$'}DST_PACKAGE_DIR" 2>/dev/null || true
                chmod -R u+rwX,go+rX "${'$'}DST_PACKAGE_DIR" 2>/dev/null || true
            """.trimIndent()
        )
    }

    internal fun buildNodeModuleIntegrityVerifyShell(
        srcNodeModulesVar: String,
        dstNodeModulesVar: String
    ): String {
        return buildNodeModulePackageVisitShell(
            srcNodeModulesVar = srcNodeModulesVar,
            dstNodeModulesVar = dstNodeModulesVar,
            actionOnMismatch = "exit 2"
        )
    }

    private fun buildNodeModulePackageVisitShell(
        srcNodeModulesVar: String,
        dstNodeModulesVar: String,
        actionOnMismatch: String
    ): String {
        val indentedAction = actionOnMismatch.prependIndent("      ")
        return """
            SRC_ROOT="${'$'}$srcNodeModulesVar"
            DST_ROOT="${'$'}$dstNodeModulesVar"

            check_node_package() {
              PKG="${'$'}1"
              SRC_DEP="${'$'}SRC_ROOT/${'$'}PKG/package.json"
              DST_DEP="${'$'}DST_ROOT/${'$'}PKG/package.json"
              if [ -f "${'$'}SRC_DEP" ]; then
                SRC_DEP_SUM="${'$'}(cksum "${'$'}SRC_DEP" 2>/dev/null | tr ' ' ':' | cut -d: -f1-2)"
                DST_DEP_SUM="${'$'}(cksum "${'$'}DST_DEP" 2>/dev/null | tr ' ' ':' | cut -d: -f1-2)"
                if [ -n "${'$'}SRC_DEP_SUM" ] && [ "${'$'}SRC_DEP_SUM" != "${'$'}DST_DEP_SUM" ]; then
$indentedAction
                fi
              fi
            }

            [ -d "${'$'}SRC_ROOT" ] || exit 0
            mkdir -p "${'$'}DST_ROOT" 2>/dev/null || true
            for ENTRY in "${'$'}SRC_ROOT"/*; do
              [ -d "${'$'}ENTRY" ] || continue
              BASE="${'$'}{ENTRY##*/}"
              case "${'$'}BASE" in
                .* ) continue ;;
                @* )
                  for SCOPED_ENTRY in "${'$'}ENTRY"/*; do
                    [ -d "${'$'}SCOPED_ENTRY" ] || continue
                    SCOPED_NAME="${'$'}{SCOPED_ENTRY##*/}"
                    [ -n "${'$'}SCOPED_NAME" ] && check_node_package "${'$'}BASE/${'$'}SCOPED_NAME"
                  done
                  ;;
                * )
                  check_node_package "${'$'}BASE"
                  ;;
              esac
            done
        """.trimIndent()
    }
}
