package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.data.remote.github.GithubRemoteService
import com.example.danmuapiapp.data.service.PullRequestUpdateCoverage
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CoreInfo
import com.example.danmuapiapp.domain.model.CorePullRequest
import com.example.danmuapiapp.domain.model.CorePullRequestInclusion
import com.example.danmuapiapp.domain.model.CoreRemoteCommit
import com.example.danmuapiapp.domain.model.CoreSourceStatus
import com.example.danmuapiapp.domain.model.CoreUpdateRelation
import com.example.danmuapiapp.domain.model.GithubRelease
import com.example.danmuapiapp.domain.model.RunMode
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreUpdateCheckControllerTest {

    private class FakeTransport : CoreRemoteTransport {
        var cancellableResponder: (String) -> String? = { null }
        val requests = mutableListOf<String>()

        override fun apiUrlCandidates(path: String): List<String> = listOf("https://api/$path")

        override fun withProxyCandidates(url: String): List<String> = listOf(url)

        override fun <T> requestMapped(
            urls: List<String>,
            headers: Map<String, String>,
            mapper: (String) -> T?
        ): T? = null

        override fun rawUrlCandidates(repo: String, filePath: String): List<String> =
            listOf("https://raw/$repo/$filePath")

        override suspend fun requestTextResponseCancellable(
            urls: List<String>,
            headers: Map<String, String>,
            bodyValidator: (String) -> Boolean
        ): GithubRemoteService.TextResponsePayload? = null

        override suspend fun <T> requestMappedCancellable(
            urls: List<String>,
            headers: Map<String, String>,
            mapper: (String) -> T?
        ): T? {
            urls.forEach { url ->
                requests += url
                val body = cancellableResponder(url)
                if (body != null) mapper(body)?.let { return it }
            }
            return null
        }
    }

    private class Harness {
        val transport = FakeTransport()
        val warnings = mutableListOf<String>()
        val pullRequestHeads = mutableMapOf<Int, String>()
        val pullRequests = mutableMapOf<Int, CorePullRequest>()
        var coverageInclusion = CorePullRequestInclusion.Unknown
        var coverageReason = ""
        var coverageFailure: Exception? = null
        var quotaChecks = 0
        var quotaBlocked = false
        var repo = "owner/repo"
        var branch: String? = "main"
        var localMetadata: CoreSourceMetadata? = null
        var remoteSource: CoreRemoteSource? = null
        var loadedMode: RunMode? = null
        var infos = listOf(
            CoreInfo(variant = ApiVariant.Custom, version = "1.0.0", isInstalled = true)
        )

        val controller = CoreUpdateCheckController(
            requireQuotaAvailable = { quotaChecks++ },
            isQuotaBlocked = { quotaBlocked },
            remoteDataSource = CoreRemoteDataSource(transport),
            loadPullRequestHead = { _, number ->
                pullRequestHeads[number] ?: throw IOException("无法读取 PR #$number")
            },
            loadPullRequest = { _, number ->
                pullRequests[number] ?: throw IOException("无法读取 PR #$number")
            },
            coverageForUpdate = { _, _, _, _, _ ->
                coverageFailure?.let { throw it }
                PullRequestUpdateCoverage(coverageInclusion, coverageReason)
            },
            resolveRepo = { repo },
            resolveBranch = { branch },
            currentRunMode = { RunMode.Normal },
            readLocalMetadata = { _, mode ->
                loadedMode = mode
                localMetadata
            },
            resolveRemoteSource = { remoteSource },
            readCoreInfoList = { infos },
            updateCoreInfoList = { transform -> infos = transform(infos) },
            logWarning = { message, _ -> warnings += message }
        )

        fun info(): CoreInfo = infos.first { it.variant == ApiVariant.Custom }
    }

    private fun remoteSource(
        versionLabel: String,
        commitSha: String = "",
        repo: String = "owner/repo",
        branch: String = "main"
    ) = CoreRemoteSource(
        release = GithubRelease(
            tagName = "",
            name = "远程核心",
            body = "发布说明",
            publishedAt = "2026-10-21T00:00:00Z",
            zipballUrl = "https://example.test/archive.zip",
            version = versionLabel
        ),
        metadata = CoreSourceMetadata(
            repo = repo,
            branch = branch,
            commitSha = commitSha,
            versionLabel = versionLabel
        ),
        remoteCommit = CoreRemoteCommit(
            sha = commitSha,
            title = "远程提交",
            message = "远程提交",
            committedAt = "2026-10-21T00:00:00Z"
        ).takeIf { commitSha.isNotBlank() }
    )

    private fun compareBody(status: String, remoteSha: String): String = """
        {
          "status": "$status",
          "ahead_by": 2,
          "behind_by": 0,
          "total_commits": 1,
          "commits": [{"sha": "$remoteSha", "commit": {"message": "远程提交", "author": {"name": "tester"}}}],
          "files": []
        }
    """.trimIndent()

    @Test
    fun `marks version update when remote version is newer`() = runBlocking {
        val harness = Harness()
        harness.remoteSource = remoteSource(versionLabel = "1.1.0")

        harness.controller.checkAndMarkUpdate(ApiVariant.Custom)

        val info = harness.info()
        assertTrue(info.hasVersionUpdate)
        assertEquals("1.1.0", info.availableVersion)
        assertEquals(CoreUpdateRelation.Changed, info.updateRelation)
        assertNull(info.updateCheckError)
        assertNotNull(info.updateCheckedAtEpochMillis)
        assertEquals(1, harness.quotaChecks)
        assertEquals(RunMode.Normal, harness.loadedMode)
    }

    @Test
    fun `keeps local ahead state without offering update entry`() = runBlocking {
        val harness = Harness()
        harness.infos = listOf(
            CoreInfo(variant = ApiVariant.Custom, version = "2.0.0", isInstalled = true)
        )
        harness.remoteSource = remoteSource(versionLabel = "1.0.0")

        harness.controller.checkAndMarkUpdate(ApiVariant.Custom)

        val info = harness.info()
        assertFalse(info.hasVersionUpdate)
        assertNull(info.availableVersion)
        assertEquals(CoreUpdateRelation.LocalAhead, info.updateRelation)
    }

    @Test
    fun `records update check error when comparison cannot be resolved`() = runBlocking {
        val harness = Harness()
        harness.localMetadata = CoreSourceMetadata(
            repo = "owner/repo",
            branch = "main",
            commitSha = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        )
        harness.remoteSource = remoteSource(
            versionLabel = "1.1.0",
            commitSha = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        )
        harness.transport.cancellableResponder = { null }

        harness.controller.checkAndMarkUpdate(ApiVariant.Custom)

        val info = harness.info()
        assertEquals("无法确认提交更新方向，请稍后重试", info.updateCheckError)
        assertFalse(info.hasVersionUpdate)
        assertNull(harness.controller.latestVersionLabel(ApiVariant.Custom, "owner/repo", "main"))
    }

    @Test
    fun `caches comparison result between repeated checks`() = runBlocking {
        val harness = Harness()
        harness.localMetadata = CoreSourceMetadata(
            repo = "owner/repo",
            branch = "main",
            commitSha = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        )
        harness.remoteSource = remoteSource(
            versionLabel = "1.1.0",
            commitSha = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        )
        val remoteSha = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        harness.transport.cancellableResponder = { compareBody("ahead", remoteSha) }

        harness.controller.checkAndMarkUpdate(ApiVariant.Custom)
        harness.controller.checkAndMarkUpdate(ApiVariant.Custom)

        assertEquals(1, harness.transport.requests.size)
        assertEquals(CoreUpdateRelation.RemoteAhead, harness.info().updateRelation)
        assertTrue(harness.info().hasVersionUpdate)
    }

    @Test
    fun `pull request head change marks update when branch commit matches`() = runBlocking {
        val harness = Harness()
        val sha = "cccccccccccccccccccccccccccccccccccccccc"
        harness.localMetadata = CoreSourceMetadata(
            repo = "owner/repo",
            branch = "main",
            commitSha = sha,
            pullRequestNumbers = listOf(12),
            pullRequestHeadShas = listOf("oldhead")
        )
        harness.remoteSource = remoteSource(versionLabel = "1.0.0", commitSha = sha)
        harness.pullRequestHeads[12] = "newhead"

        harness.controller.checkAndMarkUpdate(ApiVariant.Custom)

        val info = harness.info()
        assertEquals(mapOf(12 to "newhead"), info.updatedPullRequestHeads)
        assertEquals(CoreUpdateRelation.Changed, info.updateRelation)
        assertTrue(info.hasVersionUpdate)
        assertEquals("1.0.0@${sha.take(7)}", info.availableVersion)
    }

    @Test
    fun `pull request lookup failure is ignored when branch already has update`() = runBlocking {
        val harness = Harness()
        harness.localMetadata = CoreSourceMetadata(
            repo = "owner/repo",
            branch = "main",
            commitSha = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            pullRequestNumbers = listOf(7),
            pullRequestHeadShas = listOf("oldhead")
        )
        harness.remoteSource = remoteSource(
            versionLabel = "1.1.0",
            commitSha = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        )
        harness.transport.cancellableResponder = {
            compareBody("ahead", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")
        }

        harness.controller.checkAndMarkUpdate(ApiVariant.Custom)

        val info = harness.info()
        assertTrue(info.hasVersionUpdate)
        assertNull(info.updateCheckError)
        assertEquals(CoreUpdateRelation.RemoteAhead, info.updateRelation)
        assertTrue(harness.warnings.any { it.contains("PR #7") })
    }

    @Test
    fun `reports source mismatch instead of update entry`() = runBlocking {
        val harness = Harness()
        harness.localMetadata = CoreSourceMetadata(
            repo = "someone/else",
            branch = "main",
            commitSha = "dddddddddddddddddddddddddddddddddddddddd"
        )
        harness.remoteSource = remoteSource(
            versionLabel = "1.1.0",
            commitSha = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        )

        harness.controller.checkAndMarkUpdate(ApiVariant.Custom)

        val info = harness.info()
        assertTrue(info.sourceMismatch)
        assertEquals(CoreSourceStatus.Mismatched, info.sourceStatus)
        assertFalse(info.hasVersionUpdate)
        assertEquals(CoreUpdateRelation.Unknown, info.updateRelation)
    }

    @Test
    fun `check all updates stops while quota is blocked`() = runBlocking {
        val harness = Harness()
        harness.quotaBlocked = true
        harness.remoteSource = remoteSource(versionLabel = "1.1.0")

        harness.controller.checkAllUpdates()

        assertEquals(0, harness.quotaChecks)
        assertNull(harness.info().updateCheckedAtEpochMillis)
        assertFalse(harness.info().hasVersionUpdate)
    }

    @Test
    fun `fetch comparison publishes relation and version label`() = runBlocking {
        val harness = Harness()
        val localSha = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val remoteSha = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        harness.localMetadata = CoreSourceMetadata(
            repo = "owner/repo",
            branch = "main",
            commitSha = localSha
        )
        harness.remoteSource = remoteSource(versionLabel = "1.1.0", commitSha = remoteSha)
        harness.transport.cancellableResponder = { compareBody("ahead", remoteSha) }
        harness.controller.checkAndMarkUpdate(ApiVariant.Custom)

        val comparison = harness.controller.fetchUpdateComparison(ApiVariant.Custom).getOrThrow()

        assertEquals(CoreUpdateRelation.RemoteAhead, comparison.relation)
        assertEquals(remoteSha, comparison.remoteCommit.sha)
        assertEquals(2, comparison.aheadBy)
        assertTrue(harness.info().hasVersionUpdate)
        assertEquals("1.1.0@${remoteSha.take(7)}", harness.info().availableVersion)
    }

    @Test
    fun `latest version label expires when repo or branch changes`() = runBlocking {
        val harness = Harness()
        harness.remoteSource = remoteSource(versionLabel = "1.1.0")
        harness.controller.checkAndMarkUpdate(ApiVariant.Custom)

        assertEquals("1.1.0", harness.controller.latestVersionLabel(ApiVariant.Custom, "owner/repo", "main"))
        assertNull(harness.controller.latestVersionLabel(ApiVariant.Custom, "owner/other", "main"))
        assertNull(harness.controller.latestVersionLabel(ApiVariant.Custom, "owner/repo", "dev"))

        harness.controller.forgetLatestVersion(ApiVariant.Custom)
        assertNull(harness.controller.latestVersionLabel(ApiVariant.Custom, "owner/repo", "main"))
    }

    private fun pullRequest(number: Int, headSha: String) = CorePullRequest(
        number = number,
        state = "open",
        title = "PR #$number",
        body = "",
        author = "tester",
        htmlUrl = "https://example.test/pull/$number",
        draft = false,
        updatedAt = "2026-10-21T00:00:00Z",
        headSha = headSha,
        headLabel = "tester:feature",
        baseSha = "base",
        baseRef = "main",
        baseRepository = "owner/repo"
    )

    @Test
    fun `update plan reports installed pull requests missing from target commit`() = runBlocking {
        val harness = Harness()
        val installedSha = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val targetSha = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        harness.localMetadata = CoreSourceMetadata(
            repo = "owner/repo",
            branch = "main",
            commitSha = installedSha,
            pullRequestNumbers = listOf(12),
            pullRequestHeadShas = listOf("oldhead")
        )
        harness.remoteSource = remoteSource(versionLabel = "1.1.0", commitSha = targetSha)
        harness.pullRequests[12] = pullRequest(12, "newhead")
        harness.coverageInclusion = CorePullRequestInclusion.NotIncluded
        harness.coverageReason = "目标提交未包含该 PR"

        val plan = harness.controller.prepareUpdatePlan(ApiVariant.Custom).getOrThrow()

        assertEquals(listOf(12), plan.pullRequestNumbers)
        assertEquals(listOf(12), plan.notIncludedPullRequestNumbers)
        assertTrue(plan.unknownPullRequestNumbers.isEmpty())
        assertTrue(plan.requiresPullRequestChoice)
        assertEquals(listOf(12), plan.pullRequestsToReapply)
        assertEquals("newhead", plan.confirmedPullRequestHeads[12])
        assertEquals("oldhead", plan.installedPullRequestHeads[12])
        assertEquals("目标提交未包含该 PR", plan.pullRequestNotes[12])
        assertEquals(targetSha, plan.targetCommitSha)
        assertEquals("1.1.0", plan.versionLabel)
        assertEquals(RunMode.Normal, plan.runMode)
    }

    @Test
    fun `update plan keeps unknown state when coverage lookup fails`() = runBlocking {
        val harness = Harness()
        harness.localMetadata = CoreSourceMetadata(
            repo = "owner/repo",
            branch = "main",
            commitSha = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            pullRequestNumbers = listOf(7),
            pullRequestHeadShas = listOf("oldhead")
        )
        harness.remoteSource = remoteSource(
            versionLabel = "1.1.0",
            commitSha = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        )
        harness.pullRequests[7] = pullRequest(7, "newhead")
        harness.coverageFailure = IOException("网络异常")

        val plan = harness.controller.prepareUpdatePlan(ApiVariant.Custom).getOrThrow()

        assertEquals(listOf(7), plan.unknownPullRequestNumbers)
        assertTrue(plan.notIncludedPullRequestNumbers.isEmpty())
        assertEquals("查询失败，尚未确认是否包含；可以取消后重试", plan.pullRequestNotes[7])
        assertTrue(plan.requiresPullRequestChoice)
        assertTrue(harness.warnings.any { it.contains("PR #7") })
    }

    @Test
    fun `update plan rejects installed pull request stack from another repository`() = runBlocking {
        val harness = Harness()
        harness.localMetadata = CoreSourceMetadata(
            repo = "someone/else",
            branch = "main",
            commitSha = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            pullRequestNumbers = listOf(12),
            pullRequestHeadShas = listOf("oldhead")
        )
        harness.remoteSource = remoteSource(
            versionLabel = "1.1.0",
            commitSha = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        )

        val failure = harness.controller.prepareUpdatePlan(ApiVariant.Custom).exceptionOrNull()

        assertEquals("无法确认 PR 组合的远程基线，请检查来源与网络后重试", failure?.message)
    }
}
