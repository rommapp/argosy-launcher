package com.nendo.argosy.libretro.frame

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FrameRegistry @Inject constructor(@ApplicationContext private val context: Context) {

    enum class Source { LIBRETRO, DUIMON, CUSTOM }

    data class FrameEntry(
        val id: String,
        val displayName: String,
        val platforms: Set<String>,
        val githubPath: String,
        val source: Source = Source.LIBRETRO
    )

    private var installedCache: Set<String>? = null

    fun getInstalledIds(): Set<String> {
        installedCache?.let { return it }
        adoptLegacyFlatLayout()
        val ids = listOf(getFramesDir(), getCustomFramesDir())
            .filter { it.exists() }
            .flatMap { dir ->
                dir.listFiles()
                    ?.filter { it.extension.lowercase() == "png" }
                    ?.map { it.nameWithoutExtension }
                    ?: emptyList()
            }
            .toSet()
        installedCache = ids
        return ids
    }

    /**
     * Moves frames written before the catalog/custom split into the catalog directory. Without
     * it every already-downloaded frame reads as missing and downloads a second time.
     */
    private fun adoptLegacyFlatLayout() {
        val legacyDir = File(context.getExternalFilesDir(null), "frames")
        val stale = legacyDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() == "png" }
            ?: return
        if (stale.isEmpty()) return

        val target = getFramesDir().apply { mkdirs() }
        stale.forEach { file ->
            val moved = File(target, file.name)
            if (!moved.exists() && !file.renameTo(moved)) {
                file.copyTo(moved, overwrite = true)
                file.delete()
            }
        }
    }

    fun invalidateInstalledCache() {
        installedCache = null
    }

    private val catalogFrames = listOf(
        FrameEntry(
            "nes", "NES", setOf("nes", "fc"),
            "16x9%20Collections/Nosh01%201440%20Plain/Nintendo-Entertainment-System-Bezel-16x9-2560x1440.png"
        ),
        FrameEntry(
            "snes", "SNES", setOf("snes", "sfc"),
            "16x9%20Collections/Nosh01%201440%20Plain/Super-Nintendo-Entertainment-System-Bezel-16x9-2560x1440.png"
        ),
        FrameEntry(
            "gb", "Game Boy", setOf("gb"),
            "16x9%20Collections/Nosh01%201440%20Plain/Nintendo-Game-Boy-Bezel-16x9-2560x1440.png"
        ),
        FrameEntry(
            "gbc", "Game Boy Color", setOf("gbc"),
            "16x9%20Collections/Nosh01%201440%20Plain/Nintendo-Game-Boy-Color-Bezel-16x9-2560x1440.png"
        ),
        FrameEntry(
            "gba", "Game Boy Advance", setOf("gba"),
            "16x9%20Collections/Nosh01%201440%20Plain/Nintendo-Game-Boy-Advance-Bezel-16x9-2560x1440.png"
        ),
        FrameEntry(
            "n64", "Nintendo 64", setOf("n64"),
            "16x9%20Collections/Nosh01%201440%20Plain/Nintendo-64-Bezel-16x9-2560x1440.png"
        ),
        FrameEntry(
            "genesis", "Sega Genesis", setOf("genesis", "megadrive"),
            "16x9%20Collections/Nosh01%201440%20Plain/Sega-Genesis-Bezel-16x9-2560x1440.png"
        ),
        FrameEntry(
            "mastersystem", "Master System", setOf("mastersystem", "sms"),
            "16x9%20Collections/Nosh01%201440%20Plain/Sega-Master-System-Bezel-16x9-2560x1440.png"
        ),
        FrameEntry(
            "gamegear", "Game Gear", setOf("gamegear", "gg"),
            "16x9%20Collections/Nosh01%201440%20Plain/Sega-Game-Gear-Bezel-16x9-2560x1440.png"
        ),
        FrameEntry(
            "saturn", "Sega Saturn", setOf("saturn"),
            "16x9%20Collections/Nosh01%201440%20Plain/Sega-Saturn-Bezel-16x9-2560x1440.png"
        ),
        FrameEntry(
            "dreamcast", "Dreamcast", setOf("dreamcast"),
            "16x9%20Collections/NyNy77%201080%20Bezel/SegaDreamcast-nyny77.png"
        ),
        FrameEntry(
            "psx", "PlayStation", setOf("psx", "playstation"),
            "16x9%20Collections/Nosh01%201440%20Plain/Sony-Playstation-Bezel-16x9-2560x1440.png"
        ),
        FrameEntry(
            "psp", "PlayStation Portable", setOf("psp"),
            "16x9%20Collections/Nosh01%201440%20Plain/Sony-Playstation-Portable-Bezel-16x9-2560x1440.png"
        ),
        FrameEntry(
            "tg16", "TurboGrafx-16", setOf("tg16", "pce", "pcengine"),
            "16x9%20Collections/Nosh01%201440%20Plain/NEC-TurboGrafx-16-Bezel-16x9-2560x1440.png"
        ),
        FrameEntry(
            "ngp", "Neo Geo Pocket", setOf("ngp", "ngpc"),
            "16x9%20Collections/Nosh01%201440%20Plain/SNK-Neo-Geo-Pocket-Bezel-16x9-2560x1440.png"
        ),
        FrameEntry(
            "atari2600", "Atari 2600", setOf("atari2600", "2600"),
            "16x9%20Collections/Nosh01%201440%20Plain/Atari-2600-Bezel-16x9-2560x1440.png"
        ),
        FrameEntry(
            "lynx", "Atari Lynx", setOf("lynx"),
            "16x9%20Collections/Nosh01%201440%20Plain/Atari-Lynx-Horizontal-Bezel-16x9-2560x1440.png"
        ),
        FrameEntry(
            "wonderswan", "WonderSwan", setOf("wonderswan", "ws"),
            "16x9%20Collections/Nosh01%201440%20Plain/Bandai-WonderSwan-Horizontal-Bezel-16x9-2560x1440.png"
        ),
        FrameEntry(
            "wscolor", "WonderSwan Color", setOf("wonderswancolor", "wsc"),
            "16x9%20Collections/Nosh01%201440%20Plain/Bandai-WonderSwan-Color-Horizontal-Bezel-16x9-2560x1440.png"
        ),
    )

    fun getCatalogFrames(): List<FrameEntry> = catalogFrames

    fun getFramesForPlatform(platformSlug: String): List<FrameEntry> =
        catalogFrames.filter { platformSlug in it.platforms }

    fun getAllFrames(): List<FrameEntry> = catalogFrames

    fun findById(id: String): FrameEntry? =
        catalogFrames.find { it.id == id }

    fun isInstalled(entry: FrameEntry): Boolean =
        entry.id in getInstalledIds()

    fun isInstalled(id: String): Boolean =
        id in getInstalledIds()

    fun getInstalledFramesForPlatform(platformSlug: String): List<FrameEntry> =
        getFramesForPlatform(platformSlug).filter { isInstalled(it) }

    /**
     * [maxWidth] and [maxHeight] are the surface the frame is drawn onto. Source art runs to
     * 4K, which decodes to roughly 33MB of ARGB before it reaches GL; sampling halves both axes
     * together, so the aspect ratio is preserved by construction.
     */
    fun loadFrame(id: String, maxWidth: Int = 0, maxHeight: Int = 0): Bitmap? {
        val file = listOf(getFramesDir(), getCustomFramesDir())
            .map { File(it, "$id.png") }
            .firstOrNull { it.exists() }
            ?: return null

        if (maxWidth <= 0 || maxHeight <= 0) return BitmapFactory.decodeFile(file.absolutePath)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxWidth &&
            bounds.outHeight / (sample * 2) >= maxHeight
        ) {
            sample *= 2
        }

        return BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )
    }

    fun loadFrame(entry: FrameEntry): Bitmap? = loadFrame(entry.id)

    fun getFramesDir(): File =
        File(context.getExternalFilesDir(null), "frames/catalog")

    /**
     * User-imported frames, kept apart from the catalog so [clearDownloadedFrames] cannot take
     * them: a catalog frame re-downloads on demand and an imported one is gone for good.
     */
    fun getCustomFramesDir(): File =
        File(context.getExternalFilesDir(null), "frames/custom")

    fun installedFileFor(entry: FrameEntry): File = when (entry.source) {
        Source.CUSTOM -> File(getCustomFramesDir(), "${entry.id}.png")
        else -> File(getFramesDir(), "${entry.id}.png")
    }

    fun ensureDirectoryExists() {
        getFramesDir().mkdirs()
        getCustomFramesDir().mkdirs()
    }

    /**
     * Clears catalog frames only. The row offering this says they re-download on demand, which
     * is true of a catalog frame and false of one the user imported.
     */
    fun clearDownloadedFrames() {
        val dir = getFramesDir()
        if (dir.exists()) dir.deleteRecursively()
        installedCache = null
    }

    companion object {
        private const val TAG = "FrameRegistry"

        const val GITHUB_RAW_BASE =
            "https://raw.githubusercontent.com/libretro/overlay-borders/master/"

        /**
         * Pinned rather than tracking a branch: this is one person's repository, and a renamed
         * folder would break bezels for users with no app change involved.
         */
        const val DUIMON_RAW_BASE =
            "https://raw.githubusercontent.com/Duimon/Duimon-Mega-Bezel/" +
                "d03dabf6e6b190dbf9b692efd492d8edd21abbb9/Graphics/"

        fun downloadUrl(entry: FrameEntry): String = when (entry.source) {
            Source.LIBRETRO -> "$GITHUB_RAW_BASE${entry.githubPath}"
            Source.DUIMON -> "$DUIMON_RAW_BASE${entry.githubPath}"
            Source.CUSTOM -> ""
        }
    }
}
