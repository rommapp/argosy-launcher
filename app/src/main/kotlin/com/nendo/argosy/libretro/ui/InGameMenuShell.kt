package com.nendo.argosy.libretro.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.generated.DimensionTokens
import com.nendo.argosy.ui.theme.gripReserveBottomInset
import com.nendo.argosy.ui.theme.motionSpec
import com.nendo.argosy.ui.util.clickableNoFocus

@Composable
internal fun InGameMenuPanel(
    menuItems: List<Pair<Int, InGameMenuAction>>,
    openSection: InGameMenuSection?,
    condensedWidth: Dp,
    listHandler: InputHandler,
    onFocusChange: (Int) -> Unit,
    onAction: (InGameMenuAction) -> Unit,
    onCloseSection: () -> Unit,
    list: @Composable (collapsed: Boolean) -> Unit,
    sectionContent: @Composable (InGameMenuSection) -> InputHandler
): InputHandler {
    val currentOpenSection = rememberUpdatedState(openSection)
    val currentOnFocusChange = rememberUpdatedState(onFocusChange)
    val currentOnAction = rememberUpdatedState(onAction)
    val currentOnCloseSection = rememberUpdatedState(onCloseSection)

    LaunchedEffect(openSection, menuItems) {
        val action = openSection?.action ?: return@LaunchedEffect
        val index = menuItems.indexOfFirst { it.second == action }
        if (index >= 0) currentOnFocusChange.value(index)
    }

    val uiScale = LocalUiScale.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val broadWidthPct = if (uiScale.aspectRatioClass.isWide) {
        DimensionTokens.Layout.inGameMenuBroadWidthPctWide
    } else {
        DimensionTokens.Layout.inGameMenuBroadWidthPct
    }
    val collapsed = openSection != null
    val panelWidth by animateDpAsState(
        targetValue = if (collapsed) screenWidth * (broadWidthPct / 100f) else condensedWidth,
        animationSpec = motionSpec(tween(Motion.durationSlide, easing = Motion.argosyEase)),
        label = "inGameMenuPanelWidth"
    )
    val listWidth by animateDpAsState(
        targetValue = if (collapsed) {
            DimensionTokens.Layout.inGameMenuRailWidth.dp * uiScale.scale
        } else {
            condensedWidth
        },
        animationSpec = motionSpec(tween(Motion.durationSlide, easing = Motion.argosyEase)),
        label = "inGameMenuListWidth"
    )

    val isDarkTheme = isSystemInDarkTheme()
    val overlayColor = if (isDarkTheme) {
        Color.Black.copy(alpha = SCRIM_ALPHA_DARK)
    } else {
        Color.White.copy(alpha = SCRIM_ALPHA_LIGHT)
    }
    val panelShape = RoundedCornerShape(topEnd = Dimens.radiusXl, bottomEnd = Dimens.radiusXl)

    var sectionHandler: InputHandler? = null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(overlayColor)
            .padding(bottom = gripReserveBottomInset())
            .clickableNoFocus {
                if (currentOpenSection.value != null) {
                    currentOnCloseSection.value()
                } else {
                    currentOnAction.value(InGameMenuAction.Resume)
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier
                .width(panelWidth)
                .fillMaxHeight()
                .clip(panelShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = PANEL_SURFACE_ALPHA))
                .clickableNoFocus {}
                .focusProperties { canFocus = false }
        ) {
            Box(
                modifier = Modifier
                    .width(listWidth)
                    .fillMaxHeight()
            ) {
                list(collapsed)
            }
            if (openSection != null) {
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val contentAlpha = remember(openSection) { Animatable(0f) }
                val contentFade = motionSpec(tween<Float>(Motion.durationContent))
                LaunchedEffect(openSection) {
                    contentAlpha.animateTo(1f, contentFade)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .graphicsLayer { alpha = contentAlpha.value }
                ) {
                    sectionHandler = key(openSection) { sectionContent(openSection) }
                }
            }
        }
    }

    val currentSectionHandler = rememberUpdatedState(sectionHandler)

    val shellHandler = remember {
        object : InputHandler {
            private fun forward(block: (InputHandler) -> InputResult): InputResult =
                currentSectionHandler.value?.let(block) ?: InputResult.HANDLED

            private fun forwardOrClose(block: (InputHandler) -> InputResult): InputResult {
                if (!forward(block).handled) currentOnCloseSection.value()
                return InputResult.HANDLED
            }

            override fun onUp(): InputResult = forward { it.onUp() }
            override fun onDown(): InputResult = forward { it.onDown() }
            override fun onLeft(): InputResult = forwardOrClose { it.onLeft() }
            override fun onRight(): InputResult = forward { it.onRight() }
            override fun onConfirm(): InputResult = forward { it.onConfirm() }
            override fun onBack(): InputResult = forwardOrClose { it.onBack() }
            override fun onMenu(): InputResult = forward { it.onMenu() }
            override fun onSecondaryAction(): InputResult = forward { it.onSecondaryAction() }
            override fun onContextMenu(): InputResult = forward { it.onContextMenu() }
            override fun onPrevSection(): InputResult = forward { it.onPrevSection() }
            override fun onNextSection(): InputResult = forward { it.onNextSection() }
            override fun onPrevTrigger(): InputResult = forward { it.onPrevTrigger() }
            override fun onNextTrigger(): InputResult = forward { it.onNextTrigger() }
            override fun onSelect(): InputResult = forward { it.onSelect() }
            override fun onLeftStickClick(): InputResult = forward { it.onLeftStickClick() }
            override fun onRightStickClick(): InputResult = forward { it.onRightStickClick() }
            override fun onLongConfirm(): InputResult = forward { it.onLongConfirm() }
            override fun onLongSelect(): InputResult = forward { it.onLongSelect() }
        }
    }

    return if (openSection == null) listHandler else shellHandler
}

private const val SCRIM_ALPHA_DARK = 0.7f
private const val SCRIM_ALPHA_LIGHT = 0.5f
private const val PANEL_SURFACE_ALPHA = 0.95f
