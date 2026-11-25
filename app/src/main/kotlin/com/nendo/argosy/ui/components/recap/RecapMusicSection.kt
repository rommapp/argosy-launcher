package com.nendo.argosy.ui.components.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.DigestMusic
import com.nendo.argosy.ui.screens.music.rememberMusicArtModel
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.generated.ComponentDefaults

@Composable
fun RecapMusicSection(music: DigestMusic, short: Boolean) {
    val theme = LocalArgosyTheme.current
    val separator = stringResource(R.string.music_library_separator)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingLg)
    ) {
        if (!short && music.topAlbum != null) AlbumCover(music)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
            Text(
                text = pluralStringResource(R.plurals.music_stats_count_plays, music.playCount, music.playCount),
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textDim,
                maxLines = 1
            )
            music.topTrack?.let { track ->
                FactLine(
                    label = stringResource(R.string.music_stats_recap_top_track),
                    value = listOfNotNull(track.title, track.artistCredit).joinToString(separator)
                )
            }
            music.topAlbum?.let { album ->
                val title = album.title ?: album.trackGameTitle
                if (title != null) {
                    FactLine(label = stringResource(R.string.music_stats_recap_top_album), value = title)
                }
            }
            if (!short) {
                music.topArtist?.let { artist ->
                    FactLine(label = stringResource(R.string.music_stats_recap_top_artist), value = artist.name)
                }
            }
        }
    }
}

@Composable
private fun AlbumCover(music: DigestMusic) {
    val theme = LocalArgosyTheme.current
    val model = rememberMusicArtModel(music.topAlbumArt)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(ComponentDefaults.MusicStats.recapCoverDp.dp * LocalUiScale.current.scale)
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .background(theme.surfaceRaised)
    ) {
        if (model == null) {
            Icon(
                imageVector = Icons.Filled.Album,
                contentDescription = null,
                tint = theme.textMute,
                modifier = Modifier.size(Dimens.iconMd)
            )
        } else {
            AsyncImage(
                model = model,
                contentDescription = music.topAlbum?.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun FactLine(label: String, value: String) {
    val theme = LocalArgosyTheme.current
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textDim,
            maxLines = 1
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}
