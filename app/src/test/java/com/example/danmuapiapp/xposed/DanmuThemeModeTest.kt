package com.example.danmuapiapp.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DanmuThemeModeTest {

    @Test
    fun `持久化主题值应稳定并为未知值提供跟随宿主兜底`() {
        assertEquals(DanmuThemeMode.FOLLOW_HOST, DanmuThemeMode.fromPersistedValue(0))
        assertEquals(DanmuThemeMode.LIGHT, DanmuThemeMode.fromPersistedValue(1))
        assertEquals(DanmuThemeMode.DARK, DanmuThemeMode.fromPersistedValue(2))
        assertEquals(DanmuThemeMode.FOLLOW_HOST, DanmuThemeMode.fromPersistedValue(99))
    }

    @Test
    fun `显式主题不依赖宿主上下文且旧布尔值可迁移`() {
        assertFalse(DanmuThemeMode.LIGHT.resolveDark(null))
        assertTrue(DanmuThemeMode.DARK.resolveDark(null))
        assertEquals(DanmuThemeMode.LIGHT, DanmuThemeMode.fromLegacyDarkTheme(false))
        assertEquals(DanmuThemeMode.DARK, DanmuThemeMode.fromLegacyDarkTheme(true))
    }
}
