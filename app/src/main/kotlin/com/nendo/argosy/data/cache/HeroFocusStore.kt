package com.nendo.argosy.data.cache

import com.nendo.argosy.data.model.HeroFocus
import com.nendo.argosy.data.model.HeroFocusSpec
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "HeroFocusStore"

class HeroPixels(val pixels: IntArray, val width: Int, val height: Int)

interface HeroPixelSource {
    fun aspect(file: File): Float?
    fun pixels(file: File, longSide: Int): HeroPixels?
}

/**
 * Hero framing per image file, computed once and kept in [file] across restarts. An entry belongs
 * to one path, that file's last-modified time and size, and the [HeroFocusSpec] it was computed
 * for, so a replaced file or a different column layout computes again. [peek] never touches disk;
 * [resolve] and [precompute] block on file reads and run off the main thread.
 */
class HeroFocusStore(
    private val file: File,
    private val source: HeroPixelSource = BitmapHeroPixelSource
) {
    private class Stamp(val lastModified: Long, val size: Long)

    private class Entry(val specKey: String, val path: String, val stamp: Stamp, val focus: HeroFocus) {
        fun matches(other: Stamp): Boolean = stamp.lastModified == other.lastModified && stamp.size == other.size

        fun line(): String = listOf(
            specKey,
            stamp.lastModified,
            stamp.size,
            focus.narrowX,
            focus.wideX,
            focus.y,
            focus.zoom,
            path
        ).joinToString(FIELD_SEPARATOR)
    }

    private val entries = ConcurrentHashMap<String, Entry>()
    private val computeLock = Mutex()

    @Volatile
    private var loaded = false

    @Volatile
    var lastSpec: HeroFocusSpec? = null
        private set

    fun peek(path: String, spec: HeroFocusSpec): HeroFocus? = entries[keyOf(spec.key, path)]?.focus

    suspend fun resolve(path: String, spec: HeroFocusSpec): HeroFocus = withContext(Dispatchers.IO) {
        lastSpec = spec
        ensureLoaded()
        val image = File(path)
        val stamp = stampOf(image) ?: return@withContext HeroFocus.CENTERED
        cached(spec, path, stamp) ?: computeLock.withLock {
            cached(spec, path, stamp) ?: run {
                val aspect = source.aspect(image) ?: return@withLock HeroFocus.CENTERED
                compute(spec, path, stamp, aspect) { longSide -> source.pixels(image, longSide) }
            }
        }
    }

    /**
     * Computes and keeps the framing of [image] from pixels already in hand, for the layout last
     * passed to [resolve]. Does nothing before any layout has asked, or when the entry exists.
     */
    fun precompute(image: File, width: Int, height: Int, pixels: (longSide: Int) -> HeroPixels?) {
        val spec = lastSpec ?: return
        if (width <= 0 || height <= 0) return
        ensureLoaded()
        val stamp = stampOf(image) ?: return
        if (cached(spec, image.path, stamp) != null) return
        compute(spec, image.path, stamp, width.toFloat() / height, pixels)
    }

    @Synchronized
    fun clear() {
        entries.clear()
        file.delete()
        loaded = true
    }

    private fun cached(spec: HeroFocusSpec, path: String, stamp: Stamp): HeroFocus? =
        entries[keyOf(spec.key, path)]?.takeIf { it.matches(stamp) }?.focus

    private fun compute(
        spec: HeroFocusSpec,
        path: String,
        stamp: Stamp,
        aspect: Float,
        pixels: (Int) -> HeroPixels?
    ): HeroFocus {
        val focus = try {
            if (aspect <= spec.wideAspect) {
                HeroFocus.CENTERED
            } else {
                val image = pixels(spec.analysisSize) ?: return HeroFocus.CENTERED
                HeroFocusAnalyzer.analyze(image.pixels, image.width, image.height, spec)
            }
        } catch (e: Exception) {
            Logger.warn(TAG, "Hero focus failed for $path: ${e.javaClass.simpleName}: ${e.message}")
            return HeroFocus.CENTERED
        }
        val entry = Entry(spec.key, path, stamp, focus)
        entries[keyOf(spec.key, path)] = entry
        append(entry)
        return focus
    }

    private fun stampOf(image: File): Stamp? =
        if (image.isFile) Stamp(image.lastModified(), image.length()) else null

    private fun keyOf(specKey: String, path: String): String = "$specKey$FIELD_SEPARATOR$path"

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            var lines = 0
            if (file.exists()) {
                file.forEachLine { line ->
                    lines++
                    parse(line)?.let { entries[keyOf(it.specKey, it.path)] = it }
                }
            }
            if (lines > entries.size * 2 + COMPACT_SLACK) rewrite()
            loaded = true
        }
    }

    @Synchronized
    private fun append(entry: Entry) {
        try {
            file.parentFile?.mkdirs()
            file.appendText(entry.line() + LINE_SEPARATOR)
        } catch (e: Exception) {
            Logger.warn(TAG, "Could not record hero focus: ${e.message}")
        }
    }

    private fun rewrite() {
        try {
            val staging = File(file.path + STAGING_SUFFIX)
            staging.writeText(entries.values.joinToString("") { it.line() + LINE_SEPARATOR })
            if (!staging.renameTo(file)) staging.delete()
        } catch (e: Exception) {
            Logger.warn(TAG, "Could not compact hero focus file: ${e.message}")
        }
    }

    private fun parse(line: String): Entry? {
        val fields = line.split(FIELD_SEPARATOR, limit = FIELD_COUNT)
        if (fields.size != FIELD_COUNT) return null
        val lastModified = fields[1].toLongOrNull() ?: return null
        val size = fields[2].toLongOrNull() ?: return null
        val focus = HeroFocus(
            narrowX = fields[3].toFloatOrNull() ?: return null,
            wideX = fields[4].toFloatOrNull() ?: return null,
            y = fields[5].toFloatOrNull() ?: return null,
            zoom = fields[6].toFloatOrNull() ?: return null
        )
        return Entry(fields[0], fields[7], Stamp(lastModified, size), focus)
    }

    private companion object {
        const val FIELD_SEPARATOR = "\t"
        const val LINE_SEPARATOR = "\n"
        const val FIELD_COUNT = 8
        const val COMPACT_SLACK = 64
        const val STAGING_SUFFIX = ".tmp"
    }
}
