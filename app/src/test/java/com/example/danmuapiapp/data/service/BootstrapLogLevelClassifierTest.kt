package com.example.danmuapiapp.data.service

import com.example.danmuapiapp.domain.model.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class BootstrapLogLevelClassifierTest {

    @Test
    fun `successful json response with errorCode fields stays info`() {
        val line = """{"errorCode":0,"success":true,"errorMessage":"","bangumi":{"animeId":9860389}}"""

        assertEquals(LogLevel.Info, BootstrapLogLevelClassifier.infer(line))
    }

    @Test
    fun `failed json response is error`() {
        val line = """{"errorCode":500,"success":false,"errorMessage":"boom"}"""

        assertEquals(LogLevel.Error, BootstrapLogLevelClassifier.infer(line))
    }

    @Test
    fun `errorCode without success only errors when non zero`() {
        assertEquals(LogLevel.Info, BootstrapLogLevelClassifier.infer("""{"errorCode":0,"errorMessage":""}"""))
        assertEquals(LogLevel.Error, BootstrapLogLevelClassifier.infer("""{"errorCode":403,"errorMessage":"Forbidden"}"""))
    }

    @Test
    fun `plain words use token boundaries so errorCode is not treated as error`() {
        assertEquals(LogLevel.Info, BootstrapLogLevelClassifier.infer("response has errorCode and errorMessage fields"))
        assertEquals(LogLevel.Error, BootstrapLogLevelClassifier.infer("RootNodeEntry crashed: node failed"))
        assertEquals(LogLevel.Warn, BootstrapLogLevelClassifier.infer("warning: startup is slow"))
    }
}
