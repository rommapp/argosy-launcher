package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.core.content.ContextCompat
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.ScreenRole
import com.nendo.argosy.ui.components.ScreenNumberBadge
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.screens.settings.ScreenAssignment
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.util.clickableNoFocus

internal fun screenRoleTitleRes(role: ScreenRole): Int = when (role) {
    ScreenRole.PRIMARY -> R.string.settings_screens_role_primary
    ScreenRole.PRESENTATION -> R.string.settings_screens_role_presentation
    ScreenRole.APP_TARGET -> R.string.settings_screens_role_app_target
    ScreenRole.OFF -> R.string.settings_screens_role_off
}

internal fun screenRoleSubtitleRes(role: ScreenRole): Int = when (role) {
    ScreenRole.PRIMARY -> R.string.settings_screens_role_primary_detail
    ScreenRole.PRESENTATION -> R.string.settings_screens_role_presentation_detail
    ScreenRole.APP_TARGET -> R.string.settings_screens_role_app_target_detail
    ScreenRole.OFF -> R.string.settings_screens_role_off_detail
}

private const val INTERNAL_WIDTH_SHARE = 0.4f

internal fun screensMaxFocusIndex(screens: List<ScreenAssignment>): Int =
    (screens.size - 1).coerceAtLeast(0)

/**
 * The index a move lands on in the two columns the map draws, internal screens stacked on the
 * left and attached ones on the right, or null when the move leaves the map.
 */
internal fun screensFocusMove(
    screens: List<ScreenAssignment>,
    current: Int,
    dx: Int,
    dy: Int
): Int? {
    if (screens.isEmpty()) return null
    val internals = screens.indices.filter { screens[it].builtIn }
    val externals = screens.indices.filter { !screens[it].builtIn }
    val column = if (current in internals) internals else externals
    val row = column.indexOf(current)
    if (row < 0) return null
    if (dy != 0) return column.getOrNull(row + dy)
    if (dx == 0) return null
    val target = if (dx > 0) externals else internals
    if (target.isEmpty() || current in target) return null
    return target[row.coerceAtMost(target.lastIndex)]
}

@Composable
fun ScreensSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val screens = uiState.display.screens
    val builtIn = remember(screens) { screens.filter { it.builtIn } }
    val attached = remember(screens) { screens.filterNot { it.builtIn } }
    val widestInternalPx = remember(builtIn) {
        builtIn.maxOfOrNull { it.widthPx }?.coerceAtLeast(1) ?: 1
    }
    val widestExternalPx = remember(attached) {
        attached.maxOfOrNull { it.widthPx }?.coerceAtLeast(1) ?: 1
    }

    val context = LocalContext.current
    val hereDisplayId = remember(context) { ContextCompat.getDisplayOrDefault(context).displayId }
    val hereNumber = remember(screens, hereDisplayId) {
        screens.find { it.displayId == hereDisplayId }?.number
    }
    DisposableEffect(hereDisplayId) {
        val manager = DualScreenManagerHolder.instance
        manager?.showScreenNumbers(inContentDisplayId = hereDisplayId)
        onDispose { manager?.hideScreenNumbers() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingLg),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
    ) {
        Text(
            text = stringResource(R.string.settings_screens_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val internalSpan =
                if (attached.isEmpty()) maxWidth else maxWidth * INTERNAL_WIDTH_SHARE
            val externalSpan = maxWidth * (1f - INTERNAL_WIDTH_SHARE) - Dimens.spacingLg
            val internalPerPixel = internalSpan / widestInternalPx.toFloat()
            val externalPerPixel = externalSpan / widestExternalPx.toFloat()

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.width(internalSpan),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    builtIn.forEach { screen ->
                        ScreenCard(
                            screen = screen,
                            perPixel = internalPerPixel,
                            isFocused = uiState.focusedIndex == screens.indexOf(screen),
                            onClick = { viewModel.focusScreen(screens.indexOf(screen)) }
                        )
                    }
                }

                if (attached.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                    ) {
                        attached.forEach { screen ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .width(Dimens.spacingLg)
                                        .height(Dimens.borderMedium)
                                        .background(MaterialTheme.colorScheme.onSurfaceVariant)
                                )
                                ScreenCard(
                                    screen = screen,
                                    perPixel = externalPerPixel,
                                    isFocused = uiState.focusedIndex == screens.indexOf(screen),
                                    onClick = { viewModel.focusScreen(screens.indexOf(screen)) }
                                )
                            }
                        }
                    }
                }
            }
        }

    }

    ScreenSetupIdentity(
        number = hereNumber,
        modifier = Modifier.align(Alignment.BottomEnd).padding(Dimens.spacingLg)
    )
    }

    if (uiState.display.screenRoleModalOpen) {
        ScreenRoleModal(
            screen = screens.getOrNull(uiState.focusedIndex),
            focusIndex = uiState.display.screenRoleModalFocus,
            onSelect = { viewModel.assignScreenRole(it) },
            onDismiss = { viewModel.closeScreenRoleModal() }
        )
    }
}

@Composable
internal fun ScreenSetupIdentity(number: Int?, modifier: Modifier = Modifier) {
    if (number != null) ScreenNumberBadge(number = number, modifier = modifier)
}

@Composable
internal fun ScreenCard(
    screen: ScreenAssignment,
    perPixel: Dp,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val dimmed = screen.role == ScreenRole.OFF
    Box(
        modifier = Modifier
            .width(perPixel * screen.widthPx.toFloat())
            .height(perPixel * screen.heightPx.toFloat())
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .background(
                if (dimmed) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .argosyFocusIndicators(
                focused = isFocused,
                indicators = FocusIndicators.ListRow,
                shape = RoundedCornerShape(Dimens.radiusSm)
            )
            .clickableNoFocus(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = screen.number.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(Dimens.spacingSm)
        )
        Text(
            text = stringResource(screenRoleTitleRes(screen.role)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(Dimens.spacingSm)
        )
    }
}

@Composable
private fun ScreenRoleModal(
    screen: ScreenAssignment?,
    focusIndex: Int,
    onSelect: (ScreenRole) -> Unit,
    onDismiss: () -> Unit
) {
    if (screen == null) return
    com.nendo.argosy.ui.input.ModalPresenceEffect()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f))
            .clickableNoFocus(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(Dimens.modalWidthLg)
                .clip(RoundedCornerShape(Dimens.radiusPanel))
                .background(MaterialTheme.colorScheme.surface)
                .clickableNoFocus(enabled = false) {}
                .padding(Dimens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            Text(
                text = stringResource(R.string.settings_screens_role_modal_title, screen.number),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )

            ScreenRole.entries.forEachIndexed { index, role ->
                ScreenRoleOption(
                    role = role,
                    isCurrent = role == screen.role,
                    isFocused = index == focusIndex,
                    onClick = { onSelect(role) }
                )
            }
        }
    }
}

@Composable
private fun ScreenRoleOption(
    role: ScreenRole,
    isCurrent: Boolean,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .argosyFocusIndicators(
                focused = isFocused,
                indicators = FocusIndicators.ListRow,
                shape = RoundedCornerShape(Dimens.radiusSm)
            )
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(screenRoleTitleRes(role)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (isCurrent) {
                Text(
                    text = stringResource(R.string.settings_screens_role_current),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Text(
            text = stringResource(screenRoleSubtitleRes(role)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
