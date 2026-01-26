package com.nendo.argosy.ui.quickmenu.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.nendo.argosy.ui.util.clickableNoFocus
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File
import com.nendo.argosy.ui.quickmenu.GameRowUi
import com.nendo.argosy.ui.quickmenu.MetadataType
import com.nendo.argosy.ui.theme.Dimens

@Composable
fun QuickMenuGameRow(
    game: GameRowUi,
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(Dimens.radiusLg)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isFocused) {
                    Modifier.border(Dimens.borderMedium, MaterialTheme.colorScheme.primary, shape)
                } else Modifier
            )
            .background(
                if (isFocused) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
                shape
            )
            .clip(shape)
            .clickableNoFocus(onClick = onClick)
            .padding(Dimens.radiusLg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val imageData = game.coverPath?.let { path ->
            if (path.startsWith("/")) File(path) else path
        }
        AsyncImage(
            model = imageData,
            contentDescription = game.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(Dimens.iconXl + Dimens.spacingSm)
                .clip(RoundedCornerShape(Dimens.radiusMd))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )

        Spacer(modifier = Modifier.width(Dimens.spacingMd))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = game.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = game.platformName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )

                if (game.isDownloaded) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Downloaded",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(Dimens.iconXs)
                    )
                }
            }
        }

        if (game.metadata.isNotEmpty()) {
            Spacer(modifier = Modifier.width(Dimens.spacingMd))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
            ) {
                if (game.metadataType == MetadataType.RATING) {
                    Icon(
                        Icons.Default.People,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(Dimens.spacingMd)
                    )
                }
                Text(
                    text = game.metadata,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
