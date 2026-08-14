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
