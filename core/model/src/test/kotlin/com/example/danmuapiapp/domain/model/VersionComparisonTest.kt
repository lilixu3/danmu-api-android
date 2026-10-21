package com.example.danmuapiapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparisonTest {

    @Test
    fun `compares numeric core components`() {
        assertTrue(compareVersions("1.10.0", "1.9.9") > 0)
        assertTrue(compareVersions("v1.0.5.116", "1.0.5.115") > 0)
        assertEquals(0, compareVersions("v1.2", "1.2.0"))
    }

    @Test
    fun `release sorts after prerelease`() {
        assertTrue(compareVersions("1.0.0", "1.0.0-rc.1") > 0)
        assertTrue(compareVersions("1.0.0-beta.2", "1.0.0-beta.1") > 0)
        assertTrue(compareVersions("1.0.0-1", "1.0.0-alpha") < 0)
    }

    @Test
    fun `handles non numeric and empty versions without throwing`() {
        assertEquals(0, compareVersions("", "0"))
        assertTrue(compareVersions("2.0.0", "") > 0)
        assertEquals(0, compareVersions("v2.0.0+build.7", "2.0.0+build.9"))
    }
}
