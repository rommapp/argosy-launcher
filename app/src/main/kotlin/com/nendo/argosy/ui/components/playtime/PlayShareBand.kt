package com.nendo.argosy.ui.components.playtime

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.ui.components.PlatformIconAssets
import com.nendo.argosy.ui.components.SegmentedMeterBar
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.generated.ComponentDefaults

/**
 * One segment of a proportional band and its legend row. [platformSlug] fetches the platform
 * glyph when the app has one; [badge] is a pill after the name.
 */
data class ShareSegment(
    val label: String,
    val valueMs: Long,
    val color: Color,
    val valueLabel: String,
    val shareLabel: String,
    val platformSlug: String? = null,
    val badge: String? = null
)

/**
 * A slim proportional band with surface gaps between segments and a legend beneath it. Legend
 * text stays in text ink; the swatch beside it carries the identity.
 */
@Composable
fun PlayShareBand(
    title: String?,
    segments: List<ShareSegment>,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    val total = remember(segments) { segments.sumOf { it.valueMs } }
    val fills = remember(segments) { segments.map { it.color to it.valueMs } }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = theme.textDim,
                maxLines = 1
            )
        }
        SegmentedMeterBar(
            totalBytes = total,
            segments = fills,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
            segments.forEach { segment -> ShareLegendRow(segment) }
        }
    }
}

@Composable
private fun ShareLegendRow(segment: ShareSegment) {
    val theme = LocalArgosyTheme.current
    val s = LocalUiScale.current.scale
    val context = LocalContext.current
    val swatch = (ComponentDefaults.PlayTimeChart.legendSwatch * s).dp
    val glyph = segment.platformSlug?.let { slug ->
        remember(slug) { PlatformIconAssets.resolveAssetUri(context, slug) }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
        Box(modifier = Modifier.size(swatch).clip(CircleShape).background(segment.color))
        if (glyph != null) {
            AsyncImage(
                model = glyph,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(Dimens.iconSm)
            )
        }
        Text(
            text = segment.label,
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (segment.badge != null) {
            Text(
                text = segment.badge,
                style = MaterialTheme.typography.labelSmall,
                color = theme.textPrimary,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(Dimens.radiusPill))
                    .background(theme.surfaceRaised)
                    .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs)
            )
        }
        Text(
            text = segment.valueLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textPrimary,
            maxLines = 1
        )
        Text(
            text = segment.shareLabel,
            style = MaterialTheme.typography.bodySmall,
            color = theme.textDim,
            maxLines = 1,
            textAlign = TextAlign.End,
            modifier = Modifier.width((ComponentDefaults.PlayTimeChart.legendShareWidth * s).dp)
        )
    }
}
