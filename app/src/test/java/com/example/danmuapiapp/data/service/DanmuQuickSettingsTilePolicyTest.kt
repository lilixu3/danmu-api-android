package com.example.danmuapiapp.data.service

import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.ServiceStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class DanmuQuickSettingsTilePolicyTest {

    @Test
    fun stoppedNormalModeShowsInactiveStartState() {
        val presentation = DanmuQuickSettingsTilePolicy.presentation(
            status = ServiceStatus.Stopped,
            runMode = RunMode.Normal,
            port = 9321
        )

        assertEquals(DanmuQuickSettingsTilePolicy.VisualState.Inactive, presentation.visualState)
        assertEquals("弹幕API", presentation.label)
        assertEquals("未启动", presentation.subtitle)
        assertEquals(
            DanmuQuickSettingsTilePolicy.ClickAction.Start,
            DanmuQuickSettingsTilePolicy.clickAction(ServiceStatus.Stopped)
        )
    }

    @Test
    fun runningRootModeShowsActiveStopState() {
        val presentation = DanmuQuickSettingsTilePolicy.presentation(
            status = ServiceStatus.Running,
            runMode = RunMode.Root,
            port = 80
        )

        assertEquals(DanmuQuickSettingsTilePolicy.VisualState.Active, presentation.visualState)
        assertEquals("弹幕API", presentation.label)
        assertEquals("运行中", presentation.subtitle)
        assertEquals(
            DanmuQuickSettingsTilePolicy.ClickAction.Stop,
            DanmuQuickSettingsTilePolicy.clickAction(ServiceStatus.Running)
        )
    }

    @Test
    fun transitionalStatesAreUnavailableAndIgnoreRepeatedClicks() {
        val starting = DanmuQuickSettingsTilePolicy.presentation(
            status = ServiceStatus.Starting,
            runMode = RunMode.Root,
            port = 9321
        )
        val stopping = DanmuQuickSettingsTilePolicy.presentation(
            status = ServiceStatus.Stopping,
            runMode = RunMode.Normal,
            port = 9321
        )

        assertEquals(DanmuQuickSettingsTilePolicy.VisualState.Unavailable, starting.visualState)
        assertEquals("弹幕API", starting.label)
        assertEquals("启动中", starting.subtitle)
        assertEquals(DanmuQuickSettingsTilePolicy.VisualState.Unavailable, stopping.visualState)
        assertEquals("弹幕API", stopping.label)
        assertEquals("停止中", stopping.subtitle)
        assertEquals(
            DanmuQuickSettingsTilePolicy.ClickAction.Ignore,
            DanmuQuickSettingsTilePolicy.clickAction(ServiceStatus.Starting)
        )
        assertEquals(
            DanmuQuickSettingsTilePolicy.ClickAction.Ignore,
            DanmuQuickSettingsTilePolicy.clickAction(ServiceStatus.Stopping)
        )
    }

    @Test
    fun errorStateAllowsRetry() {
        val presentation = DanmuQuickSettingsTilePolicy.presentation(
            status = ServiceStatus.Error,
            runMode = RunMode.Normal,
            port = 9321
        )

        assertEquals(DanmuQuickSettingsTilePolicy.VisualState.Inactive, presentation.visualState)
        assertEquals("弹幕API", presentation.label)
        assertEquals("失败，点按重试", presentation.subtitle)
        assertEquals(
            DanmuQuickSettingsTilePolicy.ClickAction.Start,
            DanmuQuickSettingsTilePolicy.clickAction(ServiceStatus.Error)
        )
    }
}
