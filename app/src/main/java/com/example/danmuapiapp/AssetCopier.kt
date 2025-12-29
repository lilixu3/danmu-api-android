package com.example.danmuapiapp

import android.content.Context
import android.os.Build
import java.io.File
import java.io.FileOutputStream

object AssetCopier {

    /**
     * 旧逻辑只要 filesDir/nodejs-project/main.js 存在，就认为已解压完成。
     * 但 Android “覆盖安装/版本升级”不会清除 filesDir，因此升级后仍然会用旧的解压目录。
     *
     * 这里仅用 App versionCode 来判断是否需要重新解压。
     *
     * 你的需求是：
     * - 只有在版本升级（versionCode 变化，例如 40 -> 41）时才覆盖/重新解压 danmu-api 代码
     * - config/.env 和 config.yaml 属于运行时配置：升级时要保留用户已修改的配置，不要被 assets 默认配置覆盖
     */
    private const val PREFS_NAME = "asset_copier"
    private const val KEY_LAST_EXTRACTED_VERSION_CODE = "last_extracted_version_code"

    private const val ASSET_ROOT = "nodejs-project"
    fun ensureNodeProjectExtracted(context: Context): File {
        val targetRoot = File(context.filesDir, "nodejs-project")
        val mainJs = File(targetRoot, "main.js")

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentVersionCode = getAppVersionCode(context)
        val lastVersionCode = prefs.getLong(KEY_LAST_EXTRACTED_VERSION_CODE, -1L)

        val needExtract = !mainJs.exists() || lastVersionCode != currentVersionCode

        if (!needExtract) return targetRoot

        // 备份运行时配置（用户可能通过网页修改过），升级覆盖时不要被 assets 默认配置覆盖
        val envFile = File(targetRoot, "config/.env")
        val yamlFile = File(targetRoot, "config/config.yaml")
        val envBackup = runCatching { if (envFile.exists()) envFile.readBytes() else null }.getOrNull()
        val yamlBackup = runCatching { if (yamlFile.exists()) yamlFile.readBytes() else null }.getOrNull()

        // 删除旧目录，避免新旧文件混用
        if (targetRoot.exists()) safeDeleteRecursively(targetRoot)

        copyAssetDir(context, ASSET_ROOT, targetRoot)

        // 还原运行时配置（仅当旧版本里存在时才还原；否则保留 assets 默认配置）
        if (envBackup != null) {
            runCatching {
                val out = File(targetRoot, "config/.env")
                out.parentFile?.mkdirs()
                out.writeBytes(envBackup)
            }
        }
        if (yamlBackup != null) {
            runCatching {
                val out = File(targetRoot, "config/config.yaml")
                out.parentFile?.mkdirs()
                out.writeBytes(yamlBackup)
            }
        }

        // 写入标记，供下次判断
        prefs.edit()
            .putLong(KEY_LAST_EXTRACTED_VERSION_CODE, currentVersionCode)
            .apply()
        return targetRoot
    }

    private fun getAppVersionCode(context: Context): Long {
        return try {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            val pi = if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                pm.getPackageInfo(context.packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= 28) {
                pi.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pi.versionCode.toLong()
            }
        } catch (_: Throwable) {
            // 兜底：取不到就返回 -1，触发重新解压，保证不会卡在旧文件
            -1L
        }
    }

    private fun safeDeleteRecursively(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { child -> safeDeleteRecursively(child) }
        }
        runCatching { file.delete() }
    }

    private fun copyAssetDir(context: Context, assetPath: String, outDir: File) {
        val assetManager = context.assets
        val list = assetManager.list(assetPath) ?: emptyArray()
        if (list.isEmpty()) {
            // It's a file
            copyAssetFile(context, assetPath, outDir)
            return
        }

        if (!outDir.exists()) outDir.mkdirs()
        for (child in list) {
            val childAssetPath = "$assetPath/$child"
            val childOut = File(outDir, child)
            val grandChildren = assetManager.list(childAssetPath) ?: emptyArray()
            if (grandChildren.isEmpty()) {
                copyAssetFile(context, childAssetPath, childOut)
            } else {
                copyAssetDir(context, childAssetPath, childOut)
            }
        }
    }

    private fun copyAssetFile(context: Context, assetPath: String, outFile: File) {
        outFile.parentFile?.mkdirs()
        context.assets.open(assetPath).use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        }
    }
}
