package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.CoreRemoteCommit
import com.example.danmuapiapp.domain.model.GithubRelease

internal data class CoreRemoteSource(
    val release: GithubRelease,
    val metadata: CoreSourceMetadata? = null,
    val remoteCommit: CoreRemoteCommit? = null
)

internal data class BranchHeadInfo(
    val sha: String,
    val publishedAt: String,
    val title: String,
    val message: String,
    val author: String,
    val htmlUrl: String
)
