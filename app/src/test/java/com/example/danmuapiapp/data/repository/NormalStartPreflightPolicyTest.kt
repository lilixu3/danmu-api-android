package com.example.danmuapiapp.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class NormalStartPreflightPolicyTest {

    @Test
    fun `普通模式端口未被占用时应允许继续启动`() {
        val decision = decideNormalStartPreflight(
            portOpen = false,
            ownership = RuntimeOwnership.Foreign
        )

        assertEquals(NormalStartPreflightDecision.Proceed, decision)
    }

    @Test
    fun `普通模式端口被本应用实例占用时应允许继续走现有启动链路`() {
        val decision = decideNormalStartPreflight(
            portOpen = true,
            ownership = RuntimeOwnership.OwnedLegacy
        )

        assertEquals(NormalStartPreflightDecision.Proceed, decision)
    }

    @Test
    fun `普通模式端口被外部实例占用时应前置拦截`() {
        val decision = decideNormalStartPreflight(
            portOpen = true,
            ownership = RuntimeOwnership.Foreign
        )

        assertEquals(NormalStartPreflightDecision.ForeignInstanceOccupiesPort, decision)
    }

    @Test
    fun `普通模式前置拦截文案应直接提示已有其他实例运行`() {
        assertEquals(
            "端口 9321 已有其他实例在运行，请先停止外部进程后再启动",
            buildNormalForeignPortOccupiedMessage(9321)
        )
    }
}
