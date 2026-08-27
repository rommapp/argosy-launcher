package com.nendo.argosy.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import coil.compose.AsyncImage
import com.nendo.argosy.data.netplay.CoreSubState
import com.nendo.argosy.data.netplay.JoinCandidate
import com.nendo.argosy.data.netplay.JoinVariant
import com.nendo.argosy.data.netplay.NetplayJoinState
import com.nendo.argosy.data.netplay.VerifySubState
import com.nendo.argosy.ui.primitives.ArgosyProgressBar
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

@Composable
fun NetplayJoinModal(
    state: NetplayJoinState,
    onDismiss: () -> Unit
) {
    if (state is NetplayJoinState.Idle ||
        state is NetplayJoinState.Cancelled ||
        state is NetplayJoinState.LaunchReady
    ) return

    val hostUsername = state.hostUsername()
    val gameTitle = state.gameTitle() ?: stringResource(R.string.ui_netplay_join_unknown_game)

    Modal(
        title = "",
        baseWidth = 420.dp,
        onDismiss = onDismiss,
        titleContent = {
            Text(
                text = stringResource(
                    R.string.ui_netplay_join_heading,
                    hostUsername ?: stringResource(R.string.ui_netplay_join_unknown_host)
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = gameTitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    ) {
        CurrentStateContent(state)
    }
}

private fun NetplayJoinState.hostUsername(): String? = when (this) {
    is NetplayJoinState.MatchingCore -> friend.username
    is NetplayJoinState.VerifyingGame -> friend.username
    is NetplayJoinState.JoiningSession -> friend.username
    else -> null
}

private fun NetplayJoinState.gameTitle(): String? = when (this) {
    is NetplayJoinState.MatchingCore -> session.gameTitle
    is NetplayJoinState.VerifyingGame -> session.gameTitle
    is NetplayJoinState.JoiningSession -> session.gameTitle
    else -> null
}

@Composable
private fun CurrentStateContent(state: NetplayJoinState) {
    when (state) {
        is NetplayJoinState.MatchingCore -> MatchingCoreContent(state.sub)
        is NetplayJoinState.VerifyingGame -> VerifyingGameContent(state.sub)
        is NetplayJoinState.JoiningSession ->
            StatusLine(stringResource(R.string.ui_netplay_join_status_joining))
        is NetplayJoinState.Failed -> Text(
            text = state.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        else -> Unit
    }
}

@Composable
private fun MatchingCoreContent(sub: CoreSubState) {
    when (sub) {
        CoreSubState.Resolving ->
            StatusLine(stringResource(R.string.ui_netplay_join_status_matching_core))
        is CoreSubState.Downloading -> {
            StatusLine(stringResource(R.string.ui_netplay_join_status_downloading_core))
            Spacer(Modifier.height(Dimens.spacingSm))
            ArgosyProgressBar(progress = sub.pct.coerceIn(0f, 1f))
        }
        is CoreSubState.Ready ->
            StatusLine(stringResource(R.string.ui_netplay_join_status_core_ready))
    }
}

@Composable
private fun VerifyingGameContent(sub: VerifySubState) {
    when (sub) {
        VerifySubState.Probing ->
            StatusLine(stringResource(R.string.ui_netplay_join_status_verifying))
        is VerifySubState.Confirmed ->
            StatusLine(stringResource(R.string.ui_netplay_join_status_verified))
        is VerifySubState.AmbiguousCandidates -> CandidatePicker(sub)
        is VerifySubState.HashMismatchVariants -> VariantPicker(sub)
    }
}

@Composable
private fun StatusLine(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CandidatePicker(sub: VerifySubState.AmbiguousCandidates) {
    val listState = rememberLazyListState()
    FocusedScroll(listState = listState, focusedIndex = sub.focusIndex)
    val downloading = sub.selectedGameId != null && sub.downloadProgress != null

    val label = when {
        sub.candidates.size == 1 && !sub.candidates[0].isInstalled ->
            stringResource(R.string.ui_netplay_join_candidates_single_missing)
        sub.candidates.size == 1 ->
            stringResource(R.string.ui_netplay_join_candidates_single_failed)
        sub.candidates.none { it.isInstalled } ->
            stringResource(R.string.ui_netplay_join_candidates_none_installed)
        else -> stringResource(R.string.ui_netplay_join_candidates_multiple)
    }
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Dimens.spacingSm))

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
        ) {
            itemsIndexed(sub.candidates, key = { _, c -> c.gameId }) { index, candidate ->
                val isSelected = candidate.gameId == sub.selectedGameId
                val isFocused = index == sub.focusIndex
                val dimmed = downloading && !isSelected
                val progress = if (isSelected) sub.downloadProgress else null
                CandidateRow(
                    candidate = candidate,
                    isFocused = isFocused,
                    dimmed = dimmed,
                    progress = progress
                )
            }
        }
    }
}

@Composable
private fun CandidateRow(
    candidate: JoinCandidate,
    isFocused: Boolean,
    dimmed: Boolean,
    progress: Float?
) {
    val alpha by animateFloatAsState(if (dimmed) 0.4f else 1f, label = "candidateAlpha")
    val focusAccent = LocalArgosyTheme.current.focusAccent
    val containerColor = if (isFocused) {
        focusAccent.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val titleColor = if (isFocused) {
        lerp(focusAccent, Color.White, 0.45f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val metaColor = if (isFocused) {
        lerp(focusAccent, Color.White, 0.45f).copy(alpha = 0.8f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(containerColor)
            .alpha(alpha)
            .padding(Dimens.spacingSm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoverThumb(candidate.coverPath)
            Spacer(Modifier.width(Dimens.spacingSm))
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = candidate.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = titleColor,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = candidate.platformSlug,
                        style = MaterialTheme.typography.bodySmall,
                        color = metaColor
                    )
                    Text(
                        text = if (candidate.isInstalled) {
                            stringResource(R.string.ui_netplay_join_candidate_installed)
                        } else {
                            stringResource(R.string.ui_netplay_join_candidate_not_installed)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (candidate.isInstalled) MaterialTheme.colorScheme.primary else metaColor
                    )
                }
            }
        }
        if (progress != null) {
            Spacer(Modifier.height(Dimens.spacingXs))
            ArgosyProgressBar(progress = progress.coerceIn(0f, 1f))
        }
    }
}

@Composable
private fun CoverThumb(coverPath: String?) {
    val size = 48.dp
    Box(
        modifier = Modifier
            .size(width = size * 0.75f, height = size)
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (!coverPath.isNullOrEmpty()) {
            AsyncImage(
                model = coverPath,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun VariantPicker(sub: VerifySubState.HashMismatchVariants) {
    val listState = rememberLazyListState()
    FocusedScroll(listState = listState, focusedIndex = sub.focusIndex)

    Column {
        Text(
            text = stringResource(R.string.ui_netplay_join_variants_prompt),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Dimens.spacingSm))
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
        ) {
            itemsIndexed(sub.variants, key = { _, v -> v.fileId }) { index, variant ->
                VariantRow(
                    variant = variant,
                    isFocused = index == sub.focusIndex,
                    isTrying = variant.fileId == sub.tryingFileId
                )
            }
        }
    }
}

@Composable
private fun VariantRow(variant: JoinVariant, isFocused: Boolean, isTrying: Boolean) {
    val focusAccent = LocalArgosyTheme.current.focusAccent
    val containerColor = if (isFocused) {
        focusAccent.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val titleColor = if (isFocused) {
        lerp(focusAccent, Color.White, 0.45f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val metaColor = if (isFocused) {
        lerp(focusAccent, Color.White, 0.45f).copy(alpha = 0.8f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(containerColor)
            .padding(Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = variant.fileName,
                style = MaterialTheme.typography.bodyMedium,
                color = titleColor
            )
            if (!variant.category.isNullOrEmpty()) {
                Text(
                    text = variant.category,
                    style = MaterialTheme.typography.bodySmall,
                    color = metaColor
                )
            }
        }
        if (isTrying) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
