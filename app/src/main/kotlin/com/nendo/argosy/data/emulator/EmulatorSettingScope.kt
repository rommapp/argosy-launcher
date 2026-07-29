package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.platform.PlatformDefinitions

/**
 * When an emulator-dependent setting applies.
 *
 * Platform settings and per-game overrides describe the same choices at two scopes, and each used
 * to decide visibility for itself. The predicates drifted: a core row appeared for a platform with
 * one core but not for a game, and a memory card row appeared for any PS2 platform whether or not
 * a card existed. A setting offered in one place and withheld in the other reads as a bug in
 * whichever place the user looked second, so both scopes ask here instead.
 *
 * Whether a scope may override a setting at all is a separate question, and stays with the scope:
 * scanning files or removing them belongs to a platform and never to one game.
 */
object EmulatorSettingScope {

    fun showsCoreSelection(isCoreSelectable: Boolean, coreCount: Int): Boolean =
        isCoreSelectable && coreCount > 0

    /**
     * A memory card row is a picker over the cards that exist, so it needs at least one. A count
     * that was never resolved is reported as negative rather than zero and hides the row too.
     */
    fun showsMemoryCard(platformSlug: String, cardCount: Int): Boolean =
        PlatformDefinitions.getCanonicalSlug(platformSlug) == "ps2" && cardCount > 0

    fun showsExtensionSelection(optionCount: Int): Boolean = optionCount > 0

    fun showsDisplayTarget(hasSecondaryDisplay: Boolean): Boolean = hasSecondaryDisplay

    /**
     * A save path a single game can own. Folder platforms nest every game under one console tree,
     * so there is no per-game path to set; those scopes show the inherited location instead.
     */
    fun showsPerGameSavePath(config: SavePathConfig?, platformSlug: String): Boolean =
        SavePathRegistry.supportsPerGameSavePath(config, platformSlug)
}
