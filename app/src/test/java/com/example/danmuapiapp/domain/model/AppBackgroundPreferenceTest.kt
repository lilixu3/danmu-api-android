package com.example.danmuapiapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBackgroundPreferenceTest {

    @Test
    fun defaultBackground_isSolid() {
        val preference = AppBackgroundPreference()

        assertEquals(AppBackgroundMode.Solid, preference.mode)
        assertEquals("https://www.loliapi.com/acg/pe", preference.randomImageUrl)
        assertNull(preference.resolveImageData(foregroundKey = 1L))
    }

    @Test
    fun onlineBackground_keepsStableUrl() {
        val preference = AppBackgroundPreference(
            mode = AppBackgroundMode.OnlineImage,
            onlineImageUrl = "https://example.com/background.jpg"
        )

        assertEquals(
            "https://example.com/background.jpg",
            preference.resolveImageData(foregroundKey = 12L)
        )
    }

    @Test
    fun randomBackground_addsForegroundCacheKeyBeforeFragment() {
        val preference = AppBackgroundPreference(
            mode = AppBackgroundMode.RandomOnlineImage,
            randomImageUrl = "https://picsum.photos/1080/1920?blur=1#preview"
        )

        assertEquals(
            "https://picsum.photos/1080/1920?blur=1&random=42#preview",
            preference.resolveImageData(foregroundKey = 42L)
        )
    }

    @Test
    fun randomBackground_replacesExistingRandomKey() {
        val preference = AppBackgroundPreference(
            mode = AppBackgroundMode.RandomOnlineImage,
            randomImageUrl = "https://picsum.photos/1080/1920?random=old"
        )

        assertEquals(
            "https://picsum.photos/1080/1920?random=9",
            preference.resolveImageData(foregroundKey = 9L)
        )
    }

    @Test
    fun randomBackground_supportsPlaceholder() {
        val preference = AppBackgroundPreference(
            mode = AppBackgroundMode.RandomOnlineImage,
            randomImageUrl = "https://example.com/image/{random}.jpg"
        )

        assertEquals(
            "https://example.com/image/7.jpg",
            preference.resolveImageData(foregroundKey = 7L)
        )
    }

    @Test
    fun urlValidation_acceptsHttpSchemesAndRejectsMalformedValues() {
        assertTrue(isValidBackgroundImageUrl("https://picsum.photos/1080/1920"))
        assertTrue(isValidBackgroundImageUrl("http://example.com/image/{random}.jpg"))
        assertFalse(isValidBackgroundImageUrl("content://media/image/1"))
        assertFalse(isValidBackgroundImageUrl("https:image.jpg"))
        assertFalse(isValidBackgroundImageUrl("not a url"))
    }

    @Test
    fun refreshPolicy_defaultsToForegroundAndRestoresStoredIntervals() {
        val preference = AppBackgroundPreference()

        assertEquals(AppBackgroundRefreshPolicy.OnForeground, preference.randomRefreshPolicy)
        assertNull(preference.randomRefreshPolicy.intervalMillis)
        assertEquals(
            AppBackgroundRefreshPolicy.Seconds30,
            AppBackgroundRefreshPolicy.fromStorageValue(1)
        )
        assertEquals(30_000L, AppBackgroundRefreshPolicy.Seconds30.intervalMillis)
        assertEquals(600_000L, AppBackgroundRefreshPolicy.Minutes10.intervalMillis)
        assertEquals(
            90_000L,
            AppBackgroundRefreshPolicy.Custom.resolveIntervalMillis(90L)
        )
        assertNull(AppBackgroundRefreshPolicy.Custom.resolveIntervalMillis(0L))
        assertEquals(
            AppBackgroundRefreshPolicy.OnForeground,
            AppBackgroundRefreshPolicy.fromStorageValue(Int.MAX_VALUE)
        )
    }
}
