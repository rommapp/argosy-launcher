package com.nendo.argosy.ui.screens.setup.components

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import com.nendo.argosy.R
import com.nendo.argosy.data.preferences.BoxArtShape
import com.nendo.argosy.data.preferences.LibraryLayout
import com.nendo.argosy.domain.model.ArgosyPermission
import com.nendo.argosy.domain.model.HomeLayoutSettings
import com.nendo.argosy.domain.model.PermissionAvailability
import com.nendo.argosy.domain.model.ScreenRole
import com.nendo.argosy.ui.common.androidNameRes
import com.nendo.argosy.ui.common.icon
import com.nendo.argosy.ui.common.rowState
import com.nendo.argosy.ui.common.titleRes
import com.nendo.argosy.ui.components.HomeLayoutPreview
import com.nendo.argosy.ui.components.PermissionRow
import com.nendo.argosy.ui.input.ButtonGlyphSwaps
import com.nendo.argosy.ui.input.ControllerPosition
import com.nendo.argosy.ui.primitives.ArgosyProgressBar
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalMotionTier
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.MotionTier

/**
 * What the second screen shows for one setup page. Each case carries only values; text resolves
 * on the companion in its own locale.
 */
sealed interface SetupCompanionBody {
    data object Brand : SetupCompanionBody
    data class Permission(val permission: ArgosyPermission, val availability: PermissionAvailability) : SetupCompanionBody
    data class Folder(val path: String, val freeBytes: Long?, val totalBytes: Long?) : SetupCompanionBody
    data class SyncDiagram(val server: String) : SetupCompanionBody
    data class Screens(val role: ScreenRole) : SetupCompanionBody
    data class Controller(val lit: ControllerPosition?, val swaps: ButtonGlyphSwaps) : SetupCompanionBody
    data class HomePreview(val settings: HomeLayoutSettings) : SetupCompanionBody
    data class LibraryPreview(val layout: LibraryLayout, val shape: BoxArtShape) : SetupCompanionBody
}

data class SetupCompanionState(
    val rail: SetupRailState,
    @StringRes val title: Int,
    val body: SetupCompanionBody
)

/**
 * The setup page on the second screen: the rail along the top, the page title, and the page's
 * context body at glance size. It takes no input.
 */
@Composable
fun SetupCompanion(state: SetupCompanionState, modifier: Modifier = Modifier) {
    val theme = LocalArgosyTheme.current
    val reduced = LocalMotionTier.current == MotionTier.Reduced
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.spacingXl),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingLg),
    ) {
        SetupRail(state = state.rail, onCompanion = true, modifier = Modifier.fillMaxWidth())
        Text(
            text = stringResource(state.title),
            style = MaterialTheme.typography.headlineSmall,
            color = theme.textPrimary,
        )
        AnimatedContent(
            targetState = state.body,
            modifier = Modifier.fillMaxWidth().weight(1f),
            transitionSpec = {
                if (reduced) {
                    fadeIn(snap()) togetherWith fadeOut(snap())
                } else {
                    fadeIn(tween(Motion.durationContent)) togetherWith fadeOut(tween(Motion.durationContent))
                }
            },
            contentKey = { it::class },
            label = "setup-companion-body",
        ) { body ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CompanionBody(body)
            }
        }
    }
}

@Composable
private fun CompanionBody(body: SetupCompanionBody) {
    val theme = LocalArgosyTheme.current
    when (body) {
        SetupCompanionBody.Brand -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(R.drawable.ic_helm),
                contentDescription = null,
                tint = theme.focusAccent,
                modifier = Modifier.size(Dimens.iconXl),
            )
            Text(
                text = stringResource(R.string.setup_companion_brand),
                style = MaterialTheme.typography.bodyLarge,
                color = theme.textDim,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Dimens.spacingMd),
            )
        }
        is SetupCompanionBody.Permission -> {
            val permission = body.permission
            PermissionRow(
                icon = permission.icon,
                title = stringResource(permission.titleRes),
                androidName = permission.androidNameRes?.let { stringResource(it) }.orEmpty(),
                state = body.availability.rowState,
                isFocused = false,
                onClick = null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        is SetupCompanionBody.Folder -> FolderBody(body)
        is SetupCompanionBody.SyncDiagram -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
        ) {
            Text(
                text = stringResource(R.string.setup_save_sync_context_device),
                style = MaterialTheme.typography.headlineSmall,
                color = theme.textPrimary,
            )
            Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = null,
                tint = theme.focusAccent,
                modifier = Modifier.size(Dimens.iconXl),
            )
            Text(text = body.server, style = MaterialTheme.typography.headlineSmall, color = theme.textPrimary)
        }
        is SetupCompanionBody.Screens -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        ) {
            Text(
                text = stringResource(screenRoleLabelRes(body.role)),
                style = MaterialTheme.typography.headlineSmall,
                color = theme.focusAccent,
            )
            Text(
                text = stringResource(R.string.setup_screens_line),
                style = MaterialTheme.typography.bodyLarge,
                color = theme.textDim,
            )
        }
        is SetupCompanionBody.Controller -> ControllerDiagram(
            litPosition = body.lit,
            focused = false,
            onPress = null,
            swaps = body.swaps,
        )
        is SetupCompanionBody.HomePreview -> HomeLayoutPreview(settings = body.settings, modifier = Modifier.fillMaxWidth())
        is SetupCompanionBody.LibraryPreview -> LibraryLookPreview(
            layout = body.layout,
            shape = body.shape,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FolderBody(body: SetupCompanionBody.Folder) {
    val theme = LocalArgosyTheme.current
    val context = LocalContext.current
    val free = body.freeBytes
    val total = body.totalBytes
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
    ) {
        Text(
            text = body.path,
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = FontFamily.Monospace,
            color = theme.textPrimary,
        )
        if (free != null && total != null && total > 0) {
            ArgosyProgressBar(progress = 1f - free.toFloat() / total.toFloat())
            Text(
                text = stringResource(
                    R.string.setup_folders_free_of_total,
                    Formatter.formatShortFileSize(context, free),
                    Formatter.formatShortFileSize(context, total)
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = theme.textDim,
            )
        }
    }
}

@StringRes
fun screenRoleLabelRes(role: ScreenRole): Int =
    if (role == ScreenRole.PRIMARY) R.string.setup_screens_role_menus else R.string.setup_screens_role_games
