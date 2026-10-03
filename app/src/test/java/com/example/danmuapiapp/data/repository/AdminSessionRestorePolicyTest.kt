package com.example.danmuapiapp.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class AdminSessionRestorePolicyTest {
    private val token = "saved-admin-token"

    @Test fun coldStartPreservesSessionUntilConfigurationIsRead() {
        val pending = restoreAdminSessionToken(token, null)
        assertEquals(token, pending)
        assertEquals(token, restoreAdminSessionToken(pending, mapOf("ADMIN_TOKEN" to token)))
    }

    @Test fun failedReadThenRetryRestoresSavedSession() {
        var saved = restoreAdminSessionToken(token, null)
        saved = restoreAdminSessionToken(saved, null)
        assertEquals(token, saved)
        assertEquals(token, restoreAdminSessionToken(saved, mapOf("ADMIN_TOKEN" to token)))
    }

    @Test fun reloadPreservesExistingSessionWithSameKey() {
        val saved = restoreAdminSessionToken(token, mapOf("ADMIN_TOKEN" to token))
        assertEquals(token, restoreAdminSessionToken(saved, null))
        assertEquals(token, restoreAdminSessionToken(saved, mapOf("ADMIN_TOKEN" to "  $token  ")))
    }

    @Test fun successfulReadWithRemovedKeyRevokesSession() {
        assertEquals("", restoreAdminSessionToken(token, emptyMap()))
        assertEquals("", restoreAdminSessionToken(token, mapOf("ADMIN_TOKEN" to "  ")))
    }

    @Test fun changedKeyRevokesSessionAndOldKeyCannotRestoreIt() {
        val saved = restoreAdminSessionToken(token, mapOf("ADMIN_TOKEN" to "new-admin-token"))
        assertEquals("", saved)
        assertEquals("", restoreAdminSessionToken(saved, mapOf("ADMIN_TOKEN" to token)))
    }

    @Test fun loggedOutSessionDoesNotAutomaticallyLogIn() {
        assertEquals("", restoreAdminSessionToken("", null))
        assertEquals("", restoreAdminSessionToken("", mapOf("ADMIN_TOKEN" to token)))
    }
}
