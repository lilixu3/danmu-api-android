package com.example.danmuapiapp.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreRemoteBranchSourceControllerTest {
    private class FakeTransport : CoreRemoteTransport {
        val apiPaths = mutableListOf<String>()
        val proxyUrls = mutableListOf<String>()
        val requests = mutableListOf<Request>()

        var apiResponder: (String) -> String? = { null }
        var proxyResponder: (String) -> String? = { null }

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
            requests += Request(urls)
            urls.forEach { url ->
                val body = when {
                    url.startsWith("https://api/") || url.startsWith("https://raw/") -> apiResponder(url)
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
        ): com.example.danmuapiapp.data.remote.github.GithubRemoteService.TextResponsePayload? = null

        override suspend fun <T> requestMappedCancellable(
            urls: List<String>,
            headers: Map<String, String>,
            mapper: (String) -> T?
        ): T? = requestMapped(urls, headers, mapper)
    }

    private data class Request(val urls: List<String>)

    private fun controller(
        transport: FakeTransport,
        defaultCache: MutableMap<String, String> = mutableMapOf(),
        sourceCache: MutableMap<String, CoreRemoteSource> = mutableMapOf()
    ): CoreRemoteBranchSourceController {
        val dataSource = CoreRemoteDataSource(transport)
        return CoreRemoteBranchSourceController(
            remoteDataSource = dataSource,
            readDefaultBranch = { repo -> defaultCache[repo.lowercase()] },
            writeDefaultBranch = { repo, branch -> defaultCache[repo.lowercase()] = branch },
            readBranchSource = { key -> sourceCache[key] },
            writeBranchSource = { key, source -> sourceCache[key] = source }
        )
    }

    @Test
    fun `default branch is cached and reused without another request`() {
        val transport = FakeTransport()
        transport.apiResponder = { """{"default_branch":"main"}""" }
        val cache = mutableMapOf<String, String>()
        val controller = controller(transport, cache)

        assertEquals("main", controller.resolveDefaultBranch("Owner/Core"))
        assertEquals("main", controller.resolveDefaultBranch("owner/core"))
        assertEquals(listOf("main"), cache.values.toList())
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `default branch falls back to the first branch when metadata has no default`() {
        val transport = FakeTransport()
        transport.apiResponder = { url ->
            when {
                url.endsWith("repos/owner/core") -> "{}"
                url.endsWith("page=1") -> """[{"name":"release"},{"name":"dev"}]"""
                else -> null
            }
        }
        val controller = controller(transport)

        assertEquals("release", controller.resolveDefaultBranch("owner/core"))
        assertEquals(
            listOf("repos/owner/core", "repos/owner/core/branches?per_page=100&page=1"),
            transport.apiPaths
        )
    }

    @Test
    fun `branch source builds metadata and reuses a matching cached source`() {
        val transport = FakeTransport()
        transport.apiResponder = { url ->
            when {
                url.endsWith("/commits/main") -> head("abc1234", "release title")
                url.contains("refs/heads/main/danmu_api/configs/globals.js") ->
                    "export const Globals = { VERSION: '2.0.0' };"
                else -> null
            }
        }
        val cache = mutableMapOf<String, CoreRemoteSource>()
        val controller = controller(transport, sourceCache = cache)

        val source = controller.fetchSource("owner/core", "main")
        assertNotNull(source)
        assertEquals("owner/core", source!!.metadata?.repo)
        assertEquals("main", source.metadata?.branch)
        assertEquals("abc1234", source.metadata?.commitSha)
        assertEquals("2.0.0", source.metadata?.versionLabel)
        assertEquals("2.0.0", source.release.tagName)
        assertEquals("main @ abc1234", source.release.name)
        assertEquals("https://api.github.com/repos/owner/core/zipball/main", source.release.zipballUrl)
        assertEquals("abc1234", source.remoteCommit?.sha)
        assertEquals(1, cache.size)

        val requestsBefore = transport.requests.size
        val cached = controller.fetchSource("owner/core", "main")
        assertEquals(requestsBefore + 1, transport.requests.size)
        assertEquals(source, cached)
    }

    @Test
    fun `branch source resolves a suffix branch and requests its head again`() {
        val transport = FakeTransport()
        transport.apiResponder = { url ->
            when {
                url.endsWith("/commits/release") -> null
                url.contains("refs/heads/release/danmu_api/configs/globals.js") -> null
                url.contains("refs/heads/release/danmu-api/configs/globals.js") -> null
                url.contains("/branches?") -> """[{"name":"feature/release"}]"""
                url.contains("/commits/feature%2Frelease") -> head("def5678", "feature title")
                url.endsWith("/commits/feature/release") -> head("def5678", "feature title")
                url.contains("refs/heads/feature/release/danmu_api/configs/globals.js") ->
                    "export const Globals = { VERSION: '3.0.0' };"
                else -> null
            }
        }
        val controller = controller(transport)

        val source = controller.fetchSource("owner/core", "release")
        assertNotNull(source)
        assertEquals("feature/release", source!!.metadata?.branch)
        assertEquals("def5678", source.metadata?.commitSha)
        assertEquals("3.0.0", source.metadata?.versionLabel)
        assertEquals("feature/release @ def5678", source.release.name)
        assertTrue(transport.requests.any { request ->
            request.urls.any { it.contains("/commits/feature%2Frelease") }
        })
    }

    @Test
    fun `missing head with a version falls back to an archive url`() {
        val transport = FakeTransport()
        transport.apiResponder = { url ->
            when {
                url.endsWith("/commits/main") -> null
                url.contains("refs/heads/main/danmu_api/configs/globals.js") ->
                    "export const Globals = { VERSION: '1.5.0' };"
                else -> null
            }
        }
        val controller = controller(transport)

        val source = controller.fetchSource("owner/core", "main")
        assertNotNull(source)
        assertEquals("1.5.0", source!!.release.tagName)
        assertEquals("", source.metadata?.commitSha)
        assertEquals("https://github.com/owner/core/archive/refs/heads/main.zip", source.release.zipballUrl)
        assertNull(source.remoteCommit)
    }

    @Test
    fun `missing head without a version has no source`() {
        val transport = FakeTransport()
        val controller = controller(transport)
        assertNull(controller.fetchSource("owner/core", "main"))
    }

    private fun head(sha: String, title: String): String = """
        {
          "sha": "$sha",
          "html_url": "https://github.com/owner/core/commit/$sha",
          "commit": {
            "message": "$title\n\nbody",
            "author": {"name": "Alice", "date": "2026-10-20T00:00:00Z"}
          }
        }
    """.trimIndent()
}
