package com.nendo.argosy.data.model

import com.nendo.argosy.data.local.entity.GameListItem
import java.time.Instant
import java.time.temporal.ChronoUnit

enum class SortOption(val label: String, val defaultDescending: Boolean) {
    TITLE("A-Z", false),
    RATING("Rating", true),
    USER_RATING("User Rating", true),
    DIFFICULTY("Difficulty", true),
    RELEASE_YEAR("Release Year", true),
    PLAY_COUNT("Play Count", true),
    PLAY_TIME("Play Time", true),
    LAST_PLAYED("Last Played", true),
    RECENTLY_ADDED("Recently Added", true)
}

data class ActiveSort(
    val option: SortOption = SortOption.TITLE,
    val descending: Boolean = option.defaultDescending
)

data class GameSection(
    val label: String,
    val sidebarLabel: String,
    val games: List<GameListItem>
)

data class Section<T>(
    val label: String,
    val sidebarLabel: String,
    val items: List<T>
)

fun computeSections(
    games: List<GameListItem>,
    sort: ActiveSort
): List<GameSection> {
    val props = GameListItemProps
    val sections = computeGenericSections(games, sort, props)
    val result = sections.map { GameSection(it.label, it.sidebarLabel, it.items) }
    return if (sort.descending) result else result.reversed()
}

fun <T> computeGenericSections(
    items: List<T>,
    sort: ActiveSort,
    props: SortableProps<T>
): List<Section<T>> {
    return when (sort.option) {
        SortOption.TITLE -> bucketByTitle(items, props)
        SortOption.RATING -> bucketByRating(items, props)
        SortOption.USER_RATING -> bucketByUserRating(items, props)
        SortOption.DIFFICULTY -> bucketByDifficulty(items, props)
        SortOption.RELEASE_YEAR -> bucketByReleaseYear(items, props)
        SortOption.PLAY_COUNT -> bucketByPlayCount(items, props)
        SortOption.PLAY_TIME -> bucketByPlayTime(items, props)
        SortOption.LAST_PLAYED -> bucketByLastPlayed(items, props)
        SortOption.RECENTLY_ADDED -> bucketByRecentlyAdded(items, props)
    }
}

interface SortableProps<T> {
    fun sortTitle(item: T): String
    fun rating(item: T): Float?
    fun userRating(item: T): Int
    fun userDifficulty(item: T): Int
    fun releaseYear(item: T): Int?
    fun playCount(item: T): Int
    fun playTimeMinutes(item: T): Int
    fun lastPlayedEpochMilli(item: T): Long?
    fun addedAtEpochMilli(item: T): Long
}

object GameListItemProps : SortableProps<GameListItem> {
    override fun sortTitle(item: GameListItem) = item.sortTitle
    override fun rating(item: GameListItem) = item.rating
    override fun userRating(item: GameListItem) = item.userRating
    override fun userDifficulty(item: GameListItem) = item.userDifficulty
    override fun releaseYear(item: GameListItem) = item.releaseYear
    override fun playCount(item: GameListItem) = item.playCount
    override fun playTimeMinutes(item: GameListItem) = item.playTimeMinutes
    override fun lastPlayedEpochMilli(item: GameListItem) = item.lastPlayed?.toEpochMilli()
    override fun addedAtEpochMilli(item: GameListItem) = item.addedAt.toEpochMilli()
}

private fun <T> bucketByTitle(items: List<T>, p: SortableProps<T>): List<Section<T>> {
    val sorted = items.sortedBy { p.sortTitle(it) }
    val grouped = sorted.groupBy { item ->
        val first = p.sortTitle(item).uppercase().firstOrNull()
        if (first != null && first.isLetter()) first.toString() else "#"
    }
    val letterKeys = grouped.keys.filter { it != "#" }.sortedDescending()
    val orderedKeys = if (grouped.containsKey("#")) letterKeys + listOf("#") else letterKeys
    return orderedKeys.mapNotNull { key ->
        grouped[key]?.let { Section(label = key, sidebarLabel = key, items = it) }
    }
}

