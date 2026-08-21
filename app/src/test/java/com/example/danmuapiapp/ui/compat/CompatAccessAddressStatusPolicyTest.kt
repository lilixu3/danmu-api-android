package com.example.danmuapiapp.ui.compat

import com.example.danmuapiapp.domain.model.ServiceStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class CompatAccessAddressStatusPolicyTest {

    @Test
    fun `non-running service never reports ready even when cached urls exist`() {
        listOf(
            ServiceStatus.Stopped,
            ServiceStatus.Starting,
            ServiceStatus.Stopping,
            ServiceStatus.Error
        ).forEach { status ->
            assertEquals(
                CompatAccessAddressStatus.Waiting,
                CompatAccessAddressStatusPolicy.resolve(
                    serviceStatus = status,
                    hasLocalAddress = true,
                    hasLanIpv4Address = true,
                    hasLanIpv6Address = false,
                    localNetworkPermissionMissing = false
                )
            )
        }
    }

    @Test
    fun `running service without Android 17 permission reports local only`() {
        assertEquals(
            CompatAccessAddressStatus.LocalOnly,
            CompatAccessAddressStatusPolicy.resolve(
                serviceStatus = ServiceStatus.Running,
                hasLocalAddress = true,
                hasLanIpv4Address = true,
                hasLanIpv6Address = false,
                localNetworkPermissionMissing = true
            )
        )
    }

    @Test
    fun `running service with lan address reports ready`() {
        assertEquals(
            CompatAccessAddressStatus.Ready,
            CompatAccessAddressStatusPolicy.resolve(
                serviceStatus = ServiceStatus.Running,
                hasLocalAddress = true,
                hasLanIpv4Address = true,
                hasLanIpv6Address = false,
                localNetworkPermissionMissing = false
            )
        )
    }

    @Test
    fun `running service with only loopback reports local available`() {
        assertEquals(
            CompatAccessAddressStatus.LocalAvailable,
            CompatAccessAddressStatusPolicy.resolve(
                serviceStatus = ServiceStatus.Running,
                hasLocalAddress = true,
                hasLanIpv4Address = false,
                hasLanIpv6Address = false,
                localNetworkPermissionMissing = false
            )
        )
    }

    @Test
    fun `running service with IPv6-only lan address reports ready`() {
        assertEquals(
            CompatAccessAddressStatus.Ready,
            CompatAccessAddressStatusPolicy.resolve(
                serviceStatus = ServiceStatus.Running,
                hasLocalAddress = false,
                hasLanIpv4Address = false,
                hasLanIpv6Address = true,
                localNetworkPermissionMissing = false
            )
        )
    }

    @Test
    fun `running service with IPv6-only lan and missing permission reports local only`() {
        assertEquals(
            CompatAccessAddressStatus.LocalOnly,
            CompatAccessAddressStatusPolicy.resolve(
                serviceStatus = ServiceStatus.Running,
                hasLocalAddress = false,
                hasLanIpv4Address = false,
                hasLanIpv6Address = true,
                localNetworkPermissionMissing = true
            )
        )
    }

    @Test
    fun `running service without generated addresses still waits even when permission is missing`() {
        listOf(false, true).forEach { permissionMissing ->
            assertEquals(
                CompatAccessAddressStatus.Waiting,
                CompatAccessAddressStatusPolicy.resolve(
                    serviceStatus = ServiceStatus.Running,
                    hasLocalAddress = false,
                    hasLanIpv4Address = false,
                    hasLanIpv6Address = false,
                    localNetworkPermissionMissing = permissionMissing
                )
            )
        }
    }
}
