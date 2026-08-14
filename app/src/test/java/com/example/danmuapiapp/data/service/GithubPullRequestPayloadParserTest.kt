package com.example.danmuapiapp.data.service

import com.example.danmuapiapp.domain.model.CorePullRequestFilter
import com.example.danmuapiapp.domain.model.PullRequestFirstContribution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GithubPullRequestPayloadParserTest {
    @Test
    fun `parses pull request list fields used by the merge plan`() {
        val pullRequests = GithubPullRequestPayloadParser.parseList(
            """
            [
              {
                "number": 441,
                "state": "open",
                "author_association": "FIRST_TIME_CONTRIBUTOR",
                "merged_at": null,
                "closed_at": null,
                "title": "Add selective cache cleanup",
                "body": "Expose eight cleanup switches.",
                "html_url": "https://github.com/huangxd-/danmu_api/pull/441",
                "draft": false,
                "updated_at": "2026-08-13T12:34:56Z",
                "user": { "login": "contributor" },
                "head": {
                  "sha": "1111111111111111111111111111111111111111",
                  "label": "contributor:cache-cleanup",
                  "ref": "cache-cleanup"
                },
                "base": {
                  "sha": "2222222222222222222222222222222222222222",
                  "ref": "main",
                  "repo": { "full_name": "huangxd-/danmu_api" }
                }
              }
            ]
            """.trimIndent()
        )

        requireNotNull(pullRequests)
        assertEquals(1, pullRequests.size)
        with(pullRequests.single()) {
            assertEquals(441, number)
            assertEquals("open", state)
            assertEquals("FIRST_TIME_CONTRIBUTOR", authorAssociation)
            assertEquals(PullRequestFirstContribution.Repository, firstContribution)
            assertNull(mergedAt)
            assertNull(closedAt)
            assertEquals("Add selective cache cleanup", title)
            assertEquals("contributor", author)
            assertFalse(draft)
            assertEquals("contributor:cache-cleanup", headLabel)
            assertEquals("main", baseRef)
            assertEquals("huangxd-/danmu_api", baseRepository)
            assertEquals("1111111", headSha.take(7))
        }
    }

    @Test
    fun `parses detail-only change totals and mergeability`() {
        val pullRequest = GithubPullRequestPayloadParser.parseOne(
            """
            {
              "number": 7,
              "state": "closed",
              "author_association": "FIRST_TIMER",
              "title": "Detail",
              "draft": true,
              "merged_at": "2026-08-13T13:00:00Z",
              "closed_at": "2026-08-13T13:00:00Z",
              "mergeable": true,
              "additions": 25,
              "deletions": 8,
              "changed_files": 3,
              "head": { "sha": "abc", "ref": "topic" },
              "base": {
                "sha": "def",
                "ref": "main",
                "repo": { "full_name": "owner/repo" }
              }
            }
            """.trimIndent()
        )

        requireNotNull(pullRequest)
        assertTrue(pullRequest.draft)
        assertEquals(PullRequestFirstContribution.Github, pullRequest.firstContribution)
        assertEquals("2026-08-13T13:00:00Z", pullRequest.mergedAt)
        assertEquals("2026-08-13T13:00:00Z", pullRequest.closedAt)
        assertEquals(true, pullRequest.mergeable)
        assertEquals(25, pullRequest.additions)
        assertEquals(8, pullRequest.deletions)
        assertEquals(3, pullRequest.changedFiles)
        assertEquals("topic", pullRequest.headLabel)
    }

    @Test
    fun `rejects malformed payloads`() {
        assertNull(GithubPullRequestPayloadParser.parseList("not-json"))
        assertNull(GithubPullRequestPayloadParser.parseOne("[]"))
        assertNull(GithubPullRequestPayloadParser.parseOne("{\"title\":\"missing number\"}"))
    }

    @Test
    fun `parses github issue search items as pull request summaries`() {
        val payload = GithubPullRequestPayloadParser.parseSearch(
            raw = """
                {
                  "total_count": 2,
                  "items": [
                    {
                      "number": 436,
                      "state": "open",
                      "title": "Fix match bounds",
                      "body": "Details",
                      "html_url": "https://github.com/owner/repo/pull/436",
                      "draft": false,
                      "updated_at": "2026-08-11T09:19:07Z",
                      "closed_at": null,
                      "author_association": "NONE",
                      "user": { "login": "first-author" },
                      "pull_request": { "merged_at": null }
                    }
                  ]
                }
            """.trimIndent(),
            repository = "owner/repo",
            baseBranch = "main"
        )

        requireNotNull(payload)
        assertEquals(2, payload.totalCount)
        with(payload.items.single()) {
            assertEquals(436, number)
            assertEquals("first-author", author)
            assertEquals("owner/repo", baseRepository)
            assertEquals("main", baseRef)
            assertEquals("NONE", authorAssociation)
            assertTrue(PullRequestFirstContributionPolicy.needsRepositoryLookup(this))
        }
    }

    @Test
    fun `pr search scopes github query to repository branch and selected state`() {
        val open = GithubPullRequestSearchQuery.plan(
            repository = "owner/repo",
            baseBranch = "main",
            filter = CorePullRequestFilter.Open,
            input = "contributor"
        )
        assertEquals(
            "author:contributor repo:owner/repo is:pr base:main is:open",
            open.primaryQuery
        )
        assertEquals(
            "contributor repo:owner/repo is:pr base:main is:open",
            open.fallbackQuery
        )

        val merged = GithubPullRequestSearchQuery.plan(
            repository = "owner/repo",
            baseBranch = "main",
            filter = CorePullRequestFilter.Merged,
            input = "is:open repo:other/repo cache cleanup"
        )
        assertEquals(
            "cache cleanup repo:owner/repo is:pr base:main is:merged",
            merged.primaryQuery
        )

        val all = GithubPullRequestSearchQuery.plan(
            repository = "owner/repo",
            baseBranch = "main",
            filter = CorePullRequestFilter.All,
            input = "#436"
        )
        assertEquals(
            "436 in:number repo:owner/repo is:pr base:main",
            all.primaryQuery
        )
    }

    @Test
    fun `does not flag established or unknown associations as first contributors`() {
        fun payload(association: String) =
            """
            {
              "number": 8,
              "state": "open",
              "author_association": "$association",
              "head": { "sha": "abc", "ref": "topic" },
              "base": {
                "sha": "def",
                "ref": "main",
                "repo": { "full_name": "owner/repo" }
              }
            }
            """.trimIndent()

        assertNull(GithubPullRequestPayloadParser.parseOne(payload("CONTRIBUTOR"))?.firstContribution)
        assertNull(GithubPullRequestPayloadParser.parseOne(payload("UNKNOWN"))?.firstContribution)
    }

    @Test
    fun `open none association without repository commits is recovered as first contributor`() {
        val pullRequest = pullRequestWithAssociation("NONE")

        assertTrue(PullRequestFirstContributionPolicy.needsRepositoryLookup(pullRequest))
        assertEquals(
            PullRequestFirstContribution.Repository,
            PullRequestFirstContributionPolicy.applyRepositoryLookup(
                pullRequest = pullRequest,
                hasExistingContribution = false
            ).firstContribution
        )
    }

    @Test
    fun `repository lookup never guesses when commits exist or request failed`() {
        val pullRequest = pullRequestWithAssociation("NONE")

        assertNull(
            PullRequestFirstContributionPolicy.applyRepositoryLookup(
                pullRequest = pullRequest,
                hasExistingContribution = true
            ).firstContribution
        )
        assertNull(
            PullRequestFirstContributionPolicy.applyRepositoryLookup(
                pullRequest = pullRequest,
                hasExistingContribution = null
            ).firstContribution
        )
    }

    @Test
    fun `established and closed pull requests skip repository lookup`() {
        val contributor = pullRequestWithAssociation("CONTRIBUTOR")
        val unknown = pullRequestWithAssociation("UNKNOWN")
        val mannequin = pullRequestWithAssociation("MANNEQUIN")
        val closed = pullRequestWithAssociation("NONE").copy(state = "closed")

        assertFalse(PullRequestFirstContributionPolicy.needsRepositoryLookup(contributor))
        assertFalse(PullRequestFirstContributionPolicy.needsRepositoryLookup(unknown))
        assertFalse(PullRequestFirstContributionPolicy.needsRepositoryLookup(mannequin))
        assertFalse(PullRequestFirstContributionPolicy.needsRepositoryLookup(closed))
    }

    @Test
    fun `parses paged file patches for the mobile diff viewer`() {
        val files = GithubPullRequestPayloadParser.parseFiles(
            """
            [
              {
                "filename": "danmu_api/cache.js",
                "previous_filename": "danmu_api/old-cache.js",
                "status": "renamed",
                "additions": 1,
                "deletions": 1,
                "changes": 2,
                "patch": "@@ -8,2 +8,2 @@\n-old value\n+new value\n context"
              }
            ]
            """.trimIndent()
        )

        requireNotNull(files)
        val file = files.single()
        assertEquals("danmu_api/cache.js", file.path)
        assertEquals("danmu_api/old-cache.js", file.previousPath)
        assertEquals(1, file.additions)
        assertEquals(1, file.deletions)
        assertEquals(4, file.lines.size)
        assertNull(file.lines[1].newLineNumber)
        assertEquals(8, file.lines[2].newLineNumber)
        assertEquals(9, file.lines[3].newLineNumber)
    }

    private fun pullRequestWithAssociation(association: String) = requireNotNull(
        GithubPullRequestPayloadParser.parseOne(
            """
            {
              "number": 436,
              "state": "open",
              "author_association": "$association",
              "user": { "login": "new-author" },
              "head": { "sha": "abc", "ref": "topic" },
              "base": {
                "sha": "def",
                "ref": "main",
                "repo": { "full_name": "owner/repo" }
              }
            }
            """.trimIndent()
        )
    )
}
