package com.example.danmuapiapp.data.service

import android.content.Context
import android.system.Os
import android.system.OsConstants
import com.example.danmuapiapp.domain.model.CorePullRequest
import com.example.danmuapiapp.domain.model.PullRequestMergeConflictException
import com.example.danmuapiapp.domain.model.PullRequestStackResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeCommand
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.merge.MergeStrategy
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.TagOpt
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PullRequestMergeService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pullRequestService: GithubPullRequestService,
    private val githubProxyService: GithubProxyService
) {
    suspend fun buildInto(
        repository: String,
        baseBranch: String,
        pullRequestNumbers: List<Int>,
        destination: File,
        preferredBaseCommitSha: String = "",
        onProgress: (stage: String, progress: Float?) -> Unit
    ): PullRequestStackResult = withContext(Dispatchers.IO) {
        val numbers = pullRequestNumbers.distinct()
        if (numbers.isEmpty()) throw IOException("至少选择一个 PR")
        if (numbers.any { it <= 0 }) throw IOException("PR 编号无效")

        onProgress("正在确认所选 PR", null)
        val pullRequests = numbers.map { number -> pullRequestService.get(repository, number) }
        validatePlan(repository, baseBranch, pullRequests)
        currentCoroutineContext().ensureActive()
        val remoteCandidates = gitRemoteCandidates(repository)

        val workRoot = File(context.cacheDir, "pull-request-lab")
        val workTree = File(workRoot, "merge-${UUID.randomUUID()}")
        workRoot.mkdirs()
        val coroutineJob = currentCoroutineContext()[Job]
        val monitor = GitProgressMonitor(coroutineJob, onProgress)
        var git: Git? = null
        try {
            onProgress("正在获取 $baseBranch 基线", null)
            val clonedBase = cloneBaseBranch(
                remoteCandidates = remoteCandidates,
                baseBranch = baseBranch,
                workTree = workTree,
                monitor = monitor,
                onProgress = onProgress
            )
            git = clonedBase.git
            val fetchRemoteCandidates = listOf(clonedBase.remote)
                .plus(remoteCandidates)
                .distinct()
            currentCoroutineContext().ensureActive()

            git.repository.config.apply {
                setString("user", null, "name", "Danmu API App")
                setString("user", null, "email", "local-pr-lab@localhost")
                save()
            }
            val baseCommitSha = checkoutBuildBase(
                git = git,
                preferredCommitSha = preferredBaseCommitSha,
                baseBranch = baseBranch,
                remoteCandidates = fetchRemoteCandidates,
                monitor = monitor,
                onProgress = onProgress
            )
            ensurePullRequestBasesAvailable(
                git = git,
                pullRequests = pullRequests,
                baseBranch = baseBranch,
                remoteCandidates = fetchRemoteCandidates,
                monitor = monitor,
                onProgress = onProgress
            )

            val refs = pullRequests.mapIndexed { index, pullRequest ->
                currentCoroutineContext().ensureActive()
                onProgress(
                    "正在获取 PR #${pullRequest.number}（${index + 1}/${pullRequests.size}）",
                    index.toFloat() / pullRequests.size.toFloat()
                )
                val localRef = "refs/remotes/pull/${pullRequest.number}/head"
                val commitId = fetchPullRequestHead(
                    git = git,
                    remoteCandidates = fetchRemoteCandidates,
                    pullRequestNumber = pullRequest.number,
                    localRef = localRef,
                    monitor = monitor
                )
                if (pullRequest.headSha.isNotBlank() &&
                    !commitId.name.equals(pullRequest.headSha, ignoreCase = true)
                ) {
                    throw IOException("PR #${pullRequest.number} 已更新，请刷新列表后重试")
                }
                PullRequestCommit(pullRequest.number, commitId)
            }

            onProgress("正在按所选顺序合并", null)
            val localMergeSha = JGitPullRequestMerger.merge(git, refs) { index, total, number ->
                onProgress(
                    "正在合并 PR #$number（$index/$total）",
                    index.toFloat() / total.toFloat()
                )
            }
            currentCoroutineContext().ensureActive()

            val coreDirectory = locateCoreDirectory(workTree)
                ?: throw IOException("合并结果中未找到有效的 danmu_api 核心目录")
            onProgress("正在整理合并后的核心文件", null)
            copyCoreTree(coreDirectory, destination, coroutineJob)
            val version = NodeProjectManager.readCoreVersion(destination)

            PullRequestStackResult(
                repository = repository,
                baseBranch = baseBranch,
                baseCommitSha = baseCommitSha,
                localMergeSha = localMergeSha,
                pullRequests = pullRequests,
                version = version
            )
        } catch (error: Error) {
            if (error is VirtualMachineError || error is ThreadDeath) throw error
            throw IOException(
                "本地 Git 运行库初始化失败：${error.cause?.message ?: error.message ?: error::class.java.simpleName}",
                error
            )
        } finally {
            runCatching { git?.close() }
            runCatching { workTree.deleteRecursively() }
        }
    }

    private fun validatePlan(
        repository: String,
        baseBranch: String,
        pullRequests: List<CorePullRequest>
    ) {
        pullRequests.forEach { pullRequest ->
            val canMergeLocally = pullRequest.state.equals("open", ignoreCase = true) ||
                !pullRequest.mergedAt.isNullOrBlank()
            if (!canMergeLocally) {
                throw IOException("PR #${pullRequest.number} 已关闭，请刷新列表")
            }
            if (!pullRequest.baseRef.equals(baseBranch, ignoreCase = true)) {
                throw IOException("PR #${pullRequest.number} 的目标分支已变为 ${pullRequest.baseRef}")
            }
            if (pullRequest.baseRepository.isNotBlank() &&
                !pullRequest.baseRepository.equals(repository, ignoreCase = true)
            ) {
                throw IOException("PR #${pullRequest.number} 不属于当前核心仓库")
            }
        }
    }

    private fun gitRemoteCandidates(repository: String): List<String> {
        val original = "https://github.com/$repository.git"
        return PullRequestGitSourcePolicy.candidates(
            original = original,
            proxyCandidates = githubProxyService.buildUrlCandidates(original)
        )
    }

    private suspend fun cloneBaseBranch(
        remoteCandidates: List<String>,
        baseBranch: String,
        workTree: File,
        monitor: ProgressMonitor,
        onProgress: (stage: String, progress: Float?) -> Unit
    ): ClonedBase {
        var lastFailure: Exception? = null
        remoteCandidates.forEachIndexed { index, remote ->
            currentCoroutineContext().ensureActive()
            if (index > 0) onProgress("当前 Git 线路不可用，正在切换", null)
            runCatching { workTree.deleteRecursively() }
            try {
                val cloned = Git.cloneRepository()
                    .setURI(remote)
                    .setDirectory(workTree)
                    .setBranch("refs/heads/$baseBranch")
                    .setBranchesToClone(listOf("refs/heads/$baseBranch"))
                    .setCloneAllBranches(false)
                    .setNoTags()
                    .setDepth(GIT_SHALLOW_DEPTH)
                    .setTimeout(GIT_TRANSPORT_TIMEOUT_SECONDS)
                    .setProgressMonitor(monitor)
                    .call()
                return ClonedBase(git = cloned, remote = remote)
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                lastFailure = error
            }
        }
        throw IOException(
            "无法获取 $baseBranch 基线：${lastFailure?.message ?: "Git 线路不可用"}",
            lastFailure
        )
    }

    private suspend fun fetchPullRequestHead(
        git: Git,
        remoteCandidates: List<String>,
        pullRequestNumber: Int,
        localRef: String,
        monitor: ProgressMonitor
    ): ObjectId {
        var lastFailure: Exception? = null
        remoteCandidates.forEach { remote ->
            currentCoroutineContext().ensureActive()
            try {
                git.fetch()
                    .setRemote(remote)
                    .setRefSpecs(RefSpec("+refs/pull/$pullRequestNumber/head:$localRef"))
                    .setTagOpt(TagOpt.NO_TAGS)
                    .setTimeout(GIT_TRANSPORT_TIMEOUT_SECONDS)
                    .setProgressMonitor(monitor)
                    .call()
                return git.repository.resolve(localRef)
                    ?: throw IOException("远端未返回 PR #$pullRequestNumber 的提交")
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                lastFailure = error
            }
        }
        throw IOException(
            "无法获取 PR #$pullRequestNumber 的提交：${lastFailure?.message ?: "Git 线路不可用"}",
            lastFailure
        )
    }

    private suspend fun checkoutBuildBase(
        git: Git,
        preferredCommitSha: String,
        baseBranch: String,
        remoteCandidates: List<String>,
        monitor: ProgressMonitor,
        onProgress: (stage: String, progress: Float?) -> Unit
    ): String {
        val requested = preferredCommitSha.trim()
        if (requested.isBlank()) {
            return git.repository.resolve(Constants.HEAD)?.name
                ?: throw IOException("无法确定目标分支基线提交")
        }
        if (!COMMIT_SHA_PATTERN.matches(requested)) {
            throw IOException("当前核心的提交标识无效，无法在该版本上并入 PR")
        }
        var commit = git.repository.resolve("$requested^{commit}")
        if (commit == null && isShallowRepository(git)) {
            onProgress("当前核心较旧，正在补充基线历史", null)
            fetchFullBaseHistory(git, baseBranch, remoteCandidates, monitor)
            commit = git.repository.resolve("$requested^{commit}")
        }
        commit ?: throw IOException("当前核心版本已不在仓库历史中，无法在该版本上并入 PR")
        runCatching {
            git.checkout().setName(commit.name).call()
        }.getOrElse { error ->
            throw IOException("无法切换到当前核心版本，PR 尚未应用", error)
        }
        return commit.name
    }

    private suspend fun ensurePullRequestBasesAvailable(
        git: Git,
        pullRequests: List<CorePullRequest>,
        baseBranch: String,
        remoteCandidates: List<String>,
        monitor: ProgressMonitor,
        onProgress: (stage: String, progress: Float?) -> Unit
    ) {
        if (!isShallowRepository(git)) return
        val hasMissingBase = pullRequests.asSequence()
            .map(CorePullRequest::baseSha)
            .filter(COMMIT_SHA_PATTERN::matches)
            .any { sha -> git.repository.resolve("$sha^{commit}") == null }
        if (!hasMissingBase) return
        onProgress("选中的 PR 基线较旧，正在补充历史", null)
        fetchFullBaseHistory(git, baseBranch, remoteCandidates, monitor)
    }

    private suspend fun fetchFullBaseHistory(
        git: Git,
        baseBranch: String,
        remoteCandidates: List<String>,
        monitor: ProgressMonitor
    ) {
        var lastFailure: Exception? = null
        remoteCandidates.forEach { remote ->
            currentCoroutineContext().ensureActive()
            try {
                git.fetch()
                    .setRemote(remote)
                    .setRefSpecs(
                        RefSpec("+refs/heads/$baseBranch:refs/remotes/origin/$baseBranch")
                    )
                    .setUnshallow(true)
                    .setTagOpt(TagOpt.NO_TAGS)
                    .setTimeout(GIT_TRANSPORT_TIMEOUT_SECONDS)
                    .setProgressMonitor(monitor)
                    .call()
                return
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                lastFailure = error
            }
        }
        throw IOException(
            "无法补充基线历史：${lastFailure?.message ?: "Git 线路不可用"}",
            lastFailure
        )
    }

    private fun isShallowRepository(git: Git): Boolean =
        File(git.repository.directory, "shallow").isFile

    private data class ClonedBase(
        val git: Git,
        val remote: String
    )

    private fun locateCoreDirectory(workTree: File): File? {
        return listOf(
            File(workTree, "danmu_api"),
            File(workTree, "danmu-api"),
            workTree
        ).firstOrNull { candidate ->
            candidate.isDirectory && File(candidate, "worker.js").isFile
        }
    }

    private fun copyCoreTree(source: File, destination: File, job: Job?) {
        if (!destination.exists() && !destination.mkdirs()) {
            throw IOException("无法创建 PR 核心暂存目录")
        }
        var fileCount = 0
        var copiedBytes = 0L

        fun copyEntry(input: File, output: File) {
            job?.ensureActive()
            if (input.name == ".git") return
            val stat = runCatching { Os.lstat(input.absolutePath) }
                .getOrElse { throw IOException("无法检查文件：${input.name}", it) }
            if (OsConstants.S_ISLNK(stat.st_mode)) {
                throw IOException("PR 合并结果包含不支持的符号链接：${input.name}")
            }
            if (input.isDirectory) {
                if (!output.exists() && !output.mkdirs()) {
                    throw IOException("无法创建目录：${output.name}")
                }
                input.listFiles().orEmpty().forEach { child ->
                    copyEntry(child, File(output, child.name))
                }
                return
            }
            if (!input.isFile) return
            fileCount += 1
            copiedBytes += input.length().coerceAtLeast(0L)
            if (fileCount > MAX_CORE_FILES || copiedBytes > MAX_CORE_BYTES) {
                throw IOException("PR 合并结果体积异常，已停止处理")
            }
            output.parentFile?.mkdirs()
            FileInputStream(input).use { sourceStream ->
                FileOutputStream(output).use { targetStream ->
                    sourceStream.copyTo(targetStream)
                }
            }
        }

        source.listFiles().orEmpty().forEach { child ->
            copyEntry(child, File(destination, child.name))
        }
    }

    companion object {
        private const val MAX_CORE_FILES = 30_000
        private const val MAX_CORE_BYTES = 512L * 1024L * 1024L
        private const val GIT_TRANSPORT_TIMEOUT_SECONDS = 30
        private const val GIT_SHALLOW_DEPTH = 128
        private val COMMIT_SHA_PATTERN = Regex("^[0-9a-fA-F]{7,40}$")
    }
}

