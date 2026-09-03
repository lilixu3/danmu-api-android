package com.example.danmuapiapp.data.service

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationEndpointTextPolicyTest {

    @Test
    fun `collapsed text stays on one concise line`() {
        assertEquals(
            "服务运行中 · 接口地址：http://192.168.1.20:9321",
            NotificationEndpointTextPolicy.compact(
                status = " 服务运行中 ",
                infoTitle = "接口地址",
                infoText = "http://192.168.1.20:9321"
            )
        )
    }

    @Test
    fun `expanded text puts status and endpoint on separate lines`() {
        assertEquals(
            "服务运行中\n接口地址：http://192.168.1.20:9321",
            NotificationEndpointTextPolicy.expanded(
                status = "服务运行中",
                infoTitle = "接口地址",
                infoText = "http://192.168.1.20:9321"
            )
        )
    }

    @Test
    fun `empty values do not create punctuation or blank lines`() {
        assertEquals(
            "服务运行中",
            NotificationEndpointTextPolicy.compact("服务运行中", "接口地址", "")
        )
        assertEquals(
            "接口地址：http://192.168.1.20:9321",
            NotificationEndpointTextPolicy.expanded("", "接口地址", "http://192.168.1.20:9321")
        )
    }
}
