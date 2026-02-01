package com.nendo.argosy.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

data class ParsedTitle(
    val seriesName: String?,
    val gameName: String
)

fun parseGameTitle(title: String): ParsedTitle {
    val colonIndex = title.indexOf(':')
    return if (colonIndex > 0 && colonIndex < title.length - 1) {
        ParsedTitle(
            seriesName = title.substring(0, colonIndex).trim(),
            gameName = title.substring(colonIndex + 1).trim()
        )
    } else {
        ParsedTitle(seriesName = null, gameName = title)
    }
}

@Composable
fun GameTitle(
    title: String,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = MaterialTheme.typography.titleMedium,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    seriesScale: Float = 0.75f,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    textAlign: TextAlign? = null,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    titleId: String? = null,
    titleIdColor: Color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
) {
    val parsed = remember(title) { parseGameTitle(title) }

    val seriesStyle = titleStyle.copy(
        fontSize = titleStyle.fontSize * seriesScale,
        fontWeight = FontWeight.Normal
    )

    val titleIdStyle = MaterialTheme.typography.labelSmall.copy(
        fontWeight = FontWeight.Light
    )

    if (parsed.seriesName != null) {
        if (titleId != null) {
            Row(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = horizontalAlignment
                ) {
                    Text(
                        text = parsed.seriesName,
                        style = seriesStyle,
                        color = titleColor.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = overflow,
                        textAlign = textAlign
                    )
                    Text(
                        text = parsed.gameName,
                        style = titleStyle,
                        color = titleColor,
                        maxLines = maxLines,
                        overflow = overflow,
                        textAlign = textAlign
                    )
                }
                Text(
                    text = titleId,
                    style = titleIdStyle,
                    color = titleIdColor
                )
            }
        } else {
            Column(
                modifier = modifier,
                horizontalAlignment = horizontalAlignment
            ) {
                Text(
                    text = parsed.seriesName,
                    style = seriesStyle,
                    color = titleColor.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = overflow,
                    textAlign = textAlign
                )
                Text(
                    text = parsed.gameName,
                    style = titleStyle,
                    color = titleColor,
                    maxLines = maxLines,
                    overflow = overflow,
                    textAlign = textAlign
                )
            }
        }
    } else {
        if (titleId != null) {
            Row(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = parsed.gameName,
                    style = titleStyle,
                    color = titleColor,
                    maxLines = maxLines,
                    overflow = overflow,
                    textAlign = textAlign,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = titleId,
                    style = titleIdStyle,
                    color = titleIdColor
                )
            }
        } else {
            Text(
                text = parsed.gameName,
                style = titleStyle,
                color = titleColor,
                maxLines = maxLines,
                overflow = overflow,
                textAlign = textAlign,
                modifier = modifier
            )
        }
    }
}
