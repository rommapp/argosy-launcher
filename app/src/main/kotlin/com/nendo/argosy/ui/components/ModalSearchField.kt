package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import com.nendo.argosy.ui.components.keyboard.ControllerKeyboardSession
import com.nendo.argosy.ui.components.keyboard.ControllerKeyboardTapTarget
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import com.nendo.argosy.R
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

/**
 * The one search field a modal uses. The caller owns [keyboardOpen] and [focused] so the field is a
 * stop in its modal's focus order: confirm on the stop or a tap on the field calls [onOpenKeyboard],
 * and [onCloseKeyboard] runs whenever the Argosy keyboard closes itself. The requester exists only
 * for the system keyboard hand-off and decides what is typed into, never what is selected.
 */
@Composable
fun ModalSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    keyboardOpen: Boolean,
    onOpenKeyboard: () -> Unit,
    onCloseKeyboard: () -> Unit,
    focused: Boolean,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.ui_modal_search_placeholder),
    onSearch: (() -> Unit)? = null
) {
    val theme = LocalArgosyTheme.current
    val focusRequester = remember { FocusRequester() }
    val shape = RoundedCornerShape(Dimens.radiusControl)

    ControllerKeyboardSession(
        open = keyboardOpen,
        label = placeholder,
        text = query,
        onTextChange = onQueryChange,
        onSubmit = {
            onCloseKeyboard()
            onSearch?.invoke()
        },
        onClose = onCloseKeyboard,
        submitLabel = stringResource(R.string.ui_modal_search_keyboard_submit),
        onSystemKeyboard = {
            onCloseKeyboard()
            focusRequester.requestFocus()
        }
    )

    ControllerKeyboardTapTarget(onTap = onOpenKeyboard, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(theme.surfaceRaised)
                .argosyFocusIndicators(
                    focused = focused,
                    indicators = FocusIndicators.ListRow,
                    shape = shape
                )
                .padding(Dimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = theme.textDim,
                modifier = Modifier.size(Dimens.iconSm)
            )
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onFocusChanged { if (it.isFocused && keyboardOpen) onCloseKeyboard() },
                textStyle = TextStyle(
                    color = theme.textPrimary,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize
                ),
                cursorBrush = SolidColor(theme.focusAccent),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = if (onSearch != null) ImeAction.Search else ImeAction.Default
                ),
                keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
                decorationBox = { innerTextField ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyMedium,
                                color = theme.textDim
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }
    }
}
