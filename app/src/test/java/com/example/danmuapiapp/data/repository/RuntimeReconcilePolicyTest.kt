package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.ServiceStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeReconcilePolicyTest {

    @Test
    fun `应用后台时应暂停周期状态校准`() {
        assertFalse(shouldRunPeriodicRuntimeReconcile(appForeground = false))
    }

    @Test
    fun `应用前台时应允许周期状态校准`() {
        assertTrue(shouldRunPeriodicRuntimeReconcile(appForeground = true))
    }

    @Test
    fun `普通模式运行中也应校准前台服务和通知`() {
        assertTrue(
            shouldRunPeriodicNormalStateReconcile(
                runMode = RunMode.Normal,
                status = ServiceStatus.Running
            )
        )
    }

    @Test
    fun `普通模式启动中和停止中仍应执行周期状态校准`() {
        assertTrue(
            shouldRunPeriodicNormalStateReconcile(
                runMode = RunMode.Normal,
                status = ServiceStatus.Starting
            )
        )
        assertTrue(
            shouldRunPeriodicNormalStateReconcile(
                runMode = RunMode.Normal,
                status = ServiceStatus.Stopping
            )
        )
    }

    @Test
    fun `非普通模式不走普通模式周期状态校准`() {
        assertFalse(
            shouldRunPeriodicNormalStateReconcile(
                runMode = RunMode.Root,
                status = ServiceStatus.Starting
            )
        )
    }

    @Test
    fun `接口可用但前台服务缺失时应重新挂接`() {
        assertTrue(
            decideNormalRunningReconcileAction(
                consecutiveUnreachableCount = 0,
                serviceRunning = false,
                processRunning = true,
                portOpen = true,
                notificationActive = false,
                canDisplayNotification = true
            ) == NormalRunningReconcileAction.RestoreForeground
        )
    }

    @Test
    fun `接口可用但通知消失时应重新发布通知`() {
        assertTrue(
            decideNormalRunningReconcileAction(
                consecutiveUnreachableCount = 0,
                serviceRunning = true,
                processRunning = true,
                portOpen = true,
                notificationActive = false,
                canDisplayNotification = true
            ) == NormalRunningReconcileAction.RestoreForeground
        )
    }

    @Test
    fun `用户选择尊重关闭时不应自动恢复手动划掉的通知`() {
        assertTrue(
            decideNormalRunningReconcileAction(
                consecutiveUnreachableCount = 0,
                serviceRunning = true,
                processRunning = true,
                portOpen = true,
                notificationActive = false,
                canDisplayNotification = true,
                notificationManuallyHidden = true
            ) == NormalRunningReconcileAction.RespectUserDismissal
        )
    }

    @Test
    fun `运行信号短暂缺失时不应立即判停`() {
        assertTrue(
            decideNormalRunningReconcileAction(
                consecutiveUnreachableCount = 1,
                serviceRunning = false,
                processRunning = false,
                portOpen = false,
                notificationActive = false,
                canDisplayNotification = true
            ) == NormalRunningReconcileAction.WaitForNextProbe
        )
    }

    @Test
    fun `运行信号连续缺失后才标记停止`() {
        assertTrue(
            decideNormalRunningReconcileAction(
                consecutiveUnreachableCount = NORMAL_RUNNING_UNREACHABLE_THRESHOLD,
                serviceRunning = false,
                processRunning = false,
                portOpen = false,
                notificationActive = false,
                canDisplayNotification = true
            ) == NormalRunningReconcileAction.MarkStopped
        )
    }
}
