package com.nendo.argosy.ui.screens.home

import com.nendo.argosy.data.social.FriendActivity
import com.nendo.argosy.ui.dualscreen.CompanionDetail
import com.nendo.argosy.ui.dualscreen.CompanionGameStats

fun HomeGameUi.toCompanionDetail(friends: List<FriendActivity> = emptyList()): CompanionDetail =
    CompanionDetail(
        title = title,
        subtitle = platformDisplayName,
        platformSlug = platformSlug,
        artUrl = coverPath,
        backdropUrl = backgroundPath,
        backdropIsScreenshotOrArt = backgroundIsScreenshotOrArt,
        isGameTitle = true,
        spineUrl = boxSpinePath,
        box3dUrl = box3dPath,
        logoUrl = logoPath,
        gameId = id,
        videoPath = videoNormalizedPath,
        youtubeVideoId = youtubeVideoId,
        stats = CompanionGameStats(
            developer = developer,
            releaseYear = releaseYear,
            players = players,
            genre = genre,
            communityRating = rating,
            userRating = userRating,
            userDifficulty = userDifficulty,
            playTimeMinutes = playTimeMinutes,
            timeToBeatMainSec = timeToBeatMainSec,
            timeToBeatCompletionistSec = timeToBeatCompletionistSec,
            status = status,
            achievementCount = achievementCount,
            earnedAchievementCount = earnedAchievementCount,
            friends = friends
        )
    )
