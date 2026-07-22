package com.nendo.argosy.ui.screens.musicbrowser

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.MusicOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.remote.romm.RomMMusicFacet
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.util.clickableNoFocus

@Composable
fun MusicBrowserScreen(
    mode: MusicBrowserMode,
    sfxTargetLabel: String? = null,
    onSfxSelected: ((String) -> Unit)? = null,
    onDismiss: () -> Unit,
    viewModel: MusicBrowserViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentOnSfxSelected by rememberUpdatedState(onSfxSelected)

    LaunchedEffect(mode) {
        viewModel.open(mode)
    }

    LaunchedEffect(Unit) {
        viewModel.sfxAssignedEvent.collect { path ->
            currentOnSfxSelected?.invoke(path)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) viewModel.stopPreview()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onBrowserClosed()
        }
    }

    val inputHandler = remember(viewModel) {
        object : InputHandler {
            override fun onUp(): InputResult {
                val st = viewModel.uiState.value
                val moved = if (st.facetPicker != null) viewModel.moveFacetFocus(-1) else viewModel.moveFocus(-1)
                return if (moved) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)
            }

            override fun onDown(): InputResult {
                val st = viewModel.uiState.value
                val moved = if (st.facetPicker != null) viewModel.moveFacetFocus(1) else viewModel.moveFocus(1)
                return if (moved) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)
            }

            override fun onLeft(): InputResult = adjustDurationOrSilent(-1)
            override fun onRight(): InputResult = adjustDurationOrSilent(1)

            private fun adjustDurationOrSilent(delta: Int): InputResult {
                val st = viewModel.uiState.value
                val picker = st.facetPicker
                val onDurationRow = picker?.stage == FacetPickerStage.CHOOSER &&
                    picker.options.getOrNull(picker.focusIndex) == DURATION_FILTER_OPTION
                if (onDurationRow) {
                    viewModel.adjustSfxMaxDuration(delta)
                    return InputResult.HANDLED
                }
                return InputResult.handled(SoundType.SILENT)
            }

            override fun onConfirm(): InputResult {
                val st = viewModel.uiState.value
                when {
                    st.facetPicker != null -> viewModel.confirmFacetSelection()
                    st.errorMessage != null -> viewModel.retry()
                    st.focusedIndex == -1 -> viewModel.focusSearch()
                    else -> viewModel.confirmRow()
                }
                return InputResult.HANDLED
            }

            override fun onBack(): InputResult {
                val st = viewModel.uiState.value
                when {
                    st.facetPicker != null -> viewModel.dismissFacetPicker()
                    st.previewingId != null -> viewModel.stopPreview()
                    else -> currentOnDismiss()
                }
                return InputResult.HANDLED
            }

            override fun onSecondaryAction(): InputResult = InputResult.handled(SoundType.SILENT)

            override fun onContextMenu(): InputResult {
                val st = viewModel.uiState.value
                if (st.facetPicker != null) return InputResult.handled(SoundType.SILENT)
                viewModel.togglePreview()
                return InputResult.HANDLED
            }

            override fun onPrevSection(): InputResult {
                val st = viewModel.uiState.value
                if (st.facetPicker == null) viewModel.openFacetChooser()
                return InputResult.HANDLED
            }

            override fun onNextSection(): InputResult = InputResult.handled(SoundType.SILENT)

            override fun onPrevTrigger(): InputResult {
                val st = viewModel.uiState.value
                if (st.facetPicker != null) return InputResult.handled(SoundType.SILENT)
                return if (viewModel.jumpGroup(-1)) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)
            }

            override fun onNextTrigger(): InputResult {
                val st = viewModel.uiState.value
                if (st.facetPicker != null) return InputResult.handled(SoundType.SILENT)
                return if (viewModel.jumpGroup(1)) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)
            }

            override fun onMenu(): InputResult = InputResult.handled(SoundType.SILENT)
            override fun onSelect(): InputResult = InputResult.handled(SoundType.SILENT)
        }
    }

    ModalInputEffect(active = true, handler = inputHandler)

    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(uiState.showKeyboard) {
        if (uiState.showKeyboard) {
            focusRequester.requestFocus()
        } else {
            keyboardController?.hide()
        }
    }

    val density = LocalDensity.current
    val trackRowHeightPx = with(density) { Dimens.menuRowHeightLg.toPx() }
    val trackRowStridePx = with(density) { (Dimens.menuRowHeightLg + Dimens.spacingSm).toPx() }

    LaunchedEffect(uiState.focusedIndex, uiState.groups.size) {
        val groupIndex = uiState.groupIndexOf(uiState.focusedIndex)
        val group = uiState.groups.getOrNull(groupIndex) ?: return@LaunchedEffect
        val indexInGroup = uiState.focusedIndex - group.startIndex
        val viewportHeight = listState.layoutInfo.viewportEndOffset
        val centerOffset = (viewportHeight / 2) - (trackRowHeightPx / 2).toInt()
        val scrollOffset = (indexInGroup * trackRowStridePx).toInt() - centerOffset
        listState.animateScrollToItem(groupIndex, scrollOffset)
    }

    LaunchedEffect(listState, uiState.groups.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisible ->
                val groupCount = viewModel.uiState.value.groups.size
                if (lastVisible != null && groupCount > 0 && lastVisible >= groupCount - 2) {
                    viewModel.onListEndApproached()
                }
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickableNoFocus {}
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            MusicBrowserHeader(
                mode = mode,
                sfxTargetLabel = sfxTargetLabel,
                notice = uiState.notice,
                onBack = onDismiss
            )

            MusicSearchField(
                query = uiState.searchQuery,
                isSearchFocused = uiState.focusedIndex == -1,
                focusRequester = focusRequester,
                onQueryChange = { viewModel.updateSearch(it) },
                onTap = { viewModel.focusSearch() }
            )

            MusicFilterChips(
                artistFilter = uiState.artistFilter,
                albumFilter = uiState.albumFilter,
                genreFilter = uiState.genreFilter,
                hasActiveFilters = uiState.hasActiveFilters,
                sfxMaxDuration = if (mode == MusicBrowserMode.SFX) uiState.sfxMaxDuration else null,
                onFacetTap = { viewModel.openFacetValues(it) },
                onDurationTap = { viewModel.openFacetChooser() },
                onClearAll = { viewModel.clearFilters() }
            )

            Box(modifier = Modifier.weight(1f)) {
                when {
                    uiState.isLoading -> MusicBrowserLoading()
                    uiState.isOffline -> MusicBrowserMessage(
                        icon = Icons.Outlined.CloudOff,
                        message = "Not connected to your RomM server"
                    )
                    uiState.isUnsupported -> MusicBrowserMessage(
                        icon = Icons.Outlined.MusicOff,
                        message = "This RomM server does not support music browsing yet"
                    )
                    uiState.errorMessage != null -> MusicBrowserError(
                        message = uiState.errorMessage ?: "Something went wrong",
                        onRetry = { viewModel.retry() }
                    )
                    uiState.tracks.isEmpty() -> MusicBrowserMessage(
                        icon = Icons.Outlined.MusicNote,
                        message = "No tracks found"
                    )
                    else -> LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            start = Dimens.spacingLg,
                            end = Dimens.spacingLg,
                            top = Dimens.spacingSm,
                            bottom = Dimens.footerHeight
                        ),
                        verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
                    ) {
                        itemsIndexed(uiState.groups, key = { _, group -> group.romId }) { groupIndex, group ->
                            var rowHeightPx by remember { mutableIntStateOf(0) }
                            Column {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
                                    modifier = Modifier.onSizeChanged { rowHeightPx = it.height }
                                ) {
                                    GameGroupPanel(
                                        group = group,
                                        listState = listState,
                                        itemIndex = groupIndex,
                                        containerHeightPx = { rowHeightPx }
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                                    ) {
                                        group.tracks.forEachIndexed { trackIndex, track ->
                                            val flatIndex = group.startIndex + trackIndex
                                            MusicTrackRow(
                                                track = track,
                                                isFocused = uiState.focusedIndex == flatIndex,
                                                isDownloaded = uiState.isDownloaded(track),
                                                isDownloading = track.romFileId in uiState.downloadingIds,
                                                isInPlaylist = mode == MusicBrowserMode.BGM && uiState.isInPlaylist(track),
                                                isPreviewing = uiState.previewingId == track.romFileId,
                                                onClick = {
                                                    viewModel.setFocusIndex(flatIndex)
                                                    viewModel.confirmRow(flatIndex)
                                                },
                                                onPreview = { viewModel.togglePreview(flatIndex) }
                                            )
                                        }
                                    }
                                }
                                if (groupIndex < uiState.groups.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(top = Dimens.spacingMd),
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                }
                            }
                        }
                        if (uiState.isLoadingMore) {
                            item(key = "loading-more") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(Dimens.spacingMd),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(Dimens.iconMd))
                                }
                            }
                        }
                    }
                }
            }
        }

        uiState.facetPicker?.let { picker ->
            FacetPickerPopup(
                picker = picker,
                sfxMaxDuration = if (mode == MusicBrowserMode.SFX) uiState.sfxMaxDuration else null,
                onAdjustDuration = { viewModel.adjustSfxMaxDuration(it) },
                onSelect = { viewModel.confirmFacetSelection(it) },
                onDismiss = { viewModel.dismissFacetPicker() }
            )
        }

        val focusedTrack = uiState.tracks.getOrNull(uiState.focusedIndex)
        val hints = if (uiState.facetPicker != null || uiState.isUnsupported || uiState.isOffline) {
            emptyList()
        } else {
            buildList {
                add(InputButton.LB to "Filters")
                if (uiState.groups.size > 1) {
                    add(InputButton.LT_RT to "Game")
                }
                if (focusedTrack != null) {
                    add(InputButton.X to if (uiState.previewingId == focusedTrack.romFileId) "Stop" else "Preview")
                    add(
                        InputButton.A to when {
                            mode == MusicBrowserMode.SFX -> "Assign"
                            uiState.isInPlaylist(focusedTrack) -> "Remove"
                            else -> "Add"
                        }
                    )
                }
            }
        }

        FooterHints(
            hints = hints,
            onHintClick = { button ->
                when (button) {
                    InputButton.A -> inputHandler.onConfirm()
                    InputButton.B -> inputHandler.onBack()
                    InputButton.X -> inputHandler.onContextMenu()
                    InputButton.LB -> inputHandler.onPrevSection()
                    InputButton.LT_RT -> inputHandler.onNextTrigger()
                    else -> {}
                }
            }
        )
    }
}

