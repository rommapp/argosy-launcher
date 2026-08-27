package com.nendo.argosy.ui.screens.social

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.data.social.DailyPlaytime
import com.nendo.argosy.data.social.MostPlayedGame
import com.nendo.argosy.data.social.PresenceStatus
import com.nendo.argosy.data.social.SocialUser
import com.nendo.argosy.data.social.UserProfileData
import androidx.compose.ui.graphics.lerp
import com.nendo.argosy.ui.theme.AspectRatioClass
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.components.friends.SocialAvatar
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.util.clickableNoFocus
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private const val PROFILE_DISPLAY_SECTIONS = 3

fun profileFocusToItemIndex(focusIndex: Int, mostPlayedCount: Int): Int {
    // Items: AccountCard(0), StatsGrid(1), Chart(2),
    //        MostPlayedHeader(3, if count>0), MostPlayedRows(4..3+count)
    val headerOffset = if (mostPlayedCount > 0) 1 else 0
    return when {
        focusIndex < PROFILE_DISPLAY_SECTIONS -> focusIndex
        else -> {
            val gameIndex = focusIndex - PROFILE_DISPLAY_SECTIONS
            PROFILE_DISPLAY_SECTIONS + headerOffset + gameIndex
        }
    }
}

@Composable
fun AccountInfoCard(
    user: SocialUser,
    profile: UserProfileData?,
    avatarDoodle: String? = null,
    avatarPngBase64: String? = null,
    onEditAvatar: (() -> Unit)? = null
) {
    val isWide = LocalUiScale.current.aspectRatioClass.let {
        it == AspectRatioClass.WIDE || it == AspectRatioClass.ULTRA_WIDE
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onEditAvatar != null) Modifier.clickableNoFocus { onEditAvatar() }
                else Modifier
            ),
        shape = RoundedCornerShape(Dimens.radiusLg),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.spacingMd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
        ) {
            SocialAvatar(
                displayName = user.displayName,
                avatarColor = user.avatarColor,
                size = Dimens.avatarXl,
                avatarDoodle = avatarDoodle,
                avatarPngBase64 = avatarPngBase64,
                userId = user.id
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = user.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (profile != null) {
                        RelationshipIcons(profile)
                    }
                }
                Text(
                    text = "@${user.username}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                if (profile != null) {
                    val presence = profile.presence
                    if (presence != null && profile.relationship != "self") {
                        val presenceText = when (presence.presenceStatus) {
                            PresenceStatus.IN_GAME -> presence.gameTitle?.let {
                                stringResource(R.string.social_accountcard_presence_playing, it)
                            } ?: stringResource(R.string.social_accountcard_presence_in_game)
                            PresenceStatus.WATCHING -> presence.gameTitle?.let {
                                stringResource(R.string.social_accountcard_presence_watching_game, it)
                            } ?: stringResource(R.string.social_accountcard_presence_watching)
                            PresenceStatus.ONLINE -> stringResource(R.string.social_accountcard_presence_online)
                            PresenceStatus.AWAY -> stringResource(R.string.social_accountcard_presence_away)
                            PresenceStatus.OFFLINE -> null
                        }
                        val presenceColor = when (presence.presenceStatus) {
                            PresenceStatus.ONLINE, PresenceStatus.IN_GAME, PresenceStatus.WATCHING -> Color(0xFF4CAF50)
                            PresenceStatus.AWAY -> Color(0xFFFFC107)
                            PresenceStatus.OFFLINE -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        }
                        if (presenceText != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(presenceColor)
                                )
                                Text(
                                    text = presenceText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }

            if (profile != null && isWide) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val memberSince = formatMemberSince(profile.memberSince)
                    if (memberSince != null) {
                        Text(
                            text = stringResource(R.string.social_accountcard_member_since_wide, memberSince),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    Text(
                        text = pluralStringResource(
                            R.plurals.social_accountcard_friend_count_wide,
                            profile.friendCount,
                            profile.friendCount
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }

        if (profile != null && !isWide) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val memberSince = formatMemberSince(profile.memberSince)
                if (memberSince != null) {
                    Text(
                        text = stringResource(R.string.social_accountcard_member_since_compact, memberSince),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                Text(
                    text = pluralStringResource(
                        R.plurals.social_accountcard_friend_count_compact,
                        profile.friendCount,
                        profile.friendCount
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun RelationshipIcons(profile: UserProfileData) {
    when (profile.relationship) {
        "friend" -> {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    imageVector = Icons.Outlined.HowToReg,
                    contentDescription = stringResource(R.string.social_relationship_friends_desc),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = if (profile.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = stringResource(
                        if (profile.isFavorite) R.string.social_relationship_favorite_desc
                        else R.string.social_relationship_not_favorite_desc
                    ),
                    modifier = Modifier.size(18.dp),
                    tint = if (profile.isFavorite) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
        }
        "none" -> {
            Icon(
                imageVector = Icons.Filled.PersonAdd,
                contentDescription = stringResource(R.string.social_relationship_not_friends_desc),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
        "pending_outgoing", "pending_incoming" -> {
            Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = stringResource(R.string.social_relationship_pending_desc),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
fun ProfileStatsGrid(profile: UserProfileData, modifier: Modifier = Modifier) {
    val isWide = LocalUiScale.current.aspectRatioClass.let {
        it == AspectRatioClass.WIDE || it == AspectRatioClass.ULTRA_WIDE
    }
    val columns = if (isWide) 3 else 2

    val dash = stringResource(R.string.social_statsgrid_placeholder_dash)
    val stats = listOf(
        formatPlayHours(profile.totalPlayHours) to stringResource(R.string.social_statsgrid_total_play_time),
        "${profile.gameCount}" to stringResource(R.string.social_statsgrid_games_played),
        "${profile.friendCount}" to stringResource(R.string.social_statsgrid_friends),
        (profile.topGenre ?: dash) to stringResource(R.string.social_statsgrid_top_genre),
        (profile.topPlatform ?: dash) to stringResource(R.string.social_statsgrid_top_platform),
        (profile.favoriteDecade ?: dash) to stringResource(R.string.social_statsgrid_fav_decade)
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            stats.chunked(columns).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { (value, label) ->
                        StatCell(
                            value = value,
                            label = label,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(columns - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

@Composable
fun PlaytimeLineChart(dailyPlaytime: List<DailyPlaytime>, modifier: Modifier = Modifier) {
    val hasData = dailyPlaytime.any { it.hours > 0 }
    if (!hasData) return

    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val maxHours = dailyPlaytime.maxOf { it.hours }.coerceAtLeast(0.1)
    val peakLabel = formatPlayHours(maxHours)

    val labels = remember(dailyPlaytime) {
        buildChartLabels(dailyPlaytime)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.social_playtimechart_header),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.social_playtimechart_peak, peakLabel),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
            ) {
                val points = dailyPlaytime
                val w = size.width
                val h = size.height
                val count = points.size
                if (count < 2) return@Canvas

                val stepX = w / (count - 1)

                val linePath = Path().apply {
                    points.forEachIndexed { i, dp ->
                        val x = i * stepX
                        val y = h - (dp.hours / maxHours).toFloat() * h
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                }

                val fillPath = Path().apply {
                    addPath(linePath)
                    lineTo((count - 1) * stepX, h)
                    lineTo(0f, h)
                    close()
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.3f),
                            primaryColor.copy(alpha = 0.05f)
                        )
                    )
                )

                drawPath(
                    path = linePath,
                    color = primaryColor,
                    style = Stroke(width = 2f)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                labels.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = onSurfaceColor.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

@Composable
fun MostPlayedGameItem(
    game: MostPlayedGame,
    isFocused: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onGameClick: (Int) -> Unit
) {
    val shape = when {
        isFirst && isLast -> RoundedCornerShape(12.dp)
        isFirst -> RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
        isLast -> RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
        else -> RoundedCornerShape(0.dp)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        MostPlayedGameRow(
            game = game,
            isFocused = isFocused,
            onClick = { onGameClick(game.igdbId) }
        )
        if (!isLast) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}

@Composable
private fun MostPlayedGameRow(
    game: MostPlayedGame,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isFocused) {
        LocalArgosyTheme.current.focusAccent.copy(alpha = 0.15f)
    } else {
        Color.Transparent
    }
    val textColor = if (isFocused) {
        lerp(LocalArgosyTheme.current.focusAccent, Color.White, 0.45f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val bitmap = remember(game.coverThumb) {
        game.coverThumb?.let {
            try {
                val bytes = Base64.decode(it, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (e: Exception) { null }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = game.title,
                modifier = Modifier
                    .size(width = 40.dp, height = 54.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 54.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surface)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = game.title,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = game.genre
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatPlayHours(game.totalHours),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Text(
                text = pluralStringResource(
                    R.plurals.social_mostplayed_sessions,
                    game.sessionCount,
                    game.sessionCount
                ),
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun formatPlayHours(hours: Double): String {
    return when {
        hours < 1.0 -> stringResource(R.string.social_playhours_minutes, (hours * 60).roundToInt())
        hours < 100.0 -> stringResource(R.string.social_playhours_hours_decimal, "%.1f".format(hours))
        hours < 1000.0 -> pluralStringResource(
            R.plurals.social_playhours_hours_whole,
            hours.roundToInt(),
            hours.roundToInt()
        )
        else -> stringResource(R.string.social_playhours_hours_thousands, "%.1f".format(hours / 1000))
    }
}

private fun formatMemberSince(isoDate: String): String? {
    return try {
        val date = LocalDate.parse(isoDate.take(10))
        val month = date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        "$month ${date.year}"
    } catch (e: Exception) {
        null
    }
}

private fun buildChartLabels(dailyPlaytime: List<DailyPlaytime>): List<String> {
    if (dailyPlaytime.isEmpty()) return emptyList()
    val len = dailyPlaytime.size
    if (len <= 7) return dailyPlaytime.map { it.date.substring(5) }
    val step = if (len <= 14) 2 else 5
    val labels = mutableListOf<String>()
    labels.add(dailyPlaytime.first().date.substring(5))
    var i = step
    while (i < len - 1) {
        labels.add(dailyPlaytime[i].date.substring(5))
        i += step
    }
    labels.add(dailyPlaytime.last().date.substring(5))
    return labels
}
