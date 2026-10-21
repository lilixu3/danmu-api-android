package com.example.danmuapiapp.ui.screen.home.support

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeDisplayFormattersTest {
    @Test
    fun formatsUptimeWithHours() {
        assertEquals("1:02:03", formatHomeUptime(3723L))
    }

    @Test
    fun formatsUptimeWithoutHours() {
        assertEquals("02:03", formatHomeUptime(123L))
        assertEquals("00:00", formatHomeUptime(0L))
    }

    @Test
    fun formatsUptimeUsingDefaultLocaleSeparator() {
        assertEquals(
            String.format(Locale.getDefault(), "%d:%02d:%02d", 2, 3, 4),
            formatHomeUptime(7384L)
        )
    }
}
