package com.example.danmuapiapp.data.service

import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupEnvironmentPolicyTest {
    @Test
    fun exportExcludesCredentials() {
        val values = BackupEnvironmentPolicy.exportValues(
            "TOKEN=runtime-secret\nADMIN_TOKEN=admin-secret\nAPI_KEY=api-secret\nDANMU_API_PORT=9321\n"
        )
        assertFalse(values.containsKey("TOKEN"))
        assertFalse(values.containsKey("ADMIN_TOKEN"))
        assertFalse(values.containsKey("API_KEY"))
        assertEquals("9321", values["DANMU_API_PORT"])
    }

    @Test
    fun mergeKeepsLocalSecretsAndComments() {
        val current = "# local\nTOKEN=keep-me\nDANMU_API_PORT=9321\n"
        val merged = BackupEnvironmentPolicy.merge(
            current,
            mapOf("DANMU_API_PORT" to "9527", "DANMU_API_HOST" to "::")
        )
        assertTrue(merged.contains("# local"))
        assertTrue(merged.contains("TOKEN=keep-me"))
        assertTrue(merged.contains("DANMU_API_PORT=9527"))
        assertTrue(merged.contains("DANMU_API_HOST=::"))
    }

    @Test
    fun mergeRejectsCredentialAndInvalidKeysFromImportedBackup() {
        val merged = BackupEnvironmentPolicy.merge(
            "TOKEN=keep-local\nADMIN_TOKEN=keep-admin\n",
            mapOf(
                "TOKEN" to "attacker-token",
                "ADMIN_TOKEN" to "attacker-admin",
                "BAD\nINJECTED" to "value",
                "DANMU_API_PORT" to "9527"
            )
        )

        assertTrue(merged.contains("TOKEN=keep-local"))
        assertTrue(merged.contains("ADMIN_TOKEN=keep-admin"))
        assertFalse(merged.contains("attacker"))
        assertFalse(merged.contains("INJECTED"))
        assertTrue(merged.contains("DANMU_API_PORT=9527"))
    }

    @Test
    fun backupReaderRejectsOversizedInputBeforeDecoding() {
        val error = runCatching {
            AppBackupCodec.readUtf8(
                input = ByteArrayInputStream("12345".toByteArray()),
                maxBytes = 4,
                label = "测试文件"
            )
        }.exceptionOrNull()

        assertTrue(error is IOException)
    }
}
