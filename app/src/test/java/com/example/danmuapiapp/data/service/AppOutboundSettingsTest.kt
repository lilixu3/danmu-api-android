package com.example.danmuapiapp.data.service

import org.junit.Assert.*
import org.junit.Test

class AppOutboundSettingsTest {
    @Test fun upgradeFromOldEnhancedSettingsKeepsEverySelectedSourceAndConnectionParameter() {
        // Version 200 has no proxy object; opening the new page must not write defaults.
        val legacy = AppOutboundSettings.decode("""{"enabled":true,"sources":["bahamut","douban","animeko"],"httpVersion":"h3","dohUrl":"https://dns.example/dns-query","connectTimeoutMs":4500}""")
        val catalog = ProxySourceCatalog(AppOutboundSettings.supportedSources, setOf("bahamut", "douban"))
        for (source in listOf("bahamut", "douban", "animeko")) assertEquals("enhanced", legacy.sourceRoute(source, catalog))
        assertEquals("inherit", legacy.sourceRoute("tmdb", catalog))
        val changed = legacy.withSourceRoute("douban", "inherit")
        assertEquals("enhanced", changed.sourceRoute("bahamut", catalog))
        assertEquals("enhanced", changed.sourceRoute("animeko", catalog))
        assertEquals("h3", changed.httpVersion)
        assertEquals(4500, changed.connectTimeoutMs)
        assertEquals("https://dns.example/dns-query", changed.dohUrl)
        assertEquals("inherit", legacy.copy(enabled = false).sourceRoute("bahamut", catalog))
    }

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
        val expanded = AppOutboundSettings(true, setOf("bahamut", "tmdb", "dandan", "animeko", "douban"))
        assertEquals(expanded, AppOutboundSettings.decode(expanded.encode()))
        val existing = AppOutboundSettings.decode("""{"enabled":true,"sources":["bahamut","tmdb"]}""")
        assertEquals(setOf("bahamut", "tmdb"), existing.sources)
        assertTrue(existing.enabled)
        assertTrue(AppOutboundSettings(sources = emptySet()).validate().sources.isEmpty())
        assertFalse("aiyifan" in AppOutboundSettings.supportedSources)
        assertThrows(IllegalArgumentException::class.java) { AppOutboundSettings(sources = setOf("aiyifan")).validate() }
    }

    @Test fun unsafeOrAmbiguousResolversAndInvalidDeadlinesAreRejected() {
        for (url in listOf("http://resolver.example/dns-query", "https://user:secret@resolver.example/", "https://resolver.example/#fragment")) {
            assertThrows(IllegalArgumentException::class.java) { AppOutboundSettings(dohUrl = url).validate() }
        }
        assertThrows(IllegalArgumentException::class.java) { AppOutboundSettings(connectTimeoutMs = 0).validate() }
        assertThrows(IllegalArgumentException::class.java) { AppOutboundSettings(sources = setOf("other")).validate() }
    }

    @Test fun rootAutostartUsesNativeBackendAndQuotesConfigPath() {
        val script = RootAutoStartScriptBuilders.buildServiceSh(
            moduleId = "test", moduleDir = "/data/adb/modules/test", flagDir = "/data/adb/test",
            flagFile = "/data/adb/test/enabled", modeFile = "/data/adb/test/mode", mainClass = "RootEntry",
            outboundConfigPath = "/data/user/0/test/files/outbound/it's-config.json"
        )
        assertFalse(script.contains("DANMU_APP_OUTBOUND_HELPER"))
        assertTrue(script.contains("RootFrpcLogEntry"))
        assertTrue(script.contains("it's-config".replace("'", "'\"'\"'")))
    }

    @Test fun sourceRouteChoiceIsExclusiveAcrossCoreEnhancedAndProxy() {
        val node = AppProxyNode(id = "hk", name = "香港", host = "node.test")
        val original = AppOutboundSettings(enabled = true, sources = setOf("douban", "bahamut"),
            proxy = AppProxySettings(nodes = listOf(node)))
        val catalog = ProxySourceCatalog(listOf("douban", "bahamut"), setOf("douban"))
        val proxied = original.withSourceRoute("douban", "node:hk")
        assertEquals("node:hk", proxied.sourceRoute("douban", catalog))
        assertFalse("douban" in proxied.sources)
        assertEquals("enhanced", proxied.sourceRoute("bahamut", catalog))
        val enhanced = proxied.withSourceRoute("douban", "enhanced")
        assertTrue("douban" in enhanced.sources)
        assertEquals("enhanced", enhanced.proxy.rules["douban"])
        val inherited = enhanced.withSourceRoute("douban", "inherit")
        assertFalse("douban" in inherited.sources)
        assertEquals("inherit", inherited.sourceRoute("douban", catalog))
        assertEquals("enhanced", inherited.sourceRoute("bahamut", catalog))
        assertEquals(inherited, AppOutboundSettings.decode(inherited.encode()))
    }

    @Test fun selectingOneSourceDoesNotReactivateDisabledRulesOrEnhancedSources() {
        val original = AppOutboundSettings(enabled = false, sources = setOf("bahamut", "tmdb"),
            proxy = AppProxySettings(enabled = false, defaultRoute = "node:old", rules = mapOf("aiyifan" to "node:old")))
        val selected = original.withSourceRoute("douban", "enhanced")
        val catalog = ProxySourceCatalog(listOf("douban", "bahamut", "tmdb", "aiyifan"), emptySet())
        assertEquals(setOf("douban"), selected.sources)
        assertEquals("inherit", selected.proxy.defaultRoute)
        for (source in listOf("bahamut", "tmdb", "aiyifan")) assertEquals("inherit", selected.sourceRoute(source, catalog))
        val disabled = original.copy(proxy = original.proxy.copy(enabled = true, defaultRoute = "inherit", rules = mapOf("bahamut" to "enhanced")))
        assertEquals("inherit", disabled.withSourceRoute("douban", "enhanced").sourceRoute("bahamut", catalog))
    }

    @Test fun explicitSourceRouteSupersedesLegacyAndDefaultButDoesNotChangeOtherSources() {
        val catalog = ProxySourceCatalog(listOf("douban", "bahamut"), setOf("douban"), setOf("douban", "bahamut"))
        val original = AppOutboundSettings(enabled = true, sources = setOf("douban", "bahamut"))
        assertEquals("legacy", original.sourceRoute("douban", catalog))
        val selected = original.withSourceRoute("douban", "enhanced")
        assertEquals("enhanced", selected.sourceRoute("douban", catalog))
        assertEquals("legacy", selected.sourceRoute("bahamut", catalog))
        assertThrows(IllegalArgumentException::class.java) { selected.withSourceRoute("aiyifan", "enhanced") }
        assertThrows(IllegalArgumentException::class.java) { selected.withSourceRoute("douban", "node:missing") }
    }
}
