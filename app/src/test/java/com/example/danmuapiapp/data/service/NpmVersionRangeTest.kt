package com.example.danmuapiapp.data.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NpmVersionRangeTest {
    @Test
    fun `caret 范围允许同一主版本升级`() {
        assertTrue(NpmVersionRange.isSatisfied("^2.1.0", "2.2.0"))
        assertFalse(NpmVersionRange.isSatisfied("^2.1.0", "3.0.0"))
    }

    @Test
    fun `零主版本 caret 只允许同一 minor`() {
        assertTrue(NpmVersionRange.isSatisfied("^0.4.2", "0.4.9"))
        assertFalse(NpmVersionRange.isSatisfied("^0.4.2", "0.5.0"))
    }

    @Test
    fun `tilde 与比较器组合按 npm 常见范围判断`() {
        assertTrue(NpmVersionRange.isSatisfied("~1.2.3", "1.2.9"))
        assertFalse(NpmVersionRange.isSatisfied("~1.2.3", "1.3.0"))
        assertTrue(NpmVersionRange.isSatisfied(">=1.2.0 <2.0.0", "1.9.5"))
        assertFalse(NpmVersionRange.isSatisfied(">=1.2.0 <2.0.0", "2.0.0"))
    }

    @Test
    fun `标签与预发布版本必须失败关闭`() {
        assertFalse(NpmVersionRange.isSatisfied("latest", "1.2.3"))
        assertFalse(NpmVersionRange.isSatisfied("^1.0.0", "1.1.0-beta.1"))
        assertFalse(NpmVersionRange.isSatisfied("1.0.0-beta.1", "1.0.0"))
        assertFalse(NpmVersionRange.isSatisfied("^1.0.0-beta.1", "1.0.0-beta.2"))
    }

    @Test
    fun `通配符与或范围可判断`() {
        assertTrue(NpmVersionRange.isSatisfied("1.x", "1.8.0"))
        assertFalse(NpmVersionRange.isSatisfied("1.x", "2.0.0"))
        assertTrue(NpmVersionRange.isSatisfied("^1.0.0 || ^2.0.0", "2.3.0"))
    }
}
