package com.example.danmuapiapp.desktop.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DesktopSettingsTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun settingsRoundTripPreservesUtf8Path() {
        val file = temp.newFile("settings.properties")
        val path = "C:\\数据\\弹幕API"
        DesktopSettings(file).apply {
            setRuntimeRoot(path)
            setCloseAction("tray")
        }
        val loaded = DesktopSettings(file)
        assertEquals(path, loaded.runtimeRootOverride)
        assertEquals("tray", loaded.closeAction)
    }

    @Test
    fun adminTokenAndGithubRouteConfirmationRoundTripWithoutExposingSecretInSnapshot() {
        val file = temp.newFile("secure-settings.properties")
        DesktopSettings(file).apply {
            setAdminTokenOverride("secret-admin-token")
            setGithubProxy("gh_proxy_org")
            setGithubProxyConfirmed(true)
        }
        val loaded = DesktopSettings(file)
        assertEquals("secret-admin-token", loaded.adminTokenOverride)
        assertEquals("gh_proxy_org", loaded.githubProxyId)
        assertTrue(loaded.githubProxyConfirmed)
        assertFalse(file.readText(Charsets.UTF_8).contains("githubTokenConfigured"))
    }

    @Test
    fun clearingAdminTokenRemovesSecretFromPersistedFile() {
        val file = temp.newFile("clear-admin-settings.properties")
        DesktopSettings(file).apply {
            setAdminTokenOverride("secret-admin-token")
            setAdminTokenOverride("")
        }
        val loaded = DesktopSettings(file)
        assertEquals("", loaded.adminTokenOverride)
        assertFalse(file.readText(Charsets.UTF_8).contains("secret-admin-token"))
    }

    @Test
    fun ipv6SettingRoundTrips() {
        val file = temp.newFile("ipv6-settings.properties")
        DesktopSettings(file).apply {
            setIpv6Enabled(true)
        }
        assertTrue(DesktopSettings(file).ipv6Enabled)

        DesktopSettings(file).setIpv6Enabled(false)
        assertFalse(DesktopSettings(file).ipv6Enabled)
    }

    @Test
    fun malformedSettingsAreReported() {
        val file = temp.newFile("broken.properties")
        file.writeText("bad=\\uZZZZ\n", Charsets.UTF_8)
        val error = assertThrows(IllegalStateException::class.java) { DesktopSettings(file) }
        assertTrue(error.message.orEmpty().contains("无法读取桌面端设置文件"))
    }
}
