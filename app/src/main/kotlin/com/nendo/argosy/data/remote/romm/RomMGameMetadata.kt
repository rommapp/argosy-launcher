package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.util.SearchNormalizer
import java.time.Instant
import java.time.ZoneOffset

/**
 * Every game field that comes straight off the RomM model, in one place.
 *
 * A library sync and a per-game refresh both write these, and they used to each carry their
 * own list, so a field added to sync never reached refresh: refreshing a game silently
 * dropped its box art and never picked up soundtrack availability. Anything derived purely
 * from [rom] belongs here so both callers get it.
 *
 * Deliberately excluded: identity (ids, platform, source), local state (play counts,
 * favourites, disc flags), and image paths, which sync resolves to cached files while
 * refresh resolves to URLs. Those stay with the caller.
 */
/**
 * Re-applies the materialised per-account columns from [current] onto a row built from a
 * pre-network snapshot.
 *
 * A full-row `@Update` after a round trip writes back whatever those columns held when the row
 * was read, which is the wrong account's values if a switch landed in between and stale values
 * otherwise. The overlay is the record; this keeps the mirror from overwriting it.
 */
internal fun GameEntity.withCurrentUserColumns(current: GameEntity): GameEntity = copy(
    isFavorite = current.isFavorite,
    userRating = current.userRating,
    userDifficulty = current.userDifficulty,
    completion = current.completion,
    status = current.status,
    backlogged = current.backlogged,
    nowPlaying = current.nowPlaying,
    playCount = current.playCount,
    playTimeMinutes = current.playTimeMinutes,
    lastPlayed = current.lastPlayed,
    earnedAchievementCount = current.earnedAchievementCount
)

internal fun GameEntity.withRomMetadata(rom: RomMRom): GameEntity = copy(
    title = rom.name,
    sortTitle = RomMUtils.createSortTitle(rom.name),
    searchTitle = SearchNormalizer.normalize(rom.name),
    description = rom.summary,
    releaseYear = rom.firstReleaseDateMillis?.let {
        Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).year
    },
    genre = rom.genres?.firstOrNull(),
    developer = rom.companies?.firstOrNull(),
    rating = rom.metadatum?.averageRating?.takeIf { rom.igdbId != null && it < 98f },
    regions = rom.regions?.joinToString(","),
    languages = rom.languages?.joinToString(","),
    gameModes = rom.metadatum?.gameModes?.joinToString(","),
    franchises = rom.metadatum?.franchises?.joinToString(","),
    genres = rom.genres?.joinToString(","),
    collections = rom.metadatum?.collections?.joinToString(","),
    players = rom.metadatum?.playerCount,
    ageRatings = rom.metadatum?.ageRatings?.joinToString(","),
    alternativeNames = rom.alternativeNames?.joinToString(","),
    mobyId = rom.mobyId,
    sgdbId = rom.sgdbId,
    ssId = rom.ssId,
    launchboxId = rom.launchboxId,
    hasheousId = rom.hasheousId,
    tgdbId = rom.tgdbId,
    hltbId = rom.hltbId,
    timeToBeatMainSec = rom.hltbMetadata?.mainStorySec,
    timeToBeatExtraSec = rom.hltbMetadata?.mainPlusExtraSec,
    timeToBeatCompletionistSec = rom.hltbMetadata?.completionistSec,
    flashpointId = rom.flashpointId,
    gamelistId = rom.gamelistId,
    libretroId = rom.libretroId,
    crcHash = rom.crcHash,
    md5Hash = rom.md5Hash,
    sha1Hash = rom.sha1Hash,
    raHash = rom.raHash,
    hasManual = rom.hasManual,
    manualPath = rom.manualPath,
    remoteHasSoundtrack = rom.hasSoundtrack,
    isIdentified = rom.isIdentified,
    youtubeVideoId = rom.youtubeVideoId,
    achievementCount = rom.raMetadata?.achievements?.size ?: achievementCount,
    fileSizeBytes = rom.fileSize.takeIf { it > 0 }
)
