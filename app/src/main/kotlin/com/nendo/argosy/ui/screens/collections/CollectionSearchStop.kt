package com.nendo.argosy.ui.screens.collections

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.compositeOver
import com.nendo.argosy.ui.components.keyboard.ControllerKeyboardTapTarget
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus

private const val SEARCH_FIELD_FOCUS_TINT_ALPHA = 0.15f

@Composable
internal fun CollectionSearchField(
    query: String,
    placeholder: String,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onFieldFocused: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val baseColor = MaterialTheme.colorScheme.surfaceVariant
    val containerColor = if (isFocused) {
        LocalArgosyTheme.current.focusAccent.copy(alpha = SEARCH_FIELD_FOCUS_TINT_ALPHA).compositeOver(baseColor)
    } else {
        baseColor
    }
    ControllerKeyboardTapTarget(onTap = onTap, modifier = modifier) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { if (it.isFocused) onFieldFocused() },
            placeholder = { Text(placeholder) },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = containerColor,
                unfocusedContainerColor = containerColor
            )
        )
    }
}

@Composable
internal fun CollectionSearchButton(
    isFocused: Boolean,
    contentDescription: String,
    onClick: () -> Unit
) {
    Icon(
        Icons.Default.Search,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(CircleShape)
            .argosyFocusIndicators(
                focused = isFocused,
                indicators = FocusIndicators.Button,
                shape = CircleShape
            )
            .clickableNoFocus(onClick = onClick)
            .padding(Dimens.spacingMd)
    )
}
