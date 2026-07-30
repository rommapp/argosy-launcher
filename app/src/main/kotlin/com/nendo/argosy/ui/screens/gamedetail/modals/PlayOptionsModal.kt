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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.theme.ALauncherColors
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus

sealed class PlayOptionAction {
    data object Resume : PlayOptionAction()
    data object ResumeNoSync : PlayOptionAction()
    data object NewCasual : PlayOptionAction()
    data object NewHardcore : PlayOptionAction()
    data object ResumeHardcore : PlayOptionAction()
}

/** The grouped sections play options are laid out under, with their display headers. */
enum class PlayOptionSection(val label: String) {
    Continue("CONTINUE"),
    NewGame("NEW GAME")
}

/**
 * A single play-options row: the action it fires plus everything needed to render it. The ordered
 * list from [buildPlayOptions] is the single source of truth for row order, visibility, and focus
 * index -- both [PlayOptionsModal] (rendering) and PlayOptionsDelegate (focus/confirm) walk it, so
 * the two cannot drift out of lock-step.
 */
data class PlayOptionItem(
    val action: PlayOptionAction,
    val section: PlayOptionSection,
    val icon: ImageVector,
    val label: String,
    val subtext: String? = null,
    val iconTint: Color? = null,
    val isEnabled: Boolean = true
)

/**
 * Builds the visible play options in display order. Visibility conditions live here only; callers
 * must not re-derive them. [canSkipSync] gates the "play without syncing" row; [hardcoreAvailable]
 * is the already-resolved answer to whether hardcore applies at all. The Continue "Hardcore" row
 * loads the latest hardcore save if present, else continues the active casual save in a hardcore
 * session.
 */
fun buildPlayOptions(
    hasSaves: Boolean,
    hasHardcoreSave: Boolean,
    hasRASupport: Boolean,
    hardcoreAvailable: Boolean,
    isOnline: Boolean,
    canSkipSync: Boolean
): List<PlayOptionItem> = buildList {
    val showHardcoreOptions = hardcoreAvailable
    val hasContinueSection = hasSaves || hasHardcoreSave

    if (hasSaves) {
        add(
            PlayOptionItem(
                action = PlayOptionAction.Resume,
                section = PlayOptionSection.Continue,
                icon = Icons.Default.PlayArrow,
                label = "Latest"
            )
        )
    }
    if (hasSaves && canSkipSync) {
        add(
            PlayOptionItem(
                action = PlayOptionAction.ResumeNoSync,
                section = PlayOptionSection.Continue,
                icon = Icons.Default.PlayArrow,
                label = "Play without syncing",
                subtext = "Skip the pre-launch save sync check"
            )
        )
    }
    if (hasContinueSection && showHardcoreOptions) {
        add(
            PlayOptionItem(
                action = PlayOptionAction.ResumeHardcore,
                section = PlayOptionSection.Continue,
                icon = Icons.Default.EmojiEvents,
                label = "Hardcore",
                subtext = if (hasHardcoreSave) null else "Continue this save in hardcore",
                iconTint = ALauncherColors.StarGold
            )
        )
    }

    add(
        PlayOptionItem(
            action = PlayOptionAction.NewCasual,
            section = PlayOptionSection.NewGame,
            icon = Icons.Default.SportsEsports,
            label = "Casual",
            subtext = if (hasRASupport) "Save states and cheats available" else null
        )
    )
    if (showHardcoreOptions) {
        add(
            PlayOptionItem(
                action = PlayOptionAction.NewHardcore,
                section = PlayOptionSection.NewGame,
                icon = Icons.Default.EmojiEvents,
                label = "Hardcore",
                subtext = if (isOnline) "Online-only, no save states or cheats" else "Requires internet connection",
                iconTint = if (isOnline) ALauncherColors.StarGold else null,
                isEnabled = isOnline
            )
        )
    }
}

@Composable
fun PlayOptionsModal(
    focusIndex: Int,
    hasSaves: Boolean,
    hasHardcoreSave: Boolean,
    hasRASupport: Boolean,
    hardcoreAvailable: Boolean,
    isOnline: Boolean,
    canSkipSync: Boolean = false,
    onAction: (PlayOptionAction) -> Unit,
    onDismiss: () -> Unit
) {
    val items = buildPlayOptions(
        hasSaves = hasSaves,
        hasHardcoreSave = hasHardcoreSave,
        hasRASupport = hasRASupport,
        hardcoreAvailable = hardcoreAvailable,
        isOnline = isOnline,
        canSkipSync = canSkipSync
    )

    Modal(title = "START GAME", onDismiss = onDismiss) {
        var lastSection: PlayOptionSection? = null
        items.forEachIndexed { index, item ->
            if (item.section != lastSection) {
                if (lastSection != null) Spacer(Modifier.height(Dimens.spacingMd))
                SectionLabel(item.section.label)
                Spacer(Modifier.height(Dimens.spacingXs))
                lastSection = item.section
            }
            PlayOptionRow(
                icon = item.icon,
                iconTint = item.iconTint,
                label = item.label,
                subtext = item.subtext,
                isFocused = focusIndex == index,
                isEnabled = item.isEnabled,
                onClick = { onAction(item.action) }
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
        isFocused -> lerp(LocalArgosyTheme.current.focusAccent, Color.White, 0.45f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    val subtextColor = when {
        !isEnabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = disabledAlpha)
        isFocused -> lerp(LocalArgosyTheme.current.focusAccent, Color.White, 0.45f).copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val backgroundColor = when {
        !isEnabled -> Color.Transparent
        isFocused -> LocalArgosyTheme.current.focusAccent.copy(alpha = 0.15f)
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
