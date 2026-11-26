package com.nendo.argosy.ui.screens.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.MusicAlbum
import com.nendo.argosy.domain.model.MusicArt
import com.nendo.argosy.domain.model.MusicColumn
import com.nendo.argosy.domain.model.MusicColumnKind
import com.nendo.argosy.ui.dualscreen.PresentationSlot
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import java.util.Locale

private const val SHOWCASE_SLEEVES = 3

/**
 * What the presentation screen shows beside the Music browser and Your listening: the focused
 * column, the album an opened column has in front, or one listening item.
 */
sealed interface MusicShowcaseState {
    data class Column(
        val column: MusicColumn,
        val stack: MusicStackUi?,
        val offline: Boolean
    ) : MusicShowcaseState

    data class Contents(
        val column: MusicColumn,
        val front: List<MusicAlbum>,
        val albumIndex: Int,
        val albumCount: Int,
        val isFlat: Boolean
    ) : MusicShowcaseState

    data class Item(
        val kicker: String?,
        val title: String,
        val subtitle: String?,
        val facts: String?,
        val art: MusicArt?,
        val backdropPath: String?
    ) : MusicShowcaseState
}

/**
 * The showcase slot for the browser, or null while it has nothing to describe: no sources, an
 * error, an empty or loading library, or a column whose contents failed to load. A column still
 * loading its contents keeps its column showcase.
 */
fun MusicUiState.showcaseSlot(): PresentationSlot.MusicShowcase? {
    val opened = contents
    if (opened != null) {
        if (opened.loadFailed) return null
        if (opened.isLoading) {
            return PresentationSlot.MusicShowcase(MusicShowcaseState.Column(opened.column, null, page?.offline == true))
        }
        val front = if (opened.isFlat) emptyList() else opened.albums.drop(opened.albumIndex).take(SHOWCASE_SLEEVES)
        return PresentationSlot.MusicShowcase(
            MusicShowcaseState.Contents(
                column = opened.column,
                front = front,
                albumIndex = opened.albumIndex,
                albumCount = opened.albums.size,
                isFlat = opened.isFlat
            )
        )
    }
    if (!showsColumns) return null
    val column = focusedColumn ?: return null
    return PresentationSlot.MusicShowcase(MusicShowcaseState.Column(column, focusedStack, page?.offline == true))
}

@Composable
fun MusicShowcase(state: MusicShowcaseState, bottomInset: Dp) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (state) {
            is MusicShowcaseState.Item -> {
                val backdrop = state.backdropPath?.let { MusicArt.Local(it) } ?: state.art
                backdrop?.let { BlurredHero(it) }
            }
            else -> state.backdrop?.let { BlurredHero(it) }
        }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = Dimens.spacingXl,
                    end = Dimens.spacingXl,
                    top = Dimens.headerHeight,
                    bottom = bottomInset + Dimens.spacingXl
                )
        ) {
            val wide = maxWidth / maxHeight >= ComponentDefaults.HomeColumns.wideAspectMin
            val artWeight = ComponentDefaults.MusicColumns.showcaseStackWeightRatio
            if (wide) {
                Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(artWeight).fillMaxHeight()) { ShowcaseArt(state) }
                    Spacer(modifier = Modifier.width(Dimens.spacingXl))
                    Column(
                        modifier = Modifier.weight(1f - artWeight),
                        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
                    ) { ShowcaseText(state) }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(artWeight).fillMaxWidth()) { ShowcaseArt(state) }
                    Spacer(modifier = Modifier.height(Dimens.spacingLg))
                    Column(
                        modifier = Modifier.weight(1f - artWeight).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
                    ) { ShowcaseText(state) }
                }
            }
        }
    }
}

private val MusicShowcaseState.backdrop: MusicArt?
    get() = when (this) {
        is MusicShowcaseState.Column -> stack?.front?.art ?: column.heroArt ?: column.sleeves.firstOrNull()
        is MusicShowcaseState.Contents -> if (isFlat) {
            column.heroArt ?: column.sleeves.firstOrNull()
        } else {
            front.firstOrNull()?.art
        }
        is MusicShowcaseState.Item -> art
    }

private data class ShowcaseSleeves(
    val sleeves: List<MusicArt>,
    val titles: List<String>,
    val hiddenCount: Int,
    val sizeRatio: Float
)

