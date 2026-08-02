package com.nendo.argosy.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * What can be done to the tile under the cursor. The caller supplies only the entries that would
 * actually succeed, so nothing here is greyed out or silently refused.
 */
@Composable
fun CustomTileMenuModal(
    title: String,
    entries: List<String>,
    focusIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
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
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.textPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .argosyFocusIndicators(
                            focused = index == focusIndex,
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
