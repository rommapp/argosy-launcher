package com.nendo.argosy.ui.screens.gamedetail

import com.nendo.argosy.data.social.GameReview

const val REVIEW_BODY_MAX_RUNES = 1024

enum class ReviewEditorSection { VERDICT, VISIBILITY, BODY, SUBMIT, DELETE }

enum class ReviewEditorAction { TOGGLE, OPEN_KEYBOARD, SUBMIT, DELETE }

/**
 * A draft review as the editor holds it, on either surface. The transitions below are the whole
 * of the editor's behaviour, so the single-screen view model and DualScreenManager route their
 * input through the same rules instead of each keeping a copy.
 *
 * [keyboardRequest] is a counter, not a flag: the text field raises the keyboard whenever it
 * changes, so two A presses in a row both work.
 */
data class ReviewEditorState(
    val igdbId: Int,
    val gameTitle: String,
    val platformName: String,
    val coverPath: String?,
    val existing: GameReview?,
    val recommended: Boolean,
    val visibility: String,
    val body: String,
    val section: ReviewEditorSection = ReviewEditorSection.VERDICT,
    val isSubmitting: Boolean = false,
    val showDiscardConfirm: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val confirmFocusIndex: Int = 0,
    val keyboardRequest: Int = 0
) {
    val isEditing: Boolean get() = existing != null

    val sections: List<ReviewEditorSection>
        get() = if (isEditing) ReviewEditorSection.entries
        else ReviewEditorSection.entries.filterNot { it == ReviewEditorSection.DELETE }

    val bodyRunes: Int get() = body.codePointCount(0, body.length)

    val runesLeft: Int get() = REVIEW_BODY_MAX_RUNES - bodyRunes

    val hasConfirm: Boolean get() = showDiscardConfirm || showDeleteConfirm

    val canSubmit: Boolean get() = !isSubmitting && runesLeft >= 0

    val isDirty: Boolean
        get() {
            val current = existing
                ?: return body.isNotBlank() || !recommended || visibility != GameReview.VISIBILITY_FRIENDS
            return recommended != current.recommended ||
                visibility != current.visibility ||
                body.trim() != current.body.orEmpty()
        }

    val bodyToSend: String? get() = body.trim().takeIf { it.isNotEmpty() }

    fun confirmAction(): ReviewEditorAction = when (section) {
        ReviewEditorSection.VERDICT, ReviewEditorSection.VISIBILITY -> ReviewEditorAction.TOGGLE
        ReviewEditorSection.BODY -> ReviewEditorAction.OPEN_KEYBOARD
        ReviewEditorSection.SUBMIT -> ReviewEditorAction.SUBMIT
        ReviewEditorSection.DELETE -> ReviewEditorAction.DELETE
    }

    companion object {
        fun forGame(
            igdbId: Int,
            gameTitle: String,
            platformName: String,
            coverPath: String?,
            existing: GameReview?
        ): ReviewEditorState = ReviewEditorState(
            igdbId = igdbId,
            gameTitle = gameTitle,
            platformName = platformName,
            coverPath = coverPath,
            existing = existing,
            recommended = existing?.recommended ?: true,
            visibility = existing?.visibility ?: GameReview.VISIBILITY_FRIENDS,
            body = existing?.body.orEmpty()
        )
    }
}

fun ReviewEditorState.movedSection(delta: Int): ReviewEditorState {
    if (hasConfirm) return this
    val list = sections
    val index = list.indexOf(section).coerceAtLeast(0)
    return copy(section = list[(index + delta).mod(list.size)])
}

fun ReviewEditorState.focusedOn(target: ReviewEditorSection): ReviewEditorState =
    if (hasConfirm || target !in sections) this else copy(section = target)

/**
 * Left and right on a two-way choice both flip it; on an open confirmation they move between
 * its buttons instead.
 */
fun ReviewEditorState.adjusted(delta: Int): ReviewEditorState = when {
    hasConfirm -> copy(confirmFocusIndex = (confirmFocusIndex + delta).coerceIn(0, 1))
    section == ReviewEditorSection.VERDICT -> copy(recommended = !recommended)
    section == ReviewEditorSection.VISIBILITY -> copy(
        visibility = if (visibility == GameReview.VISIBILITY_PUBLIC) {
            GameReview.VISIBILITY_FRIENDS
        } else {
            GameReview.VISIBILITY_PUBLIC
        }
    )
    else -> this
}

fun ReviewEditorState.withVerdict(recommended: Boolean): ReviewEditorState =
    if (hasConfirm) this else copy(recommended = recommended, section = ReviewEditorSection.VERDICT)

fun ReviewEditorState.withVisibility(visibility: String): ReviewEditorState =
    if (hasConfirm) this else copy(visibility = visibility, section = ReviewEditorSection.VISIBILITY)

fun ReviewEditorState.withBody(text: String): ReviewEditorState =
    if (hasConfirm || isSubmitting) this else copy(body = text, section = ReviewEditorSection.BODY)

fun ReviewEditorState.requestingKeyboard(): ReviewEditorState =
    if (hasConfirm) this else copy(section = ReviewEditorSection.BODY, keyboardRequest = keyboardRequest + 1)

fun ReviewEditorState.submitting(): ReviewEditorState =
    copy(isSubmitting = true, showDeleteConfirm = false, showDiscardConfirm = false, confirmFocusIndex = 0)

fun ReviewEditorState.settled(): ReviewEditorState = copy(isSubmitting = false)

fun ReviewEditorState.promptingDelete(): ReviewEditorState =
    if (!isEditing || hasConfirm || isSubmitting) this
    else copy(showDeleteConfirm = true, confirmFocusIndex = 0)

fun ReviewEditorState.promptingDiscard(): ReviewEditorState =
    if (hasConfirm) this else copy(showDiscardConfirm = true, confirmFocusIndex = 0)

fun ReviewEditorState.withoutConfirm(): ReviewEditorState =
    copy(showDiscardConfirm = false, showDeleteConfirm = false, confirmFocusIndex = 0)
