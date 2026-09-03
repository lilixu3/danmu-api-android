package com.example.danmuapiapp.data.service

import com.example.danmuapiapp.domain.model.RunMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuntimeModePrefsTest {

    @Test
    fun `run mode mirror accepts known mode keys`() {
        assertEquals(RunMode.Normal, RuntimeModePrefs.parseModeMirror("normal"))
        assertEquals(RunMode.Root, RuntimeModePrefs.parseModeMirror(" ROOT "))
        assertNull(RuntimeModePrefs.parseModeMirror("unknown"))
        assertNull(RuntimeModePrefs.parseModeMirror(null))
    }
}
