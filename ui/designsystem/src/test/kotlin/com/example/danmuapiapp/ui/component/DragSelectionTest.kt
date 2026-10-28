package com.example.danmuapiapp.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DragSelectionTest {
    @Test fun selectsContinuousRangeWithoutLosingOtherSelections() {
        val session = DragSelectionSession((1..8).toList(), setOf(8), 2)
        assertEquals(setOf(2, 8), session.selectionThrough(2))
        assertEquals(setOf(2, 3, 4, 5, 8), session.selectionThrough(5))
    }

    @Test fun reversingRestoresTheOriginalSelectionOutsideTheRange() {
        val session = DragSelectionSession((1..8).toList(), setOf(4, 8), 2)
        session.selectionThrough(6)
        assertEquals(setOf(2, 3, 4, 8), session.selectionThrough(3))
        assertEquals(setOf(1, 2, 4, 8), session.selectionThrough(1))
    }

    @Test fun startingOnSelectedItemDeselectsAndCanReverse() {
        val session = DragSelectionSession((1..6).toList(), setOf(1, 2, 4, 6), 4)
        assertEquals(setOf(1, 6), session.selectionThrough(2))
        assertEquals(setOf(1, 2, 6), session.selectionThrough(4))
    }

    @Test fun onlySelectableKeysParticipateAndUnknownTargetsAreIgnored() {
        val session = DragSelectionSession(listOf(1, 3, 5), setOf(2), 1)
        assertEquals(setOf(1, 2, 3, 5), session.selectionThrough(5))
        assertEquals(setOf(2), session.selectionThrough(9))
    }

    @Test fun hitTestingUsesRealRowHeightsAndHandlesSpacing() {
        val bounds = listOf(SelectionItemBounds("first", -20, 60), SelectionItemBounds("second", 48, 120))
        assertEquals("first", selectionKeyAt(bounds, 0f, false))
        assertEquals("second", selectionKeyAt(bounds, 150f, false))
        assertNull(selectionKeyAt(bounds, 44f, false))
        assertEquals("second", selectionKeyAt(bounds, 46f, true))
        assertEquals("second", selectionKeyAt(bounds, 300f, true))
        assertNull(selectionKeyAt(emptyList<SelectionItemBounds<String>>(), 0f, true))
    }

    @Test fun edgeScrollIsBoundedAndStopsAwayFromEdges() {
        assertEquals(0f, selectionScrollStep(200f, 400, 56f, 16f), 0f)
        assertEquals(-8f, selectionScrollStep(28f, 400, 56f, 16f), 0f)
        assertEquals(8f, selectionScrollStep(372f, 400, 56f, 16f), 0f)
        assertEquals(-16f, selectionScrollStep(-100f, 400, 56f, 16f), 0f)
        assertEquals(16f, selectionScrollStep(500f, 400, 56f, 16f), 0f)
        assertEquals(0f, selectionScrollStep(0f, 0, 56f, 16f), 0f)
    }

    @Test fun hitTestingAccountsForListContentPadding() {
        val bounds = listOf(SelectionItemBounds("first", 0, 60), SelectionItemBounds("second", 68, 120))
        assertNull(selectionKeyAt(bounds, 4f, false, viewportStartOffset = -14))
        assertEquals("first", selectionKeyAt(bounds, 70f, false, viewportStartOffset = -14))
        assertEquals("second", selectionKeyAt(bounds, 84f, false, viewportStartOffset = -14))
    }
}
