package com.example.danmuapiapp.data.service

import org.junit.Assert.*
import org.junit.Test

class RuntimeDataIdentityTest {
    @Test fun `normal and root cannot share a file library`() {
        assertNotEquals(RuntimeDataIdentity.fileLibrary("/data/data/example/files/nodejs-project"),
            RuntimeDataIdentity.fileLibrary("/data/adb/danmuapi_runtime/example/nodejs-project"))
    }

    @Test fun `android home aliases identify the same library`() {
        assertEquals(RuntimeDataIdentity.fileLibrary("/data/user/0/example/files/nodejs-project/"),
            RuntimeDataIdentity.fileLibrary("/data/data/example/files/nodejs-project"))
        assertEquals(RuntimeDataIdentity.fileLibrary("/data/data/example/files/nodejs-project"),
            RuntimeDataIdentity.fileLibrary("/data/data/example/files/unused/../nodejs-project/./"))
    }

    @Test fun `changing core is not changing the physical library`() {
        assertTrue(RuntimeDataIdentity.matches("/data/user/0/example/files/nodejs-project/",
            "/data/data/example/files/nodejs-project", "stable", "stable"))
        assertFalse(RuntimeDataIdentity.matches("/normal", "/root", "stable", "stable"))
        assertFalse(RuntimeDataIdentity.matches("/normal", "/normal", "stable", "dev"))
        assertFalse(RuntimeDataIdentity.matches("", "", "stable", "stable"))
    }
}
