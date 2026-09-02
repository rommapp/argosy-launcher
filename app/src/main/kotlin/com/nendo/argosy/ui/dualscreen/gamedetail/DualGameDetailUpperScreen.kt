/**
 * DUAL-SCREEN COMPONENT - Upper display game detail view.
 * Shows rich info: boxart, description, achievements.
 * When screenshot is selected on lower, becomes full viewer.
 */
package com.nendo.argosy.ui.dualscreen.gamedetail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.nendo.argosy.R
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.data.emulator.DiscOption
import androidx.compose.material3.OutlinedTextField
import com.nendo.argosy.domain.model.UnifiedStateEntry
import androidx.compose.foundation.layout.fillMaxHeight
import com.nendo.argosy.ui.common.displayName
import com.nendo.argosy.ui.common.rememberCoverAspectRatio
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.common.sizeFormatted
import com.nendo.argosy.ui.components.Box3dCover
import com.nendo.argosy.ui.components.GameTitle
import com.nendo.argosy.ui.components.SystemStatusBar
import com.nendo.argosy.ui.dualscreen.ShowcaseAmbience
import com.nendo.argosy.ui.dualscreen.ShowcaseEyebrow
import com.nendo.argosy.ui.dualscreen.ShowcaseRatingsCluster
import com.nendo.argosy.ui.dualscreen.ShowcaseStatsRow
import com.nendo.argosy.ui.dualscreen.ShowcaseTimeToBeatRow
import com.nendo.argosy.ui.primitives.ActionButton
import com.nendo.argosy.ui.primitives.GlassPanel
import com.nendo.argosy.ui.primitives.RowButton
import com.nendo.argosy.ui.screens.collections.dialogs.CreateCollectionDialog
import com.nendo.argosy.ui.screens.gamedetail.RatingType
import com.nendo.argosy.ui.screens.gamedetail.modals.RatingPickerModal
import com.nendo.argosy.ui.screens.gamedetail.modals.StatusPickerModal
import com.nendo.argosy.ui.theme.ALauncherColors
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalBoxArtStyle
import com.nendo.argosy.ui.util.touchOnly
import com.nendo.argosy.util.formatPlayTime
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit

