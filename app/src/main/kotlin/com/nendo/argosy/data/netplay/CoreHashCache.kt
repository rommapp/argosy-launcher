package com.nendo.argosy.data.netplay

import android.content.Context
import android.content.SharedPreferences
import com.nendo.argosy.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoreHashCache @Inject constructor(
    @ApplicationContext context: Context
) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val memoryCache = ConcurrentHashMap<String, String>()

    init {
        val cachedVersion = prefs.getInt(KEY_VERSION_CODE, -1)
        if (cachedVersion != BuildConfig.VERSION_CODE) {
            prefs.edit().clear().putInt(KEY_VERSION_CODE, BuildConfig.VERSION_CODE).apply()
        }
    }

    fun getHashForCore(corePath: String): String? {
        memoryCache[corePath]?.let { return it }

        val persistedKey = keyFor(corePath)
        val persisted = prefs.getString(persistedKey, null)
        if (persisted != null) {
            memoryCache[corePath] = persisted
            return persisted
        }

        val file = File(corePath)
        if (!file.exists() || !file.isFile) return null

        val computed = try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            return null
        }

        memoryCache[corePath] = computed
        prefs.edit().putString(persistedKey, computed).apply()
        return computed
    }

    fun invalidate(corePath: String) {
        memoryCache.remove(corePath)
        prefs.edit().remove(keyFor(corePath)).apply()
    }

    private fun keyFor(corePath: String): String = "core_hash:$corePath"

    companion object {
        private const val PREFS_NAME = "argosy_netplay_core_hashes"
        private const val KEY_VERSION_CODE = "version_code"
    }
}
