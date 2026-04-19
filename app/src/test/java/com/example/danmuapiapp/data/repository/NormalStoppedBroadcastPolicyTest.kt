package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.ServiceStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class NormalStoppedBroadcastPolicyTest {

    @Test
    fun `重启停机中的 STOPPED 广播应作为停机完成信号`() {
        val action = decideNormalStoppedBroadcastAction(
            runMode = RunMode.Normal,
            status = ServiceStatus.Stopping,
            pendingNormalRestart = true,
            normalStartIssuedAtMs = 0L
        )

        assertEquals(NormalStoppedBroadcastAction.CompleteRestartWait, action)
    }

    @Test
    fun `启动阶段的旧 STOPPED 广播应忽略`() {
        val action = decideNormalStoppedBroadcastAction(
            runMode = RunMode.Normal,
            status = ServiceStatus.Starting,
            pendingNormalRestart = false,
            normalStartIssuedAtMs = 123L
        )

        assertEquals(NormalStoppedBroadcastAction.IgnoreAsStale, action)
    }

    @Test
    fun `普通停止场景的 STOPPED 广播应落为已停止`() {
        val action = decideNormalStoppedBroadcastAction(
            runMode = RunMode.Normal,
            status = ServiceStatus.Stopping,
            pendingNormalRestart = false,
            normalStartIssuedAtMs = 0L
        )

        assertEquals(NormalStoppedBroadcastAction.MarkStopped, action)
    }
}
