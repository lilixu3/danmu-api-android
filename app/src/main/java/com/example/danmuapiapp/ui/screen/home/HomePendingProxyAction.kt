package com.example.danmuapiapp.ui.screen.home

import com.example.danmuapiapp.domain.model.ApiVariant

/**
 * 等待用户选择代理后再继续的核心操作。
 *
 * 从 `HomeViewModel.PendingProxyAction` 原样抽出，供各子控制器与门面共享。
 */
internal sealed interface HomePendingProxyAction {
    data class Install(val variant: ApiVariant) : HomePendingProxyAction
    data class Update(val variant: ApiVariant) : HomePendingProxyAction
    data class CheckUpdate(val variant: ApiVariant) : HomePendingProxyAction
    data class LoadUpdateDetails(val variant: ApiVariant) : HomePendingProxyAction
    data object RepairDependenciesOnline : HomePendingProxyAction
}
