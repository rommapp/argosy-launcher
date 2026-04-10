package com.nendo.argosy.data.netplay

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

class CoreHashCacheTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun hashOf(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private class InMemoryPrefs : SharedPreferences {
        private val map = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = map
        override fun getString(key: String, defValue: String?): String? = (map[key] as? String) ?: defValue
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST") (map[key] as? MutableSet<String>) ?: defValues
        override fun getInt(key: String, defValue: Int): Int = (map[key] as? Int) ?: defValue
        override fun getLong(key: String, defValue: Long): Long = (map[key] as? Long) ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = (map[key] as? Float) ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
        override fun contains(key: String): Boolean = map.containsKey(key)

        override fun edit(): SharedPreferences.Editor {
            val self = this
            return object : SharedPreferences.Editor {
                override fun putString(key: String, value: String?): SharedPreferences.Editor {
                    self.map[key] = value; return this
                }
                override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor {
                    self.map[key] = values; return this
                }
                override fun putInt(key: String, value: Int): SharedPreferences.Editor {
                    self.map[key] = value; return this
                }
                override fun putLong(key: String, value: Long): SharedPreferences.Editor {
                    self.map[key] = value; return this
                }
                override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
                    self.map[key] = value; return this
                }
                override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
                    self.map[key] = value; return this
                }
                override fun remove(key: String): SharedPreferences.Editor {
                    self.map.remove(key); return this
                }
                override fun clear(): SharedPreferences.Editor {
                    self.map.clear(); return this
                }
                override fun commit(): Boolean = true
                override fun apply() {}
            }
        }

        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) {}
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) {}
    }

    private fun buildCache(prefs: InMemoryPrefs = InMemoryPrefs()): Pair<CoreHashCache, InMemoryPrefs> {
        val context = mockk<Context>()
        val prefsNameSlot = slot<String>()
        every { context.getSharedPreferences(capture(prefsNameSlot), any()) } returns prefs
        return CoreHashCache(context) to prefs
    }

    @Test
    fun nonexistentCore_returnsNull() {
        val (cache, _) = buildCache()
        assertNull(cache.getHashForCore("/does/not/exist.so"))
    }

    @Test
    fun sameCore_returnsConsistentHash() {
        val core = tempFolder.newFile("core.so")
        core.writeBytes(ByteArray(2048) { it.toByte() })

        val (cache, _) = buildCache()
        val first = cache.getHashForCore(core.absolutePath)
        val second = cache.getHashForCore(core.absolutePath)
        assertNotNull(first)
        assertEquals(first, second)
        assertEquals(hashOf(core), first)
    }

    @Test
    fun cachedValue_persistsAcrossInstances() {
        val core = tempFolder.newFile("core.so")
        core.writeBytes(ByteArray(1024) { 0x7E })

        val prefs = InMemoryPrefs()
        val (cache1, _) = buildCache(prefs)
        val first = cache1.getHashForCore(core.absolutePath)

        core.delete()

        val (cache2, _) = buildCache(prefs)
        val second = cache2.getHashForCore(core.absolutePath)
        assertEquals(first, second)
    }

    @Test
    fun invalidate_clearsCachedValue() {
        val core = tempFolder.newFile("core.so")
        core.writeBytes(ByteArray(512) { 0x11 })

        val (cache, _) = buildCache()
        cache.getHashForCore(core.absolutePath)
        cache.invalidate(core.absolutePath)

        core.delete()
        assertNull(cache.getHashForCore(core.absolutePath))
    }
}