internal object PullRequestGitSourcePolicy {
    fun candidates(original: String, proxyCandidates: List<String>): List<String> {
        return (proxyCandidates + original)
            .map(String::trim)
            .filter { it.startsWith("https://") || it.startsWith("http://") }
            .distinct()
    }
}

internal data class PullRequestCommit(
    val number: Int,
    val commitId: ObjectId
)

internal object JGitPullRequestMerger {
    fun merge(
        git: Git,
        commits: List<PullRequestCommit>,
        onMerge: (index: Int, total: Int, pullRequestNumber: Int) -> Unit = { _, _, _ -> }
    ): String {
        commits.forEachIndexed { index, pullRequest ->
            onMerge(index + 1, commits.size, pullRequest.number)
            val result = git.merge()
                .include("PR #${pullRequest.number}", pullRequest.commitId)
                .setStrategy(MergeStrategy.RECURSIVE)
                .setFastForward(MergeCommand.FastForwardMode.NO_FF)
                .setCommit(true)
                .setMessage("Local merge of PR #${pullRequest.number}")
                .call()
            if (!result.mergeStatus.isSuccessful) {
                val conflictFiles = buildSet {
                    addAll(result.conflicts?.keys.orEmpty())
                    addAll(result.failingPaths?.keys.orEmpty())
                }.sorted()
                throw PullRequestMergeConflictException(pullRequest.number, conflictFiles)
            }
        }
        return git.repository.resolve(Constants.HEAD)?.name
            ?: throw IOException("无法生成本地 PR 合并提交")
    }
}

private class GitProgressMonitor(
    private val job: Job?,
    private val onProgress: (String, Float?) -> Unit
) : ProgressMonitor {
    private var title: String = "正在处理 Git 数据"
    private var totalWork: Int = ProgressMonitor.UNKNOWN
    private var completedWork: Int = 0
    private var lastEmitAt: Long = 0L

    override fun start(totalTasks: Int) = Unit

    override fun beginTask(title: String?, totalWork: Int) {
        this.title = title?.trim().orEmpty().ifBlank { "正在处理 Git 数据" }
        this.totalWork = totalWork
        completedWork = 0
        emit(force = true)
    }

    override fun update(completed: Int) {
        completedWork += completed.coerceAtLeast(0)
        emit(force = false)
    }

    override fun endTask() {
        emit(force = true)
    }

    override fun isCancelled(): Boolean = job?.isActive == false

    private fun emit(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastEmitAt < 180L) return
        lastEmitAt = now
        val progress = totalWork.takeIf { it > 0 }
            ?.let { (completedWork.toFloat() / it.toFloat()).coerceIn(0f, 1f) }
        onProgress(title, progress)
    }
}
