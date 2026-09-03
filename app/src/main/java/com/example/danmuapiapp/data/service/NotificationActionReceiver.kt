package com.example.danmuapiapp.data.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.danmuapiapp.BuildConfig
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Root 模式通知的动作入口。
 *
 * Root 模式没有前台服务，通知里用 PendingIntent.getService 启动服务会被后台限制拦截，
 * 因此这里改用广播：停止/重启都交回主进程仓库执行。
 */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject lateinit var runtimeRepository: RuntimeRepository

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != ACTION_STOP_RUNTIME && action != NodeService.ACTION_RESTART) return
        AppDiagnosticLogger.i(context, TAG, "通知触发主进程动作：$action")
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_STOP_RUNTIME -> runtimeRepository.stopService()
                    NodeService.ACTION_RESTART -> runtimeRepository.restartServiceAndAwait()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                AppDiagnosticLogger.w(context, TAG, "通知动作失败：${error.message}", error)
            } finally {
                runCatching { pendingResult.finish() }
            }
        }
    }

    companion object {
        const val TAG = "NotifActionReceiver"

        val ACTION_STOP_RUNTIME: String
            get() = "${BuildConfig.APPLICATION_ID}.STOP_RUNTIME"
    }
}