private fun <T> bucketByRating(items: List<T>, p: SortableProps<T>): List<Section<T>> {
    data class Tier(val label: String, val sidebarLabel: String, val range: IntRange?)
    val tiers = listOf(
        Tier("90+", "90+", 90..100),
        Tier("80-89", "80+", 80..89),
        Tier("70-79", "70+", 70..79),
        Tier("60-69", "60+", 60..69),
        Tier("50-59", "50+", 50..59),
        Tier("Under 50", "<50", 0..49),
        Tier("Unrated", "N/A", null)
    )
    val sorted = items.sortedByDescending { p.rating(it) ?: -1f }
    return tiers.mapNotNull { tier ->
        val matching = sorted.filter { item ->
            val r = p.rating(item)
            if (tier.range == null) r == null
            else r != null && r.toInt() in tier.range
        }
        if (matching.isNotEmpty()) Section(tier.label, tier.sidebarLabel, matching) else null
    }
}

private fun <T> bucketByUserRating(items: List<T>, p: SortableProps<T>): List<Section<T>> {
    val sorted = items.sortedByDescending { p.userRating(it) }
    val buckets = (10 downTo 1).mapNotNull { value ->
        val matching = sorted.filter { p.userRating(it) == value }
        if (matching.isNotEmpty()) Section("$value", "$value", matching) else null
    }
    val unrated = sorted.filter { p.userRating(it) == 0 }
    return if (unrated.isNotEmpty()) buckets + Section("Unrated", "N/A", unrated) else buckets
}

private fun <T> bucketByDifficulty(items: List<T>, p: SortableProps<T>): List<Section<T>> {
    val sorted = items.sortedByDescending { p.userDifficulty(it) }
    val buckets = (10 downTo 1).mapNotNull { value ->
        val matching = sorted.filter { p.userDifficulty(it) == value }
        if (matching.isNotEmpty()) Section("$value", "$value", matching) else null
    }
    val unrated = sorted.filter { p.userDifficulty(it) == 0 }
    return if (unrated.isNotEmpty()) buckets + Section("Unrated", "N/A", unrated) else buckets
}

private fun <T> bucketByReleaseYear(items: List<T>, p: SortableProps<T>): List<Section<T>> {
    data class Decade(val label: String, val sidebarLabel: String, val range: IntRange?)
    val decades = listOf(
        Decade("2020s", "20s", 2020..2029),
        Decade("2010s", "10s", 2010..2019),
        Decade("2000s", "00s", 2000..2009),
        Decade("1990s", "90s", 1990..1999),
        Decade("1980s", "80s", 1980..1989),
        Decade("Older", "Older", 0..1979),
        Decade("Unknown", "N/A", null)
    )
    val sorted = items.sortedByDescending { p.releaseYear(it) ?: 0 }
    return decades.mapNotNull { decade ->
        val matching = sorted.filter { item ->
            val y = p.releaseYear(item)
            if (decade.range == null) y == null
            else y != null && y in decade.range
        }
        if (matching.isNotEmpty()) Section(decade.label, decade.sidebarLabel, matching) else null
    }
}

private fun <T> bucketByPlayCount(items: List<T>, p: SortableProps<T>): List<Section<T>> {
    data class Tier(val label: String, val sidebarLabel: String, val min: Int, val max: Int)
    val tiers = listOf(
        Tier("50+", "50+", 50, Int.MAX_VALUE),
        Tier("20-49", "20+", 20, 49),
        Tier("10-19", "10+", 10, 19),
        Tier("5-9", "5+", 5, 9),
        Tier("1-4", "1+", 1, 4),
        Tier("Never Played", "0", 0, 0)
    )
    val sorted = items.sortedByDescending { p.playCount(it) }
    return tiers.mapNotNull { tier ->
        val matching = sorted.filter { p.playCount(it) in tier.min..tier.max }
        if (matching.isNotEmpty()) Section(tier.label, tier.sidebarLabel, matching) else null
    }
}

