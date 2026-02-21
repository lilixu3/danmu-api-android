package com.example.danmuapiapp

import android.annotation.SuppressLint
import java.io.File

/**
 * Node 运行时 JNI 桥接。
 */
object NodeBridge {

    init {
        val firstErr = runCatching {
            // 某些系统顺序敏感：先 JNI，再 libnode。
            System.loadLibrary("native-lib")
            System.loadLibrary("node")
        }.exceptionOrNull()

        if (firstErr != null) {
            val secondErr = runCatching {
                // 兼容部分 ROM 的反向加载顺序。
                System.loadLibrary("node")
                System.loadLibrary("native-lib")
            }.exceptionOrNull()

            if (secondErr != null) {
                val ok = tryLoadFromAbsolutePaths()
                if (!ok) throw firstErr
            }
        }
    }

    external fun startNodeWithArguments(args: Array<String>): Int

    private fun cleanPath(raw: String): String {
        var s = raw.trim()
        while (s.length >= 2) {
            val first = s.first()
            val last = s.last()
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                s = s.substring(1, s.length - 1).trim()
                continue
            }
            break
        }
        return s.trim { it == '"' || it == '\'' }
    }

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    private fun tryLoadFromAbsolutePaths(): Boolean {
        val candidates = linkedSetOf<String>()

        System.getenv("DANMUAPI_LIBDIR")
            ?.let { cleanPath(it) }
            ?.takeIf { it.isNotEmpty() }
            ?.let { candidates.add(it) }

        System.getenv("LD_LIBRARY_PATH")
            ?.split(':')
            ?.map { cleanPath(it) }
            ?.filter { it.isNotEmpty() }
            ?.forEach { candidates.add(it) }

        System.getenv("CLASSPATH")
            ?.let { cleanPath(it) }
            ?.takeIf { it.isNotEmpty() }
            ?.let { apkPath ->
                val appDir = runCatching { File(apkPath).parentFile }.getOrNull()
                if (appDir != null) {
                    val libRoot = File(appDir, "lib")
                    if (libRoot.exists() && libRoot.isDirectory) {
                        runCatching {
                            libRoot.listFiles()?.forEach { file ->
                                if (file.isDirectory) candidates.add(file.absolutePath)
                            }
                        }
                    }
                }
            }

        if (candidates.isEmpty()) return false

        for (dir in candidates) {
            val d = File(dir)
            if (!d.exists() || !d.isDirectory) continue

            val libcxx = File(d, "libc++_shared.so")
            val libnode = File(d, "libnode.so")
            val libjni = File(d, "libnative-lib.so")
            if (!libnode.exists() || !libjni.exists()) continue

            runCatching {
                if (libcxx.exists()) System.load(libcxx.absolutePath)
            }

            val loaded = runCatching {
                System.load(libnode.absolutePath)
                System.load(libjni.absolutePath)
            }.isSuccess
            if (loaded) return true
        }

        return false
    }
}