@Composable
private fun MusicBrowserHeader(
    mode: MusicBrowserMode,
    sfxTargetLabel: String?,
    notice: String?,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.width(Dimens.spacingSm))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Server Music",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = when (mode) {
                    MusicBrowserMode.BGM -> "Add tracks to the BGM playlist"
                    MusicBrowserMode.SFX -> "Assign a sound for ${sfxTargetLabel ?: "this action"}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        notice?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = Dimens.spacingMd)
            )
        }
    }
}

@Composable
private fun MusicSearchField(
    query: String,
    isSearchFocused: Boolean,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onTap: () -> Unit
) {
    val focusAccent = LocalArgosyTheme.current.focusAccent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingXs)
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(
                if (isSearchFocused) focusAccent.copy(alpha = 0.15f).compositeOver(MaterialTheme.colorScheme.surfaceVariant)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickableNoFocus(onClick = onTap)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            tint = if (isSearchFocused) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Dimens.iconSm)
        )
        Spacer(modifier = Modifier.width(Dimens.spacingSm))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = "Search tracks...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        }
    }
}

@Composable
private fun MusicFilterChips(
    artistFilter: String?,
    albumFilter: String?,
    genreFilter: String?,
    hasActiveFilters: Boolean,
    sfxMaxDuration: Int?,
    onFacetTap: (RomMMusicFacet) -> Unit,
    onDurationTap: () -> Unit,
    onClearAll: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingXs),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(label = artistFilter ?: "Artist", isActive = artistFilter != null) {
            onFacetTap(RomMMusicFacet.ARTISTS)
        }
        FilterChip(label = albumFilter ?: "Album", isActive = albumFilter != null) {
            onFacetTap(RomMMusicFacet.ALBUMS)
        }
        FilterChip(label = genreFilter ?: "Genre", isActive = genreFilter != null) {
            onFacetTap(RomMMusicFacet.GENRES)
        }
        if (sfxMaxDuration != null) {
            FilterChip(
                label = "Under ${sfxMaxDuration}s",
                isActive = sfxMaxDuration != SFX_DURATION_DEFAULT_SECONDS,
                onClick = onDurationTap
            )
        }
        if (hasActiveFilters) {
            FilterChip(label = "Clear", isActive = false, onClick = onClearAll)
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = if (isActive) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.radiusPill))
            .background(
                if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingXs)
    )
}

