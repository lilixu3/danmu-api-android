package com.example.danmuapiapp.ui.compat

import com.example.danmuapiapp.domain.model.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CompatManagementTest {
    @Test fun schemaControlsCountAndPreservesFalseZeroAndEmptyValues() {
        val root = JSONObject("""{"envVarConfig":{"A":{"type":"boolean"},"B":{"type":"number","min":0},"C":{"type":"text"}},"originalEnvVars":{"A":false,"B":0,"C":""}}""")
        val entries = parseCompatConfig(root, mapOf("C" to ""), emptyMap()).associateBy { it.definition.key }
        assertEquals(3, entries.size)
        assertEquals("false", entries.getValue("A").value)
        assertEquals("0", entries.getValue("B").value)
        assertEquals("", entries.getValue("C").value)
        assertTrue(entries.getValue("C").configured)
        assertFalse(entries.getValue("A").configured)
    }
    @Test fun dynamicallyAcceptsNewKeysAndMetadata() {
        val root = JSONObject("""{"envVarConfig":{"NEW_SWITCH":{"type":"select","category":"source","description":"New feature","options":["off","auto"]},"TMDB_API_KEY":{"type":"text"}},"envs":{"NEW_SWITCH":{"value":"off"}}}""")
        val entries = parseCompatConfig(root, emptyMap(), emptyMap()).associateBy { it.definition.key }
        assertEquals("off", entries.getValue("NEW_SWITCH").value)
        assertEquals(listOf("off", "auto"), entries.getValue("NEW_SWITCH").definition.options)
        assertTrue(entries.getValue("TMDB_API_KEY").definition.sensitive)
    }
    @Test fun fallsBackToExplicitAndDefaultsWithoutLosingUnsetValues() {
        val root = JSONObject("""{"envVarConfig":{"A":{},"B":{},"C":{}}}""")
        val entries = parseCompatConfig(root, mapOf("A" to "saved"), mapOf("B" to "default")).associateBy { it.definition.key }
        assertEquals("saved", entries.getValue("A").value)
        assertEquals("default", entries.getValue("B").value)
        assertFalse(entries.getValue("B").configured)
        assertEquals("", entries.getValue("C").value)
    }
    @Test fun rejectsInvalidNumbersAndSelectOptions() {
        val number = EnvVarDef("PORT", "system", EnvType.NUMBER, "", min = 0, max = 10)
        assertNotNull(validateCompatConfigValue(number, "NaN"))
        assertNotNull(validateCompatConfigValue(number, "11"))
        assertNotNull(validateCompatConfigValue(number, "-1"))
        assertNull(validateCompatConfigValue(number, "0"))
        val select = number.copy(type = EnvType.SELECT, options = listOf("h2", "h3"))
        assertNotNull(validateCompatConfigValue(select, "h1"))
        assertNull(validateCompatConfigValue(select, "h3"))
    }
    @Test fun combinesLevelSourceAndCaseInsensitiveSearch() {
        val logs = listOf(LogEntry(level = LogLevel.Error, message = "ECH Failed", source = AppLogSource.Core, category = "bahamut"),
            LogEntry(level = LogLevel.Info, message = "ok", source = AppLogSource.Core, category = "tmdb"))
        assertEquals(listOf(logs[0]), filterCompatLogs(logs, "Error", "bahamut", "ech"))
        assertTrue(filterCompatLogs(logs, "Error", "tmdb", "").isEmpty())
    }
    @Test fun browserLinksUseCorrectAuthAndIpv6AndNeverLocalhostOnAnotherDevice() {
        val runtime = RuntimeState(status = ServiceStatus.Running, token = "user", lanUrl = "http://192.168.1.2:9321/user?old=value")
        assertEquals("http://192.168.1.2:9321/admin?app_section=env", compatWebUrl(runtime, "env", "admin"))
        assertEquals("http://192.168.1.2:9321/user?app_section=logs", compatWebUrl(runtime, "logs"))
        assertEquals("", compatWebUrl(runtime, "env"))
        assertEquals("", compatWebUrl(runtime.copy(status = ServiceStatus.Stopped), "logs"))
        assertEquals("http://[fd00::1]:9321/admin?app_section=env",
            compatWebUrl(runtime.copy(lanUrl = "", lanIpv6Url = "http://[fd00::1]:9321/user"), "env", "admin"))
    }
}
