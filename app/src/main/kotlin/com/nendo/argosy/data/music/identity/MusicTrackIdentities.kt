package com.nendo.argosy.data.music.identity

import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameFileEntity
import com.nendo.argosy.data.remote.romm.RomMMusicTrack
import com.nendo.argosy.domain.model.MusicSourceKind
import com.nendo.argosy.domain.model.MusicTrack
import com.nendo.argosy.domain.model.MusicTrackIdentity
import com.nendo.argosy.domain.music.MusicKeys

object MusicTrackIdentities {

    fun forLibraryTrack(track: MusicTrack, accounts: MusicAccounts): MusicTrackIdentity = when (track.source) {
        MusicSourceKind.NAVIDROME -> MusicTrackIdentity(
            source = MusicSourceKind.NAVIDROME.token,
            sourceAccountKey = accounts.navidromeAccountKey,
            sourceTrackId = track.id,
            trackKey = track.key,
            albumKey = track.albumId?.let { MusicKeys.albumKey(MusicSourceKind.NAVIDROME.token, it) },
            isrc = track.isrc,
            musicBrainzId = track.musicBrainzId,
            artistCredit = track.artist,
            albumTitle = track.album,
            title = track.title,
            durationSec = track.durationSec
        )
        MusicSourceKind.ROMM -> MusicTrackIdentity(
            source = MusicSourceKind.ROMM.token,
            sourceAccountKey = accounts.rommAccountKey,
            sourceTrackId = track.romFileId?.toString() ?: track.id,
            trackKey = track.romFileId?.let(MusicKeys::rommTrackKey) ?: track.key,
            albumKey = track.albumId?.let { MusicKeys.albumKey(MusicSourceKind.ROMM.token, it) },
            romId = track.romId,
            romFileId = track.romFileId,
            artistCredit = track.artist,
            albumTitle = track.album,
            title = track.title,
            durationSec = track.durationSec
        )
    }

    fun forRommTrack(track: RomMMusicTrack, title: String, accounts: MusicAccounts): MusicTrackIdentity =
        MusicTrackIdentity(
            source = MusicSourceKind.ROMM.token,
            sourceAccountKey = accounts.rommAccountKey,
            sourceTrackId = track.romFileId.toString(),
            trackKey = MusicKeys.rommTrackKey(track.romFileId),
            albumKey = MusicKeys.rommRomAlbumKey(track.romId),
            romId = track.romId,
            romFileId = track.romFileId,
            artistCredit = track.artist?.takeIf { it.isNotBlank() },
            albumTitle = track.album?.takeIf { it.isNotBlank() } ?: track.gameName?.takeIf { it.isNotBlank() },
            title = title,
            durationSec = track.durationSeconds?.toInt()
        )

    fun forDeviceFile(
        path: String,
        file: GameFileEntity?,
        game: GameEntity?,
        title: String,
        accounts: MusicAccounts
    ): MusicTrackIdentity {
        val romFileId = file?.rommFileId
        val albumKey = game?.let { found ->
            found.rommId?.let(MusicKeys::rommRomAlbumKey) ?: MusicKeys.rommGameAlbumKey(found.id)
        }
        return if (romFileId != null) {
            MusicTrackIdentity(
                source = MusicSourceKind.ROMM.token,
                sourceAccountKey = accounts.rommAccountKey,
                sourceTrackId = romFileId.toString(),
                trackKey = MusicKeys.rommTrackKey(romFileId),
                albumKey = albumKey,
                romId = game?.rommId,
                romFileId = romFileId,
                albumTitle = game?.title,
                title = title,
                durationSec = file.durationSeconds?.toInt()
            )
        } else {
            MusicTrackIdentity(
                source = MusicTrackIdentity.SOURCE_LOCAL,
                sourceAccountKey = MusicTrackIdentity.SOURCE_LOCAL,
                sourceTrackId = path,
                trackKey = MusicKeys.localTrackKey(path),
                albumKey = albumKey,
                romId = game?.rommId,
                albumTitle = game?.title,
                title = title,
                durationSec = file?.durationSeconds?.toInt()
            )
        }
    }
}