@Composable
private fun GameGroupPanel(
    group: GameGroup,
    listState: LazyListState,
    itemIndex: Int,
    containerHeightPx: () -> Int
) {
    var panelHeightPx by remember { mutableIntStateOf(0) }
    val stickyTranslation by remember(itemIndex) {
        derivedStateOf {
            val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == itemIndex }
            val maxShift = (containerHeightPx() - panelHeightPx).coerceAtLeast(0).toFloat()
            if (info == null || panelHeightPx == 0) 0f
            else (-info.offset.toFloat()).coerceIn(0f, maxShift)
        }
    }
    Column(
        modifier = Modifier
            .width(Dimens.coverPanelWidth)
            .onSizeChanged { panelHeightPx = it.height }
            .graphicsLayer { translationY = stickyTranslation },
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        val model = rememberFileImageModel(group.coverPath)
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = group.gameName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f)
                    .clip(RoundedCornerShape(Dimens.radiusMd))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f)
                    .clip(RoundedCornerShape(Dimens.radiusMd))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(Dimens.iconLg)
                )
            }
        }
        Text(
            text = group.gameName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = group.platformName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MusicTrackRow(
    track: MusicTrackUi,
    isFocused: Boolean,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    isInPlaylist: Boolean,
    isPreviewing: Boolean,
    onClick: () -> Unit,
    onPreview: () -> Unit
) {
    val focusAccent = LocalArgosyTheme.current.focusAccent
    val focusedContent = lerp(focusAccent, Color.White, 0.45f)
    val successColor = LocalLauncherTheme.current.semanticColors.success
    val rowShape = RoundedCornerShape(Dimens.radiusMd)
    val borderModifier = if (isInPlaylist) {
        Modifier.border(Dimens.borderMedium, focusAccent, rowShape)
    } else Modifier

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.menuRowHeightLg)
            .clip(rowShape)
            .background(
                if (isFocused) focusAccent.copy(alpha = 0.15f).compositeOver(MaterialTheme.colorScheme.surface)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .then(borderModifier)
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isDownloading) {
            CircularProgressIndicator(
                modifier = Modifier.size(Dimens.iconMd),
                strokeWidth = Dimens.borderMedium
            )
        } else {
            Icon(
                imageVector = if (isPreviewing) Icons.Default.GraphicEq else Icons.Outlined.MusicNote,
                contentDescription = if (isInPlaylist) "In playlist" else null,
                tint = when {
                    isPreviewing -> MaterialTheme.colorScheme.primary
                    isInPlaylist -> focusAccent
                    isDownloaded -> successColor
                    isFocused -> focusedContent
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(Dimens.iconMd)
            )
        }

        Spacer(modifier = Modifier.width(Dimens.spacingMd))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isFocused) focusedContent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            track.artistAlbum?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isFocused) focusedContent.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        track.durationLabel?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = if (isFocused) focusedContent.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Dimens.spacingSm)
            )
        }

        IconButton(onClick = onPreview) {
            Icon(
                imageVector = if (isPreviewing) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = if (isPreviewing) "Stop preview" else "Preview",
                tint = if (isPreviewing) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MusicBrowserLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun MusicBrowserMessage(
    icon: ImageVector,
    message: String
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(Dimens.iconXl)
            )
            Spacer(modifier = Modifier.height(Dimens.spacingMd))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MusicBrowserError(
    message: String,
    onRetry: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Dimens.spacingMd))
            Text(
                text = "Retry",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(Dimens.radiusMd))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .clickableNoFocus(onClick = onRetry)
                    .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingSm)
            )
        }
    }
}

