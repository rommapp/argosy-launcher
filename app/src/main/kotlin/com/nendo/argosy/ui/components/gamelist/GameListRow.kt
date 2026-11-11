package com.nendo.argosy.ui.components.gamelist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.data.social.FriendActivity
import com.nendo.argosy.domain.model.SaveListState
import com.nendo.argosy.ui.common.GameListDetails
import com.nendo.argosy.ui.common.RunningGameUi
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.components.GameTitle
import com.nendo.argosy.ui.components.RunningLineLabels
import com.nendo.argosy.ui.components.RunningSubLine
import com.nendo.argosy.ui.components.platformBadgeLabel
import com.nendo.argosy.ui.components.friends.SocialAvatar
import com.nendo.argosy.ui.screens.home.GameDownloadIndicator
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalBoxArtStyle
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.util.clickableNoFocus
import com.nendo.argosy.ui.util.focusBorder

private val GAME_LIST_RUNNING_LABELS = RunningLineLabels(
    running = R.string.gamelist_row_running,
    runningIn = R.string.gamelist_row_running_in,
    saving = R.string.gamelist_row_saving
)

private const val PLACEHOLDER_ICON_ALPHA = 0.5f
private const val PLACEHOLDER_ICON_FILL = 0.5f

internal enum class SaveMarkTone { MUTED, ACCENT, ERROR }

internal val SaveListState.markTone: SaveMarkTone
    get() = when (this) {
        SaveListState.SYNCED -> SaveMarkTone.MUTED
        SaveListState.LOCAL_AHEAD, SaveListState.SERVER_AHEAD -> SaveMarkTone.ACCENT
        SaveListState.NEEDS_ATTENTION -> SaveMarkTone.ERROR
    }

@Composable
fun GameListRow(
    title: String,
    platformSlug: String,
    platformDisplayName: String,
    coverPath: String?,
    details: GameListDetails,
    isDownloaded: Boolean,
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: () -> Unit = {},
    needsInstall: Boolean = false,
    downloadIndicator: GameDownloadIndicator = GameDownloadIndicator.NONE,
    friends: List<FriendActivity> = emptyList(),
    saveState: SaveListState? = null,
    onCoverLoadFailed: ((String) -> Unit)? = null,
    running: RunningGameUi? = null
) {
    val cornerRadius = LocalBoxArtStyle.current.cornerRadiusDp
    val focusWidth = ComponentDefaults.GameListRow.focusBorderWidth.dp
    GameListRowCard(
        isFocused = isFocused,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.height(ComponentDefaults.GameListRow.rowHeight.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            GameListRowBody(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                titleLine = {
                    TitleLine(
                        title = title,
                        saveState = saveState,
                        downloadIndicator = downloadIndicator,
                        isDownloaded = isDownloaded,
                        needsInstall = needsInstall
                    )
                },
                progress = {
                    if (running != null) {
                        RunningSubLine(running = running, labels = GAME_LIST_RUNNING_LABELS)
                    } else {
                        ProgressStats(details = details)
                    }
                },
                friends = { FriendStack(friends = friends) },
                tabs = {
                    GameListRowTabs(
                        platformDisplayName = platformBadgeLabel(platformSlug, platformDisplayName),
                        details = details,
                        cornerRadius = cornerRadius,
                        focusInset = if (isFocused) focusWidth else 0.dp
                    )
                },
                rating = { RatingGroup(details = details) }
            )
            GameListCover(title = title, coverPath = coverPath, onLoadFailed = onCoverLoadFailed)
        }
    }
}

@Composable
internal fun CompactGameListRow(
    title: String,
    isDownloaded: Boolean,
    isFocused: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    needsInstall: Boolean = false,
    downloadIndicator: GameDownloadIndicator = GameDownloadIndicator.NONE,
    saveState: SaveListState? = null
) {
    GameListRowCard(
        isFocused = isFocused,
        onClick = onClick,
        onLongClick = onLongClick,
        containerColor = if (isFocused) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ComponentDefaults.GameListRow.compactRowMinHeight.dp)
                .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs),
            contentAlignment = Alignment.CenterStart
        ) {
            TitleLine(
                title = title,
                saveState = saveState,
                downloadIndicator = downloadIndicator,
                isDownloaded = isDownloaded,
                needsInstall = needsInstall
            )
        }
    }
}

