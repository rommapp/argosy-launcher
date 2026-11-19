package com.nendo.argosy.ui.components.launch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.scrollToItemCentered
import com.nendo.argosy.ui.primitives.GlassPanel
import com.nendo.argosy.ui.primitives.RowButton
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.reducedMotion

data class LaunchQuestionChoice(
    val label: String,
    val description: String,
    val onClick: () -> Unit
)

/**
 * A question the launch stops on, drawn as a card in the launch moment. Focus index i marks
 * [choices] entry i; index `choices.size` marks the Cancel row, matching the ViewModel's
 * prompt focus range.
 */
@Composable
fun LaunchQuestionCard(
    title: String,
    message: String,
    choices: List<LaunchQuestionChoice>,
    focusIndex: Int,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null
) {
    val theme = LocalArgosyTheme.current
    val listState = rememberLazyListState()
    val animate = !reducedMotion()
    LaunchedEffect(focusIndex) {
        val listIndex = if (focusIndex >= choices.size) choices.size + 1 else focusIndex
        listState.scrollToItemCentered(listIndex, animate)
    }

    GlassPanel(
        modifier = modifier
            .padding(Dimens.spacingLg)
            .widthIn(max = Dimens.modalWidthLg)
            .fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(Dimens.spacingLg)) {
            QuestionHeader(title = title, message = message, note = note)
            Spacer(Modifier.height(Dimens.spacingMd))
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                itemsIndexed(choices, key = { index, _ -> index }) { index, choice ->
                    QuestionChoiceRow(
                        label = choice.label,
                        description = choice.description,
                        focused = focusIndex == index,
                        onClick = choice.onClick
                    )
                }
                item(key = CANCEL_RULE_KEY) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Dimens.spacingXs)
                            .height(Dimens.borderThin)
                            .background(theme.hairlineLow)
                    )
                }
                item(key = CANCEL_ROW_KEY) {
                    RowButton(
                        label = stringResource(R.string.ui_launch_overlay_cancel_option),
                        subtitle = stringResource(R.string.ui_launch_overlay_cancel_option_subtitle),
                        onClick = onCancel,
                        focused = focusIndex == choices.size,
                        trailingHint = InputButton.B
                    )
                }
            }
        }
    }
}

@Composable
private fun QuestionHeader(title: String, message: String, note: String?) {
    val theme = LocalArgosyTheme.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = LocalLauncherTheme.current.semanticColors.warning,
            modifier = Modifier.size(Dimens.iconMd)
        )
        Spacer(Modifier.width(Dimens.spacingSm))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = theme.textPrimary,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
    Spacer(Modifier.height(Dimens.spacingSm))
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = theme.textDim
    )
    if (note != null) {
        Spacer(Modifier.height(Dimens.spacingXs))
        Text(
            text = note,
            style = MaterialTheme.typography.bodySmall,
            color = theme.textMute
        )
    }
}

@Composable
private fun QuestionChoiceRow(
    label: String,
    description: String,
    focused: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    RowButton(onClick = onClick, focused = focused) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = theme.textPrimary
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = theme.textDim
            )
        }
    }
}

private const val CANCEL_RULE_KEY = "cancel_rule"
private const val CANCEL_ROW_KEY = "cancel"
