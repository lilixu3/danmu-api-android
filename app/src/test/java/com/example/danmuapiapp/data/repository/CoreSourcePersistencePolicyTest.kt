package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.ApiVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreSourcePersistencePolicyTest {
    private fun metadata(repo: String = "owner/core", branch: String = "main") =
        CoreSourceMetadata(repo = repo, branch = branch, commitSha = "abc1234")

    @Test
    fun `unchanged custom source does not request a settings write`() {
        val resolution = CoreSourcePersistencePolicy.resolve(
            variant = ApiVariant.Custom,
            metadata = metadata(repo = "https://github.com/Owner/Core.git", branch = "refs/heads/main"),
            currentRepo = "Owner/Core",
            currentBranch = "main"
        )!!
        assertFalse(resolution.hasChange)
        assertFalse(resolution.customSourceChange)
        assertFalse(resolution.standardBranchChange)
        assertEquals("Owner/Core", resolution.repo)
        assertEquals("main", resolution.branch)
    }

    @Test
    fun `changed custom source saves the resolved repository and branch`() {
        val resolution = CoreSourcePersistencePolicy.resolve(
            variant = ApiVariant.Custom,
            metadata = metadata(repo = "owner/new", branch = "release"),
            currentRepo = "owner/old",
            currentBranch = "main"
        )!!
        assertTrue(resolution.hasChange)
        assertTrue(resolution.customSourceChange)
        assertFalse(resolution.standardBranchChange)
        assertEquals("owner/new", resolution.repo)
        assertEquals("release", resolution.branch)
    }

    @Test
    fun `standard variant only updates its branch when the repository matches`() {
        val resolution = CoreSourcePersistencePolicy.resolve(
            variant = ApiVariant.Stable,
            metadata = metadata(repo = "owner/core", branch = "dev"),
            currentRepo = "owner/core",
            currentBranch = "main"
        )!!
        assertTrue(resolution.standardBranchChange)
        assertFalse(resolution.customSourceChange)
        assertEquals("dev", resolution.branch)

        assertNull(
            CoreSourcePersistencePolicy.resolve(
                variant = ApiVariant.Stable,
                metadata = metadata(repo = "other/core", branch = "dev"),
                currentRepo = "owner/core",
                currentBranch = "main"
            )
        )
    }

    @Test
    fun `blank metadata fields are ignored`() {
        assertNull(
            CoreSourcePersistencePolicy.resolve(
                variant = ApiVariant.Custom,
                metadata = CoreSourceMetadata(repo = "owner/core", branch = " "),
                currentRepo = "owner/old",
                currentBranch = "main"
            )
        )
        assertNull(
            CoreSourcePersistencePolicy.resolve(
                variant = ApiVariant.Custom,
                metadata = CoreSourceMetadata(repo = " ", branch = "main"),
                currentRepo = "owner/old",
                currentBranch = "main"
            )
        )
    }
}