@Composable
private fun GameListRowCard(
    isFocused: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(LocalBoxArtStyle.current.cornerRadiusDp)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .focusBorder(
                isFocused,
                MaterialTheme.colorScheme.primary,
                ComponentDefaults.GameListRow.focusBorderWidth.dp,
                shape
            )
            .clickableNoFocus(onClick = onClick, onLongClick = onLongClick),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        content()
    }
}

@Composable
private fun GameListRowBody(
    titleLine: @Composable () -> Unit,
    progress: @Composable () -> Unit,
    friends: @Composable () -> Unit,
    tabs: @Composable () -> Unit,
    rating: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val padding = Dimens.spacingSm
    val lineGap = Dimens.spacingXs
    val footerHeight = ComponentDefaults.GameListRow.footerHeight.dp
    Layout(
        contents = listOf(titleLine, progress, friends, tabs, rating),
        modifier = modifier
    ) { (titleMeasurables, progressMeasurables, friendMeasurables, tabMeasurables, ratingMeasurables), constraints ->
        val pad = padding.roundToPx()
        val footerPx = footerHeight.roundToPx()
        val width = constraints.maxWidth
        val innerWidth = (width - pad * 2).coerceAtLeast(0)
        val tabsPlaceable = tabMeasurables.firstOrNull()?.measure(Constraints(maxWidth = width))
        val ratingPlaceable = ratingMeasurables.firstOrNull()?.measure(Constraints(maxWidth = innerWidth))
        val ratingInFooter = ratingPlaceable != null &&
            ratingFitsFooter(tabsPlaceable?.width ?: 0, ratingPlaceable.width, pad, width - pad)
        val bodyRating = ratingPlaceable?.takeUnless { ratingInFooter }
        val titlePlaceable = titleMeasurables.firstOrNull()
            ?.measure(Constraints(minWidth = innerWidth, maxWidth = innerWidth))
        val friendsPlaceable = friendMeasurables.firstOrNull()?.measure(Constraints(maxWidth = innerWidth))
        val trailingWidth = listOfNotNull(friendsPlaceable, bodyRating).sumOf { it.width + pad }
        val progressPlaceable = progressMeasurables.firstOrNull()
            ?.measure(Constraints(maxWidth = (innerWidth - trailingWidth).coerceAtLeast(0)))
        val titleHeight = titlePlaceable?.height ?: 0
        val lineTwo = listOfNotNull(progressPlaceable, friendsPlaceable, bodyRating)
        val lineTwoHeight = lineTwo.maxOfOrNull { it.height } ?: 0
        val lineTwoTop = pad + titleHeight + lineGap.roundToPx()
        val tabsHeight = tabsPlaceable?.height ?: 0
        val height = if (constraints.hasBoundedHeight) {
            constraints.maxHeight
        } else {
            lineTwoTop + lineTwoHeight + tabsHeight
        }
        layout(width, height) {
            titlePlaceable?.place(pad, pad)
            progressPlaceable?.let { it.place(pad, lineTwoTop + (lineTwoHeight - it.height) / 2) }
            var trailingX = width - pad
            friendsPlaceable?.let {
                trailingX -= it.width
                it.place(trailingX, lineTwoTop + (lineTwoHeight - it.height) / 2)
                trailingX -= pad
            }
            bodyRating?.let {
                it.place(trailingX - it.width, lineTwoTop + (lineTwoHeight - it.height) / 2)
            }
            tabsPlaceable?.place(0, height - tabsHeight)
            if (ratingInFooter) {
                ratingPlaceable?.let {
                    it.place(width - pad - it.width, height - footerPx + (footerPx - it.height) / 2)
                }
            }
        }
    }
}

@Composable
private fun TitleLine(
    title: String,
    saveState: SaveListState?,
    downloadIndicator: GameDownloadIndicator,
    isDownloaded: Boolean,
    needsInstall: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        GameTitle(
            title = title,
            titleStyle = MaterialTheme.typography.titleMedium,
            titleColor = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        saveState?.let { SaveStateMark(it) }
        DownloadStateMark(
            indicator = downloadIndicator,
            isDownloaded = isDownloaded,
            needsInstall = needsInstall
        )
    }
}

