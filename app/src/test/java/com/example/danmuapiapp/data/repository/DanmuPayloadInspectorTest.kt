package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.DanmuDownloadFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DanmuPayloadInspectorTest {

    @Test
    fun `all text output formats validate and expose counts`() {
        val samples = listOf(
            Triple(
                DanmuDownloadFormat.Json,
                """{"count":2,"comments":[{"m":"a"},{"m":"b"}]}""",
                2
            ),
            Triple(
                DanmuDownloadFormat.Xml,
                """<?xml version="1.0"?><i><d p="1,1,25,1">a</d></i>""",
                1
            ),
            Triple(
                DanmuDownloadFormat.ArtplayerJson,
                """{"danuni":{},"danmuku":[{"text":"a","time":1}]}""",
                1
            ),
            Triple(
                DanmuDownloadFormat.BahaJson,
                """{"data":{"danmu":[{"text":"a"}],"totalCount":1}}""",
                1
            ),
            Triple(
                DanmuDownloadFormat.BiliXml,
                """<?xml version="1.0"?><i><d p="1,1,25,1">a</d><d p="2,1,25,1">b</d></i>""",
                2
            ),
            Triple(
                DanmuDownloadFormat.DanuniJson,
                """[{"content":"a","progress":1000}]""",
                1
            ),
            Triple(
                DanmuDownloadFormat.DdplayJson,
                """{"count":1,"comments":[{"p":"1,1,1,x","m":"a"}]}""",
                1
            ),
            Triple(
                DanmuDownloadFormat.DplayerJson,
                """{"code":0,"data":[[1,0,16777215,"x","a"]]}""",
                1
            ),
            Triple(
                DanmuDownloadFormat.VodJson,
                """{"code":0,"danum":1,"danmuku":[[1,"right","#FFFFFF","","a"]]}""",
                1
            )
        )

        samples.forEach { (format, payload, expectedCount) ->
            val inspected = DanmuPayloadInspector.inspect(payload.toByteArray(), format)
            assertTrue("${format.value}: ${inspected.error}", inspected.valid)
            assertEquals(format.value, expectedCount, inspected.count)
        }
    }

    @Test
    fun `protobuf accepts binary but rejects core json fallback`() {
        val binary = DanmuPayloadInspector.inspect(
            payload = byteArrayOf(0x0A, 0x00),
            format = DanmuDownloadFormat.DanuniBinPb,
            contentType = "application/octet-stream"
        )
        assertTrue(binary.valid)
        assertNull(binary.count)

        val fallback = DanmuPayloadInspector.inspect(
            payload = """{"count":0,"comments":[]}""".toByteArray(),
            format = DanmuDownloadFormat.DanuniBinPb,
            contentType = "application/json"
        )
        assertFalse(fallback.valid)
    }

    @Test
    fun `adapter request rejects generic json fallback`() {
        val fallback = """{"count":1,"comments":[{"m":"wrong schema"}]}"""
        val inspected = DanmuPayloadInspector.inspect(
            fallback.toByteArray(),
            DanmuDownloadFormat.ArtplayerJson
        )
        assertFalse(inspected.valid)
        assertTrue(inspected.error.contains("回退"))
    }
}
