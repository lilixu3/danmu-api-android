package com.example.danmuapiapp.data.service

/** Event-driven endpoint refresh guard for the service notification. */
internal object NotificationEndpointRefreshPolicy {

    fun shouldRefresh(
        endpointInfoEnabled: Boolean,
        foregroundStarted: Boolean,
        notificationSuppressed: Boolean,
        displayedEndpoint: String?,
        currentEndpoint: String
    ): Boolean {
        return endpointInfoEnabled &&
            foregroundStarted &&
            !notificationSuppressed &&
            displayedEndpoint != currentEndpoint
    }
}
