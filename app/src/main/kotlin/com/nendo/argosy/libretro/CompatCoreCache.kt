package com.nendo.argosy.libretro

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CompatCoreCache"
private const val HASH_PREFIX_LEN = 16

@Singleton
class CompatCoreCache @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cacheDir = File(context.filesDir, "libretro/compat_cores").apply { mkdirs() }
    private val metadataFile = File(cacheDir, ".metadata.json")

    fun getCompatCorePath(coreHash: String): String? {
        val prefix = coreHash.take(HASH_PREFIX_LEN)
        val file = File(cacheDir, "$prefix.so")
        if (!file.exists()) return null
        if (!file.canExecute()) file.setExecutable(true)
        Log.d(TAG, "Compat core hit: $prefix")
        return file.absolutePath
    }

    fun storeCompatCore(sourceFile: File, coreHash: String) {
        if (!sourceFile.exists()) return
        val prefix = coreHash.take(HASH_PREFIX_LEN)
        val dest = File(cacheDir, "$prefix.so")
        if (dest.exists()) {
            Log.d(TAG, "Compat core already cached: $prefix")
            touchMetadata(prefix)
            return
        }
        sourceFile.copyTo(dest, overwrite = true)
        dest.setExecutable(true)
        touchMetadata(prefix)
        Log.i(TAG, "Stored compat core: $prefix (${dest.length()} bytes)")
    }

    fun evictStale(maxAgeDays: Int = 30) {
        val metadata = readMetadata()
        val cutoff = System.currentTimeMillis() - maxAgeDays.toLong() * 86_400_000
        val stale = metadata.keys().asSequence().filter { key ->
            metadata.optLong(key, 0L) < cutoff
        }.toList()
        for (key in stale) {
            File(cacheDir, "$key.so").delete()
            metadata.remove(key)
        }
        if (stale.isNotEmpty()) {
            writeMetadata(metadata)
            Log.i(TAG, "Evicted ${stale.size} stale compat core(s)")
        }
    }

    fun clearAll() {
        cacheDir.listFiles()?.forEach { it.delete() }
        Log.i(TAG, "Cleared compat core cache")
    }

    fun totalSizeBytes(): Long =
        cacheDir.listFiles()?.filter { it.name.endsWith(".so") }?.sumOf { it.length() } ?: 0L

    private fun touchMetadata(prefix: String) {
        val metadata = readMetadata()
        metadata.put(prefix, System.currentTimeMillis())
        writeMetadata(metadata)
    }

    private fun readMetadata(): JSONObject = try {
        if (metadataFile.exists()) JSONObject(metadataFile.readText()) else JSONObject()
    } catch (e: Exception) {
        Log.w(TAG, "Corrupt metadata, resetting", e)
        JSONObject()
    }

    private fun writeMetadata(json: JSONObject) {
        metadataFile.writeText(json.toString())
    }
}
