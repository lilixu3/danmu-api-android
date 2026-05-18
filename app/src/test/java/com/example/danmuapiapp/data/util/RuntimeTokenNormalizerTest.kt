package com.example.danmuapiapp.data.util

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeTokenNormalizerTest {

    @Test
    fun normalizeInputTreatsNullLikeTokensAsBlank() {
        assertEquals("", RuntimeTokenNormalizer.normalizeInput(null))
        assertEquals("", RuntimeTokenNormalizer.normalizeInput(""))
        assertEquals("", RuntimeTokenNormalizer.normalizeInput("  "))
        assertEquals("", RuntimeTokenNormalizer.normalizeInput("null"))
        assertEquals("", RuntimeTokenNormalizer.normalizeInput(" NULL "))
        assertEquals("", RuntimeTokenNormalizer.normalizeInput("undefined"))
        assertEquals("", RuntimeTokenNormalizer.normalizeInput(" Undefined "))
    }

    @Test
    fun normalizeInputPreservesRegularTokensAfterTrim() {
        assertEquals("87654321", RuntimeTokenNormalizer.normalizeInput(" 87654321 "))
        assertEquals("null-token", RuntimeTokenNormalizer.normalizeInput("null-token"))
        assertEquals("myUndefinedToken", RuntimeTokenNormalizer.normalizeInput("myUndefinedToken"))
    }

    @Test
    fun normalizeInputDropsNullTokenParsedFromDotEnv() {
        val env = DotEnvCodec.parse("TOKEN=null\nADMIN_TOKEN=keep")

        assertEquals("", RuntimeTokenNormalizer.normalizeInput(env["TOKEN"]))
    }
}
