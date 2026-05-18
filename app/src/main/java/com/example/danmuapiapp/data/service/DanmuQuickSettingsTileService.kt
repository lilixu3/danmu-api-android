package com.example.danmuapiapp.data.service

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.danmuapiapp.MainActivity
import com.example.danmuapiapp.domain.model.LogLevel
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.RuntimeState
import com.example.danmuapiapp.domain.model.ServiceStatus
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class DanmuQuickSettingsTileService : TileService() {

    @Inject lateinit var runtimeRepository: RuntimeRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stateCollectJob: Job? = null
    private var operationJob: Job? = null

    override fun onTileAdded() {
        super.onTileAdded()
        runtimeRepository.refreshRuntimeState()
        updateTile(runtimeRepository.runtimeState.value)
    }

    override fun onStartListening() {
        super.onStartListening()
        runtimeRepository.refreshRuntimeState()
        updateTile(runtimeRepository.runtimeState.value)
        stateCollectJob?.cancel()
        stateCollectJob = serviceScope.launch {
            runtimeRepository.runtimeState.collectLatest { state ->
                updateTile(state)
            }
        }
    }

    override fun onStopListening() {
        stateCollectJob?.cancel()
        stateCollectJob = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) {
            unlockAndRun { handleClick() }
            return
        }
        handleClick()
    }

    override fun onDestroy() {
        stateCollectJob?.cancel()
        operationJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun handleClick() {
        val activeOperation = operationJob
        if (activeOperation != null && activeOperation.isActive) {
            showToast("正在处理上一次控制中心操作")
            updateTile(runtimeRepository.runtimeState.value)
            return
        }

        val state = runtimeRepository.runtimeState.value
        when (DanmuQuickSettingsTilePolicy.clickAction(state.status)) {
            DanmuQuickSettingsTilePolicy.ClickAction.Start -> startFromTile(state)
            DanmuQuickSettingsTilePolicy.ClickAction.Stop -> stopFromTile(state)
            DanmuQuickSettingsTilePolicy.ClickAction.Ignore -> {
                showToast(state.status.busyMessage())
                updateTile(state)
            }
        }
    }

    private fun startFromTile(state: RuntimeState) {
        if (state.runMode == RunMode.Normal && !hasPostNotificationsPermission()) {
            AppDiagnosticLogger.w(this, TAG, "控制中心启动普通模式被拦截：通知权限未授予")
            runtimeRepository.addLog(LogLevel.Warn, "控制中心启动普通模式失败：请先授予通知权限")
            showToast("请先在应用内授予通知权限")
            openAppFromTile()
            return
        }

        updateTile(
            DanmuQuickSettingsTilePolicy.presentation(
                status = ServiceStatus.Starting,
                runMode = state.runMode,
                port = state.port
            )
        )

        when (state.runMode) {
            RunMode.Normal -> {
                AppDiagnosticLogger.i(this, TAG, "控制中心请求启动普通模式")
                runtimeRepository.addLog(LogLevel.Info, "控制中心请求启动普通模式")
                runtimeRepository.startService()
            }

            RunMode.Root -> runRootOperation(
                action = DanmuQuickSettingsTilePolicy.ClickAction.Start,
                port = state.port
            )
        }
    }

    private fun stopFromTile(state: RuntimeState) {
        updateTile(
            DanmuQuickSettingsTilePolicy.presentation(
                status = ServiceStatus.Stopping,
                runMode = state.runMode,
                port = state.port
            )
        )

        when (state.runMode) {
            RunMode.Normal -> {
                AppDiagnosticLogger.i(this, TAG, "控制中心请求停止普通模式")
                runtimeRepository.addLog(LogLevel.Info, "控制中心请求停止普通模式")
                runtimeRepository.stopService()
            }

            RunMode.Root -> runRootOperation(
                action = DanmuQuickSettingsTilePolicy.ClickAction.Stop,
                port = state.port
            )
        }
    }

    private fun runRootOperation(
        action: DanmuQuickSettingsTilePolicy.ClickAction,
        port: Int
    ) {
        operationJob = serviceScope.launch {
            val result = withContext(Dispatchers.IO) {
                when (action) {
                    DanmuQuickSettingsTilePolicy.ClickAction.Start -> {
                        AppDiagnosticLogger.i(this@DanmuQuickSettingsTileService, TAG, "控制中心请求启动 Root 模式")
                        RootRuntimeController.start(
                            context = applicationContext,
                            port = port,
                            quickMode = false
                        )
                    }

                    DanmuQuickSettingsTilePolicy.ClickAction.Stop -> {
                        AppDiagnosticLogger.i(this@DanmuQuickSettingsTileService, TAG, "控制中心请求停止 Root 模式")
                        RootRuntimeController.stop(applicationContext, port)
                    }

                    DanmuQuickSettingsTilePolicy.ClickAction.Ignore -> {
                        RootRuntimeController.OpResult(true, "已忽略重复操作")
                    }
                }
            }

            val detail = result.detail.ifBlank { result.message }
            if (result.ok) {
                runtimeRepository.addLog(LogLevel.Info, "控制中心 Root 操作完成：${result.message}")
                val finalStatus = when (action) {
                    DanmuQuickSettingsTilePolicy.ClickAction.Start -> ServiceStatus.Running
                    DanmuQuickSettingsTilePolicy.ClickAction.Stop -> ServiceStatus.Stopped
                    DanmuQuickSettingsTilePolicy.ClickAction.Ignore -> runtimeRepository.runtimeState.value.status
                }
                updateTile(
                    DanmuQuickSettingsTilePolicy.presentation(
                        status = finalStatus,
                        runMode = RunMode.Root,
                        port = port
                    )
                )
                showToast(result.message)
            } else {
                runtimeRepository.addLog(LogLevel.Error, "控制中心 Root 操作失败：$detail")
                AppDiagnosticLogger.e(this@DanmuQuickSettingsTileService, TAG, "控制中心 Root 操作失败：$detail")
                updateTile(
                    DanmuQuickSettingsTilePolicy.presentation(
                        status = ServiceStatus.Error,
                        runMode = RunMode.Root,
                        port = port
                    )
                )
                showToast(detail.take(80))
                openAppFromTile()
            }

            runtimeRepository.refreshRuntimeState()
        }
    }

    private fun updateTile(state: RuntimeState) {
        updateTile(
            DanmuQuickSettingsTilePolicy.presentation(
                status = state.status,
                runMode = state.runMode,
                port = state.port
            )
        )
    }

    private fun updateTile(presentation: DanmuQuickSettingsTilePolicy.Presentation) {
        val tile = qsTile ?: return
        tile.label = presentation.label
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = presentation.subtitle
        }
        tile.state = when (presentation.visualState) {
            DanmuQuickSettingsTilePolicy.VisualState.Active -> Tile.STATE_ACTIVE
            DanmuQuickSettingsTilePolicy.VisualState.Inactive -> Tile.STATE_INACTIVE
            DanmuQuickSettingsTilePolicy.VisualState.Unavailable -> Tile.STATE_UNAVAILABLE
        }
        tile.updateTile()
    }

    private fun hasPostNotificationsPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun openAppFromTile() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun ServiceStatus.busyMessage(): String {
        return when (this) {
            ServiceStatus.Starting -> "服务正在启动中"
            ServiceStatus.Stopping -> "服务正在停止中"
            ServiceStatus.Running,
            ServiceStatus.Stopped,
            ServiceStatus.Error -> "当前状态无需重复操作"
        }
    }

    companion object {
        private const val TAG = "DanmuQuickTile"
    }
}
