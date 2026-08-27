package com.nendo.argosy.ui.screens.media.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.nendo.argosy.R
import com.nendo.argosy.data.media.MediaAvailability
import com.nendo.argosy.ui.theme.LocalArgosyTheme

/**
 * The downloaded mark, in the one place every media surface draws it from.
 *
 * A copy on storage that is not connected keeps its mark rather than losing it: it is still
 * downloaded, and unplugging a card is not a deletion. It is drawn hollow and dimmed instead, which
 * is the same reading the storage screen gives that state. A title verified gone carries no mark at
 * all, because by then its record has been forgotten and there is nothing on this device.
 */
@Composable
fun MediaDownloadBadge(
    availability: MediaAvailability,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    when (availability) {
        MediaAvailability.PRESENT -> Icon(
            imageVector = Icons.Filled.Download,
            contentDescription = stringResource(R.string.media_download_badge_present),
            tint = theme.textPrimary,
            modifier = modifier.size(size)
        )
        MediaAvailability.UNAVAILABLE -> Icon(
            imageVector = Icons.Outlined.Download,
            contentDescription = stringResource(R.string.media_download_badge_unavailable),
            tint = theme.textMute,
            modifier = modifier.size(size)
        )
        MediaAvailability.NOT_DOWNLOADED, MediaAvailability.ABSENT -> Unit
    }
}