@Composable
private fun GameListCover(title: String, coverPath: String?, onLoadFailed: ((String) -> Unit)?) {
    GameCoverImage(
        title = title,
        coverPath = coverPath,
        contentScale = ContentScale.Crop,
        onLoadFailed = onLoadFailed,
        modifier = Modifier
            .fillMaxHeight()
            .aspectRatio(LocalBoxArtStyle.current.aspectRatio, matchHeightConstraintsFirst = true)
            .background(MaterialTheme.colorScheme.surface)
    )
}

@Composable
internal fun GameCoverImage(
    title: String,
    coverPath: String?,
    contentScale: ContentScale,
    onLoadFailed: ((String) -> Unit)?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (coverPath != null) {
            AsyncImage(
                model = rememberFileImageModel(coverPath),
                contentDescription = title,
                contentScale = contentScale,
                onError = { onLoadFailed?.invoke(coverPath) },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.Default.SportsEsports,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = PLACEHOLDER_ICON_ALPHA),
                modifier = Modifier.fillMaxSize(PLACEHOLDER_ICON_FILL)
            )
        }
    }
}

@Composable
private fun SaveStateMark(state: SaveListState) {
    val tint = when (state.markTone) {
        SaveMarkTone.MUTED -> MaterialTheme.colorScheme.onSurfaceVariant
        SaveMarkTone.ACCENT -> MaterialTheme.colorScheme.primary
        SaveMarkTone.ERROR -> MaterialTheme.colorScheme.error
    }
    val description = when (state) {
        SaveListState.SYNCED -> R.string.gamelist_row_cd_save_synced
        SaveListState.LOCAL_AHEAD -> R.string.gamelist_row_cd_save_local_ahead
        SaveListState.SERVER_AHEAD -> R.string.gamelist_row_cd_save_server_ahead
        SaveListState.NEEDS_ATTENTION -> R.string.gamelist_row_cd_save_needs_attention
    }
    Icon(
        imageVector = Icons.Default.Save,
        contentDescription = stringResource(description),
        tint = tint,
        modifier = Modifier.size(Dimens.iconSm)
    )
}

@Composable
private fun DownloadStateMark(
    indicator: GameDownloadIndicator,
    isDownloaded: Boolean,
    needsInstall: Boolean
) {
    val size = Modifier.size(Dimens.iconSm)
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when {
        indicator.isDownloading || indicator.isExtracting -> CircularProgressIndicator(
            progress = { indicator.progress.coerceIn(0f, 1f) },
            modifier = size,
            color = primary,
            strokeWidth = Dimens.borderMedium
        )
        indicator.isPaused -> Icon(
            Icons.Default.PauseCircle,
            contentDescription = stringResource(R.string.gamelist_row_cd_download_paused),
            tint = muted,
            modifier = size
        )
        indicator.isQueued -> Icon(
            Icons.Default.HourglassEmpty,
            contentDescription = stringResource(R.string.gamelist_row_cd_download_queued),
            tint = muted,
            modifier = size
        )
        needsInstall -> Icon(
            Icons.Default.InstallMobile,
            contentDescription = stringResource(R.string.gamelist_row_cd_needs_install),
            tint = primary,
            modifier = size
        )
        isDownloaded -> Icon(
            Icons.Default.CheckCircle,
            contentDescription = stringResource(R.string.gamelist_row_cd_downloaded),
            tint = primary,
            modifier = size
        )
    }
}

@Composable
private fun FriendStack(friends: List<FriendActivity>) {
    if (friends.isEmpty()) return
    val shown = friends.sortedByDescending { it.playingNow }
        .take(ComponentDefaults.GameListRow.maxFriendAvatars)
    val avatarSize = ComponentDefaults.GameListRow.friendAvatarSize.dp
    val step = avatarSize * (1f - ComponentDefaults.GameListRow.friendOverlapRatio)
    Box(modifier = Modifier.width(avatarSize + step * (shown.size - 1))) {
        shown.forEachIndexed { index, friend ->
            SocialAvatar(
                displayName = friend.displayName,
                avatarColor = friend.avatarColor,
                avatarPngBase64 = friend.quayPassAvatar,
                userId = friend.friendId,
                size = avatarSize,
                showOnlineDot = friend.playingNow,
                modifier = Modifier
                    .offset(x = step * index)
                    .zIndex((shown.size - index).toFloat())
            )
        }
    }
}
