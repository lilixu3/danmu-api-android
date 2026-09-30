package com.example.danmuapiapp.data.tunnel

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FrpcKernelInstallerTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `verify stage then stop before replacing active custom kernel`() {
        val target = temporary.newFile("libfrpc.so").apply { writeText("old") }
        val version = temporary.newFile("version.txt").apply { writeText("1") }
        val steps = mutableListOf<String>()
        val result = installFrpcKernel("new".toByteArray(), "2", target, version, true,
            verify = {
                steps += "verify"
                assertEquals("new", it.readText())
                assertEquals("old", target.readText())
                assertNotEquals(target, it)
            },
            stop = { steps += "stop"; assertEquals("old", target.readText()); TunnelActionResult(true, "") },
            start = { steps += "start"; assertEquals("new", target.readText()); TunnelActionResult(true, "") })
        assertTrue(result.message, result.ok)
        assertEquals(listOf("verify", "stop", "start"), steps)
        assertEquals("2", version.readText())
    }

    @Test fun `failed new kernel start restores old binary version and service`() {
        val target = temporary.newFile("libfrpc.so").apply { writeText("old"); setExecutable(true) }
        val version = temporary.newFile("version.txt").apply { writeText("1") }
        var starts = 0
        val result = installFrpcKernel("new".toByteArray(), "2", target, version, true,
            verify = {}, stop = { TunnelActionResult(true, "") },
            start = { starts++; TunnelActionResult(target.readText() == "old", "new kernel failed") })
        assertFalse(result.ok)
        assertEquals("old", target.readText())
        assertEquals("1", version.readText())
        assertTrue(target.canExecute())
        assertEquals(2, starts)
        assertEquals(setOf("libfrpc.so", "version.txt"), temporary.root.list()!!.toSet())
    }

    @Test fun `failed validation or stop leaves current kernel untouched`() {
        val target = temporary.newFile("libfrpc.so").apply { writeText("old") }
        val version = temporary.newFile("version.txt").apply { writeText("1") }
        for (failVerify in listOf(true, false)) {
            val result = installFrpcKernel("new".toByteArray(), "2", target, version, true,
                verify = { if (failVerify) error("invalid kernel") },
                stop = { assertFalse(failVerify); TunnelActionResult(false, "stop failed") },
                start = { fail("must not restart"); TunnelActionResult(false, "") })
            assertFalse(result.ok)
            assertEquals("old", target.readText())
            assertEquals("1", version.readText())
        }
    }
}
