package com.example.danmuapiapp.data.repository

import org.junit.Assert.*
import org.junit.Test

class NormalRuntimeEventOrderTest {
    @Test
    fun `late stopped or error event from old process is ignored`() {
        val order = NormalRuntimeEventOrder()
        assertTrue(order.accept(100, 1, 1))
        assertTrue(order.accept(200, 1, 1))
        assertFalse(order.accept(100, 20, 1000))
        assertTrue(order.accept(200, 1, 2))
    }

    @Test
    fun `old generation and duplicate broadcasts are ignored`() {
        val order = NormalRuntimeEventOrder()
        assertTrue(order.accept(100, 2, 10))
        assertFalse(order.accept(100, 1, 100))
        assertFalse(order.accept(100, 2, 10))
        assertFalse(order.accept(100, 2, 9))
        assertTrue(order.accept(100, 2, 11))
        assertTrue(order.accept(100, 3, 1))
    }

    @Test
    fun `service reattachment keeps process generation and accepts subsequent events`() {
        val order = NormalRuntimeEventOrder()
        assertTrue(order.accept(100, 1, 1))
        assertTrue(order.accept(100, 1, 2))
        assertTrue(order.accept(100, 1, 3))
    }

    @Test
    fun `unversioned events only accepted before first versioned event`() {
        val order = NormalRuntimeEventOrder()
        assertTrue(order.accept(-1, -1, -1))
        assertTrue(order.accept(100, 1, 1))
        assertFalse(order.accept(-1, -1, -1))
    }
}
