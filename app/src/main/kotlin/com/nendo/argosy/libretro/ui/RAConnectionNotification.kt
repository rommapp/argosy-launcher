package com.nendo.argosy.libretro.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import kotlinx.coroutines.delay

/**
 * [connected] false means the server never accepted a session for this launch; the banner
 * then reports the casual fallback instead of a connection.
 */
data class RAConnectionInfo(
    val isHardcore: Boolean,
    val earnedCount: Int,
    val totalCount: Int,
    val connected: Boolean = true
)

private val goldPrimary = Color(0xFFFFD700)
private val goldDark = Color(0xFF8B6914)
private val goldDeep = Color(0xFF5C4A0F)
private val goldShine = Color(0xFFFFF8DC)

private val greenPrimary = Color(0xFF4CAF50)
private val greenDark = Color(0xFF2E5D30)
private val greenDeep = Color(0xFF1B3D1D)
private val greenShine = Color(0xFFB8E6BA)

@Composable
fun RAConnectionNotification(
    connectionInfo: RAConnectionInfo?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var visible by remember(connectionInfo) { mutableStateOf(false) }
    var showContent by remember(connectionInfo) { mutableStateOf(false) }

    LaunchedEffect(connectionInfo) {
        if (connectionInfo != null) {
            visible = true
            delay(50L)
            showContent = true
            delay(3000L)
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
        visible = visible && connectionInfo != null,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(200)
        ) + fadeIn(animationSpec = tween(150)),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(250)
        ) + fadeOut(animationSpec = tween(200)),
        modifier = modifier.fillMaxWidth()
    ) {
        connectionInfo?.let { info ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(scale),
                contentAlignment = Alignment.TopCenter
            ) {
                RAConnectionContent(info)
            }
        }
    }
}

@Composable
private fun RAConnectionContent(info: RAConnectionInfo) {
    val primary = if (info.isHardcore) goldPrimary else greenPrimary
    val dark = if (info.isHardcore) goldDark else greenDark
    val deep = if (info.isHardcore) goldDeep else greenDeep
    val shine = if (info.isHardcore) goldShine else greenShine

    val textShadow = Shadow(
        color = Color.Black.copy(alpha = 0.6f),
        offset = Offset(1f, 1f),
        blurRadius = 2f
    )

    Box(
        modifier = Modifier.padding(16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Row(
            modifier = Modifier
                .shadow(8.dp, RoundedCornerShape(12.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(deep, dark, dark, deep)
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                .drawBehind {
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (info.connected) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = null,
                tint = primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = if (info.connected) {
                        stringResource(R.string.ingame_raconnect_title)
                    } else {
                        stringResource(R.string.ingame_raconnect_unavailable_title)
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(shadow = textShadow),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (info.isHardcore) {
                            stringResource(R.string.ingame_raconnect_hardcore)
                        } else {
                            stringResource(R.string.ingame_raconnect_casual)
                        },
                        style = MaterialTheme.typography.labelSmall.copy(shadow = textShadow),
                        color = primary,
                        fontWeight = FontWeight.Bold
                    )
                    if (info.totalCount > 0) {
                        Text(
                            text = " \u2022 ",
                            style = MaterialTheme.typography.labelSmall.copy(shadow = textShadow),
                            color = shine.copy(alpha = 0.7f)
                        )
                        Text(
                            text = stringResource(
                                R.string.ingame_raconnect_earned,
                                info.earnedCount,
                                info.totalCount
                            ),
                            style = MaterialTheme.typography.labelSmall.copy(shadow = textShadow),
                            color = shine
                        )
                    }
                }
            }
        }
    }
}
