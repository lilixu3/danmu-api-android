package com.example.danmuapiapp.desktop.app

import com.example.danmuapiapp.desktop.app.settings.DesktopSettingsSnapshot
import com.example.danmuapiapp.desktop.app.settings.SettingsCategoryId
import com.example.danmuapiapp.desktop.app.settings.SettingsCategoryRegistry
import com.example.danmuapiapp.desktop.app.settings.SettingsDraft
import com.example.danmuapiapp.desktop.app.settings.SettingsDraftEdit
import com.example.danmuapiapp.desktop.app.settings.SettingsDraftReducer
import com.example.danmuapiapp.desktop.app.settings.SettingsDraftField
import com.example.danmuapiapp.desktop.app.settings.SettingsValidation
import com.example.danmuapiapp.desktop.core.CoreSourceMetadata
import com.example.danmuapiapp.desktop.core.DesktopCoreInfo
import com.example.danmuapiapp.desktop.core.DesktopCoreVariant
import com.example.danmuapiapp.desktop.runtime.ServicePhase
import com.example.danmuapiapp.desktop.runtime.ServiceUiState
import com.example.danmuapiapp.desktop.logs.displayLogTimestamp
import com.example.danmuapiapp.desktop.logs.updateLogViewport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopUiModelTest {

    @Test
    fun productionNavigationOnlyExposesImplementedPages() {
        assertEquals(
            listOf(DesktopPage.Overview, DesktopPage.Core, DesktopPage.Configuration, DesktopPage.Logs, DesktopPage.Settings, DesktopPage.About),
            DesktopPageRegistry.visible.map { it.page },
        )
        val logs = DesktopPageRegistry.visible.first { it.page == DesktopPage.Logs }
        assertEquals("查看、筛选、复制和导出运行日志", logs.description)
        assertEquals(DesktopIcons.Activity, logs.icon)
        assertTrue(DesktopPage.Logs != DesktopPage.Activity)
    }

    @Test
    fun settingsCategoriesKeepProductOrder() {
        assertEquals(
            listOf(
                SettingsCategoryId.GeneralStartup,
                SettingsCategoryId.Service,
                SettingsCategoryId.PathsRuntime,
                SettingsCategoryId.NetworkDownload,
                SettingsCategoryId.SecurityAdmin,
                SettingsCategoryId.Diagnostics,
                SettingsCategoryId.UpdatesAbout,
            ),
            SettingsCategoryRegistry.ids,
        )
    }

    @Test
    fun invalidDraftValuesRemainVisibleAsValidationErrors() {
        val draft = SettingsDraft(
            portOverride = "70000",
            listenHostOverride = "not a host",
            variantOverride = "preview",
            runtimeRootOverride = "relative\\runtime",
        )
        val validation = SettingsValidation.validate(draft)
        assertFalse(validation.isValid)
        assertNotNull(validation.errorFor(SettingsDraftField.PortOverride))
        assertNotNull(validation.errorFor(SettingsDraftField.ListenHostOverride))
        assertNotNull(validation.errorFor(SettingsDraftField.VariantOverride))
        assertNotNull(validation.errorFor(SettingsDraftField.RuntimeRootOverride))
    }

    @Test
    fun draftReducerTracksEditAndCanReset() {
        val baseline = SettingsDraft.from(
            DesktopSettingsSnapshot(portOverride = 9321, listenHostOverride = "0.0.0.0"),
        )
        val edited = SettingsDraftReducer.reduce(
            baseline,
            SettingsDraftEdit.PortOverride("19421"),
        )
        assertTrue(SettingsDraftReducer.dirty(edited, baseline))
        assertEquals("19421", edited.portOverride)
        assertEquals(baseline, SettingsDraftReducer.reset(edited, baseline))
    }

    @Test
    fun logViewportPreservesHistoryAndCountsNewEntries() {
        assertEquals(updateLogViewport(10, 15, false).pendingNewCount, 5)
        assertFalse(updateLogViewport(10, 15, false).isAtLatest)
        assertEquals(updateLogViewport(10, 15, true).pendingNewCount, 0)
        assertTrue(updateLogViewport(10, 15, true).isAtLatest)
        assertEquals("12:34:56", displayLogTimestamp("2026-08-30T12:34:56.123Z"))
        assertEquals("--:--:--", displayLogTimestamp(null))
    }

    @Test
    fun sidebarCoreVersionPrefersVersionThenShortCommit() {
        val versioned = DesktopCoreInfo(
            variant = DesktopCoreVariant.Stable,
            installed = true,
            valid = true,
            version = "1.0.5.81",
            source = CoreSourceMetadata("owner/repo", "main", "abcdef1234567890", "1.0.5.81", 1L),
            diagnostic = null,
        )
        assertEquals("1.0.5.81", displaySidebarCoreVersion(versioned))

        val shaOnly = versioned.copy(version = null)
        assertEquals("abcdef123456", displaySidebarCoreVersion(shaOnly))
        assertEquals("未安装", displaySidebarCoreVersion(versioned.copy(installed = false, valid = false)))
        assertEquals("未知", displaySidebarCoreVersion(versioned.copy(installed = true, valid = false)))
    }

    @Test
    fun trayMenuFollowsServiceState() {
        val stopped = TrayMenuModel.groups(ServiceUiState(phase = ServicePhase.Stopped))
            .flatMap { it.items }
        assertTrue(stopped.first { it.action == TrayMenuAction.Start }.enabled)
        assertFalse(stopped.first { it.action == TrayMenuAction.Stop }.enabled)

        val running = TrayMenuModel.groups(
            ServiceUiState(phase = ServicePhase.Running, port = 9321),
        ).flatMap { it.items }
        assertFalse(running.first { it.action == TrayMenuAction.Start }.enabled)
        assertTrue(running.first { it.action == TrayMenuAction.Stop }.enabled)
        assertTrue(running.any { it.action == TrayMenuAction.OpenCoreConfig && it.label == "打开核心配置" })
        assertEquals("运行中 · 127.0.0.1:9321", TrayMenuModel.statusText(ServiceUiState(phase = ServicePhase.Running, port = 9321)))
    }
}
