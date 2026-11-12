package com.nendo.argosy.ui.components.update

import android.content.Context
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.markdown.ReleaseNotesBody
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.primitives.ActionButton
import com.nendo.argosy.ui.primitives.ModalScaffold
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.verticalEdgeFade
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface UpdatePromptEntryPoint {
    fun updatePromptController(): UpdatePromptController
}

private fun updatePromptController(context: Context): UpdatePromptController =
    EntryPointAccessors.fromApplication(context.applicationContext, UpdatePromptEntryPoint::class.java)
        .updatePromptController()

private const val NOTES_HEIGHT_FRACTION = 0.5f
private const val NOTES_SCROLL_ROWS = 3

/**
 * The update prompt over the control surface. It opens for a pending release once [idle] holds
 * and Argosy is resumed, and holds the input stack while open.
 */
@Composable
fun UpdatePromptOverlay(idle: Boolean) {
    val context = LocalContext.current
    val controller = remember { updatePromptController(context) }
    val pending by controller.pending.collectAsState()
    val state by controller.state.collectAsState()
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val inFront = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)

    LaunchedEffect(pending, idle, inFront) {
        controller.onPending(pending, idle && inFront)
    }

    val open = state
    ModalInputEffect(active = open != null, handler = controller.inputHandler, onInputLost = controller::close)
    if (open != null) {
        FooterHints(
            hints = listOf(InputButton.RIGHT_STICK_VERTICAL to stringResource(R.string.update_prompt_hint_scroll)),
            onHintClick = { controller.scrollNotes(1) }
        )
    }

    var lastShown by remember { mutableStateOf<UpdatePromptState?>(null) }
    SideEffect { open?.let { lastShown = it } }

    ModalScaffold(
        visible = open != null,
        onDismiss = { controller.activate(UpdatePromptAction.LATER) },
        maxWidth = Dimens.modalWidthLg
    ) {
        val shown = open ?: lastShown ?: return@ModalScaffold
        UpdatePromptContent(
            state = shown,
            onAction = { action ->
                controller.focus(action)
                controller.activate(action)
            }
        )
    }
}

@Composable
private fun UpdatePromptContent(
    state: UpdatePromptState,
    onAction: (UpdatePromptAction) -> Unit
) {
    val theme = LocalArgosyTheme.current
    val release = state.release
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val stepPx = with(LocalDensity.current) { (Dimens.menuRowHeight * NOTES_SCROLL_ROWS).toPx() }
    val latest by rememberUpdatedState(state)

    LaunchedEffect(release.tag) { scroll.scrollTo(0) }
    LaunchedEffect(scroll, stepPx) {
        var seen = latest.bodyScrolls
        snapshotFlow { latest.bodyScrolls }.collect { requests ->
            val steps = requests - seen
            seen = requests
            if (steps > 0) scope.launch { scroll.animateScrollBy(latest.bodyScrollDirection * steps * stepPx) }
        }
    }

    val panelShape = RoundedCornerShape(Dimens.radiusControl)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.spacingLg),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Text(
            text = stringResource(R.string.update_prompt_title, release.displayVersion),
            style = MaterialTheme.typography.titleLarge,
            color = theme.textPrimary
        )
        if (release.name.isNotBlank() && release.name != release.tag) {
            Text(
                text = release.name,
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textDim
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(NOTES_HEIGHT_FRACTION)
                .clip(panelShape)
                .border(width = Dimens.borderThin, color = theme.hairlineLow, shape = panelShape)
                .verticalEdgeFade(scroll, fadeHeight = Dimens.spacingMd)
                .padding(Dimens.spacingMd)
                .verticalScroll(scroll)
        ) {
            if (release.notes.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.update_prompt_notes_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textDim
                )
            } else {
                ReleaseNotesBody(body = release.notes)
            }
        }
        val status = when {
            state.downloadProgress != null -> stringResource(R.string.update_prompt_downloading, state.downloadProgress)
            state.failed -> stringResource(R.string.update_prompt_download_failed)
            else -> null
        }
        if (status != null) {
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = if (state.failed) MaterialTheme.colorScheme.error else theme.textDim
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UpdatePromptAction.entries.forEach { action ->
                ActionButton(
                    label = stringResource(action.labelRes),
                    onClick = { onAction(action) },
                    focused = state.focusedAction == action,
                    primary = action == UpdatePromptAction.INSTALL,
                    enabled = action != UpdatePromptAction.INSTALL || state.downloadProgress == null
                )
            }
        }
    }
}

private val UpdatePromptAction.labelRes: Int
    get() = when (this) {
        UpdatePromptAction.INSTALL -> R.string.update_prompt_action_install
        UpdatePromptAction.SKIP -> R.string.update_prompt_action_skip
        UpdatePromptAction.LATER -> R.string.update_prompt_action_later
    }
