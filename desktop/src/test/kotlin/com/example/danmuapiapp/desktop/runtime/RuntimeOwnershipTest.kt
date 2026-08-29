package com.example.danmuapiapp.desktop.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RuntimeOwnershipTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun health(
        identity: String? = "install-1",
        port: Int? = 9321,
        home: String = temp.root.absolutePath,
        envHome: String? = home,
        resolvedHome: String? = home,
        cwd: String? = home,
    ) = RuntimeOwnership.Health(identity, port, envHome, resolvedHome, cwd)

    @Test
    fun acceptsOnlyMatchingIdentityPortAndAllHomes() {
        val home = temp.root
        assertTrue(RuntimeOwnership.isOwned("install-1", 9321, home, health()))
        assertFalse(RuntimeOwnership.isOwned("install-2", 9321, home, health()))
        assertFalse(RuntimeOwnership.isOwned("install-1", 19421, home, health()))
        assertFalse(
            RuntimeOwnership.isOwned(
                "install-1", 9321, home,
                health(envHome = temp.newFolder("foreign").absolutePath),
            )
        )
        assertFalse(RuntimeOwnership.isOwned("", 9321, home, health(identity = null)))
    }

    @Test
    fun refusesMissingHealthHomes() {
        assertFalse(RuntimeOwnership.isOwned("install-1", 9321, temp.root, health(cwd = null)))
        assertFalse(RuntimeOwnership.isOwned("install-1", 9321, temp.root, health(envHome = null)))
    }
}
