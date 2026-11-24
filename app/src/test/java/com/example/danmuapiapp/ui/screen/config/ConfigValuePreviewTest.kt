package com.example.danmuapiapp.ui.screen.config

import org.junit.Assert.assertEquals
import org.junit.Test

class ConfigValuePreviewTest {
    @Test fun missingAndBlankValuesDisplayAsUnsetEvenForSecrets() {
        for (value in listOf(null, "", " \t")) {
            assertEquals("未设置", configValuePreview(value, sensitive = false))
            assertEquals("未设置", configValuePreview(value, sensitive = true))
        }
    }

    @Test fun populatedSettingsKeepTheirValueOrMask() {
        for (value in listOf("false", "0", "a=>b")) {
            assertEquals(value, configValuePreview(value, sensitive = false))
        }
        assertEquals("••••••••", configValuePreview("secret", sensitive = true))
    }
}
