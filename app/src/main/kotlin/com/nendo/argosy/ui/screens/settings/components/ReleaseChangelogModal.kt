package com.nendo.argosy.ui.screens.settings.components

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.ModalScaffold
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.screens.settings.ChangelogRelease
import com.nendo.argosy.ui.screens.settings.ChangelogState
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.ColorTokens
import com.nendo.argosy.ui.util.clickableNoFocus
import com.nendo.argosy.ui.util.verticalEdgeFade
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class ChangelogPane { VERSIONS, LOG }

/** Two-pane release-history modal: version list on the left, rendered release notes on the right. */
@Composable
fun ReleaseChangelogModal(
    state: ChangelogState,
    onLoadMore: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val inputDispatcher = LocalInputDispatcher.current
    val currentState by rememberUpdatedState(state)
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    var pane by remember { mutableStateOf(ChangelogPane.VERSIONS) }
    var focusedIndex by remember { mutableIntStateOf(0) }
    val logScroll = rememberScrollState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val scrollStepPx = with(LocalDensity.current) { (Dimens.menuRowHeight * 3).toPx() }

    LaunchedEffect(state.visible) {
        if (state.visible) {
            pane = ChangelogPane.VERSIONS
            focusedIndex = 0
        }
    }
    LaunchedEffect(focusedIndex) { logScroll.scrollTo(0) }
    LaunchedEffect(state.releases.size, state.releases.firstOrNull()?.tag) {
        if (focusedIndex == 0) listState.scrollToItem(0)
    }
    LaunchedEffect(state.releases.size, state.canLoadMore, state.isLoading) {
        val extra = if (state.canLoadMore || state.isLoading) 1 else 0
        val last = (state.releases.size + extra - 1).coerceAtLeast(0)
        if (focusedIndex > last) focusedIndex = last
    }

    val inputHandler = remember {
        object : InputHandler {
            private fun loadRowIndex(): Int? {
                val s = currentState
                return if (s.canLoadMore || s.isLoading) s.releases.size else null
            }

            private fun lastFocusIndex(): Int {
                val s = currentState
                val extra = if (s.canLoadMore || s.isLoading) 1 else 0
                return (s.releases.size + extra - 1).coerceAtLeast(0)
            }

            private fun scrollLog(direction: Int) {
                scope.launch { logScroll.animateScrollBy(direction * scrollStepPx) }
            }

            override fun onUp(): InputResult {
                if (pane == ChangelogPane.VERSIONS) {
                    focusedIndex = (focusedIndex - 1).coerceAtLeast(0)
                } else {
                    scrollLog(-1)
                }
                return InputResult.HANDLED
            }

            override fun onDown(): InputResult {
                if (pane == ChangelogPane.VERSIONS) {
                    focusedIndex = (focusedIndex + 1).coerceAtMost(lastFocusIndex())
                    if (focusedIndex == loadRowIndex() && !currentState.isLoading) currentOnLoadMore()
                } else {
                    scrollLog(1)
                }
                return InputResult.HANDLED
            }

            override fun onLeft(): InputResult {
                if (pane == ChangelogPane.LOG) pane = ChangelogPane.VERSIONS
                return InputResult.HANDLED
            }

            override fun onRight(): InputResult {
                if (pane == ChangelogPane.VERSIONS) pane = ChangelogPane.LOG
                return InputResult.HANDLED
            }

            override fun onConfirm(): InputResult {
                if (pane == ChangelogPane.VERSIONS) {
                    if (focusedIndex == loadRowIndex()) {
                        if (!currentState.isLoading) currentOnLoadMore()
                    } else {
                        pane = ChangelogPane.LOG
                    }
                }
                return InputResult.HANDLED
            }

            override fun onBack(): InputResult {
                if (pane == ChangelogPane.LOG) {
                    pane = ChangelogPane.VERSIONS
                    return InputResult.HANDLED
                }
                currentOnDismiss()
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }

            override fun onMenu(): InputResult = InputResult.HANDLED
            override fun onSecondaryAction(): InputResult = InputResult.HANDLED
            override fun onContextMenu(): InputResult = InputResult.HANDLED
            override fun onPrevSection(): InputResult = InputResult.HANDLED
            override fun onNextSection(): InputResult = InputResult.HANDLED
            override fun onPrevTrigger(): InputResult = InputResult.HANDLED
            override fun onNextTrigger(): InputResult = InputResult.HANDLED
            override fun onSelect(): InputResult = InputResult.HANDLED
            override fun onLeftStickClick(): InputResult = InputResult.HANDLED
            override fun onRightStickClick(): InputResult = InputResult.HANDLED
            override fun onLongConfirm(): InputResult = InputResult.HANDLED
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, inputHandler, state.visible) {
        if (!state.visible) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inputDispatcher.pushModal(inputHandler)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            inputDispatcher.removeModal(inputHandler)
        }
    }

    ModalScaffold(
        visible = state.visible,
        onDismiss = onDismiss,
        maxWidth = Dimens.modalWidthXl
    ) {
        FocusedScroll(listState = listState, focusedIndex = focusedIndex)
        val shownRelease = state.releases.getOrNull(focusedIndex.coerceAtMost(state.releases.lastIndex))
        val paneShape = RoundedCornerShape(Dimens.radiusControl)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .padding(Dimens.spacingMd),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
        ) {
            Column(
                modifier = Modifier
                    .weight(0.38f)
                    .fillMaxHeight()
                    .clip(paneShape)
                    .border(
                        width = Dimens.borderThin,
                        color = if (pane == ChangelogPane.VERSIONS) theme.focusAccent else theme.hairlineLow,
                        shape = paneShape
                    )
                    .padding(Dimens.spacingSm)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.verticalEdgeFade(listState, fadeHeight = Dimens.spacingMd),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
                ) {
                    itemsIndexed(state.releases, key = { _, release -> release.tag }) { index, release ->
                        VersionRow(
                            release = release,
                            focused = pane == ChangelogPane.VERSIONS && index == focusedIndex,
                            selected = index == focusedIndex,
                            onClick = {
                                focusedIndex = index
                                pane = ChangelogPane.VERSIONS
                            }
                        )
                    }
                    if (state.canLoadMore || state.isLoading) {
                        item(key = "load-more") {
                            LoadMoreRow(
                                isLoading = state.isLoading,
                                focused = pane == ChangelogPane.VERSIONS && focusedIndex == state.releases.size,
                                onClick = { if (!state.isLoading) onLoadMore() }
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(0.62f)
                    .fillMaxHeight()
                    .clip(paneShape)
                    .border(
                        width = Dimens.borderThin,
                        color = if (pane == ChangelogPane.LOG) theme.focusAccent else theme.hairlineLow,
                        shape = paneShape
                    )
                    .clickableNoFocus { pane = ChangelogPane.LOG }
                    .verticalEdgeFade(logScroll, fadeHeight = Dimens.spacingMd)
                    .padding(Dimens.spacingMd)
                    .verticalScroll(logScroll)
            ) {
                if (shownRelease != null) {
                    Text(
                        text = shownRelease.name.ifBlank { shownRelease.tag },
                        style = MaterialTheme.typography.titleSmall,
                        color = theme.textPrimary
                    )
                    Spacer(modifier = Modifier.height(Dimens.spacingSm))
                    ChangelogBody(body = shownRelease.body)
                } else {
                    Text(
                        text = if (state.isLoading) "Loading releases..." else "No releases found",
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textDim
                    )
                }
            }
        }
    }
}

@Composable
private fun VersionRow(
    release: ChangelogRelease,
    focused: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    val betaColor = if (theme.isDark) ColorTokens.Semantic.Dark.progress else ColorTokens.Semantic.Light.progress
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .argosyFocusIndicators(focused = focused, indicators = FocusIndicators.ListRow, selected = selected, shape = shape)
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
        ) {
            Text(
                text = release.tag,
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textPrimary
            )
            if (release.prerelease) {
                Text(
                    text = "beta",
                    style = MaterialTheme.typography.labelSmall,
                    color = betaColor
                )
            }
        }
        release.publishedAt?.let { publishedAt ->
            Text(
                text = formatReleaseDate(publishedAt),
                style = MaterialTheme.typography.bodySmall,
                color = theme.textDim
            )
        }
    }
}

