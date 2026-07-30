package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.platform.PlatformDefinitions

/**
 * Whether an emulator-dependent setting applies. Platform settings and per-game overrides both
 * ask here so their visibility cannot diverge; do not re-derive either predicate locally.
 *
 * Whether a scope may override a setting at all stays with the scope: scanning or removing files
 * belongs to a platform, never to one game.
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
