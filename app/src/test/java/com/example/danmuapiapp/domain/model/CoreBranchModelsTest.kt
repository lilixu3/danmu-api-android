package com.example.danmuapiapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CoreBranchModelsTest {

    @Test
    fun `默认分支排在首位且分支名忽略大小写去重`() {
        val catalog = buildCoreBranchCatalog(
            variant = ApiVariant.Dev,
            repo = "lilixu3/danmu_api",
            defaultBranch = "develop",
            branches = listOf("main", "Develop", "feature/x", "develop")
        )

        assertEquals("develop", catalog.defaultBranch)
        assertEquals(listOf("develop", "main", "feature/x"), catalog.branches)
    }

    @Test
    fun `接口未返回默认分支时优先识别 main`() {
        val catalog = buildCoreBranchCatalog(
            variant = ApiVariant.Custom,
            repo = "owner/repo",
            defaultBranch = "",
            branches = listOf("feature/a", "main", "master")
        )

        assertEquals("main", catalog.defaultBranch)
        assertEquals(listOf("main", "feature/a", "master"), catalog.branches)
    }

    @Test
    fun `每个核心独立保存分支`() {
        val selections = CoreBranchSelections()
            .withSelection(ApiVariant.Stable, "stable-next")
            .withSelection(ApiVariant.Dev, "dev-next")
            .withSelection(ApiVariant.Custom, "feature/custom")

        assertEquals("stable-next", selections.resolve(ApiVariant.Stable))
        assertEquals("dev-next", selections.resolve(ApiVariant.Dev))
        assertEquals("feature/custom", selections.resolve(ApiVariant.Custom))
    }
}
