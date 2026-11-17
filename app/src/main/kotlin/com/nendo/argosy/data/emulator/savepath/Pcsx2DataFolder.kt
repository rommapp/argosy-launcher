package com.nendo.argosy.data.emulator.savepath

import com.nendo.argosy.data.storage.FileAccessLayer
import java.io.File

/**
 * What a PCSX2-family emulator's `PCSX2-Android.ini` says about its cards. [memoryCardsFolder]
 * is `[Folders] MemoryCards` as written, relative to the data folder unless absolute.
 * [slot1Card] is `[MemoryCards] Slot1_Filename`, null when slot 1 is disabled or names nothing.
 */
data class Pcsx2Settings(val memoryCardsFolder: String?, val slot1Card: String?)

/**
 * Where a PCSX2-family emulator's cards are: [cardsDir] is searched for them, and [activeCard]
 * is the path of the card slot 1 uses, null when the emulator names none.
 */
data class Pcsx2CardBase(val cardsDir: String, val activeCard: String?)

/**
 * The data folder a standalone PCSX2-family emulator runs from, told apart from stale copies by
 * the `PCSX2-Android.ini` it keeps there, in `inis/` or at the top.
 */
object Pcsx2DataFolder {
    const val INI_NAME = "PCSX2-Android.ini"
    private val INI_DIRS = listOf("inis/", "")
    private const val DEFAULT_CARDS_FOLDER = "memcards"
    private const val FOLDERS_SECTION = "Folders"
    private const val CARDS_SECTION = "MemoryCards"
    private const val CARDS_FOLDER_KEY = "MemoryCards"
    private const val SLOT1_ENABLE_KEY = "Slot1_Enable"
    private const val SLOT1_FILENAME_KEY = "Slot1_Filename"
    private const val FILE_CARD_SUFFIX = ".ps2"
    private val DISABLED_VALUES = setOf("false", "0", "no", "off")

    fun parse(text: String): Pcsx2Settings {
        val folders = IniSections.entries(text, FOLDERS_SECTION)
        val cards = IniSections.entries(text, CARDS_SECTION)
        val slot1Disabled = cards[SLOT1_ENABLE_KEY]?.trim()?.lowercase()?.let { it in DISABLED_VALUES } == true
        val slot1 = cards[SLOT1_FILENAME_KEY]?.trim()?.takeIf { !slot1Disabled && it.isNotEmpty() }
        val cardsFolder = folders[CARDS_FOLDER_KEY]?.trim()?.takeIf { it.isNotEmpty() }
        return Pcsx2Settings(cardsFolder, slot1)
    }

    /**
     * The ini settings of the data folder [dataRoot], or null when it holds no `PCSX2-Android.ini`.
     */
    fun read(dataRoot: String, fal: FileAccessLayer): Pcsx2Settings? {
        val ini = iniPath(dataRoot, fal) ?: return null
        val text = fal.getInputStream(ini)?.use { it.bufferedReader().readText() } ?: return null
        return parse(text)
    }

    /**
     * The cards folder of the data folder [dataRoot]: the ini's `MemoryCards` folder, `memcards`
     * when [settings] names none.
     */
    fun cardsDir(dataRoot: String, settings: Pcsx2Settings?): String {
        val folder = settings?.memoryCardsFolder?.trimEnd('/') ?: DEFAULT_CARDS_FOLDER
        return if (folder.startsWith("/")) folder else "${dataRoot.trimEnd('/')}/$folder"
    }

    fun cardBase(dataRoot: String, settings: Pcsx2Settings?): Pcsx2CardBase {
        val cards = cardsDir(dataRoot, settings)
        return Pcsx2CardBase(cards, settings?.slot1Card?.let { "$cards/$it" })
    }

    /**
     * Whether a card that is not on disk yet is a folder card by its name: anything not ending
     * `.ps2`, which names a file card.
     */
    fun isFolderCardName(name: String): Boolean = !name.endsWith(FILE_CARD_SUFFIX, ignoreCase = true)

    /**
     * [paths] followed by each one placed on every other storage volume in [volumes], for a path
     * that lies on one of them.
     */
    fun acrossVolumes(paths: List<String>, volumes: List<String>): List<String> {
        val roots = volumes.map { it.trimEnd('/') }.filter { it.isNotEmpty() }
        return paths.flatMap { path ->
            val volume = roots.firstOrNull { path.startsWith("$it/") } ?: return@flatMap listOf(path)
            val relative = path.removePrefix(volume)
            listOf(path) + roots.map { it + relative }
        }.distinct()
    }

    /**
     * The cards folder among [candidates] whose data folder, its parent, holds a
     * `PCSX2-Android.ini`, the newest ini first and candidate order on a tie. Null when none does.
     */
    fun liveCardsDir(candidates: List<String>, fal: FileAccessLayer): String? =
        candidates
            .map { it.trimEnd('/') }
            .distinct()
            .mapNotNull { cards ->
                val dataRoot = File(cards).parent ?: return@mapNotNull null
                iniPath(dataRoot, fal)?.let { cards to fal.lastModified(it) }
            }
            .maxByOrNull { it.second }
            ?.first

    private fun iniPath(dataRoot: String, fal: FileAccessLayer): String? {
        val root = dataRoot.trimEnd('/')
        return INI_DIRS.map { "$root/$it$INI_NAME" }.firstOrNull { fal.exists(it) }
    }
}