@Composable
fun DualGameDetailUpperScreen(
    state: DualGameDetailUpperState,
    onModalRatingSelect: (Int) -> Unit = {},
    onModalStatusSelect: (String) -> Unit = {},
    onModalEmulatorSelect: (Int) -> Unit = {},
    onModalCoreSelect: (Int) -> Unit = {},
    onModalSavePathSelect: (Int) -> Unit = {},
    onModalDisplayTargetSelect: (Int) -> Unit = {},
    onModalMemoryCardSelect: (Int) -> Unit = {},
    onModalVariantSelect: (Int) -> Unit = {},
    onModalCollectionToggle: (Long) -> Unit = {},
    onModalCollectionShowCreate: () -> Unit = {},
    onModalCollectionCreate: (String) -> Unit = {},
    onModalCollectionCreateDismiss: () -> Unit = {},
    onSaveNameTextChange: (String) -> Unit = {},
    onSaveNameConfirm: () -> Unit = {},
    onDiscSelect: (Int) -> Unit = {},
    onModalSteamInstallSelect: (Int) -> Unit = {},
    onModalDismiss: () -> Unit = {},
    onFilePickerToggle: (com.nendo.argosy.data.model.FilePickerRow) -> Unit = {},
    onFilePickerSelectAll: () -> Unit = {},
    onFilePickerConfirm: () -> Unit = {},
    onFilePickerToggleCollapse: (String) -> Unit = {},
    footerHints: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    val viewerPath = state.viewerScreenshotIndex?.let { idx ->
        state.screenshots.getOrNull(idx)
    }

    val stateScreenshotPath = state.statePreviewScreenshotPath

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(theme.surfaceBase)
    ) {
        if (viewerPath != null) {
            ScreenshotViewer(
                imagePath = viewerPath,
                modifier = Modifier.fillMaxSize()
            )
        } else if (stateScreenshotPath != null) {
            StatePreviewDisplay(
                screenshotPath = stateScreenshotPath,
                entry = state.focusedStateEntry,
                coverPath = state.coverPath,
                footerHints = footerHints
            )
        } else {
            GameInfoDisplay(
                state = state,
                footerHints = footerHints
            )
        }

        if (viewerPath == null && stateScreenshotPath == null) {
            SystemStatusBar(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(horizontal = Dimens.spacingXxl, vertical = Dimens.spacingLg)
            )
        }

        when (state.modalType) {
            ActiveModal.RATING -> RatingPickerModal(
                type = RatingType.OPINION,
                value = state.modalRatingValue,
                onValueChange = onModalRatingSelect,
                onDismiss = onModalDismiss
            )
            ActiveModal.DIFFICULTY -> RatingPickerModal(
                type = RatingType.DIFFICULTY,
                value = state.modalRatingValue,
                onValueChange = onModalRatingSelect,
                onDismiss = onModalDismiss
            )
            ActiveModal.STATUS -> StatusPickerModal(
                selectedValue = state.modalStatusSelected,
                currentValue = state.modalStatusCurrent,
                onSelect = onModalStatusSelect,
                onDismiss = onModalDismiss
            )
            ActiveModal.EMULATOR -> DualEmulatorPickerContent(
                emulatorNames = state.emulatorNames,
                emulatorVersions = state.emulatorVersions,
                currentEmulatorName = state.emulatorCurrentName,
                focusIndex = state.emulatorFocusIndex,
                onSelect = onModalEmulatorSelect,
                onDismiss = onModalDismiss
            )
            ActiveModal.CORE -> DualCorePickerContent(
                coreNames = state.coreNames,
                currentCoreName = state.coreCurrentName,
                focusIndex = state.coreFocusIndex,
                onSelect = onModalCoreSelect,
                onDismiss = onModalDismiss
            )
            ActiveModal.SAVE_PATH -> DualSavePathPickerContent(
                overridePath = state.savePathOverride,
                focusIndex = state.savePathFocusIndex,
                onSelect = onModalSavePathSelect,
                onDismiss = onModalDismiss
            )
            ActiveModal.DISPLAY_TARGET -> DualDisplayTargetPickerContent(
                targetNames = state.displayTargetNames,
                currentTargetName = state.displayTargetCurrentName,
                inheritedTargetName = state.displayTargetInheritedName,
                focusIndex = state.displayTargetFocusIndex,
                onSelect = onModalDisplayTargetSelect,
                onDismiss = onModalDismiss
            )
            ActiveModal.MEMORY_CARD -> DualMemoryCardPickerContent(
                cardNames = state.memoryCardNames,
                currentCardName = state.memoryCardCurrentName,
                inheritedCardName = state.memoryCardInheritedName,
                focusIndex = state.memoryCardFocusIndex,
                onSelect = onModalMemoryCardSelect,
                onDismiss = onModalDismiss
            )
            ActiveModal.VARIANT_PICKER -> DualVariantPickerContent(
                variantNames = state.variantNames,
                currentVariantName = state.variantCurrentName,
                focusIndex = state.variantFocusIndex,
                onSelect = onModalVariantSelect,
                onDismiss = onModalDismiss
            )
            ActiveModal.COLLECTION -> {
                DualCollectionModalContent(
                    items = state.collectionItems,
                    focusIndex = state.collectionFocusIndex,
                    showCreateDialog = state.showCreateDialog,
                    onToggle = onModalCollectionToggle,
                    onShowCreate = onModalCollectionShowCreate,
                    onCreate = onModalCollectionCreate,
                    onCreateDismiss = onModalCollectionCreateDismiss,
                    onDismiss = onModalDismiss
                )
            }
            ActiveModal.SAVE_NAME -> DualSaveNamePrompt(
                text = state.saveNameText,
                onTextChange = onSaveNameTextChange,
                onConfirm = onSaveNameConfirm,
                onDismiss = onModalDismiss
            )
            ActiveModal.DISC_PICKER -> DualDiscPickerContent(
                discs = state.discPickerOptions,
                focusIndex = state.discPickerFocusIndex,
                onSelect = onDiscSelect,
                onDismiss = onModalDismiss
            )
            ActiveModal.STEAM_INSTALL -> DualSteamInstallPickerContent(
                optionNames = state.steamInstallOptionNames,
                focusIndex = state.steamInstallFocusIndex,
                onSelect = onModalSteamInstallSelect,
                onDismiss = onModalDismiss
            )
            ActiveModal.FILE_PICKER -> {
                val isSelected = { row: com.nendo.argosy.data.model.FilePickerRow ->
                    row.versionRommId
                        ?.let { it in state.filePickerSelectedVersions }
                        ?: (row.rommFileId in state.filePickerSelected)
                }
                val summary = if (state.filePickerManageMode) {
                    val adds = state.filePickerRows.filter { !it.isHeader && !it.isLocked && !it.isDownloaded && isSelected(it) }
                    val removes = state.filePickerRows.filter { !it.isHeader && !it.isLocked && it.isDownloaded && !isSelected(it) }
                    when {
                        adds.isEmpty() && removes.isEmpty() ->
                            stringResource(R.string.dual_detail_file_picker_no_changes)
                        else -> {
                            val addsSummary = stringResource(
                                R.string.dual_detail_file_picker_summary_adds,
                                adds.size,
                                com.nendo.argosy.util.formatBytes(adds.sumOf { it.sizeBytes })
                            )
                            val removesSummary = stringResource(
                                R.string.dual_detail_file_picker_summary_removes,
                                removes.size,
                                com.nendo.argosy.util.formatBytes(removes.sumOf { it.sizeBytes })
                            )
                            buildList {
                                if (adds.isNotEmpty()) add(addsSummary)
                                if (removes.isNotEmpty()) add(removesSummary)
                            }.joinToString("   ")
                        }
                    }
                } else {
                    val selected = state.filePickerRows.filter { !it.isHeader && isSelected(it) }
                    stringResource(
                        R.string.dual_detail_file_picker_summary_selected,
                        selected.size,
                        state.filePickerRows.count { !it.isHeader },
                        com.nendo.argosy.util.formatBytes(selected.sumOf { it.sizeBytes })
                    )
                }
                com.nendo.argosy.ui.screens.gamedetail.modals.FilePickerModal(
                    gameTitle = state.title,
                    title = if (state.filePickerManageMode) {
                        stringResource(R.string.dual_detail_file_picker_manage_title)
                    } else {
                        stringResource(R.string.dual_detail_file_picker_choose_title)
                    },
                    rows = state.visibleFilePickerRows,
                    selectedIds = state.filePickerSelected,
                    selectedVersionIds = state.filePickerSelectedVersions,
                    focusIndex = state.filePickerFocusIndex,
                    summary = summary,
                    onToggleRow = onFilePickerToggle,
                    onSelectAll = onFilePickerSelectAll,
                    onConfirm = onFilePickerConfirm,
                    onDismiss = onModalDismiss,
                    allRows = state.filePickerRows,
                    collapsedGroups = state.filePickerCollapsed,
                    onToggleCollapse = onFilePickerToggleCollapse,
                    manageMode = state.filePickerManageMode
                )
            }
            ActiveModal.NONE -> {}
        }
    }
}

