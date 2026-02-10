package com.nendo.argosy.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints

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

private val KEEP_TOGETHER_PHRASES = listOf(
    "Dragon Quest",
    "Final Fantasy",
    "Super Mario",
    "Legend of Zelda",
    "Mega Man",
    "Sonic the Hedgehog",
    "Street Fighter",
    "Mortal Kombat",
    "Resident Evil",
    "Metal Gear",
    "Kingdom Hearts",
    "Castlevania"
)

private fun preprocessTitle(text: String): String {
    var result = text
    for (phrase in KEEP_TOGETHER_PHRASES) {
        result = result.replace(phrase, phrase.replace(" ", "\u00A0"))
    }
    return result
}

private fun buildEndWeightedLines(text: String): List<String> {
    val processed = preprocessTitle(text)
    val words = processed.split(" ")
    if (words.size <= 1) return listOf(text)

    var bestSplit = 1
    var bestDiff = Int.MAX_VALUE

    for (splitAt in 1 until words.size) {
        val firstLen = words.subList(0, splitAt).sumOf { it.length } + splitAt - 1
        val lastLen = words.subList(splitAt, words.size).sumOf { it.length } + (words.size - splitAt - 1)

        if (lastLen >= firstLen) {
            val diff = lastLen - firstLen
            if (diff < bestDiff) {
                bestDiff = diff
                bestSplit = splitAt
            }
        }
    }

    val firstPart = words.subList(0, bestSplit).joinToString(" ")
    val lastPart = words.subList(bestSplit, words.size).joinToString(" ")

    return listOf(firstPart, lastPart)
}

