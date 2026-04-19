package com.example.danmuapiapp.data.service

import org.junit.Assert.assertEquals
import org.junit.Test

class NodeRuntimeExitPolicyTest {

    @Test
    fun `受控停止时由 stop controller 收尾`() {
        val action = decideNodeRuntimeExitAction(
            stopping = true,
            exitCode = 0,
            crashThrowable = null
        )

        assertEquals(NodeRuntimeExitAction.DeferToStopController, action)
    }

    @Test
    fun `未停止但异常退出时应上报错误`() {
        val action = decideNodeRuntimeExitAction(
            stopping = false,
            exitCode = 1,
            crashThrowable = null
        )

        assertEquals(NodeRuntimeExitAction.ReportError, action)
    }

    @Test
    fun `未停止且正常退出时应上报停止`() {
        val action = decideNodeRuntimeExitAction(
            stopping = false,
            exitCode = 0,
            crashThrowable = null
        )

        assertEquals(NodeRuntimeExitAction.ReportStopped, action)
    }
}
