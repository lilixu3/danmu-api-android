package com.example.danmuapiapp.data.service

/** A newer same-version check can repair an incomplete prompt, but cannot erase a working asset. */
internal fun mergeForegroundAppUpdateResult(
    current: AppUpdateService.CheckResult?, incoming: AppUpdateService.CheckResult
): AppUpdateService.CheckResult {
    if (current?.latestVersion != incoming.latestVersion || !incoming.hasUpdate) return incoming
    return if (incoming.downloadUrls.isEmpty() && current.downloadUrls.isNotEmpty())
        incoming.copy(bestAsset = current.bestAsset, downloadUrls = current.downloadUrls)
    else incoming
}
