package com.example.danmuapiapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

/**
 * Quick Settings tile: start/stop the embedded Node foreground service.
 *
 * Users can add it from the system tile picker ("控制中心/快捷设置" -> 编辑 -> 添加磁贴).
 */
@RequiresApi(Build.VERSION_CODES.N)
class NodeTileService : TileService() {

    @Volatile
    private var busy: Boolean = false

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != NodeService.ACTION_NODE_STATUS) return
            val status = intent.getStringExtra(NodeService.EXTRA_STATUS) ?: return
            val msg = intent.getStringExtra(NodeService.EXTRA_MESSAGE)

            when (status) {
                NodeService.STATUS_STARTING -> {
                    busy = true
                    updateTile(
                        state = Tile.STATE_UNAVAILABLE,
                        subtitle = msg ?: "启动中…"
                    )
                }
                NodeService.STATUS_RUNNING, NodeService.STATUS_ALREADY_RUNNING -> {
                    busy = false
                    updateTile(
                        state = Tile.STATE_ACTIVE,
                        subtitle = "运行中"
                    )
                }
                NodeService.STATUS_STOPPED -> {
                    busy = false
                    updateTile(
                        state = Tile.STATE_INACTIVE,
                        subtitle = "已停止"
                    )
                }
                NodeService.STATUS_ERROR -> {
                    busy = false
                    updateTile(
                        state = Tile.STATE_INACTIVE,
                        subtitle = msg?.let { "错误" } ?: "错误"
                    )
                }
            }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        registerStatusReceiverSafe()
        // Refresh current state when the tile becomes visible.
        refreshTileState()
    }

    override fun onStopListening() {
        super.onStopListening()
        unregisterStatusReceiverSafe()
    }

    override fun onClick() {
        super.onClick()
        if (busy) return

        if (NodeService.isRunning()) {
            // Manual stop from tile -> should not auto-restart.
            NodeKeepAlive.setDesiredRunning(this, false)
            busy = true
            updateTile(state = Tile.STATE_UNAVAILABLE, subtitle = "停止中…")
            // Deliver STOP to the running foreground service.
            val it = Intent(this, NodeService::class.java).setAction(NodeService.ACTION_STOP)
            // If the service is already running, startService is safe.
            try {
                startService(it)
            } catch (_: Throwable) {
                busy = false
                refreshTileState()
            }
        } else {
            // User requests start -> mark as desired running.
            NodeKeepAlive.setDesiredRunning(this, true)
            busy = true
            updateTile(state = Tile.STATE_UNAVAILABLE, subtitle = "启动中…")
            val it = Intent(this, NodeService::class.java).setAction(NodeService.ACTION_START)
            try {
                // Start as foreground service to satisfy Android 8+ background restrictions.
                ContextCompat.startForegroundService(this, it)
            } catch (_: Throwable) {
                busy = false
                refreshTileState()
            }
        }
    }

    private fun refreshTileState() {
        val running = NodeService.isRunning()
        updateTile(
            state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE,
            subtitle = if (running) "运行中" else "已停止"
        )
    }

    private fun updateTile(state: Int, subtitle: String?) {
        val tile = qsTile ?: return
        tile.state = state

        // Subtitle is available on Android 10+ (API 29).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitle
        }

        // For older versions, contentDescription helps accessibility and long-press UI.
        tile.contentDescription = subtitle ?: ""

        tile.updateTile()
    }

    private fun registerStatusReceiverSafe() {
        val filter = IntentFilter(NodeService.ACTION_NODE_STATUS)
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(statusReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(statusReceiver, filter)
            }
        } catch (_: Throwable) {
        }
    }

    private fun unregisterStatusReceiverSafe() {
        try {
            unregisterReceiver(statusReceiver)
        } catch (_: Throwable) {
        }
    }
}
