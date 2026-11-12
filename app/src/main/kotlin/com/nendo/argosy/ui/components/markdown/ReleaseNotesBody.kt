package com.nendo.argosy.ui.components.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.ColorTokens

/**
 * Release notes rendered from markdown with [ReleaseNotesParser]. Shared by the About changelog
 * and the update prompt.
 */
@Composable
fun ReleaseNotesBody(body: String?, modifier: Modifier = Modifier) {
    val theme = LocalArgosyTheme.current
    val blocks = remember(body) { ReleaseNotesParser.parse(body.orEmpty()) }
    val bodyStyle = MaterialTheme.typography.bodySmall
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
        blocks.forEach { block ->
            when (block) {
                is NotesBlock.Heading -> Text(
                    text = annotated(block.inlines),
                    style = if (block.level <= 2) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge,
                    color = theme.textPrimary
                )
                is NotesBlock.Bullet -> ListRow(depth = block.depth, marker = {
                    Box(
                        modifier = Modifier
                            .padding(top = Dimens.spacingXs)
                            .size(Dimens.dotSm)
                            .background(theme.textDim, CircleShape)
                    )
                }) {
                    Text(text = annotated(block.inlines), style = bodyStyle, color = theme.textDim)
                }
                is NotesBlock.Numbered -> ListRow(depth = block.depth, marker = {
                    Text(text = "${block.number}.", style = bodyStyle, color = theme.textDim)
                }) {
                    Text(text = annotated(block.inlines), style = bodyStyle, color = theme.textDim)
                }
                is NotesBlock.Paragraph -> Text(
                    text = annotated(block.inlines),
                    style = bodyStyle,
                    color = theme.textDim
                )
                is NotesBlock.Code -> Text(
                    text = block.text,
                    style = bodyStyle.merge(TextStyle(fontFamily = FontFamily.Monospace)),
                    color = theme.textDim,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ColorTokens.Domain.Code.background, RoundedCornerShape(Dimens.radiusControl))
                        .padding(horizontal = Dimens.spacingSm)
                )
                NotesBlock.Blank -> Spacer(modifier = Modifier.height(Dimens.spacingXs))
            }
        }
    }
}

@Composable
private fun ListRow(
    depth: Int,
    marker: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.padding(start = Dimens.spacingMd * depth),
        verticalAlignment = Alignment.Top
    ) {
        Box(modifier = Modifier.widthIn(min = Dimens.spacingMd), contentAlignment = Alignment.TopStart) {
            marker()
        }
        Spacer(modifier = Modifier.width(Dimens.spacingXs))
        content()
    }
}

private fun annotated(inlines: List<NotesInline>): AnnotatedString = buildAnnotatedString {
    inlines.forEach { run ->
        withStyle(
            SpanStyle(
                fontWeight = if (run.bold) FontWeight.Bold else null,
                fontStyle = if (run.italic) FontStyle.Italic else null,
                fontFamily = if (run.code) FontFamily.Monospace else null,
                background = if (run.code) ColorTokens.Domain.Code.background else Color.Unspecified
            )
        ) {
            append(run.text)
        }
    }
}
