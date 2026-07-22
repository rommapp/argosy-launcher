package com.nendo.argosy.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.theme.generated.DimensionTokens

object Dimens {
    private val scale: Float
        @Composable get() = LocalUiScale.current.scale

    val spacingXs: Dp @Composable get() = maxOf(DimensionTokens.Spacing.Xs.base.dp * scale, DimensionTokens.Spacing.Xs.floor.dp)
    val spacingSm: Dp @Composable get() = maxOf(DimensionTokens.Spacing.Sm.base.dp * scale, DimensionTokens.Spacing.Sm.floor.dp)
    val spacingMd: Dp @Composable get() = maxOf(DimensionTokens.Spacing.Md.base.dp * scale, DimensionTokens.Spacing.Md.floor.dp)
    val spacingLg: Dp @Composable get() = maxOf(DimensionTokens.Spacing.Lg.base.dp * scale, DimensionTokens.Spacing.Lg.floor.dp)
    val spacingXl: Dp @Composable get() = maxOf(DimensionTokens.Spacing.Xl.base.dp * scale, DimensionTokens.Spacing.Xl.floor.dp)
    val spacingXxl: Dp @Composable get() = maxOf(DimensionTokens.Spacing.Xxl.base.dp * scale, DimensionTokens.Spacing.Xxl.floor.dp)

    val radiusSm: Dp @Composable get() = DimensionTokens.Radius.sm.dp * scale
    val radiusMd: Dp @Composable get() = DimensionTokens.Radius.md.dp * scale
    val radiusLg: Dp @Composable get() = DimensionTokens.Radius.lg.dp * scale
    val radiusXl: Dp @Composable get() = DimensionTokens.Radius.xl.dp * scale
    val radiusPanel: Dp @Composable get() = DimensionTokens.Radius.panel.dp * scale
    val radiusControl: Dp @Composable get() = DimensionTokens.Radius.control.dp * scale
    val radiusPill: Dp get() = DimensionTokens.Radius.pill.dp

    val gameCardWidth: Dp @Composable get() = DimensionTokens.Layout.gameCardWidth.dp * scale
    val gameCardHeight: Dp @Composable get() = DimensionTokens.Layout.gameCardHeight.dp * scale
    val coverPanelWidth: Dp @Composable get() = DimensionTokens.Layout.coverPanelWidth.dp * scale
    val storageGameCoverWidth: Dp @Composable get() = DimensionTokens.Layout.storageGameCoverWidth.dp * scale
    val storageGameCoverHeight: Dp @Composable get() = DimensionTokens.Layout.storageGameCoverHeight.dp * scale
    val settingsItemMinHeight: Dp @Composable get() = DimensionTokens.Layout.settingsItemMinHeight.dp * scale
    val menuRowHeight: Dp @Composable get() = DimensionTokens.Layout.menuRowHeight.dp * scale
    val menuRowHeightLg: Dp @Composable get() = DimensionTokens.Layout.menuRowHeightLg.dp * scale
    val listGap: Dp @Composable get() = maxOf(DimensionTokens.Layout.listGap.dp * scale, 4.dp)
    val buttonHeight: Dp @Composable get() = DimensionTokens.Layout.buttonHeight.dp * scale
    val buttonPaddingH: Dp @Composable get() = DimensionTokens.Layout.buttonPaddingH.dp * scale
    val buttonPaddingV: Dp @Composable get() = DimensionTokens.Layout.buttonPaddingV.dp * scale

    val dotSm: Dp @Composable get() = DimensionTokens.Dot.sm.dp * scale
    val dotLg: Dp @Composable get() = DimensionTokens.Dot.lg.dp * scale
    val iconXs: Dp @Composable get() = DimensionTokens.Icon.xs.dp * scale
    val iconSm: Dp @Composable get() = DimensionTokens.Icon.sm.dp * scale
    val iconMd: Dp @Composable get() = DimensionTokens.Icon.md.dp * scale
    val iconLg: Dp @Composable get() = DimensionTokens.Icon.lg.dp * scale
    val iconXl: Dp @Composable get() = DimensionTokens.Icon.xl.dp * scale

    val headerHeight: Dp @Composable get() = DimensionTokens.Layout.headerHeight.dp * scale
    val headerHeightLg: Dp @Composable get() = DimensionTokens.Layout.headerHeightLg.dp * scale
    val footerHeight: Dp @Composable get() = DimensionTokens.Layout.footerHeight.dp * scale
    val modalWidth: Dp @Composable get() = DimensionTokens.Layout.modalWidth.dp * scale
    val modalWidthLg: Dp @Composable get() = DimensionTokens.Layout.modalWidthLg.dp * scale
    val modalWidthXl: Dp @Composable get() = DimensionTokens.Layout.modalWidthXl.dp * scale

    val borderThin = DimensionTokens.Border.thin.dp
    val borderMedium = DimensionTokens.Border.medium.dp
    val borderThick = DimensionTokens.Border.thick.dp

    val elevationNone = DimensionTokens.Elevation.none.dp
    val elevationSm = DimensionTokens.Elevation.sm.dp
    val elevationMd = DimensionTokens.Elevation.md.dp
    val elevationLg = DimensionTokens.Elevation.lg.dp
    val elevationFocused = DimensionTokens.Elevation.focused.dp
}
