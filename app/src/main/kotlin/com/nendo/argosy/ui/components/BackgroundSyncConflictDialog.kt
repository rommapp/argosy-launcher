package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.content.Context
import com.nendo.argosy.R
import com.nendo.argosy.data.sync.ConflictInfo
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus
import java.time.Duration
import java.time.Instant

@Composable
fun BackgroundSyncConflictDialog(
    conflictInfo: ConflictInfo,
    focusIndex: Int,
    onKeepLocal: () -> Unit,
    onKeepServer: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    val localTimeStr = conflictInfo.localTimestamp.toRelativeString(context)
    val serverTimeStr = conflictInfo.serverTimestamp.toRelativeString(context)
    val localIsNewer = conflictInfo.localTimestamp.isAfter(conflictInfo.serverTimestamp)

    Modal(
        title = stringResource(R.string.ui_background_conflict_title),
        baseWidth = 400.dp,
        onDismiss = onSkip,
        titleContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                GameTitle(
                    title = conflictInfo.gameName,
                    titleStyle = MaterialTheme.typography.titleMedium,
                    titleColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(Dimens.spacingMd))
                Text(
                    text = conflictInfo.channelName
                        ?: stringResource(R.string.ui_background_conflict_default_slot),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    ) {
        Text(
            text = if (conflictInfo.isHashConflict) {
                stringResource(R.string.ui_background_conflict_message_local_changed)
            } else {
                stringResource(R.string.ui_background_conflict_message_server_newer)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(Dimens.spacingMd))

        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
            ConflictChoiceRow(
                icon = Icons.Default.PhoneAndroid,
                label = stringResource(R.string.ui_background_conflict_choice_local),
                timestamp = localTimeStr,
                isNewer = localIsNewer,
                isFocused = focusIndex == 0,
                onClick = onKeepLocal
            )
            ConflictChoiceRow(
                icon = Icons.Default.Cloud,
                label = stringResource(R.string.ui_background_conflict_choice_server),
                subtitle = conflictInfo.serverDeviceName,
                timestamp = serverTimeStr,
                isNewer = !localIsNewer,
                isFocused = focusIndex == 1,
                onClick = onKeepServer
            )
            ConflictChoiceRow(
                label = stringResource(R.string.ui_background_conflict_choice_skip),
                subtitle = stringResource(R.string.ui_background_conflict_choice_skip_subtitle),
                isFocused = focusIndex == 2,
                onClick = onSkip
            )
        }
    }
}

@Composable
private fun ConflictChoiceRow(
    label: String,
    isFocused: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    subtitle: String? = null,
    timestamp: String? = null,
    isNewer: Boolean = false
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    val labelColor = when {
        isFocused -> lerp(theme.focusAccent, Color.White, 0.45f)
        isNewer -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val metaColor = when {
        isFocused -> lerp(theme.focusAccent, Color.White, 0.45f).copy(alpha = 0.65f)
        isNewer -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .argosyFocusIndicators(
                focused = isFocused,
                indicators = FocusIndicators.NavRow,
                shape = shape
            )
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = labelColor,
                modifier = Modifier.size(Dimens.iconMd)
            )
            Spacer(modifier = Modifier.width(Dimens.spacingSm))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isNewer) FontWeight.Bold else FontWeight.Normal,
                color = labelColor
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = metaColor
                )
            }
        }
        if (timestamp != null) {
            Text(
                text = timestamp,
                style = MaterialTheme.typography.bodySmall,
                color = metaColor
            )
        }
    }
}

private fun Instant.toRelativeString(context: Context): String {
    val now = Instant.now()
    val duration = Duration.between(this, now)
    val resources = context.resources
    return when {
        duration.isNegative -> context.getString(R.string.ui_background_conflict_age_future)
        duration.toMinutes() < 1 -> context.getString(R.string.ui_background_conflict_age_now)
        duration.toHours() < 1 -> resources.getQuantityString(
            R.plurals.ui_background_conflict_age_minutes,
            duration.toMinutes().toInt(),
            duration.toMinutes().toInt()
        )
        duration.toDays() < 1 -> resources.getQuantityString(
            R.plurals.ui_background_conflict_age_hours,
            duration.toHours().toInt(),
            duration.toHours().toInt()
        )
        duration.toDays() < 30 -> resources.getQuantityString(
            R.plurals.ui_background_conflict_age_days,
            duration.toDays().toInt(),
            duration.toDays().toInt()
        )
        else -> resources.getQuantityString(
            R.plurals.ui_background_conflict_age_months,
            (duration.toDays() / 30).toInt(),
            (duration.toDays() / 30).toInt()
        )
    }
}
