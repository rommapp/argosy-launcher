package com.nendo.argosy.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * What can be done to the tile under the cursor. The caller supplies only the entries that would
 * actually succeed, so nothing here is greyed out or silently refused.
 *
 * [dangerFromIndex] marks where the destructive tail begins: those entries are tinted and fenced off
 * by a rule, so a press that lands on one reads differently from the rest before it is made.
 */
@Composable
fun CustomTileMenuModal(
    title: String,
    entries: List<String>,
    focusIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    dangerFromIndex: Int? = null
) {
    val theme = LocalArgosyTheme.current
    Modal(
        title = "TILE",
        subtitle = title,
        onDismiss = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.listGap)) {
            entries.forEachIndexed { index, label ->
                val shape = RoundedCornerShape(Dimens.radiusControl)
                val isFocused = index == focusIndex
                val isDangerous = dangerFromIndex != null && index >= dangerFromIndex
                if (index == dangerFromIndex && index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = Dimens.spacingXs),
                        color = theme.hairlineLow
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        isDangerous && isFocused -> lerp(theme.destructive, Color.White, 0.45f)
                        isDangerous -> theme.destructive
                        else -> theme.textPrimary
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .argosyFocusIndicators(
                            focused = isFocused,
                            indicators = FocusIndicators.ListRow,
                            shape = shape
                        )
                        .clickableNoFocus { onSelect(index) }
                        .padding(Dimens.spacingSm)
                )
            }
        }
    }
}
