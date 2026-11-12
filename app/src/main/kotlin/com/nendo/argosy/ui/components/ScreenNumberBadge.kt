package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.generated.ColorTokens

@Composable
fun ScreenNumberChip(number: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.radiusControl))
            .background(ColorTokens.Domain.ScreenIndicator.fill)
            .border(
                width = Dimens.borderThin,
                color = ColorTokens.Domain.ScreenIndicator.border,
                shape = RoundedCornerShape(Dimens.radiusControl)
            )
            .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = ColorTokens.Domain.ScreenIndicator.text
        )
    }
}

@Composable
fun ScreenNumberBadge(number: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.radiusPanel))
            .background(ColorTokens.Domain.ScreenIndicator.fill)
            .border(
                width = Dimens.borderMedium,
                color = ColorTokens.Domain.ScreenIndicator.border,
                shape = RoundedCornerShape(Dimens.radiusPanel)
            )
            .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingSm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.headlineMedium,
            color = ColorTokens.Domain.ScreenIndicator.text
        )
    }
}
