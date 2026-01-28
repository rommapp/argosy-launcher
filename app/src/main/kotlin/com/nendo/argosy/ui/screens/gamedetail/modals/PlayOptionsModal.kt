package com.nendo.argosy.ui.screens.gamedetail.modals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.util.clickableNoFocus

private val goldColor = Color(0xFFFFD700)

sealed class PlayOptionAction {
    data object Resume : PlayOptionAction()
    data object NewCasual : PlayOptionAction()
    data object NewHardcore : PlayOptionAction()
    data object ResumeHardcore : PlayOptionAction()
}

@Composable
fun PlayOptionsModal(
    focusIndex: Int,
    hasSaves: Boolean,
    hasHardcoreSave: Boolean,
    hasRASupport: Boolean,
    isRALoggedIn: Boolean,
    isOnline: Boolean,
    onAction: (PlayOptionAction) -> Unit,
    onDismiss: () -> Unit
) {
    var currentIndex = 0
    val showHardcoreOptions = hasRASupport && isRALoggedIn

    Modal(title = "START GAME", onDismiss = onDismiss) {
        val hasContinueSection = hasSaves || hasHardcoreSave

        if (hasContinueSection) {
            SectionLabel("CONTINUE")
            Spacer(Modifier.height(Dimens.spacingXs))

            if (hasSaves) {
                val idx = currentIndex++
                PlayOptionRow(
                    icon = Icons.Default.PlayArrow,
                    label = "Latest",
                    isFocused = focusIndex == idx,
                    onClick = { onAction(PlayOptionAction.Resume) }
                )
            }

            if (hasHardcoreSave) {
                val idx = currentIndex++
                PlayOptionRow(
                    icon = Icons.Default.EmojiEvents,
                    iconTint = goldColor,
                    label = "Hardcore",
                    isFocused = focusIndex == idx,
                    onClick = { onAction(PlayOptionAction.ResumeHardcore) }
                )
            }

            Spacer(Modifier.height(Dimens.spacingMd))
        }

        SectionLabel("NEW GAME")
        Spacer(Modifier.height(Dimens.spacingXs))

        val casualIdx = currentIndex++
        PlayOptionRow(
            icon = Icons.Default.SportsEsports,
            label = "Casual",
            subtext = if (hasRASupport) "Save states and cheats available" else null,
            isFocused = focusIndex == casualIdx,
            onClick = { onAction(PlayOptionAction.NewCasual) }
        )

        if (showHardcoreOptions) {
            val hardcoreIdx = currentIndex++
            PlayOptionRow(
                icon = Icons.Default.EmojiEvents,
                iconTint = if (isOnline) goldColor else null,
                label = "Hardcore",
                subtext = if (isOnline) "Online-only, no save states or cheats" else "Requires internet connection",
                isFocused = focusIndex == hardcoreIdx,
                isEnabled = isOnline,
                onClick = { onAction(PlayOptionAction.NewHardcore) }
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = Dimens.spacingXs)
    )
}

@Composable
private fun PlayOptionRow(
    icon: ImageVector,
    label: String,
    subtext: String? = null,
    iconTint: Color? = null,
    isFocused: Boolean = false,
    isEnabled: Boolean = true,
    onClick: () -> Unit
) {
    val disabledAlpha = 0.38f
    val contentColor = when {
        !isEnabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = disabledAlpha)
        isFocused -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val subtextColor = when {
        !isEnabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = disabledAlpha)
        isFocused -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val backgroundColor = when {
        !isEnabled -> Color.Transparent
        isFocused -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    val effectiveIconTint = when {
        !isEnabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = disabledAlpha)
        else -> iconTint ?: contentColor
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(backgroundColor)
            .then(if (isEnabled) Modifier.clickableNoFocus(onClick = onClick) else Modifier)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = effectiveIconTint,
            modifier = Modifier.size(Dimens.iconMd)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor
            )
            if (subtext != null) {
                Text(
                    text = subtext,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtextColor
                )
            }
        }
    }
}
