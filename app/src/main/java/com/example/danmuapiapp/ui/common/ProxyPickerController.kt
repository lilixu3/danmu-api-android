package com.example.danmuapiapp.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.danmuapiapp.data.service.GithubProxyService
import com.example.danmuapiapp.data.service.GithubProxySpeedTester
import com.example.danmuapiapp.domain.model.GithubProxyOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal data class ProxyPickerUiState(
    val isVisible: Boolean = false,
    val selectedId: String = "",
    val testingIds: Set<String> = emptySet(),
    val latencyMap: Map<String, Long> = emptyMap()
)

internal class ProxyPickerController(
    private val githubProxyService: GithubProxyService,
    private val githubProxySpeedTester: GithubProxySpeedTester,
    private val scope: CoroutineScope,
    private val proxyOptionsProvider: () -> List<GithubProxyOption>
) {
    var uiState by mutableStateOf(
        ProxyPickerUiState(selectedId = githubProxyService.currentSelectedOption().id)
    )
        private set

    private var testJob: Job? = null

    fun open() {
        uiState = uiState.copy(
            isVisible = true,
            selectedId = githubProxyService.currentSelectedOption().id
        )
        startSpeedTest()
    }

    fun dismiss() {
        stopSpeedTest()
        uiState = uiState.copy(isVisible = false)
    }

    fun select(proxyId: String) {
        uiState = uiState.copy(selectedId = proxyId)
    }

    fun retest() {
        startSpeedTest()
    }

    fun confirm(onConfirmed: () -> Unit = {}) {
        githubProxyService.setSelectedProxy(uiState.selectedId)
        dismiss()
        onConfirmed()
    }

    private fun startSpeedTest() {
        stopSpeedTest()
        val options = proxyOptionsProvider()
        uiState = uiState.copy(
            latencyMap = emptyMap(),
            testingIds = options.map { it.id }.toSet()
        )
        testJob = scope.launch {
            githubProxySpeedTester.testAll(options) { proxyId, latency ->
                uiState = uiState.copy(
                    latencyMap = uiState.latencyMap + (proxyId to latency),
                    testingIds = uiState.testingIds - proxyId
                )
            }
        }
    }

    private fun stopSpeedTest() {
        testJob?.cancel()
        testJob = null
        uiState = uiState.copy(testingIds = emptySet())
    }
}
