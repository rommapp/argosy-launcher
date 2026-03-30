package com.nendo.argosy.data.steam

val STEAM_GENRE_MAP = mapOf(
    "1" to "Action",
    "2" to "Strategy",
    "3" to "RPG",
    "4" to "Casual",
    "5" to "Simulation",
    "6" to "Demo",
    "9" to "Racing",
    "10" to "Utilities",
    "12" to "Adventure",
    "15" to "Audio Production",
    "16" to "Design & Illustration",
    "17" to "Animation & Modeling",
    "18" to "Sports",
    "21" to "Education",
    "23" to "Indie",
    "25" to "Free to Play",
    "28" to "Video Production",
    "29" to "Massively Multiplayer",
    "30" to "Web Publishing",
    "37" to "Early Access",
    "50" to "Accounting",
    "51" to "Software Training",
    "52" to "Game Development",
    "53" to "Photo Editing",
    "54" to "Sexual Content",
    "55" to "Documentary",
    "56" to "Tutorial",
    "57" to "360 Video",
    "58" to "Short",
    "59" to "Episodic",
    "60" to "Horror",
    "70" to "Action-Adventure",
    "71" to "Fighting",
    "72" to "Puzzle",
    "73" to "Platformer",
    "74" to "Party-Based"
)

fun resolveSteamGenres(genre: String?): String? {
    if (genre.isNullOrBlank()) return null
    val resolved = genre.split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { STEAM_GENRE_MAP[it] ?: it }
        .filter { !it.all { c -> c.isDigit() } }
    return if (resolved.isEmpty()) null else resolved.joinToString(", ")
}
