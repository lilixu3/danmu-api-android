package com.example.danmuapiapp.data.service

import com.example.danmuapiapp.domain.model.PullRequestMergeConflictException
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ObjectId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class JGitPullRequestMergerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `merges multiple pull request heads in selected order`() {
        val git = createRepository()
        git.use {
            val baseBranch = git.repository.branch
            val base = commitFile(git, "worker.js", "module.exports = {};\n", "base")
            val first = createPullRequestCommit(
                git = git,
                baseBranch = baseBranch,
                base = base,
                branch = "pr-one",
                path = "feature-one.js",
                content = "module.exports = 'one';\n"
            )
            val second = createPullRequestCommit(
                git = git,
                baseBranch = baseBranch,
                base = base,
                branch = "pr-two",
                path = "feature-two.js",
                content = "module.exports = 'two';\n"
            )

            val mergeOrder = mutableListOf<Int>()
            val resultSha = JGitPullRequestMerger.merge(
                git,
                listOf(PullRequestCommit(12, first), PullRequestCommit(34, second))
            ) { _, _, number -> mergeOrder += number }

            assertEquals(listOf(12, 34), mergeOrder)
            assertEquals(resultSha, git.repository.resolve("HEAD").name)
            assertNotEquals(base.name, resultSha)
            assertTrue(File(git.repository.workTree, "feature-one.js").isFile)
            assertTrue(File(git.repository.workTree, "feature-two.js").isFile)
            val messages = git.log().call().map { it.fullMessage }
            assertTrue(messages.any { it == "Local merge of PR #12" })
            assertTrue(messages.any { it == "Local merge of PR #34" })
        }
    }

    @Test
    fun `reports the pull request and files when sequential merge conflicts`() {
        val git = createRepository()
        git.use {
            val baseBranch = git.repository.branch
            val base = commitFile(git, "shared.js", "const value = 'base';\n", "base")
            val first = createPullRequestCommit(
                git = git,
                baseBranch = baseBranch,
                base = base,
                branch = "pr-first",
                path = "shared.js",
                content = "const value = 'first';\n"
            )
            val conflicting = createPullRequestCommit(
                git = git,
                baseBranch = baseBranch,
                base = base,
                branch = "pr-conflict",
                path = "shared.js",
                content = "const value = 'second';\n"
            )

            val error = runCatching {
                JGitPullRequestMerger.merge(
                    git,
                    listOf(PullRequestCommit(1, first), PullRequestCommit(2, conflicting))
                )
            }.exceptionOrNull()

            assertTrue(error is PullRequestMergeConflictException)
            error as PullRequestMergeConflictException
            assertEquals(2, error.pullRequestNumber)
            assertTrue(error.conflictFiles.contains("shared.js"))
        }
    }

    @Test
    fun `reapply updated PR onto newer main retains changes from both`() {
        createRepository().use { git ->
            val baseBranch = git.repository.branch
            val base = commitFile(git, "worker.js", "module.exports = {};\n", "base")
            val initialPr = createPullRequestCommit(git, baseBranch, base, "feature", "feature.js", "old PR\n")
            JGitPullRequestMerger.merge(git, listOf(PullRequestCommit(492, initialPr)))
            val oldLocalMerge = git.repository.resolve("HEAD")
            git.checkout().setName("feature").call()
            val updatedPr = commitFile(git, "feature.js", "new PR\n", "PR new commit")
            git.checkout().setName(base.name).call()
            val newMain = commitFile(git, "main-update.js", "main new commit\n", "main updated")
            PullRequestUpdateMergeGuard.verify(git, newMain.name,
                listOf(PullRequestCommit(492, updatedPr)), mapOf(492 to initialPr.name))
            val merged = JGitPullRequestMerger.merge(git, listOf(PullRequestCommit(492, updatedPr)))
            assertNotEquals(oldLocalMerge.name, merged)
            assertEquals("new PR\n", File(git.repository.workTree, "feature.js").readText())
            assertEquals("main new commit\n", File(git.repository.workTree, "main-update.js").readText())
            assertTrue(git.log().call().any { it.name == newMain.name })
        }
    }

    @Test
    fun `update guard refuses rewritten PR history without modifying target`() {
        createRepository().use { git ->
            val branch = git.repository.branch
            val base = commitFile(git, "worker.js", "base\n", "base")
            val old = createPullRequestCommit(git, branch, base, "old", "feature.js", "old change\n")
            val fresh = createPullRequestCommit(git, branch, base, "new", "replacement.js", "new change\n")
            val error = runCatching {
                PullRequestUpdateMergeGuard.verify(git, base.name, listOf(PullRequestCommit(492, fresh)), mapOf(492 to old.name))
            }.exceptionOrNull()
            assertTrue(error?.message.orEmpty().contains("历史已改写"))
            assertEquals(base.name, git.repository.resolve("HEAD").name)
            assertEquals("base\n", File(git.repository.workTree, "worker.js").readText())
        }
    }

    @Test
    fun `update guard refuses no-op remerge of a reverted PR`() {
        createRepository().use { git ->
            val branch = git.repository.branch
            val base = commitFile(git, "worker.js", "base\n", "base")
            val pr = createPullRequestCommit(git, branch, base, "pr", "worker.js", "PR change\n")
            JGitPullRequestMerger.merge(git, listOf(PullRequestCommit(492, pr)))
            val reverted = commitFile(git, "worker.js", "base\n", "revert PR")
            val error = runCatching {
                PullRequestUpdateMergeGuard.verify(git, reverted.name, listOf(PullRequestCommit(492, pr)), mapOf(492 to pr.name))
            }.exceptionOrNull()
            assertTrue(error?.message.orEmpty().contains("不能恢复回退"))
            assertEquals(reverted.name, git.repository.resolve("HEAD").name)
            assertEquals("base\n", File(git.repository.workTree, "worker.js").readText())
        }
    }

    private fun createRepository(): Git {
        val git = Git.init().setDirectory(temporaryFolder.newFolder()).call()
        git.repository.config.apply {
            setString("user", null, "name", "Test User")
            setString("user", null, "email", "test@example.invalid")
            save()
        }
        return git
    }

    private fun createPullRequestCommit(
        git: Git,
        baseBranch: String,
        base: ObjectId,
        branch: String,
        path: String,
        content: String
    ): ObjectId {
        git.branchCreate().setName(branch).setStartPoint(base.name).call()
        git.checkout().setName(branch).call()
        val commit = commitFile(git, path, content, branch)
        git.checkout().setName(baseBranch).call()
        return commit
    }

    private fun commitFile(git: Git, path: String, content: String, message: String): ObjectId {
        File(git.repository.workTree, path).apply {
            parentFile?.mkdirs()
            writeText(content, Charsets.UTF_8)
        }
        git.add().addFilepattern(path).call()
        return git.commit().setMessage(message).call()
    }
}
