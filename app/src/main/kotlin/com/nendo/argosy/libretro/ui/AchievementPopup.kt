package com.nendo.argosy.libretro.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.R
import kotlinx.coroutines.delay

data class AchievementUnlock(
    val id: Long,
    val title: String,
    val description: String?,
    val points: Int,
    val badgeUrl: String?,
    val isHardcore: Boolean
)

private val goldPrimary = Color(0xFFFFD700)
private val goldDark = Color(0xFF8B6914)
private val goldDeep = Color(0xFF5C4A0F)
private val goldShine = Color(0xFFFFF8DC)

private val bronzePrimary = Color(0xFFCD7F32)
private val bronzeDark = Color(0xFF6B4423)
private val bronzeDeep = Color(0xFF4A2F18)
private val bronzeShine = Color(0xFFDEB887)

@Composable
fun AchievementPopup(
    achievement: AchievementUnlock?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var visible by remember(achievement) { mutableStateOf(false) }
    var showContent by remember(achievement) { mutableStateOf(false) }

    LaunchedEffect(achievement) {
        if (achievement != null) {
            visible = true
            delay(50L)
            showContent = true
            delay(4000L)
            visible = false
            delay(300L)
            onDismiss()
        } else {
            visible = false
            showContent = false
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (showContent) 1f else 0.3f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )

    AnimatedVisibility(
        visible = visible && achievement != null,
        enter = fadeIn(animationSpec = tween(150)),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(250)
        ) + fadeOut(animationSpec = tween(200)),
        modifier = modifier.fillMaxWidth()
    ) {
        achievement?.let { unlock ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(scale),
                contentAlignment = Alignment.TopCenter
            ) {
                AchievementPopupContent(unlock)
            }
        }
    }
}

@Composable
private fun AchievementPopupContent(achievement: AchievementUnlock) {
    val primary = if (achievement.isHardcore) goldPrimary else bronzePrimary
    val dark = if (achievement.isHardcore) goldDark else bronzeDark
    val deep = if (achievement.isHardcore) goldDeep else bronzeDeep
    val shine = if (achievement.isHardcore) goldShine else bronzeShine

    val textShadow = Shadow(
        color = Color.Black.copy(alpha = 0.6f),
        offset = Offset(1f, 1f),
        blurRadius = 2f
    )

    Box(
        modifier = Modifier
            .padding(16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Row(
            modifier = Modifier
                .shadow(8.dp, RoundedCornerShape(12.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            deep,
                            dark,
                            dark,
                            deep
                        )
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                .drawBehind {
                    // Subtle shine overlay at top
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                shine.copy(alpha = 0.25f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = size.height * 0.4f
                        )
                    )
                    // Edge highlight
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                primary.copy(alpha = 0.3f),
                                Color.Transparent,
                                Color.Transparent,
                                primary.copy(alpha = 0.3f)
                            )
                        )
                    )
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (achievement.badgeUrl != null) {
                AsyncImage(
                    model = achievement.badgeUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(52.dp)
                        .shadow(4.dp, RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.2f)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(14.dp))
            }

            Column {
                Text(
                    text = if (achievement.isHardcore) {
                        stringResource(R.string.ingame_achievement_hardcore_heading)
                    } else {
                        stringResource(R.string.ingame_achievement_heading)
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        shadow = textShadow
                    ),
                    color = shine,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = achievement.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        shadow = textShadow
                    ),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.ingame_achievement_points,
                        achievement.points,
                        achievement.points
                    ),
                    style = MaterialTheme.typography.labelSmall.copy(
                        shadow = textShadow
                    ),
                    color = primary
                )
            }
        }
    }
}
