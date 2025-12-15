package com.nendo.argosy.ui.screens.gamedetail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.ui.screens.gamedetail.AchievementUi

@Composable
fun AchievementRow(achievement: AchievementUi) {
    val grayscaleMatrix = ColorMatrix().apply { setToSaturation(0f) }
    val goldColor = Color(0xFFFFB300)
    val lockedColor = Color.White.copy(alpha = 0.5f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(56.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (achievement.badgeUrl != null) {
                    AsyncImage(
                        model = achievement.badgeUrl,
                        contentDescription = achievement.title,
                        contentScale = ContentScale.Fit,
                        colorFilter = if (!achievement.isUnlocked) {
                            ColorFilter.colorMatrix(grayscaleMatrix)
                        } else null,
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(if (achievement.isUnlocked) 1f else 0.7f)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.EmojiEvents,
                        contentDescription = null,
                        tint = if (achievement.isUnlocked) goldColor else Color.Gray,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Text(
                text = "${achievement.points} pts",
                style = MaterialTheme.typography.labelSmall,
                color = goldColor.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = achievement.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (achievement.isUnlocked) goldColor else lockedColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!achievement.description.isNullOrBlank()) {
                Text(
                    text = achievement.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun AchievementColumn(
    achievements: List<AchievementUi>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        achievements.forEach { achievement ->
            AchievementRow(achievement)
        }
    }
}
