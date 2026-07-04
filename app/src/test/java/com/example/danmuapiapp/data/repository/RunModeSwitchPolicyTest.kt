package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.RunMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunModeSwitchPolicyTest {

    @Test
    fun `普通模式不应同步 Root 开机模块模式标记`() {
        assertFalse(
            shouldSyncRootAutoStartModeFlag(
                targetMode = RunMode.Normal,
                rootAutoStartEnabled = true
            )
        )
    }

    @Test
    fun `Root 模式且模块已启用时才同步 Root 开机模块模式标记`() {
        assertTrue(
            shouldSyncRootAutoStartModeFlag(
                targetMode = RunMode.Root,
                rootAutoStartEnabled = true
            )
        )
        assertFalse(
            shouldSyncRootAutoStartModeFlag(
                targetMode = RunMode.Root,
                rootAutoStartEnabled = false
            )
        )
    }

    @Test
    fun `普通模式应关闭持久 su 会话`() {
        assertTrue(shouldCloseRootShellSessionForMode(RunMode.Normal))
    }

    @Test
    fun `Root 模式保留持久 su 会话给显式 Root 操作复用`() {
        assertFalse(shouldCloseRootShellSessionForMode(RunMode.Root))
    }
}
