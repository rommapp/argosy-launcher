package com.nendo.argosy.ui.quickmenu

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import com.nendo.argosy.ui.quickmenu.components.QuickMenuContent
import com.nendo.argosy.ui.quickmenu.components.QuickMenuOrbRow
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.Motion

@Composable
fun QuickMenuOverlay(
    viewModel: QuickMenuViewModel,
    onGameSelect: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDarkTheme = LocalLauncherTheme.current.isDarkTheme

    val overlayColor = if (isDarkTheme) {
        Color.Black.copy(alpha = 0.85f)
    } else {
        Color.White.copy(alpha = 0.9f)
    }

    val topSpacerWeight by animateFloatAsState(
        targetValue = if (uiState.contentFocused) 0f else 1f,
        animationSpec = Motion.focusSpring,
        label = "topSpacerWeight"
    )

    val contentAlpha by animateFloatAsState(
        targetValue = if (uiState.contentFocused) 1f else 0f,
        animationSpec = tween(200),
        label = "contentAlpha"
    )

    // Scrim fade
    AnimatedVisibility(
        visible = uiState.isVisible,
        enter = fadeIn(animationSpec = tween(250)),
        exit = fadeOut(animationSpec = tween(200)),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(overlayColor)
        )
    }

    // Content with scale + fade
    AnimatedVisibility(
        visible = uiState.isVisible,
        enter = fadeIn(animationSpec = tween(300, delayMillis = 50)) +
            scaleIn(
                initialScale = 0.92f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                transformOrigin = TransformOrigin(0.5f, 0.3f)
            ),
        exit = fadeOut(animationSpec = tween(150)) +
            scaleOut(
                targetScale = 0.95f,
                animationSpec = tween(150),
                transformOrigin = TransformOrigin(0.5f, 0.3f)
            ),
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Dimens.spacingLg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (topSpacerWeight > 0.01f) {
                Spacer(modifier = Modifier.weight(topSpacerWeight))
            }

            QuickMenuOrbRow(
                selectedOrb = uiState.selectedOrb,
                isOrbRowFocused = !uiState.contentFocused,
                onOrbClick = { orb ->
                    viewModel.selectOrb(orb)
                    viewModel.enterContent()
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(Dimens.spacingLg))

            QuickMenuContent(
                uiState = uiState,
                isFocused = uiState.contentFocused,
                onSearchQueryChange = viewModel::updateSearchQuery,
                onGameSelect = { gameId ->
                    viewModel.hide()
                    onGameSelect(gameId)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .alpha(contentAlpha)
            )

            if (topSpacerWeight > 0.01f) {
                Spacer(modifier = Modifier.weight(topSpacerWeight))
            }
        }
    }
}
