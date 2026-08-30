package com.example.danmuapiapp.desktop.core

import com.example.danmuapiapp.desktop.node.GithubProxyCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class GithubCoreRemoteTest {

    @Test
    fun parsesUnifiedDiffWithStableOldAndNewLineNumbers() {
        val lines = CorePatchParser.parse(
            """@@ -10,3 +10,4 @@
 context
-removed
+added
 context-after
+tail
\ No newline at end of file""".trimIndent(),
        )

        assertEquals(CoreDiffLineType.Header, lines[0].type)
        assertEquals(10, lines[1].oldLineNumber)
        assertEquals(10, lines[1].newLineNumber)
        assertEquals(CoreDiffLineType.Removed, lines[2].type)
        assertEquals(11, lines[2].oldLineNumber)
        assertEquals(null, lines[2].newLineNumber)
        assertEquals(CoreDiffLineType.Added, lines[3].type)
        assertEquals(null, lines[3].oldLineNumber)
        assertEquals(11, lines[3].newLineNumber)
        assertEquals(12, lines[4].oldLineNumber)
        assertEquals(12, lines[4].newLineNumber)
        assertEquals(13, lines[5].newLineNumber)
        assertTrue(lines.last().content.startsWith("\\ No newline"))
    }

    @Test
    fun rejectsMalformedUnifiedDiffInsteadOfReturningPartialData() {
        val error = assertThrows(IOException::class.java) {
            CorePatchParser.parse("@@ malformed\n+line")
        }
        assertTrue(error.message.orEmpty().contains("hunk"))
    }

    @Test
    fun validatesRepositoryAndRemoteRequestParametersBeforeNetwork() {
        assertThrows(IllegalArgumentException::class.java) {
            GithubCoreRemote("not-a-repository", GithubProxyCatalog.ID_ORIGINAL)
        }
        val remote = GithubCoreRemote("owner/repo", GithubProxyCatalog.ID_ORIGINAL)
        assertThrows(IOException::class.java) { remote.branchHead(" ") }
        assertThrows(IllegalArgumentException::class.java) { remote.branches(page = 0) }
        assertThrows(IllegalArgumentException::class.java) { remote.pullRequests(state = "unknown") }
        assertThrows(IllegalArgumentException::class.java) { remote.pullRequestFiles(number = 0) }
    }
}
