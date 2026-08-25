package com.nendo.argosy.ui.dualscreen

/**
 * What the showcase screen draws while the driven screen is somewhere other than Home.
 *
 * One shape rather than one per destination. Library and Media focus different things, a platform
 * and a title, but the showcase says the same kind of thing about both: what it is called, a line
 * of context, some artwork and whatever facts are worth reading from across a room. Modelling each
 * destination separately would mean a second surface to keep in step every time either changes.
 *
 * [backdropUrl] is the full-bleed image and [artUrl] the upright one; a destination that has only
 * one of them leaves the other null rather than substituting, since a poster stretched across a
 * landscape frame is what makes a background look soft.
 *
 * [isGameTitle] routes [title] through the series/entry formatter. A film's title is one phrase and
 * splitting it on a colon would misread it, so the destination that knows it is publishing a game
 * says so rather than the renderer guessing from the punctuation.
 */
data class CompanionDetail(
    val title: String,
    val subtitle: String? = null,
    val overview: String? = null,
    val artUrl: String? = null,
    val backdropUrl: String? = null,
    val facts: List<CompanionFact> = emptyList(),
    val isGameTitle: Boolean = false
)

data class CompanionFact(
    val label: String,
    val value: String
)
