package com.example.danmuapiapp.ui.compat

import org.junit.Assert.*
import org.junit.Test

class CompatLayoutPolicyTest {
    @Test fun `landscape phones keep compact top navigation`() {
        for ((width, height) in listOf(640f to 320f, 800f to 360f, 960f to 400f)) {
            val layout = CompatLayoutPolicy.resolve(width, height, 1f)
            assertTrue(layout.compact)
            assertFalse(layout.useRail)
            assertTrue(layout.outerPadding <= 16)
        }
    }
    @Test fun `television wide windows have rail and two usable content columns`() {
        val layout = CompatLayoutPolicy.resolve(1280f, 720f, 1f)
        assertTrue(layout.useRail)
        assertTrue(layout.twoColumns)
        assertTrue(layout.contentWidth >= 720f)
    }
    @Test fun `portrait and split windows use one column without a sidebar`() {
        val layout = CompatLayoutPolicy.resolve(360f, 800f, 1f)
        assertFalse(layout.useRail)
        assertFalse(layout.twoColumns)
        assertTrue(layout.contentWidth > 0)
    }
    @Test fun `large fonts remove sidebar and extra columns before they squeeze content`() {
        val layout = CompatLayoutPolicy.resolve(1000f, 600f, 1.8f)
        assertFalse(layout.useRail)
        assertFalse(layout.twoColumns)
    }
    @Test fun `keyboard leaves a short window but never a negative content width`() {
        val layout = CompatLayoutPolicy.resolve(500f, 130f, 1f)
        assertFalse(layout.useRail)
        assertTrue(layout.compact)
        assertTrue(layout.contentWidth > 0)
    }
}
