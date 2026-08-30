package com.example.danmuapiapp.desktop.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RuntimeHealthTest {

    @Test
    fun acceptsFractionalEnvMtimeAndNormalizesToWholeMilliseconds() {
        val snapshot = StrictHealthJsonParser.parse("""
            {
              "ok": true,
              "pid": 42,
              "uptimeSec": 12,
              "envFileMtimeMs": 1787993369087.2434,
              "ports": {"main": 9321}
            }
        """.trimIndent())

        assertEquals(1787993369087L, snapshot.envFileMtimeMs)
        assertEquals(42L, snapshot.pid)
    }

    @Test
    fun acceptsIntegerExponentAndNullEnvMtime() {
        assertEquals(
            1234L,
            StrictHealthJsonParser.parse("""{"ok":true,"envFileMtimeMs":1234}""").envFileMtimeMs,
        )
        assertEquals(
            1234L,
            StrictHealthJsonParser.parse("""{"ok":true,"envFileMtimeMs":1.234e3}""").envFileMtimeMs,
        )
        assertEquals(
            null,
            StrictHealthJsonParser.parse("""{"ok":true,"envFileMtimeMs":null}""").envFileMtimeMs,
        )
    }

    @Test
    fun rejectsInvalidTimestampTypesAndNegativeValues() {
        assertThrows(Throwable::class.java) {
            StrictHealthJsonParser.parse("""{"ok":true,"envFileMtimeMs":"1234"}""")
        }
        assertThrows(Throwable::class.java) {
            StrictHealthJsonParser.parse("""{"ok":true,"envFileMtimeMs":-1}""")
        }
        assertThrows(Throwable::class.java) {
            StrictHealthJsonParser.parse("""{"ok":true,"envFileMtimeMs":9223372036854775808}""")
        }
    }

    @Test
    fun keepsOtherHealthCountersIntegerOnly() {
        assertThrows(Throwable::class.java) {
            StrictHealthJsonParser.parse("""{"ok":true,"requestCount":1.5}""")
        }
    }
}
