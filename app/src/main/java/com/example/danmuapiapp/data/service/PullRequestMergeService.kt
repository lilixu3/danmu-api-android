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
    private val pullRequestService: GithubPullRequestService
) {
    suspend fun buildInto(
        repository: String,
        baseBranch: String,
        pullRequestNumbers: List<Int>,
        destination: File,
        onProgress: (stage: String, progress: Float?) -> Unit
    ): PullRequestStackResult = withContext(Dispatchers.IO) {
        val numbers = pullRequestNumbers.distinct()
        if (numbers.isEmpty()) throw IOException("至少选择一个 PR")
        if (numbers.any { it <= 0 }) throw IOException("PR 编号无效")

        onProgress("正在确认所选 PR", null)
        val pullRequests = numbers.map { number -> pullRequestService.get(repository, number) }
        validatePlan(repository, baseBranch, pullRequests)
        currentCoroutineContext().ensureActive()

        val workRoot = File(context.cacheDir, "pull-request-lab")
        val workTree = File(workRoot, "merge-${UUID.randomUUID()}")
        workRoot.mkdirs()
        val coroutineJob = currentCoroutineContext()[Job]
        val monitor = GitProgressMonitor(coroutineJob, onProgress)
        var git: Git? = null
        try {
            onProgress("正在获取 $baseBranch 基线", null)
            git = Git.cloneRepository()
                .setURI("https://github.com/$repository.git")
                .setDirectory(workTree)
                .setBranch("refs/heads/$baseBranch")
                .setBranchesToClone(listOf("refs/heads/$baseBranch"))
                .setCloneAllBranches(false)
                .setProgressMonitor(monitor)
                .call()
            currentCoroutineContext().ensureActive()

            git.repository.config.apply {
                setString("user", null, "name", "Danmu API App")
                setString("user", null, "email", "local-pr-lab@localhost")
                save()
            }
            val baseCommitSha = git.repository.resolve(Constants.HEAD)?.name
                ?: throw IOException("无法确定目标分支基线提交")

            val refs = pullRequests.mapIndexed { index, pullRequest ->
                currentCoroutineContext().ensureActive()
                onProgress(
                    "正在获取 PR #${pullRequest.number}（${index + 1}/${pullRequests.size}）",
                    index.toFloat() / pullRequests.size.toFloat()
                )
                val localRef = "refs/remotes/pull/${pullRequest.number}/head"
                git.fetch()
                    .setRemote("origin")
                    .setRefSpecs(RefSpec("+refs/pull/${pullRequest.number}/head:$localRef"))
                    .setProgressMonitor(monitor)
                    .call()
                val commitId = git.repository.resolve(localRef)
                    ?: throw IOException("无法获取 PR #${pullRequest.number} 的提交")
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
            if (!pullRequest.state.equals("open", ignoreCase = true)) {
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