private fun <T> bucketByPlayTime(items: List<T>, p: SortableProps<T>): List<Section<T>> {
    data class Tier(val label: String, val sidebarLabel: String, val minMinutes: Int, val maxMinutes: Int)
    val tiers = listOf(
        Tier("100+ hrs", "100h+", 6000, Int.MAX_VALUE),
        Tier("50-99 hrs", "50h+", 3000, 5999),
        Tier("20-49 hrs", "20h+", 1200, 2999),
        Tier("10-19 hrs", "10h+", 600, 1199),
        Tier("5-9 hrs", "5h+", 300, 599),
        Tier("1-4 hrs", "1h+", 60, 299),
        Tier("Under 1 hr", "<1h", 1, 59),
        Tier("Never Played", "0", 0, 0)
    )
    val sorted = items.sortedByDescending { p.playTimeMinutes(it) }
    return tiers.mapNotNull { tier ->
        val matching = sorted.filter { p.playTimeMinutes(it) in tier.minMinutes..tier.maxMinutes }
        if (matching.isNotEmpty()) Section(tier.label, tier.sidebarLabel, matching) else null
    }
}

private fun <T> bucketByLastPlayed(items: List<T>, p: SortableProps<T>): List<Section<T>> {
    val now = Instant.now()
    val todayStart = now.truncatedTo(ChronoUnit.DAYS)
    val weekStart = todayStart.minus(7, ChronoUnit.DAYS)
    val monthStart = todayStart.minus(30, ChronoUnit.DAYS)
    val yearStart = todayStart.minus(365, ChronoUnit.DAYS)

    val todayMs = todayStart.toEpochMilli()
    val weekMs = weekStart.toEpochMilli()
    val monthMs = monthStart.toEpochMilli()
    val yearMs = yearStart.toEpochMilli()

    val sorted = items.sortedByDescending { p.lastPlayedEpochMilli(it) ?: 0L }

    data class Bucket(val label: String, val sidebarLabel: String, val predicate: (T) -> Boolean)
    val buckets = listOf(
        Bucket("Today", "Today") { val lp = p.lastPlayedEpochMilli(it); lp != null && lp > todayMs },
        Bucket("This Week", "Week") { val lp = p.lastPlayedEpochMilli(it); lp != null && lp > weekMs && lp <= todayMs },
        Bucket("This Month", "Month") { val lp = p.lastPlayedEpochMilli(it); lp != null && lp > monthMs && lp <= weekMs },
        Bucket("This Year", "Year") { val lp = p.lastPlayedEpochMilli(it); lp != null && lp > yearMs && lp <= monthMs },
        Bucket("Older", "Older") { val lp = p.lastPlayedEpochMilli(it); lp != null && lp <= yearMs },
        Bucket("Never Played", "Never") { p.lastPlayedEpochMilli(it) == null }
    )
    return buckets.mapNotNull { bucket ->
        val matching = sorted.filter(bucket.predicate)
        if (matching.isNotEmpty()) Section(bucket.label, bucket.sidebarLabel, matching) else null
    }
}

private fun <T> bucketByRecentlyAdded(items: List<T>, p: SortableProps<T>): List<Section<T>> {
    val now = Instant.now()
    val weekMs = now.minus(7, ChronoUnit.DAYS).toEpochMilli()
    val monthMs = now.minus(30, ChronoUnit.DAYS).toEpochMilli()
    val yearMs = now.minus(365, ChronoUnit.DAYS).toEpochMilli()

    val sorted = items.sortedByDescending { p.addedAtEpochMilli(it) }

    data class Bucket(val label: String, val sidebarLabel: String, val predicate: (T) -> Boolean)
    val buckets = listOf(
        Bucket("This Week", "Week") { p.addedAtEpochMilli(it) > weekMs },
        Bucket("This Month", "Month") { val a = p.addedAtEpochMilli(it); a > monthMs && a <= weekMs },
        Bucket("This Year", "Year") { val a = p.addedAtEpochMilli(it); a > yearMs && a <= monthMs },
        Bucket("Older", "Older") { p.addedAtEpochMilli(it) <= yearMs }
    )
    return buckets.mapNotNull { bucket ->
        val matching = sorted.filter(bucket.predicate)
        if (matching.isNotEmpty()) Section(bucket.label, bucket.sidebarLabel, matching) else null
    }
}
