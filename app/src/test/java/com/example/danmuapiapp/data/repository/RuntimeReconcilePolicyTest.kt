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
    fun `普通模式运行中不应执行周期状态校准判停`() {
        assertFalse(
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
}
