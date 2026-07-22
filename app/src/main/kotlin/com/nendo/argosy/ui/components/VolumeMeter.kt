package com.nendo.argosy.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.graphics.Color as AndroidColor
import kotlin.math.abs
import kotlin.math.min
import com.nendo.argosy.core.storage.StorageVolumeType
import com.nendo.argosy.data.storage.StorageVolumeInfo
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.util.formatBytes
import com.nendo.argosy.util.formatRelativeTimeShort
import java.time.Instant

data class VolumeMeterCategory(
    val label: String,
    val color: Color,
    val bytes: Long,
    val perVolume: Map<String, Long>
)

/**
 * Distinct segment colors derived from the theme: primary first, secondary when it reads
 * as a different hue, then analogous hue rotations of primary. Low-saturation themes fall
 * back to a brightness ramp so monochrome setups stay monochrome.
 */
fun volumeMeterCategoryColors(primary: Color, secondary: Color, count: Int): List<Color> {
    val primaryHsv = FloatArray(3).also { AndroidColor.colorToHSV(primary.toArgb(), it) }
    if (primaryHsv[1] < MIN_SATURATION_FOR_HUES) {
        return List(count) { index ->
            val value = (primaryHsv[2] * (1f - VALUE_RAMP_STEP * index)).coerceIn(0.2f, 1f)
            Color.hsv(primaryHsv[0], primaryHsv[1], value)
        }
    }
    val colors = mutableListOf(primary)
    if (count > 1 && hueDistance(primary, secondary) >= MIN_HUE_SEPARATION) colors.add(secondary)
    for (offset in HUE_OFFSETS) {
        if (colors.size >= count) break
        val candidate = rotateHue(primary, offset)
        if (colors.all { hueDistance(it, candidate) >= MIN_HUE_SEPARATION }) colors.add(candidate)
    }
    var fallbackStep = 1
    while (colors.size < count) {
        colors.add(rotateHue(primary, 360f / (count + 1) * fallbackStep++))
    }
    return colors.take(count)
}

private fun hueDistance(a: Color, b: Color): Float {
    val hsvA = FloatArray(3).also { AndroidColor.colorToHSV(a.toArgb(), it) }
    val hsvB = FloatArray(3).also { AndroidColor.colorToHSV(b.toArgb(), it) }
    if (hsvA[1] < MIN_SATURATION_FOR_HUES || hsvB[1] < MIN_SATURATION_FOR_HUES) return 0f
    val diff = abs(hsvA[0] - hsvB[0])
    return min(diff, 360f - diff)
}

private fun rotateHue(color: Color, degrees: Float): Color {
    val hsv = FloatArray(3).also { AndroidColor.colorToHSV(color.toArgb(), it) }
    hsv[0] = (hsv[0] + degrees + 360f) % 360f
    return Color.hsv(hsv[0], hsv[1], hsv[2])
}

private const val MIN_HUE_SEPARATION = 30f
private const val MIN_SATURATION_FOR_HUES = 0.15f
private const val VALUE_RAMP_STEP = 0.22f
private val HUE_OFFSETS = listOf(40f, -40f, 80f, -80f, 120f, -120f, 160f)

/**
 * Shared volume->color assignment for storage screens: internal volumes take [neutral],
 * external volumes take a theme-derived accent ramp, keyed by [StorageVolumeInfo.key].
 */
fun storageVolumeColors(
    volumes: List<StorageVolumeInfo>,
    neutral: Color,
    primary: Color,
    secondary: Color
): Map<String, Color> {
    val externalCount = volumes.count { it.type != StorageVolumeType.INTERNAL }
    val accents = if (externalCount > 0) {
        volumeMeterCategoryColors(primary, secondary, externalCount)
    } else {
        emptyList()
    }
    var accentIndex = 0
    return volumes.associate { volume ->
        volume.key to if (volume.type == StorageVolumeType.INTERNAL) {
            neutral
        } else {
            accents[accentIndex++]
        }
    }
}

