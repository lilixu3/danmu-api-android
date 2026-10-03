package com.example.danmuapiapp.ui.compat

import com.example.danmuapiapp.ui.screen.home.VendorSettingsPolicy
import org.junit.Assert.*
import org.junit.Test

class VendorSettingsPolicyTest {
    @Test fun childBrandsWinOverParentManufacturers() {
        assertEquals("realme", VendorSettingsPolicy.family("realme", "OPPO"))
        assertEquals("oneplus", VendorSettingsPolicy.family("OnePlus", "OPPO"))
        assertEquals("iqoo", VendorSettingsPolicy.family("iQOO", "vivo"))
        assertEquals("redmi", VendorSettingsPolicy.family("Redmi", "Xiaomi"))
        assertEquals("xiaomi", VendorSettingsPolicy.family("POCO", "Xiaomi"))
    }

    @Test fun unknownBrandsUseRecognizedManufacturersAndDoNotGuessTvVendors() {
        assertEquals("vivo", VendorSettingsPolicy.family("generic", "vivo"))
        assertEquals("oppo", VendorSettingsPolicy.family("unknown", "OPPO"))
        assertEquals("generic", VendorSettingsPolicy.family("generic", "Amlogic"))
        assertEquals("xiaomi", VendorSettingsPolicy.family("", "Xiaomi"))
    }

    @Test fun oemGuidesDistinguishBackgroundBatteryControlsFromAutostart() {
        assertTrue(VendorSettingsPolicy.batteryHint("iqoo").contains("后台耗电管理"))
        assertTrue(VendorSettingsPolicy.batteryHint("realme").contains("允许后台活动"))
        assertTrue(VendorSettingsPolicy.batteryHint("redmi").contains("省电策略"))
    }
    @Test fun vivoIqooAndRedmiUseTheirOwnManagers() {
        val iqoo = VendorSettingsPolicy.autoStartComponents("iqoo")
        assertEquals(VendorSettingsPolicy.autoStartComponents("vivo"), iqoo)
        assertEquals("com.vivo.permissionmanager", iqoo.first().first)
        assertEquals(VendorSettingsPolicy.autoStartComponents("xiaomi"), VendorSettingsPolicy.autoStartComponents("redmi"))
        assertTrue(VendorSettingsPolicy.autoStartComponents("redmi").all { it.first.startsWith("com.miui.") })
        assertTrue(iqoo.none { it.first.contains("coloros") || it.first.contains("miui") })
    }

    @Test fun realmeUsesColorosAndOneplusAlsoHasLegacyOxygenosFallback() {
        assertEquals(VendorSettingsPolicy.autoStartComponents("oppo"), VendorSettingsPolicy.autoStartComponents("realme"))
        val oneplus = VendorSettingsPolicy.autoStartComponents("oneplus")
        assertTrue(oneplus.first().first.contains("coloros"))
        assertTrue(oneplus.any { it.first == "com.oneplus.security" })
        assertTrue(VendorSettingsPolicy.autoStartComponents("realme").none { it.first == "com.oneplus.security" })
        assertTrue(VendorSettingsPolicy.autoStartComponents("generic").isEmpty())
        assertTrue(VendorSettingsPolicy.batteryManagerPackages("generic").isEmpty())
    }

}
