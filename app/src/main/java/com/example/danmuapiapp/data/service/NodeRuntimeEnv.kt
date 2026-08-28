package com.example.danmuapiapp.data.service

import com.example.danmuapiapp.NodeBridge
import java.io.File

/**
 * 内嵌 Node 运行时的启动环境兜底：必须在 node::Start 之前写入
 * （NodeBridge.setEnvironmentVariable 底层是进程级 setenv）。
 *
 * - TMPDIR：Android 上 os.tmpdir() 完全依赖该变量，缺省时 Node 会回退到
 *   不存在的 /tmp，任何落盘临时文件的链路都会失败。
 * - HOME：保证 os.homedir() 与依赖库的家目录写入落在运行环境可控目录。
 * - NODE_COMPILE_CACHE：V8 模块编译缓存目录，二次启动跳过重复编译；
 *   目录位于缓存区，被系统清理后自动重建。
 */
internal object NodeRuntimeEnv {

    fun install(tmpDir: File?, homeDir: File?, compileCacheDir: File? = null) {
        runCatching {
            tmpDir?.let { dir ->
                dir.mkdirs()
                NodeBridge.setEnvironmentVariable("TMPDIR", dir.absolutePath, true)
            }
            homeDir?.let { home ->
                NodeBridge.setEnvironmentVariable("HOME", home.absolutePath, true)
            }
            compileCacheDir?.let { dir ->
                if (dir.exists() || dir.mkdirs()) {
                    NodeBridge.setEnvironmentVariable("NODE_COMPILE_CACHE", dir.absolutePath, true)
                }
            }
        }
    }
}
