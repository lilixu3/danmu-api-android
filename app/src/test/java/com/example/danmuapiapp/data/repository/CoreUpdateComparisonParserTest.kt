package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.CoreRemoteCommit
import com.example.danmuapiapp.domain.model.CoreUpdateRelation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreUpdateComparisonParserTest {
    @Test
    fun parsesCommitSummaryAndFileDiffs() {
        val parsed = CoreUpdateComparisonParser.parse(
            raw = """
                {
                  "status": "ahead",
                  "ahead_by": 2,
                  "behind_by": 0,
                  "total_commits": 2,
                  "commits": [
                    {
                      "sha": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                      "html_url": "https://github.com/o/r/commit/bbbbbbb",
                      "commit": {
                        "message": "修复搜索接口\n补充说明",
                        "author": {"name": "Alice", "date": "2026-08-15T10:00:00Z"}
                      }
                    },
                    {
                      "sha": "cccccccccccccccccccccccccccccccccccccccc",
                      "html_url": "https://github.com/o/r/commit/ccccccc",
                      "commit": {
                        "message": "更新核心配置",
                        "author": {"name": "Bob", "date": "2026-08-16T10:00:00Z"}
                      }
                    }
                  ],
                  "files": [
                    {
                      "filename": "danmu_api/configs/globals.js",
                      "status": "modified",
                      "additions": 2,
                      "deletions": 1,
                      "changes": 3,
                      "patch": "@@ -1 +1,2 @@\n-old\n+new\n+next"
                    }
                  ]
                }
            """.trimIndent(),
            repo = "o/r",
            branch = "main",
            localCommitSha = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            fallbackRemoteCommit = CoreRemoteCommit(
                sha = "cccccccccccccccccccccccccccccccccccccccc",
                title = "更新核心配置"
            )
        )

        requireNotNull(parsed)
        assertEquals(CoreUpdateRelation.RemoteAhead, parsed.relation)
        assertEquals(2, parsed.totalCommits)
        assertEquals(1, parsed.changedFiles)
        assertEquals(2, parsed.additions)
        assertEquals(1, parsed.deletions)
        assertEquals("ccccccc", parsed.remoteCommit.shortSha)
        assertTrue(parsed.summary.affectedAreas.any { it.startsWith("配置") })
        assertFalse(parsed.isTruncated)
        assertEquals(4, parsed.files.single().lines.size)
    }

    @Test
    fun marksResponseAsTruncatedWhenCommitListIsPartial() {
        val parsed = CoreUpdateComparisonParser.parse(
            raw = """{"status":"ahead","ahead_by":3,"total_commits":3,"commits":[],"files":[]}""",
            repo = "o/r",
            branch = "main",
            localCommitSha = "aaaaaaa",
            fallbackRemoteCommit = CoreRemoteCommit("bbbbbbb", "latest")
        )

        requireNotNull(parsed)
        assertTrue(parsed.isTruncated)
    }
}
