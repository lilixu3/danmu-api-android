package com.example.danmuapiapp.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.RuntimeState
import com.example.danmuapiapp.domain.model.ServiceStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 网络监听：订阅系统网络事件，去抖后重算本地/局域网访问地址。
 *
 * Root 模式没有前台服务，地址变化时由 [onRootEndpointChanged] 回写通知；
 * 普通模式的网络事件由 :node 前台服务自行处理。
 */
internal class RuntimeNetworkMonitorController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<RuntimeState>,
    private val stateStore: RuntimeStateStore,
    private val onRootEndpointChanged: (RuntimeState) -> Unit
) {

    private companion object {
        const val NETWORK_URL_REFRESH_DEBOUNCE_MS = 300L
    }

    private val connectivityManager by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var networkUrlRefreshJob: Job? = null

    fun start() {
        val cm = connectivityManager ?: return
        if (networkCallback != null) return

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scheduleRuntimeUrlRefresh()
            }

            override fun onLost(network: Network) {
                scheduleRuntimeUrlRefresh()
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                scheduleRuntimeUrlRefresh()
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                scheduleRuntimeUrlRefresh()
            }
        }

        val registered = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cm.registerDefaultNetworkCallback(callback)
            } else {
                val request = NetworkRequest.Builder().build()
                @Suppress("DEPRECATION")
                cm.registerNetworkCallback(request, callback)
            }
            true
        }.getOrElse { false }

        if (registered) {
            networkCallback = callback
            scheduleRuntimeUrlRefresh()
        }
    }

    private fun scheduleRuntimeUrlRefresh() {
        networkUrlRefreshJob?.cancel()
        networkUrlRefreshJob = scope.launch {
            delay(NETWORK_URL_REFRESH_DEBOUNCE_MS)
            refreshRuntimeUrls()
        }
    }

    fun refreshRuntimeUrls(force: Boolean = false) {
        val networkAddresses = stateStore.resolveAddresses()
        var endpointChanged = false
        state.update { current ->
            val localUrl = stateStore.buildLocalUrl(current.port, current.token)
            val lanUrl = stateStore.buildLanUrl(networkAddresses.ipv4, current.port, current.token)
            val lanIpv6Url = stateStore.buildLanIpv6Url(
                networkAddresses.ipv6,
                current.port,
                current.token,
                current.listenMode
            )
            if (!force &&
                current.localUrl == localUrl &&
                current.lanUrl == lanUrl &&
                current.lanIpv6Url == lanIpv6Url
            ) {
                current
            } else {
                endpointChanged = force ||
                    current.lanUrl != lanUrl ||
                    current.lanIpv6Url != lanIpv6Url
                current.copy(
                    localUrl = localUrl,
                    lanUrl = lanUrl,
                    lanIpv6Url = lanIpv6Url
                )
            }
        }
        // 普通模式由 :node 前台服务自行监听网络事件；Root 模式没有前台服务，
        // 因此在应用进程仍存活时用同一个系统网络事件同步一次通知。
        val snapshot = state.value
        if (endpointChanged &&
            snapshot.runMode == RunMode.Root &&
            snapshot.status == ServiceStatus.Running
        ) {
            onRootEndpointChanged(snapshot)
        }
    }
}
