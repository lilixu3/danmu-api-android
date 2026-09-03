package com.example.danmuapiapp.data.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationDisplayPrefsTest {

    @Test
    fun `mirror value only accepts one and zero`() {
        assertTrue(NotificationDisplayPrefs.parseMirror("1") == true)
        assertTrue(NotificationDisplayPrefs.parseMirror(" 0 ") == false)
        assertNull(NotificationDisplayPrefs.parseMirror("true"))
        assertNull(NotificationDisplayPrefs.parseMirror(""))
        assertNull(NotificationDisplayPrefs.parseMirror(null))
    }

    @Test
    fun `service channel recognizes active and legacy ids`() {
        assertTrue(ServiceNotificationChannels.isServiceChannelId(ServiceNotificationChannels.CHANNEL_ID))
        assertFalse(ServiceNotificationChannels.isServiceChannelId("unrelated_channel"))
    }
}
