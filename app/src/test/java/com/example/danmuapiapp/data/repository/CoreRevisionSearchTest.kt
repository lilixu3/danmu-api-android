package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.CoreRevision
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class CoreRevisionSearchTest {
    @Test
    fun queryScopesKeywordToRepository() {
        assertEquals(
            "fix parser repo:owner/project",
            CoreRevisionSearch.query("owner/project", "  fix parser  ")
        )
    }

    @Test
    fun authorQueryRecognizesLoginAndExplicitFormsWithoutStealingShaSearch() {
        assertEquals(
            CoreRevisionAuthorQuery(login = "example-user", explicit = false),
            CoreRevisionSearch.authorQuery(" example-user ")
        )
        assertEquals(
            CoreRevisionAuthorQuery(login = "example-user", explicit = true),
            CoreRevisionSearch.authorQuery("@example-user")
        )
        assertEquals(
            CoreRevisionAuthorQuery(login = "ExampleUser", explicit = true),
            CoreRevisionSearch.authorQuery("author:ExampleUser")
        )
        assertEquals(null, CoreRevisionSearch.authorQuery("abcdef123456"))
        assertEquals(null, CoreRevisionSearch.authorQuery("修复缓存"))
        assertEquals(null, CoreRevisionSearch.authorQuery("fix parser"))
    }

    @Test
    fun paginationUsesGithubResultCountWithoutFixedHistoryLimit() {
        assertTrue(CoreRevisionSearch.hasNextPage(null, 1, 15, 31, 15))
        assertFalse(CoreRevisionSearch.hasNextPage(null, 3, 15, 31, 1))
        assertTrue(
            CoreRevisionSearch.hasNextPage(
                "<https://api.github.com/search/commits?page=2>; rel=\"next\"",
                1,
                15,
                0,
                15
            )
        )
        assertTrue(CoreRevisionSearch.hasNextHistoryPage(null, 15, 15))
        assertFalse(CoreRevisionSearch.hasNextHistoryPage(null, 14, 15))
    }

    @Test
    fun localBranchSearchMatchesMessageAuthorAndSha() {
        val revision = CoreRevision(
            commitSha = "abcdef1234567890",
            title = "Fix cache selector",
            message = "Fix cache selector\nKeep legacy behavior",
            author = "ExampleUser",
            committedAt = "",
            version = "",
            archiveUrl = ""
        )

        assertTrue(CoreRevisionSearch.matches(revision, "legacy behavior"))
        assertTrue(CoreRevisionSearch.matches(revision, "exampleuser"))
        assertTrue(CoreRevisionSearch.matches(revision, "abcdef1"))
        assertFalse(CoreRevisionSearch.matches(revision, "other branch"))
    }
}
