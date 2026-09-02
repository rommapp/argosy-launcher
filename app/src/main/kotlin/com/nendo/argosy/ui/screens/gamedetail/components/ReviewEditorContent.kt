package com.nendo.argosy.ui.screens.gamedetail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.data.social.GameReview
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.primitives.ActionButton
import com.nendo.argosy.ui.primitives.ArgosyConfirmModal
import com.nendo.argosy.ui.screens.gamedetail.ReviewEditorSection
import com.nendo.argosy.ui.screens.gamedetail.ReviewEditorState
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * The review editor's body, drawn the same on the single screen and on the dual-screen upper
 * display. Focus is a section index the caller owns; the only Compose focus here is the text
 * field's, raised when [ReviewEditorState.keyboardRequest] changes.
 */
@Composable
fun ReviewEditorContent(
    state: ReviewEditorState,
    onSectionFocus: (ReviewEditorSection) -> Unit,
    onVerdictSelect: (Boolean) -> Unit,
    onVisibilitySelect: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDeletePrompt: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onDiscardConfirm: () -> Unit,
    onConfirmDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    val semantic = LocalLauncherTheme.current.semanticColors
    val bodyFocusRequester = remember { FocusRequester() }

    LaunchedEffect(state.keyboardRequest) {
        if (state.keyboardRequest > 0) bodyFocusRequester.requestFocus()
    }

    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            EditorHeader(state = state)

            Spacer(modifier = Modifier.height(Dimens.spacingMd))

            ChoiceRow(
                label = stringResource(R.string.reviews_editor_verdict_label),
                isFocused = state.section == ReviewEditorSection.VERDICT,
                onFocus = { onSectionFocus(ReviewEditorSection.VERDICT) }
            ) {
                ChoiceOption(
                    label = stringResource(R.string.reviews_editor_verdict_up),
                    icon = Icons.Default.ThumbUp,
                    tint = semantic.success,
                    isSelected = state.recommended,
                    onClick = { onVerdictSelect(true) }
                )
                ChoiceOption(
                    label = stringResource(R.string.reviews_editor_verdict_down),
                    icon = Icons.Default.ThumbDown,
                    tint = theme.destructive,
                    isSelected = !state.recommended,
                    onClick = { onVerdictSelect(false) }
                )
            }

            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            ChoiceRow(
                label = stringResource(R.string.reviews_editor_visibility_label),
                isFocused = state.section == ReviewEditorSection.VISIBILITY,
                onFocus = { onSectionFocus(ReviewEditorSection.VISIBILITY) }
            ) {
                ChoiceOption(
                    label = stringResource(R.string.reviews_editor_visibility_friends),
                    icon = null,
                    tint = theme.focusAccent,
                    isSelected = state.visibility != GameReview.VISIBILITY_PUBLIC,
                    onClick = { onVisibilitySelect(GameReview.VISIBILITY_FRIENDS) }
                )
                ChoiceOption(
                    label = stringResource(R.string.reviews_editor_visibility_public),
                    icon = null,
                    tint = theme.focusAccent,
                    isSelected = state.visibility == GameReview.VISIBILITY_PUBLIC,
                    onClick = { onVisibilitySelect(GameReview.VISIBILITY_PUBLIC) }
                )
            }

            Spacer(modifier = Modifier.height(Dimens.spacingMd))

            BodyField(
                body = state.body,
                isFocused = state.section == ReviewEditorSection.BODY,
                enabled = !state.isSubmitting,
                focusRequester = bodyFocusRequester,
                onBodyChange = onBodyChange,
                onTap = {
                    onSectionFocus(ReviewEditorSection.BODY)
                    bodyFocusRequester.requestFocus()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            Spacer(modifier = Modifier.height(Dimens.spacingXs))

            RuneCounter(runesLeft = state.runesLeft, modifier = Modifier.align(Alignment.End))

            Spacer(modifier = Modifier.height(Dimens.spacingMd))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.isEditing) {
                    ActionButton(
                        label = stringResource(R.string.reviews_editor_delete),
                        onClick = onDeletePrompt,
                        focused = state.section == ReviewEditorSection.DELETE,
                        accentColor = theme.destructive,
                        enabled = !state.isSubmitting
                    )
                }
                ActionButton(
                    label = stringResource(
                        when {
                            state.isSubmitting -> R.string.reviews_editor_submitting
                            state.isEditing -> R.string.reviews_editor_submit_update
                            else -> R.string.reviews_editor_submit
                        }
                    ),
                    onClick = onSubmit,
                    focused = state.section == ReviewEditorSection.SUBMIT,
                    primary = true,
                    enabled = state.canSubmit
                )
            }
        }

        if (state.showDiscardConfirm) {
            ArgosyConfirmModal(
                title = stringResource(R.string.reviews_editor_discard_title),
                message = stringResource(R.string.reviews_editor_discard_message),
                confirmLabel = stringResource(R.string.reviews_editor_discard_action),
                onConfirm = onDiscardConfirm,
                onDismiss = onConfirmDismiss,
                focusedIndex = state.confirmFocusIndex,
                destructive = true
            )
        }

        if (state.showDeleteConfirm) {
            ArgosyConfirmModal(
                title = stringResource(R.string.reviews_editor_delete_title),
                message = stringResource(R.string.reviews_editor_delete_message),
                confirmLabel = stringResource(R.string.reviews_editor_delete_action),
                onConfirm = onDeleteConfirm,
                onDismiss = onConfirmDismiss,
                focusedIndex = state.confirmFocusIndex,
                destructive = true
            )
        }
    }
}