private enum class TitleSizeMode {
    NORMAL,
    REDUCED,
    WRAPPED
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
    titleIdColor: Color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
    adaptiveSize: Boolean = false,
    reducedScale: Float = 0.85f
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
        AdaptiveSeriesTitle(
            seriesName = parsed.seriesName,
            gameName = parsed.gameName,
            modifier = modifier,
            titleStyle = titleStyle,
            seriesStyle = seriesStyle,
            titleColor = titleColor,
            maxLines = maxLines,
            overflow = overflow,
            textAlign = textAlign,
            horizontalAlignment = horizontalAlignment,
            titleId = titleId,
            titleIdStyle = titleIdStyle,
            titleIdColor = titleIdColor,
            adaptiveSize = adaptiveSize,
            reducedScale = reducedScale
        )
    } else {
        if (adaptiveSize) {
            AdaptiveSizeTitle(
                text = parsed.gameName,
                modifier = modifier,
                titleStyle = titleStyle,
                titleColor = titleColor,
                reducedScale = reducedScale,
                textAlign = textAlign,
                horizontalAlignment = horizontalAlignment,
                titleId = titleId,
                titleIdStyle = titleIdStyle,
                titleIdColor = titleIdColor
            )
        } else if (titleId != null) {
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

@Composable
private fun AdaptiveSizeTitle(
    text: String,
    modifier: Modifier,
    titleStyle: TextStyle,
    titleColor: Color,
    reducedScale: Float,
    textAlign: TextAlign?,
    horizontalAlignment: Alignment.Horizontal,
    titleId: String?,
    titleIdStyle: TextStyle,
    titleIdColor: Color
) {
    val textMeasurer = rememberTextMeasurer()
    val reducedStyle = remember(titleStyle, reducedScale) {
        titleStyle.copy(fontSize = titleStyle.fontSize * reducedScale)
    }
    val wrappedLines = remember(text) { buildEndWeightedLines(text) }

    BoxWithConstraints(modifier = modifier) {
        val maxWidthPx = constraints.maxWidth

        val sizeMode = remember(text, titleStyle, reducedStyle, maxWidthPx) {
            val normalMeasurement = textMeasurer.measure(
                text = text,
                style = titleStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                constraints = Constraints(maxWidth = maxWidthPx)
            )

            if (!normalMeasurement.hasVisualOverflow) {
                TitleSizeMode.NORMAL
            } else {
                val reducedMeasurement = textMeasurer.measure(
                    text = text,
                    style = reducedStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    constraints = Constraints(maxWidth = maxWidthPx)
                )

                if (!reducedMeasurement.hasVisualOverflow) {
                    TitleSizeMode.REDUCED
                } else {
                    TitleSizeMode.WRAPPED
                }
            }
        }

        when (sizeMode) {
            TitleSizeMode.NORMAL -> {
                TitleWithOptionalId(
                    text = text,
                    style = titleStyle,
                    color = titleColor,
                    textAlign = textAlign,
                    titleId = titleId,
                    titleIdStyle = titleIdStyle,
                    titleIdColor = titleIdColor
                )
            }
            TitleSizeMode.REDUCED -> {
                TitleWithOptionalId(
                    text = text,
                    style = reducedStyle,
                    color = titleColor,
                    textAlign = textAlign,
                    titleId = titleId,
                    titleIdStyle = titleIdStyle,
                    titleIdColor = titleIdColor
                )
            }
            TitleSizeMode.WRAPPED -> {
                if (titleId != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = horizontalAlignment
                        ) {
                            wrappedLines.forEach { line ->
                                Text(
                                    text = line,
                                    style = reducedStyle,
                                    color = titleColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = textAlign
                                )
                            }
                        }
                        Text(
                            text = titleId,
                            style = titleIdStyle,
                            color = titleIdColor
                        )
                    }
                } else {
                    Column(horizontalAlignment = horizontalAlignment) {
                        wrappedLines.forEach { line ->
                            Text(
                                text = line,
                                style = reducedStyle,
                                color = titleColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = textAlign
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TitleWithOptionalId(
    text: String,
    style: TextStyle,
    color: Color,
    textAlign: TextAlign?,
    titleId: String?,
    titleIdStyle: TextStyle,
    titleIdColor: Color
) {
    if (titleId != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = style,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
            text = text,
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign
        )
    }
}

@Composable
private fun AdaptiveSeriesTitle(
    seriesName: String,
    gameName: String,
    modifier: Modifier,
    titleStyle: TextStyle,
    seriesStyle: TextStyle,
    titleColor: Color,
    maxLines: Int,
    overflow: TextOverflow,
    textAlign: TextAlign?,
    horizontalAlignment: Alignment.Horizontal,
    titleId: String?,
    titleIdStyle: TextStyle,
    titleIdColor: Color,
    adaptiveSize: Boolean,
    reducedScale: Float
) {
    val textMeasurer = rememberTextMeasurer()
    val reducedTitleStyle = remember(titleStyle, reducedScale) {
        titleStyle.copy(fontSize = titleStyle.fontSize * reducedScale)
    }
    val reducedSeriesStyle = remember(seriesStyle, reducedScale) {
        seriesStyle.copy(fontSize = seriesStyle.fontSize * reducedScale)
    }

    if (!adaptiveSize) {
        SeriesTitleContent(
            seriesName = seriesName,
            gameName = gameName,
            modifier = modifier,
            titleStyle = titleStyle,
            seriesStyle = seriesStyle,
            titleColor = titleColor,
            maxLines = maxLines,
            overflow = overflow,
            textAlign = textAlign,
            horizontalAlignment = horizontalAlignment,
            titleId = titleId,
            titleIdStyle = titleIdStyle,
            titleIdColor = titleIdColor
        )
    } else {
        BoxWithConstraints(modifier = modifier) {
            val maxWidthPx = constraints.maxWidth

            val needsReduction = remember(gameName, titleStyle, maxWidthPx) {
                val measurement = textMeasurer.measure(
                    text = gameName,
                    style = titleStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    constraints = Constraints(maxWidth = maxWidthPx)
                )
                measurement.hasVisualOverflow
            }

            val effectiveTitleStyle = if (needsReduction) reducedTitleStyle else titleStyle
            val effectiveSeriesStyle = if (needsReduction) reducedSeriesStyle else seriesStyle

            SeriesTitleContent(
                seriesName = seriesName,
                gameName = gameName,
                modifier = Modifier,
                titleStyle = effectiveTitleStyle,
                seriesStyle = effectiveSeriesStyle,
                titleColor = titleColor,
                maxLines = maxLines,
                overflow = overflow,
                textAlign = textAlign,
                horizontalAlignment = horizontalAlignment,
                titleId = titleId,
                titleIdStyle = titleIdStyle,
                titleIdColor = titleIdColor
            )
        }
    }
}

@Composable
private fun SeriesTitleContent(
    seriesName: String,
    gameName: String,
    modifier: Modifier,
    titleStyle: TextStyle,
    seriesStyle: TextStyle,
    titleColor: Color,
    maxLines: Int,
    overflow: TextOverflow,
    textAlign: TextAlign?,
    horizontalAlignment: Alignment.Horizontal,
    titleId: String?,
    titleIdStyle: TextStyle,
    titleIdColor: Color
) {
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
                    text = seriesName,
                    style = seriesStyle,
                    color = titleColor.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = overflow,
                    textAlign = textAlign
                )
                Text(
                    text = gameName,
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
                text = seriesName,
                style = seriesStyle,
                color = titleColor.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = overflow,
                textAlign = textAlign
            )
            Text(
                text = gameName,
                style = titleStyle,
                color = titleColor,
                maxLines = maxLines,
                overflow = overflow,
                textAlign = textAlign
            )
        }
    }
}
