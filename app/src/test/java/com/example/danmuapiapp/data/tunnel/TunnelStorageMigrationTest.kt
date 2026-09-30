package com.example.danmuapiapp.data.tunnel

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TunnelStorageMigrationTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun `CE migration preserves config kernel and existing DE choices without importing arbitrary files`() {
        val ce = temporary.newFolder("ce")
        val de = temporary.newFolder("de")
        File(ce, "frpc.conf").writeText("legacy")
        File(ce, "settings.json").writeText("legacy settings")
        File(ce, "autostart").writeText("1")
        File(ce, "kernel").mkdir()
        File(ce, "kernel/libfrpc.so").apply { writeText("kernel"); setExecutable(true) }
        File(ce, "unrelated").writeText("do not import")
        File(de, "settings.json").writeText("new settings")
        TunnelStore.migrateStorage(ce, de)
        assertEquals("legacy", File(de, "frpc.conf").readText())
        assertEquals("new settings", File(de, "settings.json").readText())
        assertTrue(File(de, "kernel/libfrpc.so").canExecute())
        assertFalse(File(de, "unrelated").exists())
        File(de, "frpc.conf").writeText("new")
        TunnelStore.migrateStorage(ce, de)
        assertEquals("new", File(de, "frpc.conf").readText())
        assertEquals("legacy", File(ce, "frpc.conf").readText())
    }
}
