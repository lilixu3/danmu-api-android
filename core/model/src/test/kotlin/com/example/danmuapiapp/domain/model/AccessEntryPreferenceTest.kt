package com.example.danmuapiapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AccessEntryPreferenceTest {
    @Test fun `unset layout defaults to tabs`() {
        assertEquals(AccessEntryLayout.Tabs, AccessEntryLayout.fromStorageValue(null))
        assertEquals(AccessEntryLayout.Tabs, AccessEntryLayout.fromStorageValue(""))
        assertEquals(AccessEntryLayout.Tabs, AccessEntryLayout.fromStorageValue("unknown"))
        assertEquals(AccessEntryTab.Ipv4, AccessEntryTab.fromStorageValue(null))
    }

    @Test fun `explicit layout selection is preserved`() {
        assertEquals(AccessEntryLayout.Expanded, AccessEntryLayout.fromStorageValue("expanded"))
        assertEquals(AccessEntryLayout.Tabs, AccessEntryLayout.fromStorageValue("tabs"))
    }

    @Test fun `temporarily unavailable preferred address recovers when it returns`() {
        val preferred = AccessEntryTab.Tunnel
        val unavailable = setOf(AccessEntryTab.Local, AccessEntryTab.Ipv4)
        assertEquals(AccessEntryTab.Ipv4, AccessEntryTab.resolve(preferred, unavailable))
        assertEquals(AccessEntryTab.Tunnel, AccessEntryTab.resolve(preferred, unavailable + preferred))
        assertEquals(AccessEntryTab.Local, AccessEntryTab.resolve(AccessEntryTab.Ipv6, setOf(AccessEntryTab.Local)))
    }
}
