package com.nendo.argosy.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.PermissionTier
import com.nendo.argosy.ui.primitives.ArgosyIndeterminateProgressBar
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.PanelSectionHeader
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.LocalMotionTier
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.MotionTier
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.util.clickableNoFocus

enum class PermissionRowState { MISSING, CHECKING, GRANTED, UNAVAILABLE }

@StringRes
private fun PermissionTier.labelRes(): Int = when (this) {
    PermissionTier.REQUIRED -> R.string.ui_permission_tier_required
    PermissionTier.RECOMMENDED -> R.string.ui_permission_tier_recommended
    PermissionTier.OTHER -> R.string.ui_permission_tier_other
}

@Composable
fun PermissionTierHeader(
    tier: PermissionTier,
    modifier: Modifier = Modifier,
    note: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        PanelSectionHeader(title = stringResource(tier.labelRes()))
        if (note != null) {
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = LocalArgosyTheme.current.textDim,
                modifier = Modifier.padding(top = Dimens.spacingXs)
            )
        }
    }
}

/**
 * A permission named by the Argosy feature it enables, with Android's name for it as the secondary
 * line and a live state chip. [dense] puts the Android name inline on a 40 dp row. [onClick] null
 * leaves the row untappable.
 */
@Composable
fun PermissionRow(
    icon: ImageVector,
    title: String,
    androidName: String,
    state: PermissionRowState,
    isFocused: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    dense: Boolean = false,
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    val titleColor = if (isFocused) lerp(theme.focusAccent, Color.White, 0.45f) else theme.textPrimary
    val stateSpec = permissionStateSpec()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(if (dense) Dimens.menuRowHeight else Dimens.menuRowHeightLg)
            .clip(shape)
            .argosyFocusIndicators(focused = isFocused, indicators = FocusIndicators.ListRow, shape = shape)
            .clickableNoFocus(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
    ) {
        Crossfade(targetState = state, animationSpec = stateSpec, label = "permission-icon") { shown ->
            PermissionLeadingIcon(icon = icon, state = shown, isFocused = isFocused)
        }
        if (dense) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                PermissionTitle(title, titleColor, Modifier.weight(1f, fill = false))
                Box(
                    modifier = Modifier
                        .width(Dimens.borderThin)
                        .height(Dimens.iconXs)
                        .background(theme.hairlineHigh)
                )
                PermissionAndroidName(androidName, Modifier.weight(1f, fill = false))
            }
        } else {
            Column(modifier = Modifier.weight(1f)) {
                PermissionTitle(title, titleColor)
                PermissionAndroidName(androidName)
            }
        }
        Crossfade(targetState = state, animationSpec = stateSpec, label = "permission-chip") { shown ->
            PermissionStateChip(state = shown)
        }
    }
}

@Composable
private fun permissionStateSpec(): FiniteAnimationSpec<Float> = when (LocalMotionTier.current) {
    MotionTier.Reduced -> snap()
    MotionTier.Snappy -> tween(Motion.durationMicro, easing = Motion.argosyEase)
    MotionTier.Comfortable -> tween(Motion.durationContent, easing = Motion.argosyEase)
}

@Composable
private fun PermissionTitle(title: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
private fun PermissionAndroidName(androidName: String, modifier: Modifier = Modifier) {
    Text(
        text = androidName,
        style = MaterialTheme.typography.labelMedium,
        color = LocalArgosyTheme.current.textDim,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
private fun PermissionLeadingIcon(icon: ImageVector, state: PermissionRowState, isFocused: Boolean) {
    val theme = LocalArgosyTheme.current
    val tint = when {
        state == PermissionRowState.GRANTED -> LocalLauncherTheme.current.semanticColors.success
        state == PermissionRowState.UNAVAILABLE -> theme.textMute
        isFocused -> lerp(theme.focusAccent, Color.White, 0.45f)
        else -> theme.textDim
    }
    Icon(
        imageVector = if (state == PermissionRowState.GRANTED) Icons.Default.CheckCircle else icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(Dimens.iconMd)
    )
}

@Composable
private fun PermissionStateChip(state: PermissionRowState) {
    val theme = LocalArgosyTheme.current
    val success = LocalLauncherTheme.current.semanticColors.success
    val s = LocalUiScale.current.scale
    val pill = RoundedCornerShape(Dimens.radiusPill)
    val fill = if (state == PermissionRowState.GRANTED) {
        success.copy(alpha = ComponentDefaults.Setup.chipGrantedFillAlpha)
    } else {
        theme.surfaceElevated
    }
    val labelColor = when (state) {
        PermissionRowState.GRANTED -> success
        PermissionRowState.UNAVAILABLE -> theme.textMute
        PermissionRowState.MISSING, PermissionRowState.CHECKING -> theme.textDim
    }
    val labelRes = when (state) {
        PermissionRowState.GRANTED -> R.string.ui_permission_row_chip_allowed
        PermissionRowState.MISSING -> R.string.ui_permission_row_chip_not_allowed
        PermissionRowState.CHECKING -> R.string.ui_permission_row_chip_checking
        PermissionRowState.UNAVAILABLE -> R.string.ui_permission_row_chip_unavailable
    }
    Box(
        modifier = Modifier
            .height((ComponentDefaults.Setup.chipHeightDp * s).dp)
            .clip(pill)
            .background(fill),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
        ) {
            if (state == PermissionRowState.GRANTED) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = success,
                    modifier = Modifier.size(Dimens.iconXs)
                )
            }
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelMedium,
                color = labelColor,
                maxLines = 1
            )
        }
        if (state == PermissionRowState.CHECKING) {
            ArgosyIndeterminateProgressBar(
                modifier = Modifier
                    .matchParentSize()
                    .wrapContentHeight(Alignment.Bottom)
            )
        }
    }
}
