package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.data.util.RuntimeApiUrls
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDanmuRequestEncodingTest {

    @Test
    fun `multipart supports chinese filename`() {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                "逐玉.第01集.xml",
                "<i></i>".toRequestBody("application/xml".toMediaType())
            )
            .build()
        assertTrue(body.contentLength() > 0)
    }

    @Test
    fun `local danmu 请求 URL 与核心路径约定一致`() {
        // 与应用实际构建方式一致：RuntimeApiUrls 提供 base，token 与路径逐段添加。
        val url = RuntimeApiUrls.local(9321).toHttpUrl()
            .newBuilder()
            .addPathSegment("token")
            .addPathSegment("api")
            .addPathSegment("v2")
            .addPathSegment("local-danmu")
            .addPathSegment("逐玉|2026|tv|5")
            .build()
        assertEquals(
            "/token/api/v2/local-danmu/%E9%80%90%E7%8E%89%7C2026%7Ctv%7C5",
            url.encodedPath
        )
    }
}
