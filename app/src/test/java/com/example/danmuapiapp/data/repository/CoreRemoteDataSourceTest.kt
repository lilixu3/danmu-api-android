package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.data.remote.github.GithubRemoteService
import com.example.danmuapiapp.domain.model.CoreRevision
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreRemoteDataSourceTest {
    private class FakeTransport : CoreRemoteTransport {
        var apiPaths = mutableListOf<String>()
        var proxyUrls = mutableListOf<String>()
        var requests = mutableListOf<Request>()
        var cancellableTextRequests = mutableListOf<CancellableRequest>()

        var apiResponder: (String) -> String? = { null }
        var proxyResponder: (String) -> String? = { null }
        var rawResponder: (String) -> String? = { null }
        var textResponse: ((List<String>, String) -> String?)? = null
        var cancellableResponder: (String) -> String? = { null }

        override fun apiUrlCandidates(path: String): List<String> {
            apiPaths += path
            return listOf("https://api/$path")
        }

        override fun withProxyCandidates(url: String): List<String> {
            proxyUrls += url
            return listOf("https://proxy/$url", url)
        }

        override fun <T> requestMapped(
            urls: List<String>,
            headers: Map<String, String>,
            mapper: (String) -> T?
        ): T? {
            requests += Request(urls, headers)
            urls.forEach { url ->
                val body = when {
                    url.startsWith("https://api/") -> apiResponder(url)
                    url.startsWith("https://raw/") -> rawResponder(url)
                    else -> proxyResponder(url)
                }
                if (body != null) mapper(body)?.let { return it }
            }
            return null
        }

        override fun rawUrlCandidates(repo: String, filePath: String): List<String> =
            listOf("https://raw/$repo/$filePath")

        override suspend fun requestTextResponseCancellable(
            urls: List<String>,
            headers: Map<String, String>,
            bodyValidator: (String) -> Boolean
        ): com.example.danmuapiapp.data.remote.github.GithubRemoteService.TextResponsePayload? {
            cancellableTextRequests += CancellableRequest(urls, headers)
            val body = textResponse?.invoke(urls, headers["Accept"].orEmpty()) ?: return null
            if (!bodyValidator(body)) return null
            return GithubRemoteService.TextResponsePayload(
                finalUrl = urls.first(),
                body = body,
                linkHeader = null
            )
        }

        override suspend fun <T> requestMappedCancellable(
            urls: List<String>,
            headers: Map<String, String>,
            mapper: (String) -> T?
        ): T? {
            requests += Request(urls, headers)
            urls.forEach { url ->
                val body = cancellableResponder(url)
                if (body != null) mapper(body)?.let { return it }
            }
            return null
        }
    }

    private data class Request(val urls: List<String>, val headers: Map<String, String>)
    private data class CancellableRequest(val urls: List<String>, val headers: Map<String, String>)

    @Test
    fun `default branch prefers API before HTML fallback`() {
        val transport = FakeTransport()
        val source = CoreRemoteDataSource(transport)
        transport.apiResponder = { """{"default_branch":"main"}""" }
        assertEquals("main", source.fetchDefaultBranch("owner/repo"))
        assertEquals(listOf("repos/owner/repo"), transport.apiPaths)
        assertEquals(1, transport.requests.size)

        val failingTransport = FakeTransport()
        failingTransport.proxyResponder = { """{"defaultBranch":"develop"}""" }
        assertEquals("develop", CoreRemoteDataSource(failingTransport).fetchDefaultBranch("owner/repo"))
        assertEquals(
            listOf("https://proxy/https://github.com/owner/repo", "https://github.com/owner/repo"),
            failingTransport.requests.last().urls
        )
        assertEquals(listOf("https://github.com/owner/repo"), failingTransport.proxyUrls)
    }

    @Test
    fun `branch list paginates until a short page and deduplicates`() {
        val transport = FakeTransport()
        transport.apiResponder = { url ->
            when {
                url.endsWith("page=1") -> branchPage(100) { "branch-$it" }
                url.endsWith("page=2") -> """[{"name":"branch-0"},{"name":"second"}]"""
                else -> null
            }
        }
        val branches = CoreRemoteDataSource(transport).fetchBranchList("owner/repo")
        assertEquals(101, branches.size)
        assertEquals("branch-0", branches.first())
        assertEquals("second", branches.last())
        assertEquals(
            listOf(
                "repos/owner/repo/branches?per_page=100&page=1",
                "repos/owner/repo/branches?per_page=100&page=2"
            ),
            transport.apiPaths
        )
        assertEquals(emptyList<String>(), transport.proxyUrls)
    }

    @Test
    fun `branch list falls back to HTML when API returns empty or malformed data`() {
        val emptyTransport = FakeTransport()
        emptyTransport.apiResponder = { "[]" }
        emptyTransport.proxyResponder = { """<a href="/owner/repo/tree/release"><a href="/owner/repo/tree/dev">""" }
        assertEquals(
            listOf("release", "dev"),
            CoreRemoteDataSource(emptyTransport).fetchBranchList("owner/repo")
        )
        assertEquals(listOf("https://github.com/owner/repo/branches/all"), emptyTransport.proxyUrls)

        val malformedTransport = FakeTransport()
        malformedTransport.apiResponder = { "not json" }
        malformedTransport.proxyResponder = { """<a href="/owner/repo/tree/main">""" }
        assertEquals(
            listOf("main"),
            CoreRemoteDataSource(malformedTransport).fetchBranchList("owner/repo")
        )
    }

    @Test
    fun `HTML branch fallback returns empty when no links match`() {
        val transport = FakeTransport()
        transport.apiResponder = { "[]" }
        transport.proxyResponder = { "<html></html>" }
        assertEquals(emptyList<String>(), CoreRemoteDataSource(transport).fetchBranchList("owner/repo"))
    }

    @Test
    fun `branch head encodes path and sends GitHub headers`() {
        val transport = FakeTransport()
        transport.apiResponder = {
            """{"sha":"abcdef123","commit":{"message":"Head commit","author":{"name":"A","date":"2026-10-20T10:00:00Z"}}}"""
        }
        val head = CoreRemoteDataSource(transport).fetchBranchHead("owner/repo", "feature/test")
        assertEquals("abcdef123", head?.sha)
        assertEquals("Head commit", head?.title)
        assertEquals("A", head?.author)
        assertEquals(
            listOf("repos/owner/repo/commits/feature%2Ftest"),
            transport.apiPaths
        )
        assertEquals(
            mapOf("Accept" to "application/vnd.github+json", "User-Agent" to "DanmuApiApp"),
            transport.requests.single().headers
        )
    }

    @Test
    fun `branch head returns null for malformed payloads`() {
        val transport = FakeTransport()
        transport.apiResponder = { """{"sha":""}"""}
        assertNull(CoreRemoteDataSource(transport).fetchBranchHead("owner/repo", "main"))
    }

    @Test
    fun `download candidates keep API zip direct first and archive proxy order`() {
        val transport = FakeTransport()
        val source = CoreRemoteDataSource(transport)
        val apiZip = "https://api.github.com/repos/o/r/zipball/main"
        assertEquals(
            listOf(apiZip, "https://proxy/$apiZip"),
            source.buildDownloadUrlCandidates(apiZip)
        )
        val archive = "https://github.com/o/r/archive/refs/heads/main.zip"
        assertEquals(
            listOf("https://proxy/$archive", archive),
            source.buildDownloadUrlCandidates(archive)
        )
    }

    @Test
    fun `latest release parses repository metadata`() {
        val transport = FakeTransport()
        transport.apiResponder = { url ->
            if (url.endsWith("releases/latest")) {
                """{"tag_name":"v1.2.3","name":"core","published_at":"2026-10-20T00:00:00Z","zipball_url":"https://zip"}"""
            } else null
        }
        val release = CoreRemoteDataSource(transport).fetchLatestRelease("owner/repo")
        assertEquals("v1.2.3", release?.tagName)
        assertEquals("core", release?.name)
        assertEquals("2026-10-20T00:00:00Z", release?.publishedAt)
        assertEquals("https://zip", release?.zipballUrl)
        assertEquals(listOf("repos/owner/repo/releases/latest"), transport.apiPaths)
    }

    @Test
    fun `globals version reads branch paths in order and skips blanks`() {
        val transport = FakeTransport()
        transport.rawResponder = { url ->
            when {
                url.endsWith("refs/heads/main/danmu_api/configs/globals.js") ->
                    "export const Globals = { VERSION: '1.20.6' };"
                url.endsWith("danmu-api/configs/globals.js") ->
                    "export const Globals = { VERSION: '9.9.9' };"
                else -> null
            }
        }
        val version = CoreRemoteDataSource(transport).fetchVersionFromGlobals(
            repo = "owner/repo",
            branches = listOf(" main ", "", "develop")
        )
        assertEquals("1.20.6", version)
        assertEquals(
            listOf(
                "https://raw/owner/repo/refs/heads/main/danmu_api/configs/globals.js"
            ),
            transport.requests.single().urls
        )
    }

    @Test
    fun `update comparison encodes both commits and keeps fallback commit`() {
        val transport = FakeTransport()
        transport.cancellableResponder = {
            """
            {
              "status":"ahead",
              "ahead_by":1,
              "behind_by":0,
              "total_commits":1,
              "commits":[{"sha":"remote123","commit":{"message":"remote update","author":{"name":"A","date":"2026-10-20T00:00:00Z"}}}],
              "files":[{"filename":"core.js","status":"modified","additions":2,"deletions":1,"changes":3}]
            }
            """.trimIndent()
        }
        val remote = com.example.danmuapiapp.domain.model.CoreRemoteCommit("remote123", "remote update")
        val comparison = runBlocking {
            CoreRemoteDataSource(transport).fetchUpdateComparison(
                repo = "owner/repo",
                branch = "feature/x",
                localCommitSha = "local123",
                remoteCommit = remote
            )
        }
        requireNotNull(comparison)
        assertEquals("feature/x", comparison.branch)
        assertEquals("local123", comparison.localCommitSha)
        assertEquals(com.example.danmuapiapp.domain.model.CoreUpdateRelation.RemoteAhead, comparison.relation)
        assertEquals(listOf("remote123"), comparison.commits.map { it.sha })
        assertEquals(
            listOf("https://api/repos/owner/repo/compare/local123...remote123"),
            transport.requests.last().urls
        )
    }

    @Test
    fun `branch zip URL encodes refs without plus signs`() {
        assertEquals(
            "https://api.github.com/repos/owner/repo/zipball/feature%2Fx%20y",
            CoreRemoteDataSource(FakeTransport()).buildBranchZipUrl("owner/repo", "feature/x y")
        )
    }

    private fun branchPage(count: Int, name: (Int) -> String): String =
        (0 until count).joinToString(prefix = "[", postfix = "]") { index ->
            """{"name":"${name(index)}"}"""
        }

    @Test
    fun `revision history uses branch order then default fallback and parses commits`() {
        val transport = FakeTransport()
        transport.textResponse = { _, _ ->
            """
            [
              {"sha":"abcdef123456","commit":{"message":"Fix search\n\ndetail","author":{"name":"Alice","date":"2026-10-20T10:00:00Z"}},"author":{"login":"alice"}},
              {"sha":"bbbbbbb","commit":{"message":"","committer":{"date":"2026-10-19T10:00:00Z"}}}
            ]
            """.trimIndent()
        }
        val page = runBlocking {
            CoreRemoteDataSource(transport).fetchRevisionCommits(
                repo = "owner/repo",
                branchCandidates = listOf("feature/x"),
                includeDefaultBranchFallback = true,
                page = 2,
                pageSize = 15
            )
        }
        assertEquals(2, page.revisions.size)
        assertEquals("abcdef123456", page.revisions[0].commitSha)
        assertEquals("Fix search", page.revisions[0].title)
        assertEquals("alice", page.revisions[0].author)
        assertEquals("提交 bbbbbbb", page.revisions[1].title)
        assertEquals("未知作者", page.revisions[1].author)
        assertEquals(
            listOf(
                "https://api/repos/owner/repo/commits?sha=feature%2Fx&per_page=15&page=2",
                "https://api/repos/owner/repo/commits?per_page=15&page=2"
            ),
            transport.cancellableTextRequests.single().urls
        )
    }

    @Test
    fun `author query falls back to keyword search only when login is empty`() {
        val transport = FakeTransport()
        transport.textResponse = { urls, _ ->
            when {
                urls.first().startsWith("https://api/search/commits?") ->
                    """{"items":[{"sha":"keyword123","commit":{"message":"keyword commit"}}],"total_count":1}"""
                urls.first().contains("author=") -> "[]"
                else -> """[{"sha":"keyword123","commit":{"message":"keyword commit"}}]"""
            }
        }
        val source = CoreRemoteDataSource(transport)
        val keyword = runBlocking {
            source.searchRevisionCommitsByAuthorOrKeyword(
                repo = "owner/repo",
                branchCandidates = listOf("main"),
                includeDefaultBranchFallback = false,
                query = "alice",
                page = 1,
                pageSize = 10,
                keywordSearch = {
                    source.searchRevisionCommits("owner/repo", "alice", 1, 10)
                }
            )
        }
        assertEquals("keyword123", keyword.revisions.single().commitSha)
        assertEquals(2, transport.cancellableTextRequests.size)
        assertTrue(transport.cancellableTextRequests.last().urls.first().startsWith("https://api/search/commits?"))
    }

    @Test
    fun `explicit author query never falls back to keyword search`() {
        val transport = FakeTransport()
        transport.textResponse = { urls, _ ->
            if (urls.first().contains("author=ExampleUser")) "[]" else
                """[{"sha":"keyword123","commit":{"message":"keyword commit"}}]"""
        }
        val source = CoreRemoteDataSource(transport)
        val page = runBlocking {
            source.searchRevisionCommitsByAuthorOrKeyword(
                repo = "owner/repo",
                branchCandidates = listOf("main"),
                includeDefaultBranchFallback = false,
                query = "author:ExampleUser",
                page = 1,
                pageSize = 10,
                keywordSearch = {
                    error("keyword search must not run for explicit author")
                }
            )
        }
        assertTrue(page.revisions.isEmpty())
        assertEquals(1, transport.cancellableTextRequests.size)
    }

    @Test
    fun `branch scoped keyword search pages local results and reports next page`() {
        val transport = FakeTransport()
        transport.textResponse = { urls, _ ->
            val page = Regex("""(?:[?&])page=(\d+)""").find(urls.first())?.groupValues?.get(1)?.toInt() ?: 1
            when {
                page == 1 -> (1..100).joinToString(prefix = "[", postfix = "]") { index ->
                    """{"sha":"a${index.toString().padStart(6, '0')}","commit":{"message":"fix $index"}}"""
                }
                else -> (1..1).joinToString(prefix = "[", postfix = "]") { index ->
                    """{"sha":"d444444","commit":{"message":"fix three"}}"""
                }
            }
        }
        val page = runBlocking {
            CoreRemoteDataSource(transport).searchRevisionCommitsOnBranch(
                repo = "owner/repo",
                branchCandidates = listOf("main"),
                query = "fix",
                page = 1,
                pageSize = 2
            )
        }
        assertEquals(listOf("a000001", "a000002"), page.revisions.map { it.commitSha })
        assertTrue(page.hasNextPage)
    }

    @Test
    fun `revision details keep file stats and patch fallback`() {
        val transport = FakeTransport()
        transport.cancellableResponder = {
            """
            {
              "files":[
                {"filename":"a.js","status":"modified","additions":2,"deletions":1,"changes":3,
                 "patch":"@@ -1 +1,2 @@\n-old\n+new\n+extra"},
                {"filename":"b.bin","status":"added","additions":0,"deletions":0,"changes":0}
              ],
              "stats":{"additions":2,"deletions":1}
            }
            """.trimIndent()
        }
        val revision = CoreRevision(
            commitSha = "abc1234",
            title = "head",
            message = "head",
            author = "a",
            committedAt = "",
            version = "",
            archiveUrl = ""
        )
        val details = runBlocking {
            CoreRemoteDataSource(transport).fetchRevisionDetailsFromGithub("owner/repo", revision)
        }
        requireNotNull(details)
        assertEquals(2, details.files.size)
        assertEquals(2, details.additions)
        assertEquals(1, details.deletions)
        assertEquals(2, details.changedFiles)
        assertTrue(details.files[0].lines.isNotEmpty())
        assertTrue(details.files[1].patchUnavailableReason?.contains("二进制") == true)
    }
}
