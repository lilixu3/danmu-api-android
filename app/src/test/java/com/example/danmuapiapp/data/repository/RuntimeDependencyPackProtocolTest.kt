package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.ApiVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeDependencyPackProtocolTest {
    @Test
    fun `稳定和开发核心共用在线依赖包而自定义核心仅允许导入`() {
        assertTrue(RuntimeDependencyPackProtocol.supportsOnlineRepair(ApiVariant.Stable))
        assertTrue(RuntimeDependencyPackProtocol.supportsOnlineRepair(ApiVariant.Dev))
        assertFalse(RuntimeDependencyPackProtocol.supportsOnlineRepair(ApiVariant.Custom))
    }

    @Test
    fun `依赖指纹不受声明顺序影响`() {
        val first = linkedMapOf("brotli" to "^1.3.3", "pako" to "^2.1.0")
        val second = linkedMapOf("pako" to "^2.1.0", "brotli" to "^1.3.3")

        assertEquals(
            RuntimeDependencyPackProtocol.dependencyFingerprint(first),
            RuntimeDependencyPackProtocol.dependencyFingerprint(second)
        )
    }

    @Test
    fun `公共依赖定义与发布仓库使用相同指纹格式`() {
        val dependencies = linkedMapOf(
            "@dan-uni/dan-any" to "2.3.9",
            "brotli" to "1.3.3",
            "https-proxy-agent" to "7.0.6",
            "node-fetch" to "3.3.2",
            "opencc-js" to "1.4.1",
            "pako" to "2.1.0"
        )

        assertEquals(
            "877443073d553038b4dfa5cb917a1834ddef796434ceacf91dd9e1cb6780d846",
            RuntimeDependencyPackProtocol.dependencyFingerprint(dependencies)
        )
    }

    @Test
    fun `签名清单序号只允许保持或前进`() {
        assertTrue(RuntimeDependencyPackManager.isManifestSerialAcceptable(7L, 7L))
        assertTrue(RuntimeDependencyPackManager.isManifestSerialAcceptable(7L, 8L))
        assertFalse(RuntimeDependencyPackManager.isManifestSerialAcceptable(7L, 6L))
    }

    @Test
    fun `公共包按顶层实际版本判断是否覆盖核心声明`() {
        val manifest = RuntimePackManifest(
            packages = listOf(
                RuntimePackPackage(
                    name = "opencc-js",
                    version = "1.4.1",
                    path = "node_modules/opencc-js"
                ),
                RuntimePackPackage(
                    name = "base64-js",
                    version = "1.5.1",
                    path = "node_modules/other/node_modules/base64-js"
                )
            )
        )

        assertEquals(
            listOf("base64-js@^1.5.0", "pako@^2.1.0"),
            RuntimeDependencyPackManager.uncoveredDependencies(
                manifest,
                linkedMapOf(
                    "opencc-js" to "^1.4.0",
                    "base64-js" to "^1.5.0",
                    "pako" to "^2.1.0"
                )
            )
        )
    }

    @Test
    fun `在线依赖包只接受 node_modules 内安全路径和纯 JavaScript 文件`() {
        assertTrue(RuntimeDependencyPackProtocol.isSafeArchivePath("node_modules/opencc-js/package.json"))
        assertFalse(RuntimeDependencyPackProtocol.isSafeArchivePath("../opencc-js/package.json"))
        assertFalse(RuntimeDependencyPackProtocol.isSafeArchivePath("node_modules/../escape.js"))
        assertTrue(RuntimeDependencyPackProtocol.isNativeArtifactPath("node_modules/addon/build/addon.node"))
        assertFalse(RuntimeDependencyPackProtocol.isNativeArtifactPath("node_modules/opencc-js/index.js"))
    }
}