@Composable
private fun FacetPickerPopup(
    picker: FacetPickerUi,
    sfxMaxDuration: Int?,
    onAdjustDuration: (Int) -> Unit,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    val isDarkTheme = LocalLauncherTheme.current.isDarkTheme
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)
    val focusAccent = LocalArgosyTheme.current.focusAccent
    val focusedContent = lerp(focusAccent, Color.White, 0.45f)

    FocusedScroll(listState = listState, focusedIndex = picker.focusIndex)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(overlayColor)
            .clickableNoFocus(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        val maxModalHeight = maxHeight * 0.85f

        Column(
            modifier = Modifier
                .width(Dimens.modalWidthLg)
                .heightIn(max = maxModalHeight)
                .clip(RoundedCornerShape(Dimens.radiusPanel))
                .background(MaterialTheme.colorScheme.surface)
                .clickableNoFocus(enabled = false) {}
                .padding(Dimens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
        ) {
            Text(
                text = picker.title.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )

            if (picker.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Dimens.spacingXl),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f, fill = false),
                    contentPadding = PaddingValues(vertical = Dimens.spacingXs),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                ) {
                    itemsIndexed(picker.options, key = { index, option -> "$index:$option" }) { index, option ->
                        val isFocused = picker.focusIndex == index
                        if (option == DURATION_FILTER_OPTION && sfxMaxDuration != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(Dimens.radiusMd))
                                    .background(
                                        if (isFocused) focusAccent.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                    .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm)
                            ) {
                                Text(
                                    text = option,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isFocused) focusedContent else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "-",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(Dimens.radiusSm))
                                        .clickableNoFocus { onAdjustDuration(-1) }
                                        .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingXs)
                                )
                                Text(
                                    text = "${sfxMaxDuration}s",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isFocused) focusedContent else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "+",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(Dimens.radiusSm))
                                        .clickableNoFocus { onAdjustDuration(1) }
                                        .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingXs)
                                )
                            }
                        } else {
                            Text(
                                text = option,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isFocused) focusedContent else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(Dimens.radiusMd))
                                    .background(
                                        if (isFocused) focusAccent.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                    .clickableNoFocus { onSelect(index) }
                                    .padding(Dimens.spacingMd)
                            )
                        }
                    }
                }
            }
        }
    }
}
