package com.example.danmuapiapp.ui.compat

import com.example.danmuapiapp.domain.model.ServiceStatus

internal enum class CompatAccessAddressStatus {
    Waiting,
    LocalOnly,
    Ready,
    LocalAvailable
}

internal object CompatAccessAddressStatusPolicy {
    fun resolve(
        serviceStatus: ServiceStatus,
        hasLocalAddress: Boolean,
        hasLanIpv4Address: Boolean,
        hasLanIpv6Address: Boolean,
        localNetworkPermissionMissing: Boolean
    ): CompatAccessAddressStatus {
        val hasLanAddress = hasLanIpv4Address || hasLanIpv6Address
        if (
            serviceStatus != ServiceStatus.Running ||
            (hasLocalAddress.not() && hasLanAddress.not())
        ) {
            return CompatAccessAddressStatus.Waiting
        }
        if (localNetworkPermissionMissing) {
            return CompatAccessAddressStatus.LocalOnly
        }
        return when {
            hasLanAddress -> CompatAccessAddressStatus.Ready
            hasLocalAddress -> CompatAccessAddressStatus.LocalAvailable
            else -> CompatAccessAddressStatus.Waiting
        }
    }
}
