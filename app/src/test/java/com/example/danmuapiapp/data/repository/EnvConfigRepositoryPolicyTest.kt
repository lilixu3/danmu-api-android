package com.example.danmuapiapp.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class EnvConfigRepositoryPolicyTest {

    @Test
    fun `raw save normalizes CRLF and appends trailing newline`() {
        assertEquals("A=1\nB=2\n", normalizeRawContentForSave("A=1\r\nB=2"))
    }

    @Test
    fun `raw save preserves existing trailing newline`() {
        assertEquals("A=1\n", normalizeRawContentForSave("A=1\n"))
    }
}
