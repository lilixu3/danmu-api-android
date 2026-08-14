package com.example.danmuapiapp.data.repository

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkDirCustomCorePreferencesTest {

    @Test
    fun `legacy config is assigned only to the initial work directory`() {
        val prefs = TestSharedPreferences()
        val store = WorkDirCustomCorePreferences(prefs)
        val legacy = StoredCustomCoreConfig(
            displayName = "My core",
            repo = "owner/original",
            branch = "develop"
        )

        assertTrue(
            store.migrateLegacyConfigIfNeeded(
                workDirIdentity = "/work/initial",
                legacyConfig = legacy,
                hasLegacyConfig = true
            )
        )

        assertEquals(legacy, store.read("/work/initial"))
        assertEquals(StoredCustomCoreConfig(), store.read("/work/new"))
    }

    @Test
    fun `switching back restores each work directory config`() {
        val store = WorkDirCustomCorePreferences(TestSharedPreferences())
        val first = StoredCustomCoreConfig("First", "owner/first", "main")
        val second = StoredCustomCoreConfig("Second", "owner/second", "release")

        store.write("/work/first", first)
        store.write("/work/second", second)

        assertEquals(second, store.read("/work/second"))
        assertEquals(first, store.read("/work/first"))
    }

    @Test
    fun `legacy migration is one time and does not leak to later directory`() {
        val prefs = TestSharedPreferences()
        val store = WorkDirCustomCorePreferences(prefs)
        val original = StoredCustomCoreConfig("Original", "owner/original", "main")

        assertTrue(store.migrateLegacyConfigIfNeeded("/work/first", original, true))
        assertFalse(
            store.migrateLegacyConfigIfNeeded(
                "/work/second",
                StoredCustomCoreConfig("Wrong", "owner/wrong", "dev"),
                true
            )
        )

        assertEquals(original, store.read("/work/first"))
        assertEquals(StoredCustomCoreConfig(), store.read("/work/second"))
    }

    @Test
    fun `stored preference keys are scoped to their work directory`() {
        val prefs = TestSharedPreferences()
        val store = WorkDirCustomCorePreferences(prefs)

        store.write("/work/first", StoredCustomCoreConfig(repo = "owner/first"))
        val storedKey = prefs.all.keys.single { it.endsWith(".repo") }

        assertTrue(store.isKeyForWorkDir(storedKey, "/work/first"))
        assertFalse(store.isKeyForWorkDir(storedKey, "/work/second"))
        assertFalse(store.isKeyForWorkDir(null, "/work/first"))
    }
}

private class TestSharedPreferences : SharedPreferences {
    private val values = linkedMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()

    override fun getString(key: String?, defValue: String?): String? {
        return values[key] as? String ?: defValue
    }

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
        return (values[key] as? Set<String>)?.toMutableSet() ?: defValues
    }

    override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        values[key] as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = values.containsKey(key)
    override fun edit(): SharedPreferences.Editor = TestEditor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit

    private inner class TestEditor : SharedPreferences.Editor {
        private val updates = linkedMapOf<String, Any?>()
        private var clearRequested = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
            updates[key.orEmpty()] = value
        }

        override fun putStringSet(
            key: String?,
            values: MutableSet<String>?
        ): SharedPreferences.Editor = apply {
            updates[key.orEmpty()] = values?.toSet()
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
            updates[key.orEmpty()] = value
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
            updates[key.orEmpty()] = value
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
            updates[key.orEmpty()] = value
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
            updates[key.orEmpty()] = value
        }

        override fun remove(key: String?): SharedPreferences.Editor = apply {
            updates[key.orEmpty()] = null
        }

        override fun clear(): SharedPreferences.Editor = apply {
            clearRequested = true
        }

        override fun commit(): Boolean {
            flush()
            return true
        }

        override fun apply() = flush()

        private fun flush() {
            if (clearRequested) values.clear()
            updates.forEach { (key, value) ->
                if (value == null) values.remove(key) else values[key] = value
            }
        }
    }
}
