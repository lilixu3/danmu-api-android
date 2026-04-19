package com.example.danmuapiapp.data.service

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeKeepAlivePrefsTest {

    @Test
    fun `setDesiredRunning should use commit for cross process visibility`() {
        val prefs = FakeSharedPreferences()

        NodeKeepAlivePrefs.setDesiredRunning(prefs, desired = true)

        assertTrue(prefs.getBoolean("desired_running", false))
        assertEquals(1, prefs.commitCount)
        assertEquals(0, prefs.applyCount)
    }

    @Test
    fun `clearRestartBackoff should use commit for cross process visibility`() {
        val prefs = FakeSharedPreferences().apply {
            seedInt("recovery_failure_count", 3)
            seedLong("recovery_block_until_ms", 123L)
        }

        NodeKeepAlivePrefs.clearRestartBackoff(prefs)

        assertEquals(0, prefs.getInt("recovery_failure_count", -1))
        assertEquals(0L, prefs.getLong("recovery_block_until_ms", -1L))
        assertEquals(1, prefs.commitCount)
        assertEquals(0, prefs.applyCount)
    }

    @Test
    fun `recordRecoveryFailure should commit updated block window`() {
        val prefs = FakeSharedPreferences().apply {
            seedBoolean("desired_running", true)
        }

        NodeKeepAlivePrefs.recordRecoveryFailure(prefs)

        assertEquals(1, prefs.getInt("recovery_failure_count", 0))
        assertTrue(prefs.getLong("recovery_block_until_ms", 0L) > System.currentTimeMillis())
        assertEquals(1, prefs.commitCount)
        assertEquals(0, prefs.applyCount)
    }

    @Test
    fun `recordRecoveryFailure should do nothing when desired running is false`() {
        val prefs = FakeSharedPreferences()

        NodeKeepAlivePrefs.recordRecoveryFailure(prefs)

        assertEquals(0, prefs.commitCount)
        assertEquals(0, prefs.applyCount)
        assertFalse(prefs.contains("recovery_failure_count"))
        assertFalse(prefs.contains("recovery_block_until_ms"))
    }
}

private class FakeSharedPreferences : SharedPreferences {
    private val data = linkedMapOf<String, Any?>()
    var commitCount: Int = 0
        private set
    var applyCount: Int = 0
        private set

    fun seedBoolean(key: String, value: Boolean) {
        data[key] = value
    }

    fun seedInt(key: String, value: Int) {
        data[key] = value
    }

    fun seedLong(key: String, value: Long) {
        data[key] = value
    }

    override fun getAll(): MutableMap<String, *> = data.toMutableMap()

    override fun getString(key: String?, defValue: String?): String? {
        return data[key] as? String ?: defValue
    }

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
        val value = data[key] as? Set<String> ?: return defValues
        return value.toMutableSet()
    }

    override fun getInt(key: String?, defValue: Int): Int {
        return data[key] as? Int ?: defValue
    }

    override fun getLong(key: String?, defValue: Long): Long {
        return data[key] as? Long ?: defValue
    }

    override fun getFloat(key: String?, defValue: Float): Float {
        return data[key] as? Float ?: defValue
    }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        return data[key] as? Boolean ?: defValue
    }

    override fun contains(key: String?): Boolean = data.containsKey(key)

    override fun edit(): SharedPreferences.Editor = EditorImpl()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit

    private inner class EditorImpl : SharedPreferences.Editor {
        private val pending = linkedMapOf<String, Any?>()
        private var clearRequested = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
            pending[key.orEmpty()] = value
        }

        override fun putStringSet(
            key: String?,
            values: MutableSet<String>?
        ): SharedPreferences.Editor = apply {
            pending[key.orEmpty()] = values?.toSet()
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
            pending[key.orEmpty()] = value
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
            pending[key.orEmpty()] = value
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
            pending[key.orEmpty()] = value
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
            pending[key.orEmpty()] = value
        }

        override fun remove(key: String?): SharedPreferences.Editor = apply {
            pending[key.orEmpty()] = null
        }

        override fun clear(): SharedPreferences.Editor = apply {
            clearRequested = true
        }

        override fun commit(): Boolean {
            commitCount += 1
            flush()
            return true
        }

        override fun apply() {
            applyCount += 1
            flush()
        }

        private fun flush() {
            if (clearRequested) {
                data.clear()
            }
            pending.forEach { (key, value) ->
                if (value == null) {
                    data.remove(key)
                } else {
                    data[key] = value
                }
            }
        }
    }
}
