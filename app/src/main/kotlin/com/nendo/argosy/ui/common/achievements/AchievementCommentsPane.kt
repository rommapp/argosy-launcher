package com.nendo.argosy.ui.common.achievements

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.AchievementComment
import com.nendo.argosy.domain.model.AchievementComments
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.animateScrollToItemCentered
import com.nendo.argosy.ui.components.keyboard.ControllerKeyboardSession
import com.nendo.argosy.ui.components.keyboard.KeyboardFieldType
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.primitives.ActionButton
import com.nendo.argosy.ui.screens.gamedetail.components.AchievementRow
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.reducedMotion
import com.nendo.argosy.util.formatRelativeTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Where a missing web API key is supplied: [SETTINGS] hands off to the RetroAchievements settings
 * row, [INLINE] opens the Argosy keyboard in the view itself.
 */
enum class CommentsKeyEntry { SETTINGS, INLINE }

/**
 * Comments on one achievement under the achievement itself, which stays pinned while the comments
 * scroll. Touch reaches the same actions as the controller through the view's buttons.
 */
@Composable
fun AchievementCommentsPane(
    state: AchievementCommentsState,
    controller: AchievementCommentsController,
    keyEntry: CommentsKeyEntry,
    onOpenKeySettings: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = Dimens.spacingLg),
    showsButtonGlyphs: Boolean = true
) {
    val actionGlyph = InputButton.A.takeIf { showsButtonGlyphs }
    Column(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.padding(contentPadding).padding(vertical = Dimens.spacingSm)) {
            AchievementRow(achievement = state.achievement)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = stringResource(R.string.achievement_comments_heading),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(contentPadding).padding(vertical = Dimens.spacingSm)
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (val content = state.content) {
                null -> CommentsLoading()
                is AchievementComments.Loaded ->
                    if (content.comments.isEmpty()) CommentsMessage(R.string.achievement_comments_empty)
                    else CommentList(content.comments, state.scrollIndex, contentPadding)
                AchievementComments.NotSignedIn -> CommentsMessage(R.string.achievement_comments_not_signed_in)
                AchievementComments.Unavailable -> CommentsMessage(
                    R.string.achievement_comments_unavailable,
                    actionRes = R.string.achievement_comments_retry,
                    onAction = controller::retry,
                    actionGlyph = actionGlyph
                )
                AchievementComments.NeedsKey, AchievementComments.KeyRejected -> KeyPrompt(
                    state = state,
                    keyEntry = keyEntry,
                    onAction = { keyAction(keyEntry, controller, onOpenKeySettings) },
                    actionGlyph = actionGlyph
                )
            }
        }
    }

    if (keyEntry == CommentsKeyEntry.INLINE) {
        ControllerKeyboardSession(
            open = state.keyEntryOpen,
            label = stringResource(R.string.achievement_comments_key_field_label),
            text = state.keyDraft,
            onTextChange = controller::setKeyDraft,
            onSubmit = controller::submitKey,
            onClose = controller::closeKeyEntry,
            submitLabel = stringResource(R.string.achievement_comments_key_save),
            fieldType = KeyboardFieldType.PASSWORD,
            error = state.keyStatus?.let { status -> keyStatusRes(status)?.let { stringResource(it) } },
            canSubmit = state.keyDraft.isNotBlank(),
            enabled = state.keyStatus != CommentsKeyStatus.CHECKING
        )
    }
}

/**
 * The controller input for an open comments view: up and down scroll the comments, A runs the
 * view's one action, B closes it. Every other button is held so nothing reaches the list behind.
 */