@Composable
private fun LoadMoreRow(
    isLoading: Boolean,
    focused: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .argosyFocusIndicators(focused = focused, indicators = FocusIndicators.ListRow, shape = shape)
            .clickableNoFocus(onClick = onClick)
            .padding(vertical = Dimens.spacingSm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isLoading) "Loading..." else "Load more",
            style = MaterialTheme.typography.bodySmall,
            color = if (focused) theme.textPrimary else theme.textDim
        )
    }
}

@Composable
private fun ChangelogBody(body: String?) {
    val theme = LocalArgosyTheme.current
    val lines = remember(body) { parseLogLines(body.orEmpty()) }
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
        lines.forEach { line ->
            when (line) {
                is LogLine.Heading -> Text(
                    text = line.text,
                    style = MaterialTheme.typography.titleSmall,
                    color = theme.textPrimary
                )
                is LogLine.Bullet -> Row {
                    Text(
                        text = "•  ",
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textDim
                    )
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textDim
                    )
                }
                is LogLine.Body -> Text(
                    text = line.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textDim
                )
                LogLine.Blank -> Spacer(modifier = Modifier.height(Dimens.spacingXs))
            }
        }
    }
}

private sealed class LogLine {
    data class Heading(val text: String) : LogLine()
    data class Bullet(val text: String) : LogLine()
    data class Body(val text: String) : LogLine()
    data object Blank : LogLine()
}

private fun stripMarkers(text: String): String = text.replace("**", "").replace("`", "")

private fun parseLogLines(body: String): List<LogLine> = body.lines().map { raw ->
    val line = raw.trim()
    when {
        line.isEmpty() -> LogLine.Blank
        line.startsWith("#") -> LogLine.Heading(stripMarkers(line.trimStart('#').trim()))
        line.startsWith("- ") || line.startsWith("* ") -> LogLine.Bullet(stripMarkers(line.substring(2).trim()))
        else -> LogLine.Body(stripMarkers(line))
    }
}

private fun formatReleaseDate(iso: String): String = runCatching {
    OffsetDateTime.parse(iso).toLocalDate()
        .format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))
}.getOrElse { iso.take(10) }
