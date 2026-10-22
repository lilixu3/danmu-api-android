package com.example.danmuapiapp.ui.screen.localdanmu

import com.example.danmuapiapp.data.repository.LocalDanmuFileBrowser
import com.example.danmuapiapp.data.repository.LocalDanmuImportedIndex
import com.example.danmuapiapp.data.repository.LocalDanmuUploadHistoryStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 本地弹幕「目录直读」子控制器：目录浏览、多选、默认目录记忆与「导入所选文件」入口。
 *
 * 只读写 [LocalDanmuUiState] 的 browser 切片；真正入队交给批量导入控制器（[onImportSelected]）。
 */
internal class LocalDanmuDirectoryBrowserController(
    private val scope: CoroutineScope,
    private val fileBrowser: LocalDanmuFileBrowser,
    private val uploadHistoryStore: LocalDanmuUploadHistoryStore,
    private val uiState: MutableStateFlow<LocalDanmuUiState>,
    private val importedIndex: () -> Map<String, String>,
    private val onImportSelected: (List<String>) -> Unit
) {


    fun openDirectoryBrowser() {
        val start = fileBrowser.preferredDirectory(
            remembered = uploadHistoryStore.recentDirectory(),
            custom = uploadHistoryStore.customDirectory()
        )
        uiState.update { it.copy(browser = it.browser.copy(visible = true)) }
        browseDirectory(start)
    }

    fun browseDirectory(path: String) {
        scope.launch {
            uiState.update {
                it.copy(
                    browser = it.browser.copy(
                        visible = true,
                        isLoading = true,
                        path = fileBrowser.normalize(path),
                        error = null
                    )
                )
            }
            fileBrowser.list(path).fold(
                onSuccess = { snapshot ->
                    uploadHistoryStore.saveRecentDirectory(snapshot.path)
                    uiState.update {
                        it.copy(
                            browser = it.browser.copy(
                                visible = true,
                                isLoading = false,
                                path = snapshot.path,
                                parent = snapshot.parent,
                                rows = snapshot.entries.map { entry -> toBrowserRow(entry, it.browser.selectedPaths) },
                                truncated = snapshot.truncated,
                                error = null,
                                isDefaultDirectory = snapshot.path == defaultDirectory()
                            )
                        )
                    }
                },
                onFailure = { error ->
                    uiState.update {
                        it.copy(
                            browser = it.browser.copy(
                                isLoading = false,
                                error = error.message?.takeIf { message -> message.isNotBlank() }
                                    ?: "无法读取该目录"
                            )
                        )
                    }
                }
            )
        }
    }

    fun browseParent() {
        val parent = uiState.value.browser.parent ?: return
        browseDirectory(parent)
    }

    fun browseDefaultDirectory() {
        browseDirectory(defaultDirectory())
    }

    fun closeDirectoryBrowser() {
        uiState.update {
            it.copy(
                browser = it.browser.copy(
                    visible = false,
                    error = null,
                    selectedPaths = emptySet()
                )
            )
        }
    }

    fun toggleBrowserSelection(path: String) {
        uiState.update { state ->
            val selected = state.browser.selectedPaths.toMutableSet()
            if (!selected.add(path)) selected.remove(path)
            state.copy(
                browser = state.browser.copy(
                    selectedPaths = selected,
                    rows = state.browser.rows.map { row ->
                        if (row.entry.isDirectory) {
                            row
                        } else {
                            row.copy(selected = row.entry.path in selected)
                        }
                    }
                )
            )
        }
    }

    /** 全选 / 取消全选当前目录下的文件（不含子目录）。 */
    fun toggleBrowserSelectAll() {
        uiState.update { state ->
            val paths = state.browser.selectablePaths
            if (paths.isEmpty()) return@update state
            val selected = if (state.browser.allSelected) {
                state.browser.selectedPaths - paths.toSet()
            } else {
                state.browser.selectedPaths + paths
            }
            state.copy(
                browser = state.browser.copy(
                    selectedPaths = selected,
                    rows = state.browser.rows.map { row ->
                        if (row.entry.isDirectory) {
                            row
                        } else {
                            row.copy(selected = row.entry.path in selected)
                        }
                    }
                )
            )
        }
    }

    fun setBrowserDefaultDirectory() {
        val path = uiState.value.browser.path
        if (path.isBlank()) return
        uploadHistoryStore.saveCustomDirectory(path)
        uiState.update {
            it.copy(
                browser = it.browser.copy(isDefaultDirectory = true),
                message = "已把该目录设为默认浏览目录"
            )
        }
    }

    /** 目录多选 → 批量导入面板。 */
    fun importSelectedBrowserFiles() {
        val paths = uiState.value.browser.selectedPaths.toList()
        if (paths.isEmpty()) return
        closeDirectoryBrowser()
        onImportSelected(paths)
    }

    private fun defaultDirectory(): String = fileBrowser.preferredDirectory(
        remembered = uploadHistoryStore.recentDirectory(),
        custom = uploadHistoryStore.customDirectory()
    )


    /** 刷新后重建当前目录的行：保留勾选状态，重算「已导入」标记。 */
    fun remapBrowserRows(
        rows: List<LocalDanmuBrowserRow>,
        selectedPaths: Set<String>
    ): List<LocalDanmuBrowserRow> = rows.map { row -> toBrowserRow(row.entry, selectedPaths) }

    private fun toBrowserRow(
        entry: LocalDanmuFileBrowser.Entry,
        selectedPaths: Set<String>
    ): LocalDanmuBrowserRow = localDanmuBrowserRow(entry, selectedPaths, importedIndex())
}

/** 目录浏览里的一行：目录/文件 + 「已导入」标记 + 多选状态。 */
data class LocalDanmuBrowserRow(
    val entry: LocalDanmuFileBrowser.Entry,
    val importedLabel: String? = null,
    val selected: Boolean = false
)

/** 用当前导入索引和勾选状态构造一行。 */
internal fun localDanmuBrowserRow(
    entry: LocalDanmuFileBrowser.Entry,
    selectedPaths: Set<String>,
    importedIndex: Map<String, String>
): LocalDanmuBrowserRow {
    val imported = if (entry.isDirectory) {
        null
    } else {
        LocalDanmuImportedIndex.lookup(
            index = importedIndex,
            path = entry.path,
            name = entry.name,
            sizeBytes = entry.sizeBytes
        )
    }
    return LocalDanmuBrowserRow(
        entry = entry,
        importedLabel = imported,
        selected = entry.path in selectedPaths
    )
}
