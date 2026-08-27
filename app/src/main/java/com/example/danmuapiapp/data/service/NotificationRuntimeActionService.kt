package com.example.danmuapiapp.data.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 主进程侧通知动作入口。通知按钮不能在 :node 进程内自行重启：
 * 主进程仓库靠 pendingNormalRestart 识别“这是重启中的停止广播”，
 * :node 内自拉起会让两边状态机互相抢。
 */
@AndroidEntryPoint
class NotificationRuntimeActionService : Service() {

    @Inject lateinit var runtimeRepository: RuntimeRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != NodeService.ACTION_RESTART) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        AppDiagnosticLogger.i(this, TAG, "通知触发主进程重启服务")
        scope.launch {
            try {
                runtimeRepository.restartServiceAndAwait()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                AppDiagnosticLogger.w(
                    this@NotificationRuntimeActionService,
                    TAG,
                    "通知重启失败：${error.message}",
                    error
                )
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "NotifRuntimeAction"
    }
}