/**
 * The bar only carries what the focused control does not already show: the horizontal adjust
 * on a two-way choice, the keyboard on the text box, and the two buttons that work from
 * anywhere in the editor. Nothing is listed while a confirmation is up.
 */
@Composable
fun reviewEditorFooterHints(state: ReviewEditorState): List<Pair<InputButton, String>> {
    if (state.hasConfirm) return emptyList()
    val adjust = stringResource(R.string.reviews_editor_footer_adjust)
    val type = stringResource(R.string.reviews_editor_footer_type)
    val select = stringResource(R.string.reviews_editor_footer_select)
    val submit = stringResource(R.string.reviews_editor_footer_submit)
    val delete = stringResource(R.string.reviews_editor_footer_delete)
    val back = stringResource(R.string.reviews_editor_footer_back)
    return buildList {
        when (state.section) {
            ReviewEditorSection.VERDICT,
            ReviewEditorSection.VISIBILITY -> add(InputButton.DPAD_HORIZONTAL to adjust)
            ReviewEditorSection.BODY -> add(InputButton.A to type)
            ReviewEditorSection.SUBMIT,
            ReviewEditorSection.DELETE -> add(InputButton.A to select)
        }
        add(InputButton.START to submit)
        if (state.isEditing) add(InputButton.Y to delete)
        add(InputButton.B to back)
    }
}

@Composable
private fun EditorHeader(state: ReviewEditorState) {
    val theme = LocalArgosyTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
    ) {
        Box(
            modifier = Modifier
                .size(width = Dimens.storageGameCoverWidth, height = Dimens.storageGameCoverHeight)
                .clip(RoundedCornerShape(Dimens.radiusSm))
                .background(theme.surfaceRaised)
        ) {
            if (state.coverPath != null) {
                AsyncImage(
                    model = rememberFileImageModel(state.coverPath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    if (state.isEditing) R.string.reviews_editor_title_edit else R.string.reviews_editor_title
                ),
                style = MaterialTheme.typography.labelMedium,
                color = theme.focusAccent
            )
            Text(
                text = state.gameTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = theme.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (state.platformName.isNotBlank()) {
                Text(
                    text = state.platformName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    isFocused: Boolean,
    onFocus: () -> Unit,
    options: @Composable () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(
                width = if (isFocused) Dimens.borderThick else Dimens.borderThin,
                color = if (isFocused) theme.focusAccent else theme.hairlineLow,
                shape = shape
            )
            .clickableNoFocus(onClick = onFocus)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (isFocused) theme.textPrimary else theme.textDim,
            modifier = Modifier.weight(1f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
            options()
        }
    }
}

@Composable
private fun ChoiceOption(
    label: String,
    icon: ImageVector?,
    tint: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusPill)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (isSelected) tint.copy(alpha = 0.2f) else theme.surfaceRaised)
            .border(
                width = Dimens.borderThin,
                color = if (isSelected) tint else theme.hairlineLow,
                shape = shape
            )
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) tint else theme.textMute,
                modifier = Modifier.size(Dimens.iconXs)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) theme.textPrimary else theme.textDim,
            maxLines = 1
        )
    }
}

@Composable
private fun BodyField(
    body: String,
    isFocused: Boolean,
    enabled: Boolean,
    focusRequester: FocusRequester,
    onBodyChange: (String) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    Box(
        modifier = modifier
            .clip(shape)
            .background(theme.surfaceRaised)
            .border(
                width = if (isFocused) Dimens.borderThick else Dimens.borderThin,
                color = if (isFocused) theme.focusAccent else theme.hairlineLow,
                shape = shape
            )
            .clickableNoFocus(onClick = onTap)
            .padding(Dimens.spacingMd)
    ) {
        if (body.isEmpty()) {
            Text(
                text = stringResource(R.string.reviews_editor_body_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textMute
            )
        }
        BasicTextField(
            value = body,
            onValueChange = onBodyChange,
            enabled = enabled,
            textStyle = TextStyle(
                color = theme.textPrimary,
                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
            ),
            cursorBrush = SolidColor(theme.focusAccent),
            singleLine = false,
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
        )
    }
}

@Composable
private fun RuneCounter(runesLeft: Int, modifier: Modifier = Modifier) {
    val theme = LocalArgosyTheme.current
    val text = if (runesLeft >= 0) {
        pluralStringResource(R.plurals.reviews_editor_chars_left, runesLeft, runesLeft)
    } else {
        pluralStringResource(R.plurals.reviews_editor_chars_over, -runesLeft, -runesLeft)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = if (runesLeft >= 0) theme.textDim else theme.destructive,
        modifier = modifier
    )
}
