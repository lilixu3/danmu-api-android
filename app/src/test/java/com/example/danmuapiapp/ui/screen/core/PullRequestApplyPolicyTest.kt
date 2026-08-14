package com.example.danmuapiapp.ui.screen.core

import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.RuntimeState
import com.example.danmuapiapp.domain.model.ServiceStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PullRequestApplyPolicyTest {
    @Test
    fun `running target core is stopped only after preparation and restarted after apply`() {
        val plan = decidePullRequestApplyPlan(
            RuntimeState(status = ServiceStatus.Running, variant = ApiVariant.Stable),
            targetVariant = ApiVariant.Stable,
            activateAfterInstall = false
        )

        assertTrue(plan.shouldRequestStop)
        assertTrue(plan.shouldAwaitStopped)
        assertTrue(plan.shouldStartTargetAfterApply)
    }

    @Test
    fun `installing another core without activation does not interrupt running service`() {
        val plan = decidePullRequestApplyPlan(
            RuntimeState(status = ServiceStatus.Running, variant = ApiVariant.Dev),
            targetVariant = ApiVariant.Stable,
            activateAfterInstall = false
        )

        assertFalse(plan.shouldRequestStop)
        assertFalse(plan.shouldAwaitStopped)
        assertFalse(plan.shouldStartTargetAfterApply)
    }

    @Test
    fun `install and switch stops the old running core before applying`() {
        val plan = decidePullRequestApplyPlan(
            RuntimeState(status = ServiceStatus.Running, variant = ApiVariant.Dev),
            targetVariant = ApiVariant.Stable,
            activateAfterInstall = true
        )

        assertTrue(plan.shouldRequestStop)
        assertTrue(plan.shouldStartTargetAfterApply)
    }

    @Test
    fun `stopped service remains stopped after install and switch`() {
        val plan = decidePullRequestApplyPlan(
            RuntimeState(status = ServiceStatus.Stopped, variant = ApiVariant.Dev),
            targetVariant = ApiVariant.Stable,
            activateAfterInstall = true
        )

        assertFalse(plan.shouldRequestStop)
        assertFalse(plan.shouldAwaitStopped)
        assertFalse(plan.shouldStartTargetAfterApply)
    }
}
