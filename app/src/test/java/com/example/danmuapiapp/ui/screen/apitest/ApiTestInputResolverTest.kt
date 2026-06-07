package com.example.danmuapiapp.ui.screen.apitest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiTestInputResolverTest {

    @Test
    fun `extracts full http url from pasted text`() {
        val url = ApiTestInputResolver.extractHttpUrl("第 1 集 https://v.qq.com/x/cover/mzc00200abc/xyz.html 1080P")

        assertEquals("https://v.qq.com/x/cover/mzc00200abc/xyz.html", url)
    }

    @Test
    fun `trims common trailing punctuation after url`() {
        val url = ApiTestInputResolver.extractHttpUrl("链接：https://www.bilibili.com/bangumi/play/ep123456。")

        assertEquals("https://www.bilibili.com/bangumi/play/ep123456", url)
    }

    @Test
    fun `does not treat normal file names as urls`() {
        assertNull(ApiTestInputResolver.extractHttpUrl("凡人修仙传 S01E01 1080P.mkv"))
    }
}
