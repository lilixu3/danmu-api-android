package com.example.danmuapiapp.desktop.node

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GithubProxyCatalogTest {

    @Test
    fun originalOptionHasSingleCandidate() {
        val candidates = GithubProxyCatalog.downloadCandidates(
            DesktopSettingsProxyIds.ORIGINAL,
            "https://codeload.github.com/a/b/zip/main",
        )
        assertEquals(listOf("https://codeload.github.com/a/b/zip/main"), candidates)
    }

    @Test
    fun mirrorOptionProducesPathPrefixCandidate() {
        val candidates = GithubProxyCatalog.downloadCandidates(
            "gh_proxy_org",
            "https://codeload.github.com/a/b/zip/main",
        )
        assertTrue(candidates.any { it == "https://gh-proxy.org/https://codeload.github.com/a/b/zip/main" })
        assertTrue(candidates.any { it == "https://gh-proxy.org/codeload.github.com/a/b/zip/main" })
    }

    @Test
    fun placeholderTemplatesAreSupported() {
        val templated = GithubProxyCatalog.buildProxyCandidates(
            "https://mirror.example/?url={url}",
            "https://github.com/a/b",
        )
        // {url} 与 url= 两种变换都会产出候选（与 Android 行为一致），首候选为模板替换结果
        assertEquals("https://mirror.example/?url=https://github.com/a/b", templated.first())
        assertTrue(templated.size >= 2)
    }

    @Test
    fun latencyTargetIsRawResource() {
        // 测速目标必须是 raw 资源（对齐 Android：避免代理对 github.com 页面 403 误判）
        val field = GithubProxyCatalog::class.java.getDeclaredField("LATENCY_TARGET_URL")
        field.isAccessible = true
        val target = field.get(null) as String
        assertTrue(target.startsWith("https://raw.githubusercontent.com/"))
    }
}

/** 引用 Android 同名常量，避免测试里散落魔法字符串。 */
private object DesktopSettingsProxyIds {
    const val ORIGINAL = "original"
}
