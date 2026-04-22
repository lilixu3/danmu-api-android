package com.example.danmuapiapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GithubRepoInputTest {

    @Test
    fun `tree 链接应自动解析仓库和分支`() {
        val resolved = resolveCustomCoreSource(
            repoInput = "https://github.com/Celestials316/danmu_api/tree/douban-cookie-hardening",
            branchInput = ""
        )

        assertTrue(resolved.isValidRepo)
        assertEquals("Celestials316/danmu_api", resolved.repo)
        assertEquals("douban-cookie-hardening", resolved.branch)
        assertEquals(
            "Celestials316/danmu_api · douban-cookie-hardening",
            resolved.sourceText
        )
    }

    @Test
    fun `仓库首页链接未填写分支时应回落到 main`() {
        val resolved = resolveCustomCoreSource(
            repoInput = "https://github.com/lilixu3/danmu_api",
            branchInput = ""
        )

        assertTrue(resolved.isValidRepo)
        assertEquals("lilixu3/danmu_api", resolved.repo)
        assertEquals(DEFAULT_CUSTOM_CORE_BRANCH, resolved.branch)
        assertEquals(DEFAULT_CUSTOM_CORE_BRANCH, resolved.suggestedBranch)
    }

    @Test
    fun `非 github 链接不应被误识别为仓库`() {
        val resolved = resolveCustomCoreSource(
            repoInput = "https://gitlab.com/lilixu3/danmu_api",
            branchInput = ""
        )

        assertFalse(resolved.isValidRepo)
        assertEquals("", resolved.repo)
        assertEquals("", resolved.branch)
    }

    @Test
    fun `手填分支应优先于链接默认分支`() {
        val resolved = resolveCustomCoreSource(
            repoInput = "https://github.com/lilixu3/danmu_api",
            branchInput = "feature/x"
        )

        assertEquals("lilixu3/danmu_api", resolved.repo)
        assertEquals("feature/x", resolved.branch)
        assertEquals(DEFAULT_CUSTOM_CORE_BRANCH, resolved.suggestedBranch)
    }

    @Test
    fun `blob 文件链接不应把文件路径误识别为分支`() {
        val resolved = resolveCustomCoreSource(
            repoInput = "https://github.com/Celestials316/danmu_api/blob/douban-cookie-hardening/src/config.js",
            branchInput = ""
        )

        assertTrue(resolved.isValidRepo)
        assertEquals("Celestials316/danmu_api", resolved.repo)
        assertEquals(DEFAULT_CUSTOM_CORE_BRANCH, resolved.branch)
    }

    @Test
    fun `repo-only 保存应保留已有分支`() {
        val resolved = resolveRepoOnlyCustomCoreSource(
            repoInput = "Celestials316/danmu_api",
            currentBranch = "douban-cookie-hardening"
        )

        assertEquals("Celestials316/danmu_api", resolved.repo)
        assertEquals("douban-cookie-hardening", resolved.branch)
    }

    @Test
    fun `repo-only 保存遇到 tree 链接时应优先采用链接分支`() {
        val resolved = resolveRepoOnlyCustomCoreSource(
            repoInput = "https://github.com/Celestials316/danmu_api/tree/douban-cookie-hardening",
            currentBranch = "main"
        )

        assertEquals("Celestials316/danmu_api", resolved.repo)
        assertEquals("douban-cookie-hardening", resolved.branch)
    }
}
