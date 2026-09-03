package com.example.danmuapiapp.data.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationEndpointRefreshPolicyTest {

    @Test
    fun `refreshes only when enabled visible endpoint changes`() {
        assertTrue(
            NotificationEndpointRefreshPolicy.shouldRefresh(
                endpointInfoEnabled = true,
                foregroundStarted = true,
                notificationSuppressed = false,
                displayedEndpoint = "192.168.1.10:9321",
                currentEndpoint = "192.168.1.11:9321"
            )
        )
    }

    @Test
    fun `does not refresh without a visible endpoint change`() {
        assertFalse(
            NotificationEndpointRefreshPolicy.shouldRefresh(
                endpointInfoEnabled = true,
                foregroundStarted = true,
                notificationSuppressed = false,
                displayedEndpoint = "192.168.1.10:9321",
                currentEndpoint = "192.168.1.10:9321"
            )
        )
        assertFalse(
            NotificationEndpointRefreshPolicy.shouldRefresh(
                endpointInfoEnabled = false,
                foregroundStarted = true,
                notificationSuppressed = false,
                displayedEndpoint = "192.168.1.10:9321",
                currentEndpoint = "192.168.1.11:9321"
            )
        )
        assertFalse(
            NotificationEndpointRefreshPolicy.shouldRefresh(
                endpointInfoEnabled = true,
                foregroundStarted = false,
                notificationSuppressed = false,
                displayedEndpoint = "192.168.1.10:9321",
                currentEndpoint = "192.168.1.11:9321"
            )
        )
        assertFalse(
            NotificationEndpointRefreshPolicy.shouldRefresh(
                endpointInfoEnabled = true,
                foregroundStarted = true,
                notificationSuppressed = true,
                displayedEndpoint = "192.168.1.10:9321",
                currentEndpoint = "192.168.1.11:9321"
            )
        )
    }
}
