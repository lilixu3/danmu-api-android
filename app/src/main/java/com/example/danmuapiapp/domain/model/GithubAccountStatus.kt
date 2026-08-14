package com.example.danmuapiapp.domain.model

/**
 * GitHub authentication and REST API core rate-limit snapshot.
 *
 * [tokenValid] is null when no token is configured or validation could not be
 * completed. This keeps connectivity failures distinct from an explicit 401.
 */
data class GithubAccountStatus(
    val isLoading: Boolean = false,
    val tokenConfigured: Boolean = false,
    val tokenValid: Boolean? = null,
    val login: String? = null,
    val coreLimit: Int? = null,
    val coreRemaining: Int? = null,
    val coreResetEpochSeconds: Long? = null,
    val error: String? = null
)

data class GithubCoreRateLimit(
    val limit: Int,
    val remaining: Int,
    val resetEpochSeconds: Long
)
