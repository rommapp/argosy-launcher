package com.nendo.argosy.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.data.social.FriendActivity
import com.nendo.argosy.domain.model.HomeColumnLabel
import com.nendo.argosy.domain.model.HomeColumnTheme
import com.nendo.argosy.domain.model.HomeMediaColumnKind
import com.nendo.argosy.domain.usecase.home.ALMOST_THERE_MAX_REMAINING_SEC
import com.nendo.argosy.domain.usecase.home.GATHERING_DUST_DAYS
import com.nendo.argosy.ui.common.labelRes
import com.nendo.argosy.ui.components.friends.friendsActivityLine
import com.nendo.argosy.util.formatMonthDay
import com.nendo.argosy.util.formatPlayTime
import com.nendo.argosy.util.formatTimeToBeat
import java.time.Instant

private const val SECONDS_PER_HOUR = 3600
private const val SECONDS_PER_MINUTE = 60

@Composable
internal fun homeColumnTitle(column: HomeColumnUi): String = when (val label = column.label) {
    is HomeColumnLabel.Theme -> (label.subject ?: stringResource(label.theme.labelRes)).uppercase()
    is HomeColumnLabel.Platform -> label.name
    is HomeColumnLabel.Media -> stringResource(label.mediaKind.labelRes).uppercase()
}

@Composable
internal fun homeColumnReason(column: HomeColumnUi): String = when (val label = column.label) {
    is HomeColumnLabel.Theme -> themeReason(label.theme, column.count)
    is HomeColumnLabel.Platform ->
        pluralStringResource(R.plurals.home_columns_platform_count, column.count, column.count)
    is HomeColumnLabel.Media -> when (label.mediaKind) {
        HomeMediaColumnKind.CONTINUE_WATCHING ->
            pluralStringResource(R.plurals.home_columns_reason_continue_watching, column.count, column.count)
        HomeMediaColumnKind.NEXT_UP ->
            pluralStringResource(R.plurals.home_columns_reason_next_up, column.count, column.count)
    }
}

@Composable
private fun themeReason(theme: HomeColumnTheme, count: Int): String = when (theme) {
    HomeColumnTheme.CONTINUE -> if (count > 0) {
        pluralStringResource(R.plurals.home_columns_reason_continue, count, count)
    } else {
        stringResource(R.string.home_columns_reason_continue_none)
    }
    HomeColumnTheme.PLAY_IT_AGAIN -> stringResource(R.string.home_columns_reason_play_it_again)
    HomeColumnTheme.ALMOST_THERE -> {
        val hours = (ALMOST_THERE_MAX_REMAINING_SEC / SECONDS_PER_HOUR).toInt()
        pluralStringResource(R.plurals.home_columns_reason_almost_there, hours, hours)
    }
    HomeColumnTheme.FRIENDS_PLAYING -> if (count > 0) {
        pluralStringResource(R.plurals.home_columns_reason_friends, count, count)
    } else {
        stringResource(R.string.home_columns_reason_friends_recent)
    }
    HomeColumnTheme.JUST_ADDED -> stringResource(R.string.home_columns_reason_just_added)
    HomeColumnTheme.GATHERING_DUST -> {
        val days = GATHERING_DUST_DAYS.toInt()
        pluralStringResource(R.plurals.home_columns_reason_gathering_dust, days, days)
    }
    HomeColumnTheme.FAVORITES -> pluralStringResource(R.plurals.home_columns_reason_favorites, count, count)
    HomeColumnTheme.PINNED_COLLECTION ->
        pluralStringResource(R.plurals.home_columns_reason_collection, count, count)
    HomeColumnTheme.MOST_PLAYED_GENRE -> stringResource(R.string.home_columns_reason_genre)
}

@Composable
internal fun homeColumnFact(
    column: HomeColumnUi,
    item: HomeRowItem,
    friends: List<FriendActivity>
): String? = when (item) {
    is HomeRowItem.Media -> item.media.subtitle
    is HomeRowItem.ViewAll -> null
    is HomeRowItem.Game -> gameFact(column.label, item.game, friends)
}

@Composable
private fun gameFact(label: HomeColumnLabel, game: HomeGameUi, friends: List<FriendActivity>): String {
    val theme = (label as? HomeColumnLabel.Theme)?.theme
    return when (theme) {
        HomeColumnTheme.FRIENDS_PLAYING ->
            if (friends.isEmpty()) {
                lastPlayedFact(game)
            } else {
                val playing = friends.filter { it.playingNow }
                friendsActivityLine(playing.ifEmpty { friends }, playingNow = playing.isNotEmpty())
            }
        HomeColumnTheme.CONTINUE -> progressFact(game)
        HomeColumnTheme.ALMOST_THERE -> remainingSec(game)
            ?.let { formatTimeToBeat(LocalContext.current, it) }
            ?.let { stringResource(R.string.home_columns_fact_left, it) }
            ?: progressFact(game)
        HomeColumnTheme.PLAY_IT_AGAIN, HomeColumnTheme.MOST_PLAYED_GENRE -> playedFact(game)
        HomeColumnTheme.JUST_ADDED -> game.platformDisplayName
        HomeColumnTheme.GATHERING_DUST,
        HomeColumnTheme.FAVORITES,
        HomeColumnTheme.PINNED_COLLECTION,
        null -> lastPlayedFact(game)
    }
}

@Composable
private fun progressFact(game: HomeGameUi): String {
    val context = LocalContext.current
    val remaining = remainingSec(game)?.let { formatTimeToBeat(context, it) }
    return when {
        game.playTimeMinutes > 0 && remaining != null -> stringResource(
            R.string.home_columns_fact_progress,
            formatPlayTime(context, game.playTimeMinutes),
            remaining
        )
        else -> playedFact(game)
    }
}

@Composable
private fun playedFact(game: HomeGameUi): String =
    if (game.playTimeMinutes > 0) {
        stringResource(R.string.home_columns_fact_played, formatPlayTime(LocalContext.current, game.playTimeMinutes))
    } else {
        lastPlayedFact(game)
    }

@Composable
private fun lastPlayedFact(game: HomeGameUi): String =
    game.lastPlayedAt?.let {
        stringResource(R.string.home_columns_fact_last_played, formatMonthDay(Instant.ofEpochMilli(it)))
    } ?: stringResource(R.string.home_columns_fact_unplayed)

private fun remainingSec(game: HomeGameUi): Int? {
    val main = game.timeToBeatMainSec?.takeIf { it > 0 } ?: return null
    return (main - game.playTimeMinutes * SECONDS_PER_MINUTE).takeIf { it > 0 }
}
