package com.nendo.argosy.ui.screens.gamedetail.modals

import com.nendo.argosy.ui.util.clickableNoFocus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.components.CenteredModal
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.screens.gamedetail.RatingType

@Composable
fun RatingPickerModal(
    type: RatingType,
    value: Int,
    onValueChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val isRating = type == RatingType.OPINION
    val title = if (isRating) "RATE GAME" else "SET DIFFICULTY"
    val filledIcon = if (isRating) Icons.Default.Star else Icons.Default.Whatshot
    val outlineIcon = if (isRating) Icons.Default.StarOutline else Icons.Outlined.Whatshot
    val filledColor = if (isRating) Color(0xFFFFD700) else Color(0xFFE53935)
    val outlineColor = Color.White.copy(alpha = 0.4f)

    CenteredModal(
        title = title,
        baseWidth = 420.dp,
        onDismiss = onDismiss,
        footerHints = listOf(
            InputButton.DPAD_HORIZONTAL to "Adjust",
            InputButton.A to "Confirm",
            InputButton.B to "Cancel"
        )
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            for (i in 1..10) {
                val isFilled = i <= value
                Icon(
                    imageVector = if (isFilled) filledIcon else outlineIcon,
                    contentDescription = null,
                    tint = if (isFilled) filledColor else outlineColor,
                    modifier = Modifier
                        .size(Dimens.iconLg)
                        .clickableNoFocus { onValueChange(i) }
                )
            }
        }

        Text(
            text = if (value == 0) "Not set" else "$value/10",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}
