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

    data class FrameEntry(
        val id: String,
        val displayName: String,
        val platforms: Set<String>,
        val githubPath: String,
        val preInstall: Boolean = false
    )

    private var installedCache: Set<String>? = null

    fun getInstalledIds(): Set<String> {
        installedCache?.let { return it }
        val dir = getFramesDir()
        val ids = if (dir.exists()) {
            dir.listFiles()
                ?.filter { it.extension.lowercase() == "png" }
                ?.map { it.nameWithoutExtension }
                ?.toSet()
                ?: emptySet()
        } else {
            emptySet()
        }
        installedCache = ids
        return ids
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

    fun loadFrame(id: String): Bitmap? {
        val file = File(getFramesDir(), "$id.png")
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    fun loadFrame(entry: FrameEntry): Bitmap? = loadFrame(entry.id)

    fun getFramesDir(): File =
        File(context.getExternalFilesDir(null), "frames")

    fun ensureDirectoryExists() {
        getFramesDir().mkdirs()
    }

    companion object {
        private const val TAG = "FrameRegistry"

        const val GITHUB_RAW_BASE =
            "https://raw.githubusercontent.com/libretro/overlay-borders/master/"

        fun downloadUrl(entry: FrameEntry): String =
            "$GITHUB_RAW_BASE${entry.githubPath}"
    }
}
