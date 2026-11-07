package com.nendo.argosy.data.emulator.armsx2

import com.nendo.argosy.data.emulator.ini.IniFileManager
import com.nendo.argosy.util.Logger
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages custom texture replacement packs and configuration for the ARMSX2 (PCSX2 libretro) core.
 *
 * Upstream PCSX2 requires replacement textures to reside in:
 * `pcsx2/textures/<serial>/replacements/`
 * where `<serial>` is the normalized PS2 serial (e.g. `SLUS-20002`, `SCES-51234`).
 *
 * Configures `LoadTextureReplacements` under section `[EmuCore/GS]` in `pcsx2/inis/armsx2-libretro.ini`
 * safely via [IniFileManager], respecting existing user configuration and avoiding unconditional overrides.
 */
@Singleton
class Armsx2TextureManager @Inject constructor(
    private val iniFileManager: IniFileManager
) {

    /**
     * Zero-argument constructor for test or direct instantiation.
     */
    constructor() : this(IniFileManager())

    /**
     * Normalizes a PlayStation 2 serial / Title ID into canonical PCSX2 format: `AAAA-NNNNN`.
     *
     * Handles:
     * - `SLUS-20002` -> `SLUS-20002`
     * - `slus-20002` -> `SLUS-20002`
     * - `SLUS20002` -> `SLUS-20002`
     * - `SLUS_200.02` -> `SLUS-20002`
     * - `SLUS_200.02;1` -> `SLUS-20002`
     * - `BASLUS-20002` (save folder prefix) -> `SLUS-20002`
     * - `BASLUS-20152AC04` -> `SLUS-20152`
     */
    fun normalizeSerial(rawSerial: String): String {
        val trimmed = rawSerial.trim().trim('[', ']', '(', ')', '{', '}').uppercase(Locale.ROOT)
        if (trimmed.isEmpty()) return ""

        // Strip PS save prefix if present (e.g. BASLUS-20002 -> SLUS-20002)
        val withoutSavePrefix = if (trimmed.matches(Regex("""^B[A-Z][A-Z]{4}[-_]?\d{3,5}.*"""))) {
            trimmed.substring(2)
        } else {
            trimmed
        }

        // Disc executable naming: SLUS_200.02 or SLUS-200.02;1
        val discExeMatch = Regex("""^([A-Z]{4})[_-](\d{3})\.(\d{2})(?:;.*)?$""").find(withoutSavePrefix)
        if (discExeMatch != null) {
            val (letters, p1, p2) = discExeMatch.destructured
            return "$letters-$p1$p2"
        }

        // Standard serial with or without dash/suffix: SLUS-20002 or SLUS20002
        val standardMatch = Regex("""^([A-Z]{4})[-_]?(\d{5})(?:.*)?$""").find(withoutSavePrefix)
        if (standardMatch != null) {
            val (letters, digits) = standardMatch.destructured
            return "$letters-$digits"
        }

        return withoutSavePrefix
    }

    /**
     * Attempts to resolve a game serial from a ROM filename or path using common patterns.
     */
    fun resolveSerialFromFileName(fileName: String): String? {
        val match = Regex("""(?:\[|\(|\b)([A-Z]{4}[-_]?\d{5}|[A-Z]{4}[_-]\d{3}\.\d{2})(?:\]|\)|\b)""", RegexOption.IGNORE_CASE)
            .find(fileName)
        return match?.groupValues?.getOrNull(1)?.let { normalizeSerial(it) }
    }

    /**
     * Returns the base texture directory under [systemDir]: `pcsx2/textures`.
     */
    fun getTexturesBaseDir(systemDir: File): File = File(systemDir, TEXTURES_BASE_PATH)

    /**
     * Returns the game-specific texture directory: `pcsx2/textures/<serial>`.
     */
    fun getGameTexturesDir(systemDir: File, serial: String): File {
        val cleanSerial = normalizeSerial(serial)
        return File(getTexturesBaseDir(systemDir), cleanSerial)
    }

    /**
     * Returns the replacement textures directory for a specific serial:
     * `pcsx2/textures/<serial>/replacements`.
     */
    fun getReplacementsDir(systemDir: File, serial: String): File {
        return File(getGameTexturesDir(systemDir, serial), REPLACEMENTS_DIR_NAME)
    }

    /**
     * Ensures that the replacement textures directory exists on disk, creating parent folders if needed.
     */
    fun ensureReplacementsDir(systemDir: File, serial: String): File {
        val dir = getReplacementsDir(systemDir, serial)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Checks if replacement textures are installed for the given [serial].
     */
    fun hasReplacements(systemDir: File, serial: String): Boolean {
        val replacementsDir = getReplacementsDir(systemDir, serial)
        if (!replacementsDir.exists() || !replacementsDir.isDirectory) return false
        val files = replacementsDir.listFiles() ?: return false
        return files.any { it.isFile && !it.name.startsWith(".") }
    }

    /**
     * Returns the file reference for ARMSX2 configuration: `pcsx2/inis/armsx2-libretro.ini`.
     */
    fun getIniFile(systemDir: File): File = File(systemDir, ARMSX2_INI_PATH)

    /**
     * Reads `LoadTextureReplacements` under `[EmuCore/GS]`.
     * Returns true if enabled, false if disabled, or null if unset / unreadable.
     */
    fun isTextureReplacementEnabled(systemDir: File): Boolean? {
        val iniFile = getIniFile(systemDir)
        val value = iniFileManager.get(iniFile, SECTION_GS, KEY_LOAD_TEXTURES) ?: return null
        return when (value.trim().lowercase(Locale.ROOT)) {
            "true", "1", "yes", "enabled" -> true
            "false", "0", "no", "disabled" -> false
            else -> null
        }
    }

    /**
     * Sets `LoadTextureReplacements` under `[EmuCore/GS]`.
     */
    fun setTextureReplacementEnabled(systemDir: File, enabled: Boolean): Boolean {
        val iniFile = getIniFile(systemDir)
        return iniFileManager.writeSetting(
            file = iniFile,
            section = SECTION_GS,
            key = KEY_LOAD_TEXTURES,
            value = enabled.toString()
        )
    }

    /**
     * Safely manages ARMSX2 texture replacement configuration for game launch.
     *
     * Principles:
     * - If [userPreference] is explicitly supplied (e.g. from UI settings or per-game config),
     *   it is honored.
     * - If [userPreference] is null and the setting has already been configured in `armsx2-libretro.ini`
     *   (e.g., user manually set `LoadTextureReplacements = false`), Argosy preserves the user choice
     *   and does NOT overwrite it.
     * - If the setting is unset in the INI, and custom textures exist for [serial], it enables
     *   texture replacements automatically.
     */
    fun configureTexturesForGame(
        systemDir: File,
        serial: String?,
        userPreference: Boolean? = null
    ): Boolean {
        try {
            // 1. Explicit user preference overrides existing INI
            if (userPreference != null) {
                val current = isTextureReplacementEnabled(systemDir)
                return if (current != userPreference) {
                    setTextureReplacementEnabled(systemDir, userPreference)
                } else {
                    true
                }
            }

            // 2. If already configured in INI (user manual choice), respect it without touching
            val existing = isTextureReplacementEnabled(systemDir)
            if (existing != null) {
                return true
            }

            // 3. Unset in INI: enable if replacements exist for this game
            if (!serial.isNullOrBlank() && hasReplacements(systemDir, serial)) {
                Logger.info(TAG, "Textures found for $serial; enabling LoadTextureReplacements")
                return setTextureReplacementEnabled(systemDir, true)
            }

            return true
        } catch (e: Exception) {
            Logger.warn(TAG, "Failed to configure textures for game: ${e.message}")
            return false
        }
    }

    companion object {
        private const val TAG = "Armsx2TextureManager"

        const val TEXTURES_BASE_PATH = "pcsx2/textures"
        const val REPLACEMENTS_DIR_NAME = "replacements"
        const val ARMSX2_INI_PATH = "pcsx2/inis/armsx2-libretro.ini"
        const val SECTION_GS = "EmuCore/GS"
        const val KEY_LOAD_TEXTURES = "LoadTextureReplacements"
        const val KEY_PRECACHE_TEXTURES = "PrecacheTextureReplacements"
    }
}
