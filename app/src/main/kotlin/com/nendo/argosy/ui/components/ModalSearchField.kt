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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

/**
 * The one search field a modal uses. Text entry is the sanctioned exception to the launcher's rule
 * against Compose focus: the requester decides what is typed into, never what is selected.
 */
@Composable
fun ModalSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search...",
    autoFocus: Boolean = true
) {
    val theme = LocalArgosyTheme.current
    val focusRequester = remember { FocusRequester() }
    val shape = RoundedCornerShape(Dimens.radiusControl)

    LaunchedEffect(autoFocus) { if (autoFocus) focusRequester.requestFocus() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(theme.surfaceRaised)
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
                .focusRequester(focusRequester),
            textStyle = TextStyle(
                color = theme.textPrimary,
                fontSize = MaterialTheme.typography.bodyMedium.fontSize
            ),
            cursorBrush = SolidColor(theme.focusAccent),
            singleLine = true,
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