/** Non-focusable per-volume usage hero: segmented meters and shared legend. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VolumeMeterHero(
    volumes: List<StorageVolumeInfo>,
    categories: List<VolumeMeterCategory>,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    val s = LocalUiScale.current.scale
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val otherColor = MaterialTheme.colorScheme.onSurfaceVariant
        .copy(alpha = ComponentDefaults.VolumeMeter.otherFillAlpha)
        .compositeOver(trackColor)
    val otherBytes = volumes.sumOf { volume ->
        val used = volume.totalBytes - volume.availableBytes
        val argosy = categories.sumOf { it.perVolume[volume.key] ?: 0L }
        (used - argosy).coerceAtLeast(0L)
    }
    val swatchRadius = (ComponentDefaults.VolumeMeter.radius * s).dp

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        volumes.forEach { volume ->
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = volume.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        color = theme.textPrimary,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${formatBytes(volume.availableBytes)} free",
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textDim
                    )
                }
                val segments = buildList {
                    categories.forEach { category ->
                        add(category.color to (category.perVolume[volume.key] ?: 0L))
                    }
                    val argosyBytes = categories.sumOf { it.perVolume[volume.key] ?: 0L }
                    val usedBytes = volume.totalBytes - volume.availableBytes
                    add(otherColor to (usedBytes - argosyBytes).coerceAtLeast(0L))
                }
                SegmentedMeterBar(
                    totalBytes = volume.totalBytes,
                    segments = segments,
                    trackColor = trackColor
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy((ComponentDefaults.StorageLegend.gap * s).dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
        ) {
            categories.forEach { category ->
                LegendEntry(
                    swatchColor = category.color,
                    swatchRadius = swatchRadius,
                    label = category.label,
                    bytes = category.bytes
                )
            }
            if (otherBytes > 0L) {
                LegendEntry(
                    swatchColor = otherColor,
                    swatchRadius = swatchRadius,
                    label = "Other apps & files",
                    bytes = otherBytes
                )
            }
        }
    }
}

/** "Computed X ago" label for storage stats; states: refreshing, computed, never computed. */
fun storageComputedLabel(computedAtMillis: Long?, isRefreshing: Boolean): String = when {
    isRefreshing -> "Computing..."
    computedAtMillis != null && computedAtMillis > 0L ->
        "Computed ${formatRelativeTimeShort(Instant.ofEpochMilli(computedAtMillis))}"
    else -> "Not computed yet"
}

@Composable
private fun LegendEntry(
    swatchColor: Color,
    swatchRadius: Dp,
    label: String,
    bytes: Long
) {
    val theme = LocalArgosyTheme.current
    val s = LocalUiScale.current.scale
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        Box(
            modifier = Modifier
                .size((ComponentDefaults.StorageLegend.swatchSize * s).dp)
                .background(swatchColor, RoundedCornerShape(swatchRadius))
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = theme.textDim
        )
        Text(
            text = formatBytes(bytes),
            style = MaterialTheme.typography.bodySmall,
            color = theme.textPrimary
        )
    }
}

/** Segmented meter: fill fraction = segment bytes over [totalBytes], drawn left to right. */
@Composable
fun SegmentedMeterBar(
    totalBytes: Long,
    segments: List<Pair<Color, Long>>,
    trackColor: Color,
    modifier: Modifier = Modifier
) {
    val s = LocalUiScale.current.scale
    val height = (ComponentDefaults.VolumeMeter.height * s).dp
    val radius = (ComponentDefaults.VolumeMeter.radius * s).dp
    val gap = (ComponentDefaults.VolumeMeter.segmentGap * s).dp
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val corner = CornerRadius(radius.toPx(), radius.toPx())
        drawRoundRect(color = trackColor, cornerRadius = corner)
        if (totalBytes <= 0L) return@Canvas
        val gapPx = gap.toPx()
        val clip = Path().apply {
            addRoundRect(RoundRect(0f, 0f, size.width, size.height, corner))
        }
        clipPath(clip) {
            var x = 0f
            segments.forEach { (color, bytes) ->
                if (bytes <= 0L || x >= size.width) return@forEach
                val width = (bytes.toDouble() / totalBytes * size.width).toFloat()
                if (width <= 0f) return@forEach
                drawRect(
                    color = color,
                    topLeft = Offset(x, 0f),
                    size = Size(width.coerceAtMost(size.width - x), size.height)
                )
                x += width + gapPx
            }
        }
    }
}

