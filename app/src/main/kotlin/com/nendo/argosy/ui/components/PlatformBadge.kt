package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.data.preferences.SystemIconPosition
import com.nendo.argosy.ui.theme.LocalBoxArtStyle

private val BASE_WIDTH_DP = 150.dp
private val BASE_FONT_SIZE_SP = 11.sp
private val BASE_HORIZONTAL_PADDING_DP = 4.dp
private val BASE_VERTICAL_PADDING_DP = 2.dp

@Composable
fun PlatformBadge(
    platformId: String,
    cardWidthDp: Dp,
    modifier: Modifier = Modifier
) {
    val boxArtStyle = LocalBoxArtStyle.current
    val platform = PlatformDefinitions.getById(platformId)
    val shortName = platform?.shortName ?: platformId.uppercase().take(6)

    val scale = (cardWidthDp / BASE_WIDTH_DP).coerceIn(0.5f, 2f)
    val cornerRadius = boxArtStyle.cornerRadiusDp * scale
    val shape = when (boxArtStyle.systemIconPosition) {
        SystemIconPosition.TOP_LEFT -> RoundedCornerShape(
            topStart = cornerRadius,
            bottomEnd = cornerRadius
        )
        SystemIconPosition.TOP_RIGHT -> RoundedCornerShape(
            topEnd = cornerRadius,
            bottomStart = cornerRadius
        )
        else -> RoundedCornerShape(bottomEnd = cornerRadius)
    }

    val fontSize = BASE_FONT_SIZE_SP * scale
    val userPadding = boxArtStyle.systemIconPaddingDp
    val horizontalPadding = (BASE_HORIZONTAL_PADDING_DP + userPadding) * scale
    val verticalPadding = (BASE_VERTICAL_PADDING_DP + userPadding / 2) * scale

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = shortName,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            lineHeight = fontSize
        )
    }
}
