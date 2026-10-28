package com.example.danmuapiapp.data.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ShellUtilsTest {

    @Test
    fun `quotes paths with spaces`() {
        assertEquals("'/data/app/my dir/app'", ShellUtils.shellQuote("/data/app/my dir/app"))
    }

    @Test
    fun `escapes embedded single quotes`() {
        assertEquals("'it'\"'\"'s safe'", ShellUtils.shellQuote("it's safe"))
    }

    @Test
    fun `quotes empty input`() {
        assertEquals("''", ShellUtils.shellQuote(""))
    }
}
