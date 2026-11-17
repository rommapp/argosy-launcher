package com.nendo.argosy.ui.quickmenu.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.primitives.InputGlyph
import com.nendo.argosy.ui.quickmenu.QuickMenuOrb
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.util.clickableNoFocus

enum class TabStripPlacement { TOP, BOTTOM }

/**
 * The section tabs. [TabStripPlacement.TOP] labels every tab and underlines the selected one;
 * [TabStripPlacement.BOTTOM] is the phone bar, labelling the selected tab only. [showGlyphs] draws
 * the bumper glyphs at the ends.
 */
@Composable
fun QuickMenuTabStrip(
    selectedOrb: QuickMenuOrb,
    placement: TabStripPlacement,
    showGlyphs: Boolean,
    showRunningDot: Boolean,
    onTabClick: (QuickMenuOrb) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showGlyphs) InputGlyph(button = InputButton.LB, modifier = Modifier.clickableNoFocus(onClick = onPrev))
        QuickMenuOrb.entries.forEach { orb ->
            val isSelected = orb == selectedOrb
            val dot = showRunningDot && orb == QuickMenuOrb.RECENT
            val tabModifier = Modifier.weight(1f)
            when (placement) {
                TabStripPlacement.TOP -> TopTab(orb, isSelected, dot, { onTabClick(orb) }, tabModifier)
                TabStripPlacement.BOTTOM -> BottomTab(orb, isSelected, dot, { onTabClick(orb) }, tabModifier)
            }
        }
        if (showGlyphs) InputGlyph(button = InputButton.RB, modifier = Modifier.clickableNoFocus(onClick = onNext))
    }
}

@Composable
private fun TopTab(
    orb: QuickMenuOrb,
    isSelected: Boolean,
    showRunningDot: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusMd)
    val accent = theme.focusAccent
    val selectedFill = if (isSelected) accent.copy(alpha = ComponentDefaults.QuickPanel.selectedFillAlpha) else Color.Transparent
    val indicatorHeight = Dimens.borderThick
    val indicatorInset = Dimens.spacingMd
    Column(
        modifier = modifier
            .clip(shape)
            .background(selectedFill, shape)
            .drawBehind {
                if (!isSelected) return@drawBehind
                val h = indicatorHeight.toPx()
                val inset = indicatorInset.toPx().coerceAtMost(size.width / 4f)
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(inset, size.height - h),
                    size = Size(size.width - inset * 2f, h),
                    cornerRadius = CornerRadius(h / 2f, h / 2f)
                )
            }
            .clickableNoFocus(onClick = onClick)
            .padding(vertical = Dimens.spacingXs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        TabIcon(orb = orb, tint = if (isSelected) accent else theme.textDim, showRunningDot = showRunningDot)
        Text(
            text = stringResource(orb.labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) theme.textPrimary else theme.textDim,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible
        )
    }
}

@Composable
private fun BottomTab(
    orb: QuickMenuOrb,
    isSelected: Boolean,
    showRunningDot: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    val accent = theme.focusAccent
    val pill = RoundedCornerShape(Dimens.radiusPill)
    Column(
        modifier = modifier
            .clickableNoFocus(onClick = onClick)
            .padding(vertical = Dimens.spacingXs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        Box(
            modifier = Modifier
                .clip(pill)
                .background(if (isSelected) accent.copy(alpha = QuickMenuTabTokens.chipTintAlpha) else Color.Transparent, pill)
                .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingXs),
            contentAlignment = Alignment.Center
        ) {
            TabIcon(orb = orb, tint = if (isSelected) accent else theme.textDim, showRunningDot = showRunningDot)
        }
        if (isSelected) {
            Text(
                text = stringResource(orb.labelRes),
                style = MaterialTheme.typography.labelSmall,
                color = theme.textPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible
            )
        }
    }
}

private val QuickMenuTabTokens = ComponentDefaults.QuickMenu

@Composable
private fun TabIcon(orb: QuickMenuOrb, tint: Color, showRunningDot: Boolean) {
    Box {
        Icon(
            imageVector = orb.icon,
            contentDescription = stringResource(orb.labelRes),
            tint = tint,
            modifier = Modifier.size(Dimens.iconSm)
        )
        if (showRunningDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(Dimens.spacingSm)
                    .background(LocalLauncherTheme.current.semanticColors.progress, CircleShape)
            )
        }
    }
}
