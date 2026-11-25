package com.nendo.argosy.data.music.stats

import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.domain.model.MusicContextTagKind
import com.nendo.argosy.domain.music.MusicNames

data class MusicGameTag(val kind: MusicContextTagKind, val tagKey: String, val label: String)

/**
 * Game identity and tags for listen contexts. Game key precedence: `igdb:<id>`, then
 * `romm:<server>:<rommId>`, then `local:<gameId>`. Franchise tags come from the IGDB franchises
 * and fall back to the collections when a game has none.
 */
object MusicGameContexts {
    private const val TAG_SEPARATOR = ','

    fun gameKey(game: GameEntity, rommServerKey: String): String =
        game.igdbId?.let { "igdb:$it" }
            ?: game.rommId?.let { "romm:$rommServerKey:$it" }
            ?: "local:${game.id}"

    fun tags(game: GameEntity): List<MusicGameTag> {
        val genres = labels(game.genres).ifEmpty { labels(game.genre) }
        val collections = labels(game.collections)
        val franchises = labels(game.franchises).ifEmpty { collections }
        return (
            genres.map { tag(MusicContextTagKind.GENRE, it) } +
                franchises.map { tag(MusicContextTagKind.FRANCHISE, it) } +
                collections.map { tag(MusicContextTagKind.COLLECTION, it) }
            ).filter { it.tagKey.isNotEmpty() }.distinctBy { it.kind to it.tagKey }
    }

    private fun labels(joined: String?): List<String> =
        joined?.split(TAG_SEPARATOR)?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

    private fun tag(kind: MusicContextTagKind, label: String) = MusicGameTag(kind, MusicNames.key(label), label)
}
