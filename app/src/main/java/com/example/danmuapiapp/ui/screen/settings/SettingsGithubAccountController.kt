package com.example.danmuapiapp.ui.screen.settings

import com.example.danmuapiapp.data.service.GithubAccountService
import com.example.danmuapiapp.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 设置页「GitHub 账号」子控制器：Token 校验保存、清空与额度刷新。
 *
 * 通过递增 generation 取消过期的异步刷新，行为与拆分前一致。
 */
internal class SettingsGithubAccountController(
    private val scope: CoroutineScope,
    private val settingsRepo: SettingsRepository,
    private val githubAccountService: GithubAccountService,
    private val postMessage: (String) -> Unit
) {
    private var githubAccountJob: Job? = null
    private var githubAccountGeneration = 0L

    fun saveGithubToken(token: String) {
        val normalized = token.trim()
        val generation = beginGithubAccountOperation()
        githubAccountJob = scope.launch {
            if (normalized.isBlank()) {
                settingsRepo.setGithubToken("")
                githubAccountService.refresh("")
                if (generation != githubAccountGeneration) return@launch
                postMessage("已切换为匿名 GitHub 额度")
                githubAccountJob = null
                return@launch
            }
            val result = githubAccountService.refresh(normalized)
            if (generation != githubAccountGeneration) return@launch
            when (result.tokenValid) {
                true -> {
                    val saveError = runCatching { settingsRepo.setGithubToken(normalized) }.exceptionOrNull()
                    if (saveError != null) {
                        githubAccountService.refresh()
                        if (generation != githubAccountGeneration) return@launch
                        postMessage(saveError.message ?: "GitHub Token 安全保存失败")
                        githubAccountJob = null
                        return@launch
                    }
                    githubAccountService.refresh()
                    if (generation != githubAccountGeneration) return@launch
                    postMessage("GitHub Token 验证成功${result.login?.let { "：$it" }.orEmpty()}")
                }
                false, null -> {
                    val errorMessage = if (result.tokenValid == false) {
                        result.error ?: "GitHub Token 无效，未保存"
                    } else {
                        result.error ?: "暂时无法验证 Token，未保存"
                    }
                    githubAccountService.refresh()
                    if (generation != githubAccountGeneration) return@launch
                    postMessage(errorMessage)
                }
            }
            if (generation == githubAccountGeneration) githubAccountJob = null
        }
    }

    fun clearGithubToken() {
        val generation = beginGithubAccountOperation()
        settingsRepo.setGithubToken("")
        githubAccountJob = scope.launch {
            githubAccountService.refresh("")
            if (generation == githubAccountGeneration) githubAccountJob = null
        }
        postMessage("已清空 GitHub Token，当前使用匿名额度")
    }

    fun refreshGithubAccount() {
        val generation = beginGithubAccountOperation()
        githubAccountJob = scope.launch {
            githubAccountService.refresh()
            if (generation == githubAccountGeneration) githubAccountJob = null
        }
    }

    private fun beginGithubAccountOperation(): Long {
        githubAccountGeneration += 1
        githubAccountJob?.cancel()
        githubAccountJob = null
        return githubAccountGeneration
    }
}
