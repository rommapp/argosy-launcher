package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nendo.argosy.domain.model.ChangelogEntry
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.domain.model.RequiredAction

@Composable
fun ChangelogModal(
    entry: ChangelogEntry,
    onDismiss: () -> Unit,
    onAction: (RequiredAction) -> Unit
) {
    val hasRequiredAction = entry.requiredActions.isNotEmpty()
    val firstAction = entry.requiredActions.firstOrNull()

    val footerHints = buildList {
        if (!hasRequiredAction) {
            add(InputButton.A to "Continue")
        }
        if (firstAction != null) {
            add(InputButton.X to firstAction.label)
        }
    }

    CenteredModal(
        title = "What's New in ${entry.version}",
        baseWidth = Dimens.modalWidthLg,
        onDismiss = if (hasRequiredAction) null else onDismiss,
        footerHints = footerHints
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            entry.highlights.forEach { highlight ->
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Circle,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(top = Dimens.spacingSm - Dimens.borderMedium)
                            .size(Dimens.spacingSm - Dimens.borderMedium),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(Dimens.radiusLg))
                    Text(
                        text = highlight,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        if (hasRequiredAction) {
            Spacer(modifier = Modifier.height(Dimens.radiusLg))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.errorContainer,
                        RoundedCornerShape(Dimens.spacingSm - Dimens.borderMedium)
                    )
                    .padding(horizontal = Dimens.spacingSm + Dimens.borderMedium, vertical = Dimens.spacingSm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(Dimens.spacingMd),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.width(Dimens.spacingSm))
                Text(
                    text = "Action required: ${entry.requiredActions.joinToString(", ") { it.label }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}
