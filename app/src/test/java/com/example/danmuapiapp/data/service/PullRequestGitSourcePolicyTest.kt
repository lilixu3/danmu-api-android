package com.example.danmuapiapp.data.service

import org.junit.Assert.assertEquals
import org.junit.Test

class PullRequestGitSourcePolicyTest {
    @Test
    fun `tries proxy candidates before direct github fallback`() {
        val original = "https://github.com/owner/repo.git"
        assertEquals(
            listOf(
                "https://proxy.example/https://github.com/owner/repo.git",
                "https://proxy.example/github.com/owner/repo.git",
                original
            ),
            PullRequestGitSourcePolicy.candidates(
                original = original,
                proxyCandidates = listOf(
                    "https://proxy.example/https://github.com/owner/repo.git",
                    "https://proxy.example/github.com/owner/repo.git"
                )
            )
        )
    }

    @Test
    fun `deduplicates candidates and ignores unsupported schemes`() {
        val original = "https://github.com/owner/repo.git"
        assertEquals(
            listOf("https://proxy.example/$original", original),
            PullRequestGitSourcePolicy.candidates(
                original = original,
                proxyCandidates = listOf("ssh://proxy/repo.git", "https://proxy.example/$original", original)
            )
        )
    }
}
