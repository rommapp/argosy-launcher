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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nendo.argosy.data.social.DailyPlaytime
import com.nendo.argosy.data.social.MostPlayedGame
import com.nendo.argosy.data.social.PresenceStatus
import com.nendo.argosy.data.social.SocialUser
import com.nendo.argosy.data.social.UserProfileData
import com.nendo.argosy.ui.theme.AspectRatioClass
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.util.clickableNoFocus
import java.time.LocalDate
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
    profile: UserProfileData?
) {
    val avatarColor = try {
        Color(android.graphics.Color.parseColor(user.avatarColor))
    } catch (e: Exception) {
        Color(0xFF6366F1)
    }

    val isWide = LocalUiScale.current.aspectRatioClass.let {
        it == AspectRatioClass.WIDE || it == AspectRatioClass.ULTRA_WIDE
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(avatarColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = user.displayName.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

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
                            PresenceStatus.IN_GAME -> presence.gameTitle?.let { "playing $it" } ?: "in game"
                            PresenceStatus.ONLINE -> "online"
                            PresenceStatus.AWAY -> "away"
                            PresenceStatus.OFFLINE -> null
                        }
                        val presenceColor = when (presence.presenceStatus) {
                            PresenceStatus.ONLINE, PresenceStatus.IN_GAME -> Color(0xFF4CAF50)
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
                            text = "Member since $memberSince",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    Text(
                        text = "${profile.friendCount} friends",
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
                        text = "Member since $memberSince",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                Text(
                    text = "${profile.friendCount} friends",
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
                    contentDescription = "Friends",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = if (profile.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (profile.isFavorite) "Favorite" else "Not favorite",
                    modifier = Modifier.size(18.dp),
                    tint = if (profile.isFavorite) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
        }
        "none" -> {
            Icon(
                imageVector = Icons.Filled.PersonAdd,
                contentDescription = "Not friends",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
        "pending_outgoing", "pending_incoming" -> {
            Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = "Pending",
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

    val stats = listOf(
        formatPlayHours(profile.totalPlayHours) to "Total Play Time",
        "${profile.gameCount}" to "Games Played",
        "${profile.friendCount}" to "Friends",
        (profile.topGenre ?: "--") to "Top Genre",
        (profile.topPlatform ?: "--") to "Top Platform",
        (profile.favoriteDecade ?: "--") to "Fav. Decade"
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
                    text = "LAST 30 DAYS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "peak: $peakLabel",
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
    val isWide = LocalUiScale.current.aspectRatioClass.let {
        it == AspectRatioClass.WIDE || it == AspectRatioClass.ULTRA_WIDE
    }

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
            isWide = isWide,
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
    isWide: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isFocused) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    val textColor = if (isFocused) {
        MaterialTheme.colorScheme.onPrimaryContainer
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

        if (isWide) {
            Text(
                text = game.title,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = formatPlayHours(game.totalHours),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Text(
                text = "${game.sessionCount} sess",
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.6f)
            )
        } else {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = game.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = formatPlayHours(game.totalHours),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = textColor.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "${game.sessionCount} sessions",
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

private fun formatPlayHours(hours: Double): String {
    return when {
        hours < 1.0 -> "${(hours * 60).roundToInt()}m"
        hours < 100.0 -> "%.1f hrs".format(hours)
        hours < 1000.0 -> "${hours.roundToInt()} hrs"
        else -> "%.1fk hrs".format(hours / 1000)
    }
}

private fun formatMemberSince(isoDate: String): String? {
    return try {
        val date = LocalDate.parse(isoDate.take(10))
        val month = date.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
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
