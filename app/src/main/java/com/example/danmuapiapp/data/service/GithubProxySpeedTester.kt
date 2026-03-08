package com.example.danmuapiapp.data.service

import com.example.danmuapiapp.domain.model.GithubProxyOption
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Singleton
class GithubProxySpeedTester @Inject constructor(
    private val githubProxyService: GithubProxyService
) {
    suspend fun testAll(
        options: List<GithubProxyOption>,
        onResult: (String, Long) -> Unit
    ) = coroutineScope {
        options.forEach { option ->
            launch {
                onResult(option.id, githubProxyService.testLatency(option))
            }
        }
    }
}
