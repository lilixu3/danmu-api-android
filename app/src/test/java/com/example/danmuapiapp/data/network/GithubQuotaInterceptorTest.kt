package com.example.danmuapiapp.data.network

import com.example.danmuapiapp.data.service.GithubQuotaExhaustedException
import com.example.danmuapiapp.data.service.GithubQuotaRequestContext
import com.example.danmuapiapp.data.service.GithubQuotaTracker
import com.example.danmuapiapp.data.repository.executeCancellable
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class GithubQuotaInterceptorTest {
    private var now = 1000L
    private val tracker = GithubQuotaTracker { now }
    private var configured = false
    private var sent = 0
    private var prompts = 0
    private var responseCode = 200
    private var remaining: Int? = null
    private var responseResource = "core"
    private var redirect = false
    private val client = OkHttpClient.Builder()
        .addInterceptor(GithubQuotaInterceptor(
            requireAvailable = { resource, notify ->
                tracker.blocked(resource, configured)?.let {
                    if (notify) prompts += 1
                    throw GithubQuotaExhaustedException(it)
                }
            },
            observe = tracker::observe
        ))
        .addInterceptor { chain ->
            sent += 1
            val request = chain.request()
            val finalRequest = if (redirect) request.newBuilder().url("https://codeload.github.com/example/repo/zip/abc").build() else request
            Response.Builder().request(finalRequest).protocol(Protocol.HTTP_1_1)
                .code(responseCode).message("fixture").body("{}".toResponseBody())
                .apply {
                    remaining?.let { header("X-RateLimit-Remaining", it.toString()) }
                    header("X-RateLimit-Reset", "1060")
                    header("X-RateLimit-Resource", responseResource)
                    if (redirect) priorResponse(Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                        .code(302).message("redirect").header("X-RateLimit-Remaining", "0")
                        .header("X-RateLimit-Reset", "1060").build())
                }.build()
        }.build()

    private fun execute(url: String = "https://api.github.com/repos/example/repo/releases", token: String? = null) {
        val request = Request.Builder().url(url).apply { token?.let { header("Authorization", "Bearer $it") } }.build()
        client.newOutboundCall(request).execute().use { assertEquals(responseCode, it.code) }
    }

    @Test fun knownExhaustionSendsNoRequestAndPromptsManualAction() {
        tracker.observe("core", 0, 1060)
        assertThrows(GithubQuotaExhaustedException::class.java) { execute() }
        assertEquals(0, sent)
        assertEquals(1, prompts)
    }

    @Test fun automaticCallsStaySilentEvenWhenQuotaChangesAfterScheduling() = runBlocking {
        tracker.observe("core", 0, 1060)
        GithubQuotaRequestContext.automatically {
            assertThrows(GithubQuotaExhaustedException::class.java) { execute() }
        }
        assertEquals(0, sent)
        assertEquals(0, prompts)
    }

    @Test fun firstExhaustionResponseStopsFollowingRequests() {
        responseCode = 403
        remaining = 0
        repeat(2) { assertThrows(GithubQuotaExhaustedException::class.java) { execute() } }
        assertEquals(1, sent)
    }

    @Test fun lastSuccessfulRequestIsNotDiscarded() {
        remaining = 0
        execute()
        assertEquals(0, prompts)
        assertThrows(GithubQuotaExhaustedException::class.java) { execute() }
        assertEquals(1, sent)
    }

    @Test fun automaticAsyncFirstExhaustionDoesNotPrompt() = runBlocking {
        responseCode = 429
        remaining = 0
        GithubQuotaRequestContext.automatically {
            val result = runCatching {
                client.newOutboundCall(Request.Builder().url("https://api.github.com/repos/test/releases").build())
                    .executeCancellable().close()
            }
            assertTrue(result.exceptionOrNull() is GithubQuotaExhaustedException)
        }
        assertEquals(1, sent)
        assertEquals(0, prompts)
        assertNotNull(tracker.blocked())
    }

    @Test fun redirectQuotaIsRecordedWithoutDiscardingSuccessfulArchive() {
        redirect = true
        execute("https://api.github.com/repos/example/repo/zipball/abc")
        assertNotNull(tracker.blocked())
        assertEquals(0, prompts)
        assertEquals(1, sent)
    }

    @Test fun resetAllowsNextRequestWithoutPollingOrRestart() {
        tracker.observe("core", 0, 1060)
        now = 1060
        execute()
        assertEquals(1, sent)
    }

    @Test fun tokenValidationAndSavedTokenAreNotBlockedByAnonymousQuota() {
        tracker.observe("core", 0, 1060)
        execute("https://api.github.com/user", token = "test-only")
        configured = true
        execute()
        assertEquals(2, sent)
        assertEquals(0, prompts)
    }

    @Test fun authenticatedHeadersDoNotOverwriteAnonymousQuota() {
        tracker.observe("core", 0, 1060)
        remaining = 5000
        execute(token = "test-only")
        assertNotNull(tracker.blocked())
    }

    @Test fun nonQuotaOperationsRemainAvailable() {
        tracker.observe("core", 0, 1060)
        listOf(
            "http://127.0.0.1:9321/api/v2/search/anime",
            "http://127.0.0.1:9321/api/v2/comment/1",
            "https://api.github.com/rate_limit",
            "https://github.com/example/repo/releases/download/v1/app.apk",
            "https://raw.githubusercontent.com/example/repo/main/file.json",
            "https://codeload.github.com/example/repo/zip/abc",
            "https://example.com/?url=https://api.github.com/repos/test",
            "https://api.github.com.evil.test/repos/test"
        ).forEach { execute(it) }
        assertEquals(8, sent)
        assertEquals(0, prompts)
    }

    @Test fun proxyCandidatesCannotRetryAroundExhaustion() {
        tracker.observe("core", 0, 1060)
        assertThrows(GithubQuotaExhaustedException::class.java) {
            execute("https://gh-proxy.org/https://api.github.com/repos/example/repo/zipball/main")
        }
        assertEquals(0, sent)
    }

    @Test fun proxyHeadersDoNotPoisonOfficialAccountQuota() {
        remaining = 0
        execute("https://gh-proxy.org/https://api.github.com/repos/example/repo/releases")
        assertNull(tracker.blocked())
    }

    @Test fun permissionErrorsAreNotMisreportedAsQuotaExhaustion() {
        responseCode = 403
        remaining = 59
        execute()
        assertNull(tracker.blocked())
        assertEquals(0, prompts)
    }

    @Test fun searchExhaustionDoesNotBlockReleases() {
        tracker.observe("search", 0, 1060)
        assertThrows(GithubQuotaExhaustedException::class.java) { execute("https://api.github.com/search/issues?q=test") }
        execute()
        assertEquals(1, sent)
    }

    @Test fun quotaEndpointIsExemptEvenThroughProxy() {
        assertNull(githubApiQuotaResource("https://gh-proxy.org/https://api.github.com/rate_limit".toHttpUrl()))
        assertEquals("core", githubApiQuotaResource("https://api.github.com/repos/test/zipball/abc".toHttpUrl()))
        assertEquals("core", githubApiQuotaResource("https://gh-proxy.org/api.github.com/repos/test/zipball/abc".toHttpUrl()))
        assertEquals("search", githubApiQuotaResource("https://hk.gh-proxy.org/api.github.com/search/issues?q=test".toHttpUrl()))
    }
}
