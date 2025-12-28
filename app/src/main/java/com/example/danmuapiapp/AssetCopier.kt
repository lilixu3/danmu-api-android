package com.example.danmuapiapp

import android.content.Context
import java.io.File
import java.io.FileOutputStream

object AssetCopier {

    fun ensureNodeProjectExtracted(context: Context): File {
        val targetRoot = File(context.filesDir, "nodejs-project")
        val mainJs = File(targetRoot, "main.js")
        if (mainJs.exists()) return targetRoot

        copyAssetDir(context, "nodejs-project", targetRoot)
        return targetRoot
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
