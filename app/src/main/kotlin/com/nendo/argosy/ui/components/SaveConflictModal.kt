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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.content.Context
import com.nendo.argosy.R
import com.nendo.argosy.ui.primitives.ActionButton
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.ColorTokens
import java.time.Duration
import java.time.Instant

data class SaveConflictInfo(
    val gameId: Long,
    val gameName: String,
    val emulatorId: String,
    val channelName: String?,
    val localTimestamp: Instant,
    val serverTimestamp: Instant,
    val serverDeviceName: String? = null
)

@Composable
fun SaveConflictModal(
    info: SaveConflictInfo,
    focusedButton: Int,
    onKeepLocal: () -> Unit,
    onOverwrite: () -> Unit
) {
    val localIsNewer = info.localTimestamp.isAfter(info.serverTimestamp)
    val context = LocalContext.current

    Modal(
        title = stringResource(R.string.ui_save_conflict_title),
        baseWidth = 400.dp,
        onDismiss = onKeepLocal,
        titleContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                GameTitle(
                    title = info.gameName,
                    titleStyle = MaterialTheme.typography.titleMedium,
                    titleColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(Dimens.spacingMd))
                Text(
                    text = info.channelName
                        ?: stringResource(R.string.ui_save_conflict_default_slot),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    ) {
        Text(
            text = stringResource(R.string.ui_save_conflict_message),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(Dimens.spacingMd))

        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
            SaveSourceRow(
                icon = Icons.Default.PhoneAndroid,
                label = stringResource(R.string.ui_save_conflict_source_local),
                timestamp = info.localTimestamp.toRelativeString(context),
                isNewer = localIsNewer
            )
            SaveSourceRow(
                icon = Icons.Default.Cloud,
                label = stringResource(R.string.ui_save_conflict_source_server),
                subtitle = info.serverDeviceName,
                timestamp = info.serverTimestamp.toRelativeString(context),
                isNewer = !localIsNewer
            )
        }

        Spacer(modifier = Modifier.height(Dimens.spacingLg))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
        ) {
            ActionButton(
                label = stringResource(R.string.ui_save_conflict_skip),
                onClick = onKeepLocal,
                focused = focusedButton == 0,
                modifier = Modifier.weight(1f)
            )

            ActionButton(
                label = stringResource(R.string.ui_save_conflict_overwrite),
                onClick = onOverwrite,
                focused = focusedButton == 1,
                primary = true,
                accentColor = LocalArgosyTheme.current.destructive,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SaveSourceRow(
    icon: ImageVector,
    label: String,
    timestamp: String,
    isNewer: Boolean,
    subtitle: String? = null
) {
    val tint = if (isNewer) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(Dimens.iconMd)
        )
        Spacer(modifier = Modifier.width(Dimens.spacingSm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isNewer) FontWeight.Bold else FontWeight.Normal,
                color = tint
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = timestamp,
            style = MaterialTheme.typography.bodySmall,
            color = if (isNewer) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun Instant.toRelativeString(context: Context): String {
    val now = Instant.now()
    val duration = Duration.between(this, now)
    val resources = context.resources
    return when {
        duration.isNegative -> context.getString(R.string.ui_save_conflict_age_future)
        duration.toMinutes() < 1 -> context.getString(R.string.ui_save_conflict_age_now)
        duration.toHours() < 1 -> resources.getQuantityString(
            R.plurals.ui_save_conflict_age_minutes,
            duration.toMinutes().toInt(),
            duration.toMinutes().toInt()
        )
        duration.toDays() < 1 -> resources.getQuantityString(
            R.plurals.ui_save_conflict_age_hours,
            duration.toHours().toInt(),
            duration.toHours().toInt()
        )
        duration.toDays() < 30 -> resources.getQuantityString(
            R.plurals.ui_save_conflict_age_days,
            duration.toDays().toInt(),
            duration.toDays().toInt()
        )
        else -> resources.getQuantityString(
            R.plurals.ui_save_conflict_age_months,
            (duration.toDays() / 30).toInt(),
            (duration.toDays() / 30).toInt()
        )
    }
}
