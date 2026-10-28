package com.example.danmuapiapp.data.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuntimeVariantKeyResolverTest {

    @Test
    fun `变体键按别名归一化且未知值视为无效`() {
        assertEquals("stable", RuntimeVariantKeyResolver.normalizeKey(" Stable "))
        assertEquals("dev", RuntimeVariantKeyResolver.normalizeKey("development"))
        assertEquals("dev", RuntimeVariantKeyResolver.normalizeKey("DEVELOP"))
        assertEquals("custom", RuntimeVariantKeyResolver.normalizeKey("custom"))
        assertEquals("stable", RuntimeVariantKeyResolver.normalizeKey(null))
        assertEquals("stable", RuntimeVariantKeyResolver.normalizeKey("unknown"))
        assertNull(RuntimeVariantKeyResolver.normalizeKeyOrNull("unknown"))
    }

    @Test
    fun `解析优先级为运行时偏好 旧偏好 环境变量 默认值`() {
        assertEquals(
            "dev",
            RuntimeVariantKeyResolver.resolve("dev", "custom", "stable")
        )
        assertEquals(
            "custom",
            RuntimeVariantKeyResolver.resolve("unknown", "custom", "stable")
        )
        assertEquals(
            "dev",
            RuntimeVariantKeyResolver.resolve(null, "", "development")
        )
        assertEquals(
            "stable",
            RuntimeVariantKeyResolver.resolve(null, null, null)
        )
    }
}
