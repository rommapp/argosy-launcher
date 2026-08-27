package com.nendo.argosy.libretro.ui.cheats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.nendo.argosy.R
import com.nendo.argosy.libretro.scanner.MemoryMatch
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.SliderPreference
import com.nendo.argosy.ui.theme.Dimens

@Composable
fun DiscoverTab(
    hasSnapshot: Boolean,
    canCompare: Boolean,
    candidateCount: Int,
    results: List<MemoryMatch>,
    knownAddresses: Map<Int, String>,
    valueSearchText: String,
    onValueSearchChange: (String) -> Unit,
    focusedIndex: Int,
    onAction: (Int) -> Unit,
    showingResults: Boolean,
    error: String? = null,
    narrowError: String? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val showActions = !hasSnapshot || (canCompare && !showingResults)

    LaunchedEffect(focusedIndex, showActions) {
        if (!showActions && focusedIndex >= 1 && focusedIndex - 1 in results.indices) {
            val layoutInfo = listState.layoutInfo
            val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
            val itemHeight = layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 60
            val centerOffset = (viewportHeight - itemHeight) / 2
            listState.animateScrollToItem(focusedIndex - 1, -centerOffset)
        }
    }

    Column(modifier = modifier.padding(Dimens.spacingSm).focusProperties { canFocus = false }) {
        if (error != null) {
            ErrorView(message = error)
        } else if (showActions) {
            ActionsView(
                hasSnapshot = hasSnapshot,
                canCompare = canCompare,
                candidateCount = candidateCount,
                resultCount = results.size,
                focusedIndex = focusedIndex,
                onAction = onAction,
                narrowError = narrowError
            )
        } else {
            ResultsView(
                candidateCount = candidateCount,
                results = results,
                knownAddresses = knownAddresses,
                valueSearchText = valueSearchText,
                onValueSearchChange = onValueSearchChange,
                focusedIndex = focusedIndex,
                onAction = onAction,
                narrowError = narrowError,
                listState = listState
            )
        }
    }
}

@Composable
private fun ErrorView(message: String) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = stringResource(R.string.ingame_cheats_discover_ram_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ActionsView(
    hasSnapshot: Boolean,
    canCompare: Boolean,
    candidateCount: Int,
    resultCount: Int,
    focusedIndex: Int,
    onAction: (Int) -> Unit,
    narrowError: String?
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        if (!hasSnapshot) {
            ActionPreference(
                title = stringResource(R.string.ingame_cheats_discover_snapshot_title),
                subtitle = stringResource(R.string.ingame_cheats_discover_snapshot_subtitle),
                isFocused = focusedIndex == 0,
                onClick = { onAction(0) }
            )

            Spacer(Modifier.weight(1f))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.ingame_cheats_discover_snapshot_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (canCompare) {
            ActionPreference(
                title = stringResource(R.string.ingame_cheats_discover_changed_title),
                subtitle = stringResource(R.string.ingame_cheats_discover_changed_subtitle),
                isFocused = focusedIndex == 0,
                onClick = { onAction(0) }
            )

            ActionPreference(
                title = stringResource(R.string.ingame_cheats_discover_same_title),
                subtitle = stringResource(R.string.ingame_cheats_discover_same_subtitle),
                isFocused = focusedIndex == 1,
                onClick = { onAction(1) }
            )

            if (resultCount > 0) {
                ActionPreference(
                    title = stringResource(R.string.ingame_cheats_discover_view_results_title),
                    subtitle = pluralStringResource(
                        R.plurals.ingame_cheats_discover_view_results_subtitle,
                        resultCount,
                        resultCount
                    ),
                    isFocused = focusedIndex == 2,
                    onClick = { onAction(2) }
                )
            }

            Spacer(Modifier.height(Dimens.spacingMd))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.spacingXs),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = pluralStringResource(
                        R.plurals.ingame_cheats_discover_actions_candidates,
                        candidateCount,
                        candidateCount
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (narrowError != null) {
                    Text(
                        text = narrowError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (resultCount > 0) {
                        stringResource(R.string.ingame_cheats_discover_hint_narrow)
                    } else {
                        stringResource(R.string.ingame_cheats_discover_hint_pick)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Spacer(Modifier.weight(1f))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                Text(
                    text = stringResource(R.string.ingame_cheats_discover_waiting_message),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Dimens.spacingMd))
                Text(
                    text = pluralStringResource(
                        R.plurals.ingame_cheats_discover_waiting_candidates,
                        candidateCount,
                        candidateCount
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ResultsView(
    candidateCount: Int,
    results: List<MemoryMatch>,
    knownAddresses: Map<Int, String>,
    valueSearchText: String,
    onValueSearchChange: (String) -> Unit,
    focusedIndex: Int,
    onAction: (Int) -> Unit,
    narrowError: String?,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SliderPreference(
            title = stringResource(R.string.ingame_cheats_discover_filter_title),
            value = valueSearchText.toIntOrNull() ?: 0,
            minValue = 0,
            maxValue = 255,
            isFocused = focusedIndex == 0,
            onClick = { onAction(0) },
            onAdjust = { delta ->
                val current = valueSearchText.toIntOrNull() ?: 0
                onValueSearchChange((current + delta).coerceIn(0, 255).toString())
            }
        )

        Spacer(Modifier.height(Dimens.spacingSm))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.spacingXs),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = pluralStringResource(
                    R.plurals.ingame_cheats_discover_results_candidates,
                    candidateCount,
                    candidateCount
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (narrowError != null) {
                Text(
                    text = narrowError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Text(
                    text = stringResource(R.string.ingame_cheats_discover_results_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.height(Dimens.spacingXs))

        if (results.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.ingame_cheats_discover_results_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).focusProperties { canFocus = false },
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
            ) {
                itemsIndexed(results, key = { _, match -> match.address }) { index, match ->
                    val knownCheatName = knownAddresses[match.address]
                    val addressHex = "0x${match.address.toString(16).uppercase().padStart(6, '0')}"
                    val valueHex = match.currentValue.toString(16).uppercase().padStart(2, '0')
                    val previousHex = match.previousValue
                        ?.toString(16)?.uppercase()?.padStart(2, '0')
                    val subtitle = when {
                        knownCheatName != null -> stringResource(
                            R.string.ingame_cheats_discover_result_saved,
                            knownCheatName
                        )
                        previousHex != null -> stringResource(
                            R.string.ingame_cheats_discover_result_value_previous,
                            valueHex,
                            previousHex
                        )
                        else -> stringResource(
                            R.string.ingame_cheats_discover_result_value,
                            valueHex
                        )
                    }
                    ActionPreference(
                        title = addressHex,
                        subtitle = subtitle,
                        isFocused = index == focusedIndex - 1,
                        isEnabled = knownCheatName == null,
                        onClick = { onAction(index + 1) }
                    )
                }
            }
        }
    }
}
