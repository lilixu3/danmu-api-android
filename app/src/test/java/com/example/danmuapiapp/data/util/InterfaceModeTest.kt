package com.example.danmuapiapp.data.util

import org.junit.Assert.assertEquals
import org.junit.Test

class InterfaceModeTest {
    @Test fun `existing forced normal preference is preserved`() {
        assertEquals(InterfaceMode.Normal, InterfaceMode.resolve(null, true))
        assertEquals(InterfaceMode.Auto, InterfaceMode.resolve(null, false))
    }
    @Test fun `explicit choices override legacy preference`() {
        assertEquals(InterfaceMode.Compat, InterfaceMode.resolve("compat", true))
        assertEquals(InterfaceMode.Auto, InterfaceMode.resolve("auto", true))
        assertEquals(InterfaceMode.Normal, InterfaceMode.resolve("normal", false))
    }
    @Test fun `unknown values fall back safely`() {
        assertEquals(InterfaceMode.Auto, InterfaceMode.resolve("broken", false))
        assertEquals(InterfaceMode.Normal, InterfaceMode.resolve("broken", true))
    }
}
