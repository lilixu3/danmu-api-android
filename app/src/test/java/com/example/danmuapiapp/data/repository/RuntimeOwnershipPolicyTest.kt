package com.example.danmuapiapp.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeOwnershipPolicyTest {

    @Test
    fun `新版 health 返回匹配 identity 时应判定为精确归属`() {
        val ownership = determineRuntimeOwnershipFromHealth(
            body = """{"runtimeIdentity":"abc123","resolvedHome":"/data/user/0/pkg/files/nodejs-project"}""",
            expectedIdentity = "abc123",
            expectedHome = "/data/user/0/pkg/files/nodejs-project"
        )

        assertEquals(RuntimeOwnership.OwnedExact, ownership)
    }

    @Test
    fun `新版 health identity 不匹配时即使 home 相同也应判定为外部实例`() {
        val ownership = determineRuntimeOwnershipFromHealth(
            body = """{"runtimeIdentity":"other","resolvedHome":"/data/user/0/pkg/files/nodejs-project"}""",
            expectedIdentity = "abc123",
            expectedHome = "/data/user/0/pkg/files/nodejs-project"
        )

        assertEquals(RuntimeOwnership.Foreign, ownership)
    }

    @Test
    fun `旧版 health 缺少 identity 但 resolvedHome 匹配时应判定为兼容归属`() {
        val ownership = determineRuntimeOwnershipFromHealth(
            body = """{"resolvedHome":"/data/user/0/pkg/files/nodejs-project","envHome":"/data/user/0/pkg/files/nodejs-project","cwd":"/data/user/0/pkg/files/nodejs-project"}""",
            expectedIdentity = "abc123",
            expectedHome = "/data/user/0/pkg/files/nodejs-project"
        )

        assertEquals(RuntimeOwnership.OwnedLegacy, ownership)
    }

    @Test
    fun `旧版 health 缺少 identity 时应兼容 data data 与 data user 0 路径别名`() {
        val ownership = determineRuntimeOwnershipFromHealth(
            body = """{"resolvedHome":"/data/data/pkg/files/nodejs-project"}""",
            expectedIdentity = "abc123",
            expectedHome = "/data/user/0/pkg/files/nodejs-project"
        )

        assertEquals(RuntimeOwnership.OwnedLegacy, ownership)
    }

    @Test
    fun `旧版 health 缺少 identity 且 home 不匹配时应判定为外部实例`() {
        val ownership = determineRuntimeOwnershipFromHealth(
            body = """{"resolvedHome":"/data/adb/danmuapi_runtime/other/nodejs-project"}""",
            expectedIdentity = "abc123",
            expectedHome = "/data/adb/danmuapi_runtime/com.example.danmuapiapp/nodejs-project"
        )

        assertEquals(RuntimeOwnership.Foreign, ownership)
    }

    @Test
    fun `兼容 home 判定应忽略末尾斜杠`() {
        val ownership = determineRuntimeOwnershipFromHealth(
            body = """{"cwd":"/data/user/0/pkg/files/nodejs-project/"}""",
            expectedIdentity = "abc123",
            expectedHome = "/data/user/0/pkg/files/nodejs-project"
        )

        assertEquals(RuntimeOwnership.OwnedLegacy, ownership)
    }
}