private fun showcaseSleeves(state: MusicShowcaseState): ShowcaseSleeves = when (state) {
    is MusicShowcaseState.Column -> {
        val stack = state.stack
        val shown = stack?.albums?.drop(stack.index)?.take(SHOWCASE_SLEEVES)?.filter { it.art != null }
        ShowcaseSleeves(
            sleeves = shown?.mapNotNull { it.art }
                ?: state.column.sleeves.take(SHOWCASE_SLEEVES).ifEmpty { listOfNotNull(state.column.photo ?: state.column.heroArt) },
            titles = shown?.map { it.title } ?: state.column.sleeveTitles,
            hiddenCount = if (stack == null) state.column.hiddenAlbumCount else 0,
            sizeRatio = ComponentDefaults.MusicColumns.sleeveSizeRatio
        )
    }
    is MusicShowcaseState.Contents -> {
        val shown = if (state.isFlat) null else state.front.filter { it.art != null }
        ShowcaseSleeves(
            sleeves = shown?.mapNotNull { it.art } ?: state.column.sleeves.take(SHOWCASE_SLEEVES),
            titles = shown?.map { it.title } ?: state.column.sleeveTitles,
            hiddenCount = 0,
            sizeRatio = ComponentDefaults.MusicColumns.contentsSleeveRatio
        )
    }
    is MusicShowcaseState.Item -> ShowcaseSleeves(
        sleeves = listOfNotNull(state.art),
        titles = listOf(state.title),
        hiddenCount = 0,
        sizeRatio = ComponentDefaults.MusicColumns.sleeveSizeRatio
    )
}

@Composable
private fun BoxScope.ShowcaseArt(state: MusicShowcaseState) {
    val shown = remember(state) { showcaseSleeves(state) }
    if (shown.sleeves.isNotEmpty()) {
        MusicSleeveStack(
            sleeves = shown.sleeves,
            titles = shown.titles,
            hiddenCount = shown.hiddenCount,
            sizeRatio = shown.sizeRatio,
            centerBias = 0f
        )
    } else if (state is MusicShowcaseState.Column) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxHeight(shown.sizeRatio)
                .aspectRatio(1f)
        ) {
            MusicNoArtGround(column = state.column, large = true)
        }
    }
}

@Composable
private fun ColumnScope.ShowcaseText(state: MusicShowcaseState) {
    when (state) {
        is MusicShowcaseState.Column -> ColumnText(state)
        is MusicShowcaseState.Contents -> ContentsText(state)
        is MusicShowcaseState.Item -> ShowcaseLines(
            kicker = state.kicker,
            title = state.title,
            facts = state.facts,
            detail = state.subtitle,
            note = null
        )
    }
}

@Composable
private fun ColumnText(state: MusicShowcaseState.Column) {
    val column = state.column
    val frontAlbum = state.stack?.front
    val frontTitle = frontAlbum?.title ?: column.sleeveTitles.firstOrNull()?.takeIf { column.kind != MusicColumnKind.ALBUM }
    val detail = frontTitle?.let { title ->
        listOfNotNull(title, frontAlbum?.year?.toString()).joinToString(stringResource(R.string.music_library_showcase_separator))
    }
    ShowcaseLines(
        kicker = stringResource(column.kind.kickerRes),
        title = musicColumnTitle(column),
        facts = musicColumnFacts(column),
        detail = detail,
        note = if (state.offline) stringResource(R.string.music_library_showcase_offline) else null
    )
}

@Composable
private fun ContentsText(state: MusicShowcaseState.Contents) {
    val separator = stringResource(R.string.music_library_showcase_separator)
    val album = state.front.firstOrNull().takeIf { !state.isFlat }
    val facts = if (album == null) {
        musicColumnFacts(state.column)
    } else {
        buildList {
            album.artist?.let(::add)
            album.year?.let { add(it.toString()) }
            if (album.trackCount > 0) {
                add(pluralStringResource(R.plurals.music_library_count_tracks, album.trackCount, album.trackCount))
            }
            if (album.durationSec > 0) add(formatMusicDuration(album.durationSec))
        }.joinToString(separator)
    }
    val position = if (album != null && state.albumCount > 1) {
        stringResource(R.string.music_library_showcase_album_position, state.albumIndex + 1, state.albumCount)
    } else {
        null
    }
    ShowcaseLines(
        kicker = musicColumnTitle(state.column),
        title = album?.title ?: musicColumnTitle(state.column),
        facts = facts.ifEmpty { null },
        detail = position,
        note = null
    )
}

@Composable
private fun ShowcaseLines(kicker: String?, title: String, facts: String?, detail: String?, note: String?) {
    val theme = LocalArgosyTheme.current
    kicker?.takeIf { it.isNotEmpty() }?.let {
        Text(
            text = it.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelMedium,
            color = theme.textDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        color = theme.textPrimary,
        maxLines = ComponentDefaults.MusicColumns.showcaseTitleMaxLines,
        overflow = TextOverflow.Ellipsis
    )
    facts?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    detail?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    note?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = theme.textDim
        )
    }
}
