package com.nendo.argosy.data.emulator.savepath

import com.nendo.argosy.data.emulator.SavePathConfig
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.data.storage.FileAccessLayer

/**
 * Whether a folder looks like somewhere a platform's saves actually live.
 *
 * [Unreadable] is not a failure of the folder, it is the absence of an answer. A card that is out
 * or a volume Argosy cannot list must never be reported as the wrong choice, because the user
 * would be told to fix something that is already correct.
 */
sealed class SavePathVerdict {
    data object Ok : SavePathVerdict()
    data object Unreadable : SavePathVerdict()
    data class LooksWrong(val reason: String) : SavePathVerdict()
}

/**
 * One platform's opinion about the shape of its own save directory.
 *
 * Adding a platform means adding a rule, never editing the resolver: the resolver asks each rule
 * whether it applies and takes the first that does. A rule reports only what it can see, so every
 * check runs through [FileAccessLayer] and an unlistable directory answers [SavePathVerdict.Unreadable].
 */
interface SavePathShapeRule {
    fun appliesTo(config: SavePathConfig, platformSlug: String): Boolean
    fun validate(path: String, config: SavePathConfig, fal: FileAccessLayer): SavePathVerdict
}

/**
 * The three ways a directory can fail to answer, kept apart. Collapsing them loses the difference
 * between "the volume is out" and "you picked a file", which is the difference between saying
 * nothing and saying something useful.
 */
private sealed class Listing {
    data class Entries(val names: List<String>) : Listing()
    data object NotADirectory : Listing()
    data object Unreadable : Listing()
}

private fun listDirectories(fal: FileAccessLayer, path: String): Listing = when {
    !fal.exists(path) -> Listing.Unreadable
    !fal.isDirectory(path) -> Listing.NotADirectory
    else -> fal.listFiles(path)
        ?.let { entries -> Listing.Entries(entries.filter { it.isDirectory }.map { it.name }) }
        ?: Listing.Unreadable
}

private fun Listing.verdictOr(onEntries: (List<String>) -> SavePathVerdict): SavePathVerdict =
    when (this) {
        is Listing.Entries -> if (names.isEmpty()) SavePathVerdict.Ok else onEntries(names)
        Listing.NotADirectory -> SavePathVerdict.LooksWrong("That's a file, not a folder.")
        Listing.Unreadable -> SavePathVerdict.Unreadable
    }

private fun isEightHex(name: String): Boolean =
    name.length == 8 && name.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }

/**
 * Wii saves live in the emulated NAND at `title/<type>/<id>/data`, rooted at the emulator's `Wii`
 * directory (Dolphin `Source/Core/Common/NandPaths.cpp`, `GetTitleDataPath`). A NAND root is
 * recognisable by the sibling directories Dolphin creates beside `title`.
 *
 * Accepts any level from the NAND root down to a title's own directory, because all of them are
 * places a user could reasonably point at and all of them can be resolved downwards. A folder with
 * none of those markers is the case worth warning about: it is usually the emulator's `files`
 * directory or an unrelated folder, and saves will never be found under it.
 */
class WiiNandShapeRule : SavePathShapeRule {
    override fun appliesTo(config: SavePathConfig, platformSlug: String): Boolean =
        PlatformDefinitions.getCanonicalSlug(platformSlug) == "wii"

    override fun validate(path: String, config: SavePathConfig, fal: FileAccessLayer): SavePathVerdict =
        listDirectories(fal, path).verdictOr { entries ->
            val lower = entries.map { it.lowercase() }
            when {
                "title" in lower -> SavePathVerdict.Ok
                entries.any { isEightHex(it) } -> SavePathVerdict.Ok
                "data" in lower || "content" in lower -> SavePathVerdict.Ok
                else -> SavePathVerdict.LooksWrong(
                    "This doesn't look like a Wii NAND folder. Pick the emulator's Wii folder, " +
                        "the one holding title."
                )
            }
        }
}

/**
 * GameCube memory cards are GCI files filed under a region and a card slot, which Argosy writes as
 * `<base>/<region>/Card A/<file>.gci`. The base is therefore the directory holding the regions.
 */
class GciShapeRule : SavePathShapeRule {
    private val regions = setOf("usa", "eur", "jap", "kor")

    override fun appliesTo(config: SavePathConfig, platformSlug: String): Boolean = config.usesGciFormat

    /**
     * A base holding `Card A` directly is one level too deep: the writer inserts the region itself,
     * so saves would land at `<base>/USA/USA/Card A` and discovery would never find them again.
     * That is worth naming rather than accepting.
     */
    override fun validate(path: String, config: SavePathConfig, fal: FileAccessLayer): SavePathVerdict =
        listDirectories(fal, path).verdictOr { entries ->
            val lower = entries.map { it.lowercase() }
            when {
                lower.any { it in regions } -> SavePathVerdict.Ok
                lower.any { it.startsWith("card") } -> SavePathVerdict.LooksWrong(
                    "This is a region folder. Pick the one above it, holding USA, EUR, JAP or KOR."
                )
                else -> SavePathVerdict.LooksWrong(
                    "No memory card folders here. Pick the folder holding USA, EUR, JAP or KOR."
                )
            }
        }
}

/**
 * The 3DS save tree hangs off the emulated SD card root, the `Nintendo 3DS` directory.
 */
class N3dsShapeRule : SavePathShapeRule {
    override fun appliesTo(config: SavePathConfig, platformSlug: String): Boolean =
        PlatformDefinitions.getCanonicalSlug(platformSlug) == "3ds"

    override fun validate(path: String, config: SavePathConfig, fal: FileAccessLayer): SavePathVerdict {
        if (path.lowercase().contains("nintendo 3ds")) return SavePathVerdict.Ok
        return listDirectories(fal, path).verdictOr { entries ->
            val lower = entries.map { it.lowercase() }
            if (lower.any { it == "nintendo 3ds" || it == "sdmc" }) {
                SavePathVerdict.Ok
            } else {
                SavePathVerdict.LooksWrong(
                    "No Nintendo 3DS folder here. Pick the emulator's sdmc folder or the one inside it."
                )
            }
        }
    }
}

/**
 * What is left: a directory holding save files directly. Only the folder itself is checked, since
 * a platform whose saves are named after the rom cannot be told apart from an empty folder the
 * user is about to fill.
 */
class FlatSaveShapeRule : SavePathShapeRule {
    override fun appliesTo(config: SavePathConfig, platformSlug: String): Boolean = true

    override fun validate(path: String, config: SavePathConfig, fal: FileAccessLayer): SavePathVerdict {
        if (!fal.exists(path)) return SavePathVerdict.Unreadable
        if (!fal.isDirectory(path)) {
            return SavePathVerdict.LooksWrong("That's a file, not a folder.")
        }
        return SavePathVerdict.Ok
    }
}
