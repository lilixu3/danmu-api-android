package com.example.danmuapiapp.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoreRemoteBranchParsingTest {
    @Test
    fun `default branch parses and normalizes API metadata`() {
        assertEquals(
            "release/main",
            CoreRemoteBranchParsing.parseDefaultBranch(
                """{"default_branch":" refs/heads/release/main/ "}"""
            )
        )
        assertNull(CoreRemoteBranchParsing.parseDefaultBranch("not json"))
        assertNull(CoreRemoteBranchParsing.parseDefaultBranch("""{"default_branch":"  "}"""))
    }

    @Test
    fun `default branch HTML fallback supports both quote styles`() {
        assertEquals(
            "develop",
            CoreRemoteBranchParsing.parseDefaultBranchFromHtml(
                """<script>{"defaultBranch":"develop"}</script>"""
            )
        )
        assertEquals(
            "main",
            CoreRemoteBranchParsing.parseDefaultBranchFromHtml(
                """<script>{'defaultBranch': 'main'}</script>"""
            )
        )
        assertNull(CoreRemoteBranchParsing.parseDefaultBranchFromHtml("<html></html>"))
    }

    @Test
    fun `branch list drops blanks and returns null for malformed payloads`() {
        assertEquals(
            listOf("main", "feature/test"),
            CoreRemoteBranchParsing.parseBranchList(
                """[{"name":" main "},{"name":"feature/test"},{"name":"  "},{"other":1}]"""
            )
        )
        assertNull(CoreRemoteBranchParsing.parseBranchList("{}"))
        assertNull(CoreRemoteBranchParsing.parseBranchList("broken"))
    }

    @Test
    fun `HTML branch fallback only keeps matching repository links`() {
        val html = """
            <a href="/owner/repo/tree/main">main</a>
            <a href='/owner/repo/tree/feature/test'>feature</a>
            <a href="/other/repo/tree/dev">dev</a>
            <a href="/owner/repo/tree/main">duplicate</a>
        """.trimIndent()
        assertEquals(
            listOf("main", "feature/test"),
            CoreRemoteBranchParsing.parseBranchListFromHtml("owner/repo", html)
        )
        assertNull(CoreRemoteBranchParsing.parseBranchListFromHtml("owner/repo", "<html></html>"))
    }

    @Test
    fun `branch head parsing keeps commit fields and author fallbacks`() {
        val head = CoreRemoteBranchParsing.parseBranchHead(
            """
            {
              "sha": " abcdef123456 ",
              "commit": {
                "message": "Fix branch fallback\n\nDetails",
                "author": {"name": "", "date": "2026-10-20T10:00:00Z"}
              },
              "author": {"login": "octocat"},
              "html_url": " https://github.com/owner/repo/commit/abcdef123456 "
            }
            """.trimIndent()
        )
        requireNotNull(head)
        assertEquals("abcdef123456", head.sha)
        assertEquals("Fix branch fallback", head.title)
        assertEquals("Fix branch fallback\n\nDetails", head.message)
        assertEquals("octocat", head.author)
        assertEquals("2026-10-20T10:00:00Z", head.publishedAt)
        assertEquals("https://github.com/owner/repo/commit/abcdef123456", head.htmlUrl)
    }

    @Test
    fun `branch head parsing uses SHA title and rejects missing SHA`() {
        val head = CoreRemoteBranchParsing.parseBranchHead(
            """{"sha":"bbbbbbb","commit":{"message":""}}"""
        )
        assertEquals("提交 bbbbbbb", head?.title)
        assertEquals("未知作者", head?.author)
        assertNull(CoreRemoteBranchParsing.parseBranchHead("""{"sha":"  "}"""))
    }

    @Test
    fun `branch cache keys normalize case`() {
        assertEquals(
            "owner/repo:feature/test",
            CoreRemoteBranchParsing.branchCacheKey("Owner/Repo", "Feature/Test")
        )
    }
}
