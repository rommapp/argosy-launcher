package com.nendo.argosy.ui.dualscreen

/**
 * What the showcase screen draws for the focused item of any destination: its name, a line of
 * context, artwork and facts.
 *
 * [backdropUrl] is the full-bleed image and [artUrl] the upright one; a destination with only one
 * leaves the other null. [logoUrl] is the game's clear logo, drawn in place of the title text where
 * a layout shows one. [isGameTitle] routes [title] through the series/entry formatter.
 * [videoPath] is the game's RomM gameplay video relative to the server's resources, and
 * [youtubeVideoId] the trailer played when it has none.
 */
data class CompanionDetail(
    val title: String,
    val subtitle: String? = null,
    val platformSlug: String? = null,
    val overview: String? = null,
    val artUrl: String? = null,
    val backdropUrl: String? = null,
    val backdropIsScreenshotOrArt: Boolean = false,
    val facts: List<CompanionFact> = emptyList(),
    val isGameTitle: Boolean = false,
    val spineUrl: String? = null,
    val backUrl: String? = null,
    val box3dUrl: String? = null,
    val logoUrl: String? = null,
    val stats: CompanionGameStats? = null,
    val gameId: Long? = null,
    val videoPath: String? = null,
    val youtubeVideoId: String? = null
)

/**
 * A focused game's values, kept typed so the presentation style decides which appear and the
 * renderer formats them. Zero counts and null values mean the game has no such value.
 */
data class CompanionGameStats(
    val developer: String? = null,
    val releaseYear: Int? = null,
    val players: String? = null,
    val genre: String? = null,
    val communityRating: Float? = null,
    val userRating: Int = 0,
    val userDifficulty: Int = 0,
    val playTimeMinutes: Int = 0,
    val timeToBeatMainSec: Int? = null,
    val timeToBeatCompletionistSec: Int? = null,
    val status: String? = null,
    val achievementCount: Int = 0,
    val earnedAchievementCount: Int = 0,
    val friends: List<com.nendo.argosy.data.social.FriendActivity> = emptyList()
)

data class CompanionFact(
    val label: String,
    val value: String
)

/**
 * What a button does on the screen being driven, spelled out on the screen describing it.
 */
data class CompanionHint(
    val button: com.nendo.argosy.ui.components.InputButton,
    val label: String,
    val forced: Boolean = false
)
