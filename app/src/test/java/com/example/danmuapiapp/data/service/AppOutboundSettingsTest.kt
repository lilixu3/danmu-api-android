package com.example.danmuapiapp.data.service

import org.junit.Assert.*
import org.junit.Test

class AppOutboundSettingsTest {
    @Test fun defaultsDoNotEnableAnExitOrAlterSourceSelection() {
        val settings = AppOutboundSettings.decode("{}")
        assertFalse(settings.enabled)
        assertEquals(setOf("bahamut", "tmdb"), settings.sources)
        assertEquals("auto", settings.httpVersion)
    }

    @Test fun independentConfigurationRoundTripsWithoutCoreMetadata() {
        val settings = AppOutboundSettings(true, setOf("bahamut"), "h2", "https://resolver.example/dns-query", 2500)
        assertEquals(settings, AppOutboundSettings.decode(settings.encode()))
    }

    @Test fun expandedSourcesRoundTripAndPreserveExistingChoices() {
        val expanded = AppOutboundSettings(true, setOf("bahamut", "tmdb", "dandan", "animeko"))
        assertEquals(expanded, AppOutboundSettings.decode(expanded.encode()))
        val existing = AppOutboundSettings.decode("""{"enabled":true,"sources":["bahamut","tmdb"]}""")
        assertEquals(setOf("bahamut", "tmdb"), existing.sources)
        assertTrue(existing.enabled)
        assertTrue(AppOutboundSettings(sources = emptySet()).validate().sources.isEmpty())
    }

    @Test fun unsafeOrAmbiguousResolversAndInvalidDeadlinesAreRejected() {
        for (url in listOf("http://resolver.example/dns-query", "https://user:secret@resolver.example/", "https://resolver.example/#fragment")) {
            assertThrows(IllegalArgumentException::class.java) { AppOutboundSettings(dohUrl = url).validate() }
        }
        assertThrows(IllegalArgumentException::class.java) { AppOutboundSettings(connectTimeoutMs = 0).validate() }
        assertThrows(IllegalArgumentException::class.java) { AppOutboundSettings(sources = setOf("other")).validate() }
    }

    @Test fun rootAutostartResolvesHelperFromCurrentInstallationAndQuotesConfigPath() {
        val script = RootAutoStartScriptBuilders.buildServiceSh(
            moduleId = "test", moduleDir = "/data/adb/modules/test", flagDir = "/data/adb/test",
            flagFile = "/data/adb/test/enabled", modeFile = "/data/adb/test/mode", mainClass = "RootEntry",
            outboundConfigPath = "/data/user/0/test/files/outbound/it's-config.json"
        )
        assertTrue(script.contains("DANMU_APP_OUTBOUND_HELPER=\"\$LIBDIR/libdanmu_outbound.so\""))
        assertTrue(script.contains("it's-config".replace("'", "'\"'\"'")))
    }
}