@Composable
internal fun DualSteamInstallPickerContent(
    optionNames: List<String>,
    focusIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .touchOnly { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        GlassPanel(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .touchOnly { }
        ) {
            Column(modifier = Modifier.padding(Dimens.spacingLg)) {
                Text(
                    text = stringResource(R.string.dual_detail_steam_install_heading),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = theme.textPrimary,
                    modifier = Modifier.padding(bottom = Dimens.spacingMd)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
                    contentPadding = PaddingValues(vertical = Dimens.spacingXs)
                ) {
                    item {
                        EmulatorPickerItem(
                            name = stringResource(R.string.dual_detail_steam_install_argosy),
                            version = null,
                            isSelected = focusIndex == 0,
                            isCurrent = false,
                            onClick = { onSelect(0) }
                        )
                    }
                    itemsIndexed(optionNames, key = { _, n -> n }) { index, name ->
                        val itemIndex = index + 1
                        EmulatorPickerItem(
                            name = stringResource(R.string.dual_detail_steam_install_external),
                            version = stringResource(
                                R.string.dual_detail_steam_install_managed_by,
                                name
                            ),
                            isSelected = focusIndex == itemIndex,
                            isCurrent = false,
                            onClick = { onSelect(itemIndex) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenshotViewer(
    imagePath: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = rememberFileImageModel(imagePath),
            contentDescription = stringResource(
                R.string.dual_detail_screenshot_viewer_description
            ),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
            onError = {}
        )
    }
}

@Composable
private fun GameInfoDisplay(
    state: DualGameDetailUpperState,
    footerHints: @Composable () -> Unit
) {
    val theme = LocalArgosyTheme.current
    ShowcaseAmbience(artPath = state.backgroundPath ?: state.coverPath)

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = Dimens.spacingXxl),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXxl)
        ) {
            if (state.coverPath != null) {
                if (state.boxSpinePath != null && state.coverPath.startsWith("/")) {
                    Box3dCover(
                        frontPath = state.coverPath,
                        spinePath = state.boxSpinePath,
                        backPath = state.boxBackPath,
                        modifier = Modifier.fillMaxHeight(0.72f)
                    )
                } else {
                    val boxArtStyle = LocalBoxArtStyle.current
                    val coverAspectRatio = if (boxArtStyle.nativeAspectRatio) {
                        rememberCoverAspectRatio(state.coverPath, boxArtStyle.aspectRatio)
                    } else {
                        boxArtStyle.aspectRatio
                    }
                    AsyncImage(
                        model = File(state.coverPath),
                        contentDescription = state.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxHeight(0.72f)
                            .aspectRatio(coverAspectRatio)
                            .clip(RoundedCornerShape(Dimens.radiusSm)),
                        onError = {}
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                ShowcaseEyebrow(
                    platformName = state.platformName,
                    releaseYear = state.releaseYear,
                    developer = state.developer,
                    titleId = state.titleId
                )
                Spacer(modifier = Modifier.height(Dimens.spacingSm))
                GameTitle(
                    title = state.title,
                    titleStyle = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    titleColor = theme.textPrimary,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(Dimens.spacingMd))
                ShowcaseRatingsCluster(
                    communityRating = state.communityRating,
                    userRating = state.rating ?: 0,
                    userDifficulty = state.userDifficulty
                )
                if (state.description != null) {
                    Spacer(modifier = Modifier.height(Dimens.spacingLg))
                    Text(
                        text = state.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = theme.textDim,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (state.achievementCount > 0) {
                    Spacer(modifier = Modifier.height(Dimens.spacingLg))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.EmojiEvents,
                            contentDescription = null,
                            tint = ALauncherColors.StarGold,
                            modifier = Modifier.size(Dimens.iconMd)
                        )
                        Text(
                            text = "${state.earnedAchievementCount}/${state.achievementCount}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = theme.textPrimary
                        )
                        Text(
                            text = stringResource(R.string.dual_detail_achievements_label),
                            style = MaterialTheme.typography.bodyMedium,
                            color = theme.textDim
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Dimens.spacingXl))
                ShowcaseStatsRow(
                    playTimeMinutes = state.playTimeMinutes,
                    lastPlayedAt = state.lastPlayedAt,
                    status = state.status,
                    valueStyle = MaterialTheme.typography.titleMedium
                )
                ShowcaseTimeToBeatRow(
                    mainSeconds = state.timeToBeatMainSec,
                    extraSeconds = state.timeToBeatExtraSec,
                    completionistSeconds = state.timeToBeatCompletionistSec,
                    modifier = Modifier.padding(top = Dimens.spacingMd)
                )
            }
        }

        HorizontalDivider(
            color = theme.hairlineLow
        )

        footerHints()
    }
}

@Composable
private fun DualEmulatorPickerContent(
    emulatorNames: List<String>,
    emulatorVersions: List<String>,
    currentEmulatorName: String?,
    focusIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .touchOnly { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        GlassPanel(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .touchOnly { }
        ) {
            Column(modifier = Modifier.padding(Dimens.spacingLg)) {
                Text(
                    text = stringResource(R.string.dual_detail_emulator_picker_heading),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = theme.textPrimary,
                    modifier = Modifier.padding(bottom = Dimens.spacingMd)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
                    contentPadding = PaddingValues(vertical = Dimens.spacingXs)
                ) {
                    item {
                        val isSelected = focusIndex == 0
                        val isCurrent = currentEmulatorName == null
                        EmulatorPickerItem(
                            name = stringResource(R.string.dual_detail_emulator_picker_default),
                            version = null,
                            isSelected = isSelected,
                            isCurrent = isCurrent,
                            onClick = { onSelect(0) }
                        )
                    }
                    itemsIndexed(emulatorNames, key = { _, n -> n }) { index, name ->
                        val itemIndex = index + 1
                        val isSelected = focusIndex == itemIndex
                        val isCurrent = name == currentEmulatorName
                        EmulatorPickerItem(
                            name = name,
                            version = emulatorVersions.getOrNull(index)
                                ?.takeIf { it.isNotBlank() },
                            isSelected = isSelected,
                            isCurrent = isCurrent,
                            onClick = { onSelect(itemIndex) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DualCorePickerContent(
    coreNames: List<String>,
    currentCoreName: String?,
    focusIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .touchOnly { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        GlassPanel(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .touchOnly { }
        ) {
            Column(modifier = Modifier.padding(Dimens.spacingLg)) {
                Text(
                    text = stringResource(R.string.dual_detail_core_picker_heading),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = theme.textPrimary,
                    modifier = Modifier.padding(bottom = Dimens.spacingMd)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
                    contentPadding = PaddingValues(vertical = Dimens.spacingXs)
                ) {
                    item {
                        val isSelected = focusIndex == 0
                        val isCurrent = currentCoreName == null
                        EmulatorPickerItem(
                            name = stringResource(R.string.dual_detail_core_picker_default),
                            version = null,
                            isSelected = isSelected,
                            isCurrent = isCurrent,
                            onClick = { onSelect(0) }
                        )
                    }
                    itemsIndexed(coreNames, key = { _, n -> n }) { index, name ->
                        val itemIndex = index + 1
                        val isSelected = focusIndex == itemIndex
                        val isCurrent = name == currentCoreName
                        EmulatorPickerItem(
                            name = name,
                            version = null,
                            isSelected = isSelected,
                            isCurrent = isCurrent,
                            onClick = { onSelect(itemIndex) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DualSavePathPickerContent(
    overridePath: String?,
    focusIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .touchOnly { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        GlassPanel(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .touchOnly { }
        ) {
            Column(modifier = Modifier.padding(Dimens.spacingLg)) {
                Text(
                    text = stringResource(R.string.dual_detail_save_path_heading),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = theme.textPrimary,
                    modifier = Modifier.padding(bottom = Dimens.spacingMd)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
                    contentPadding = PaddingValues(vertical = Dimens.spacingXs)
                ) {
                    item {
                        EmulatorPickerItem(
                            name = if (overridePath != null) {
                                stringResource(R.string.dual_detail_save_path_reset)
                            } else {
                                stringResource(R.string.dual_detail_save_path_inherited)
                            },
                            version = if (overridePath == null) {
                                stringResource(R.string.dual_detail_save_path_inherited_hint)
                            } else null,
                            isSelected = focusIndex == 0,
                            isCurrent = overridePath == null,
                            onClick = { onSelect(0) }
                        )
                    }
                    if (overridePath != null) {
                        item {
                            EmulatorPickerItem(
                                name = overridePath,
                                version = stringResource(
                                    R.string.dual_detail_save_path_current_override
                                ),
                                isSelected = focusIndex == 1,
                                isCurrent = true,
                                onClick = { onSelect(1) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DualDisplayTargetPickerContent(
    targetNames: List<String>,
    currentTargetName: String?,
    inheritedTargetName: String?,
    focusIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .touchOnly { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        GlassPanel(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .touchOnly { }
        ) {
            Column(modifier = Modifier.padding(Dimens.spacingLg)) {
                Text(
                    text = stringResource(R.string.dual_detail_display_target_heading),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = theme.textPrimary,
                    modifier = Modifier.padding(bottom = Dimens.spacingMd)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
                    contentPadding = PaddingValues(vertical = Dimens.spacingXs)
                ) {
                    item {
                        EmulatorPickerItem(
                            name = stringResource(R.string.dual_detail_display_target_default),
                            version = inheritedTargetName,
                            isSelected = focusIndex == 0,
                            isCurrent = currentTargetName == null,
                            onClick = { onSelect(0) }
                        )
                    }
                    itemsIndexed(targetNames, key = { _, n -> n }) { index, name ->
                        val itemIndex = index + 1
                        EmulatorPickerItem(
                            name = name,
                            version = null,
                            isSelected = focusIndex == itemIndex,
                            isCurrent = name == currentTargetName,
                            onClick = { onSelect(itemIndex) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DualMemoryCardPickerContent(
    cardNames: List<String>,
    currentCardName: String?,
    inheritedCardName: String?,
    focusIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .touchOnly { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        GlassPanel(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .touchOnly { }
        ) {
            Column(modifier = Modifier.padding(Dimens.spacingLg)) {
                Text(
                    text = stringResource(R.string.dual_detail_memory_card_heading),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = theme.textPrimary,
                    modifier = Modifier.padding(bottom = Dimens.spacingMd)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
                    contentPadding = PaddingValues(vertical = Dimens.spacingXs)
                ) {
                    item {
                        EmulatorPickerItem(
                            name = stringResource(R.string.dual_detail_memory_card_auto),
                            version = inheritedCardName,
                            isSelected = focusIndex == 0,
                            isCurrent = currentCardName == null,
                            onClick = { onSelect(0) }
                        )
                    }
                    itemsIndexed(cardNames, key = { _, n -> n }) { index, name ->
                        val itemIndex = index + 1
                        EmulatorPickerItem(
                            name = name,
                            version = null,
                            isSelected = focusIndex == itemIndex,
                            isCurrent = name == currentCardName,
                            onClick = { onSelect(itemIndex) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DualVariantPickerContent(
    variantNames: List<String>,
    currentVariantName: String?,
    focusIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .touchOnly { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        GlassPanel(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .touchOnly { }
        ) {
            Column(modifier = Modifier.padding(Dimens.spacingLg)) {
                Text(
                    text = stringResource(R.string.dual_detail_variant_heading),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = theme.textPrimary,
                    modifier = Modifier.padding(bottom = Dimens.spacingMd)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
                    contentPadding = PaddingValues(vertical = Dimens.spacingXs)
                ) {
                    item {
                        EmulatorPickerItem(
                            name = stringResource(R.string.dual_detail_variant_default),
                            version = null,
                            isSelected = focusIndex == 0,
                            isCurrent = currentVariantName == null,
                            onClick = { onSelect(0) }
                        )
                    }
                    itemsIndexed(variantNames, key = { _, n -> n }) { index, name ->
                        val itemIndex = index + 1
                        val isSelected = focusIndex == itemIndex
                        val isCurrent = name == currentVariantName
                        EmulatorPickerItem(
                            name = name,
                            version = null,
                            isSelected = isSelected,
                            isCurrent = isCurrent,
                            onClick = { onSelect(itemIndex) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DualDiscPickerContent(
    discs: List<DiscOption>,
    focusIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .touchOnly { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        GlassPanel(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .touchOnly { }
        ) {
            Column(modifier = Modifier.padding(Dimens.spacingLg)) {
                Text(
                    text = stringResource(R.string.dual_detail_disc_heading),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = theme.textPrimary,
                    modifier = Modifier.padding(bottom = Dimens.spacingMd)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
                    contentPadding = PaddingValues(vertical = Dimens.spacingXs)
                ) {
                    itemsIndexed(discs, key = { _, d -> d.filePath }) { index, disc ->
                        RowButton(
                            label = stringResource(
                                R.string.dual_detail_disc_label,
                                disc.discNumber
                            ),
                            subtitle = disc.fileName,
                            focused = focusIndex == index,
                            onClick = { onSelect(index) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmulatorPickerItem(
    name: String,
    version: String?,
    isSelected: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    RowButton(
        onClick = onClick,
        focused = isSelected
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.Bold
                    else FontWeight.Normal,
                color = theme.textPrimary
            )
            if (version != null) {
                Text(
                    text = version,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textDim
                )
            }
        }
        if (isCurrent) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = theme.focusAccent,
                modifier = Modifier.size(Dimens.iconSm)
            )
        }
    }
}

@Composable
private fun DualCollectionModalContent(
    items: List<DualCollectionItem>,
    focusIndex: Int,
    showCreateDialog: Boolean,
    onToggle: (Long) -> Unit,
    onShowCreate: () -> Unit,
    onCreate: (String) -> Unit,
    onCreateDismiss: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .touchOnly { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        GlassPanel(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .touchOnly { }
        ) {
            Column(modifier = Modifier.padding(Dimens.spacingLg)) {
                Text(
                    text = stringResource(R.string.dual_detail_collection_heading),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = theme.textPrimary,
                    modifier = Modifier.padding(bottom = Dimens.spacingMd)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
                    contentPadding = PaddingValues(vertical = Dimens.spacingXs)
                ) {
                    itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                        RowButton(
                            onClick = { onToggle(item.id) },
                            focused = focusIndex == index
                        ) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = theme.textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            if (item.isInCollection) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = theme.focusAccent,
                                    modifier = Modifier.size(Dimens.iconSm)
                                )
                            }
                        }
                    }

                    item(key = "create") {
                        RowButton(
                            onClick = onShowCreate,
                            focused = focusIndex == items.size
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                tint = theme.focusAccent,
                                modifier = Modifier.size(Dimens.iconSm)
                            )
                            Spacer(modifier = Modifier.width(Dimens.spacingSm))
                            Text(
                                text = stringResource(R.string.dual_detail_collection_create),
                                style = MaterialTheme.typography.bodyLarge,
                                color = theme.focusAccent
                            )
                        }
                    }
                }
            }
        }

        if (showCreateDialog) {
            CreateCollectionDialog(
                onDismiss = onCreateDismiss,
                onCreate = onCreate,
                gamepadInput = false
            )
        }
    }
}

@Composable
private fun StatePreviewDisplay(
    screenshotPath: String,
    entry: UnifiedStateEntry?,
    coverPath: String?,
    footerHints: @Composable () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            val file = File(screenshotPath)
            if (file.exists()) {
                AsyncImage(
                    model = file,
                    contentDescription = entry?.displayName(context)
                        ?: stringResource(R.string.dual_detail_state_preview_description),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (coverPath != null) {
                AsyncImage(
                    model = File(coverPath),
                    contentDescription = stringResource(
                        R.string.dual_detail_state_preview_cover_fallback
                    ),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (entry != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.surfaceRaised.copy(alpha = 0.9f))
                    .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingSm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.displayName(context),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = theme.textPrimary
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = entry.timestampFormatted,
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.textDim
                    )
                    Text(
                        text = entry.sizeFormatted(context),
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.textDim
                    )
                    val syncLabel = when (entry.syncStatus) {
                        UnifiedStateEntry.SyncStatus.SYNCED ->
                            stringResource(R.string.dual_detail_state_sync_synced)
                        UnifiedStateEntry.SyncStatus.PENDING_UPLOAD ->
                            stringResource(R.string.dual_detail_state_sync_pending)
                        UnifiedStateEntry.SyncStatus.SERVER_ONLY ->
                            stringResource(R.string.dual_detail_state_sync_server)
                        UnifiedStateEntry.SyncStatus.LOCAL_ONLY ->
                            stringResource(R.string.dual_detail_state_sync_local)
                    }
                    val syncColor = when (entry.syncStatus) {
                        UnifiedStateEntry.SyncStatus.SYNCED -> Color(0xFF4CAF50)
                        else -> theme.textDim
                    }
                    Text(
                        text = "[$syncLabel]",
                        style = MaterialTheme.typography.labelMedium,
                        color = syncColor
                    )
                }
            }
        }

        HorizontalDivider(
            color = theme.hairlineLow
        )

        footerHints()
    }
}

@Composable
private fun DualSaveNamePrompt(
    text: String,
    onTextChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .touchOnly { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        GlassPanel(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .touchOnly { }
        ) {
            Column(
                modifier = Modifier.padding(Dimens.spacingLg),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
            ) {
                Text(
                    text = stringResource(R.string.dual_detail_save_name_heading),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = theme.textPrimary
                )

                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    label = { Text(stringResource(R.string.dual_detail_save_name_field_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ActionButton(
                        label = stringResource(R.string.dual_detail_save_name_cancel),
                        onClick = onDismiss
                    )
                    Spacer(modifier = Modifier.width(Dimens.spacingSm))
                    ActionButton(
                        label = stringResource(R.string.dual_detail_save_name_create),
                        onClick = onConfirm,
                        primary = true
                    )
                }
            }
        }
    }
}