@Composable
fun rememberAchievementCommentsInput(
    controller: AchievementCommentsController,
    keyEntry: CommentsKeyEntry,
    onOpenKeySettings: () -> Unit
): InputHandler {
    val currentOnOpenKeySettings = rememberUpdatedState(onOpenKeySettings)
    return remember(controller, keyEntry) {
        object : InputHandler {
            override fun onUp(): InputResult {
                controller.scroll(-1)
                return InputResult.HANDLED
            }

            override fun onDown(): InputResult {
                controller.scroll(1)
                return InputResult.HANDLED
            }

            override fun onRightStickUp(): InputResult = onUp()

            override fun onRightStickDown(): InputResult = onDown()

            override fun onConfirm(): InputResult {
                val state = controller.state.value ?: return InputResult.HANDLED
                when {
                    state.content == AchievementComments.Unavailable -> controller.retry()
                    state.needsKey -> keyAction(keyEntry, controller, currentOnOpenKeySettings.value)
                }
                return InputResult.HANDLED
            }

            override fun onBack(): InputResult {
                controller.close()
                return InputResult.HANDLED
            }

            override fun onLeft(): InputResult = InputResult.HANDLED
            override fun onRight(): InputResult = InputResult.HANDLED
            override fun onMenu(): InputResult = InputResult.HANDLED
            override fun onSecondaryAction(): InputResult = InputResult.HANDLED
            override fun onContextMenu(): InputResult = InputResult.HANDLED
            override fun onPrevSection(): InputResult = InputResult.HANDLED
            override fun onNextSection(): InputResult = InputResult.HANDLED
            override fun onPrevTrigger(): InputResult = InputResult.HANDLED
            override fun onNextTrigger(): InputResult = InputResult.HANDLED
            override fun onSelect(): InputResult = InputResult.HANDLED
            override fun onLongConfirm(): InputResult = InputResult.HANDLED
        }
    }
}

private fun keyAction(
    keyEntry: CommentsKeyEntry,
    controller: AchievementCommentsController,
    onOpenKeySettings: () -> Unit
) {
    when (keyEntry) {
        CommentsKeyEntry.SETTINGS -> onOpenKeySettings()
        CommentsKeyEntry.INLINE -> controller.openKeyEntry()
    }
}

@Composable
private fun CommentList(
    comments: List<AchievementComment>,
    scrollIndex: Int,
    contentPadding: PaddingValues
) {
    val listState = rememberLazyListState()
    val reduced = reducedMotion()
    LaunchedEffect(scrollIndex, comments.size) {
        if (scrollIndex in comments.indices) listState.animateScrollToItemCentered(scrollIndex, reduced)
    }
    val context = LocalContext.current
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()) }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
    ) {
        itemsIndexed(comments) { _, comment ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                ) {
                    Text(
                        text = comment.user,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = formatRelativeTime(context, comment.submitted, absolute = dateFormat::format),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = comment.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun CommentsLoading() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(Dimens.iconMd))
        Text(
            text = stringResource(R.string.achievement_comments_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Dimens.spacingSm)
        )
    }
}

@Composable
private fun CommentsMessage(
    @StringRes messageRes: Int,
    @StringRes actionRes: Int? = null,
    onAction: () -> Unit = {},
    @StringRes detailRes: Int? = null,
    detailIsError: Boolean = true,
    actionGlyph: InputButton? = null
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingLg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd, Alignment.CenterVertically)
    ) {
        Text(
            text = stringResource(messageRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (detailRes != null) {
            Text(
                text = stringResource(detailRes),
                style = MaterialTheme.typography.bodySmall,
                color = if (detailIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        if (actionRes != null) {
            ActionButton(
                label = stringResource(actionRes),
                onClick = onAction,
                focused = true,
                primary = true,
                glyph = actionGlyph
            )
        }
    }
}

@Composable
private fun KeyPrompt(
    state: AchievementCommentsState,
    keyEntry: CommentsKeyEntry,
    onAction: () -> Unit,
    actionGlyph: InputButton?
) {
    CommentsMessage(
        messageRes = if (state.content == AchievementComments.KeyRejected) {
            R.string.achievement_comments_key_rejected
        } else {
            R.string.achievement_comments_needs_key
        },
        actionRes = when (keyEntry) {
            CommentsKeyEntry.SETTINGS -> R.string.achievement_comments_open_settings
            CommentsKeyEntry.INLINE -> R.string.achievement_comments_enter_key
        },
        onAction = onAction,
        detailRes = state.keyStatus?.let(::keyStatusRes),
        detailIsError = state.keyStatus != CommentsKeyStatus.CHECKING,
        actionGlyph = actionGlyph
    )
}

@StringRes
private fun keyStatusRes(status: CommentsKeyStatus): Int? = when (status) {
    CommentsKeyStatus.CHECKING -> R.string.achievement_comments_key_checking
    CommentsKeyStatus.REJECTED -> R.string.achievement_comments_key_status_rejected
    CommentsKeyStatus.UNREACHABLE -> R.string.achievement_comments_key_status_unreachable
    CommentsKeyStatus.NOT_SIGNED_IN -> R.string.achievement_comments_not_signed_in
}
