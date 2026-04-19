package com.example.danmuapiapp.ui.screen.core

import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.RuntimeState
import com.example.danmuapiapp.domain.model.ServiceStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreApplyPolicyTest {

    @Test
    fun `运行中的当前核心变更前应先停服务并在成功后重启`() {
        val plan = decideCoreApplyPlan(
            state = RuntimeState(status = ServiceStatus.Running, variant = ApiVariant.Stable),
            targetVariant = ApiVariant.Stable
        )

        assertTrue(plan.shouldStopServiceBeforeApply)
        assertTrue(plan.shouldStartServiceAfterApply)
    }

    @Test
    fun `变更非当前运行核心时不应打断现有服务`() {
        val plan = decideCoreApplyPlan(
            state = RuntimeState(status = ServiceStatus.Running, variant = ApiVariant.Dev),
            targetVariant = ApiVariant.Stable
        )

        assertFalse(plan.shouldStopServiceBeforeApply)
        assertFalse(plan.shouldStartServiceAfterApply)
    }

    @Test
    fun `服务未运行时变更当前核心不应自动启动`() {
        val plan = decideCoreApplyPlan(
            state = RuntimeState(status = ServiceStatus.Stopped, variant = ApiVariant.Stable),
            targetVariant = ApiVariant.Stable
        )

        assertFalse(plan.shouldStopServiceBeforeApply)
        assertFalse(plan.shouldStartServiceAfterApply)
    }
}
