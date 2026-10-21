package com.example.danmuapiapp.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class ByteSizeFormatterTest {

    @Test
    fun `formats each unit with a stable decimal separator`() {
        assertEquals("0 B", formatByteSize(0L))
        assertEquals("1023 B", formatByteSize(1023L))
        assertEquals("1.0 KB", formatByteSize(1024L))
        assertEquals("1.5 KB", formatByteSize(1536L))
        assertEquals("12.3 MB", formatByteSize(12_897_484L))
        assertEquals("1.25 GB", formatByteSize(1_342_177_280L))
    }

    @Test
    fun `clamps negative progress to zero`() {
        assertEquals("0 B", formatByteSize(-42L))
    }

    @Test
    fun `integer overload matches the long overload`() {
        assertEquals(formatByteSize(2048L), formatByteSize(2048))
    }
}
