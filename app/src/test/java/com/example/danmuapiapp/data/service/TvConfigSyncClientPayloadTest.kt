package com.example.danmuapiapp.data.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TvConfigSyncClientPayloadTest {

    @Test
    fun `outbound payload omits GitHub token while remaining backward compatible`() {
        val secret = "github_pat_must_never_leave_device"
        val payload = TvConfigSyncPayload(
            sourceDeviceName = "phone",
            settings = TvConfigSyncSettings(
                githubProxy = "original",
                githubToken = secret,
                customRepo = "owner/repository"
            )
        )

        val encoded = encodeTvConfigSyncPayloadWithoutGithubToken(payload)

        assertFalse(encoded.contains("githubToken"))
        assertFalse(encoded.contains(secret))
        val decoded = TvConfigSyncCodec.decodePayload(encoded)
        assertEquals("", decoded.settings.githubToken)
        assertEquals("owner/repository", decoded.settings.customRepo)
    }
}
