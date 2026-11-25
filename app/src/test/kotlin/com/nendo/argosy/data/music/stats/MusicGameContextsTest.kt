package com.nendo.argosy.data.music.stats

import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.domain.model.MusicContextTagKind
import org.junit.Assert.assertEquals
import org.junit.Test

class MusicGameContextsTest {

    private fun game(
        igdbId: Long? = null,
        rommId: Long? = null,
        genres: String? = null,
        genre: String? = null,
        franchises: String? = null,
        collections: String? = null
    ) = GameEntity(
        id = 42L,
        title = "Breath of the Wild",
        sortTitle = "breath of the wild",
        platformId = 1L,
        platformSlug = "switch",
        rommId = rommId,
        igdbId = igdbId,
        localPath = null,
        source = GameSource.ROMM_SYNCED,
        genre = genre,
        genres = genres,
        franchises = franchises,
        collections = collections
    )

    @Test
    fun `game key prefers igdb, then the romm server and id, then the local row`() {
        assertEquals("igdb:7346", MusicGameContexts.gameKey(game(igdbId = 7346, rommId = 9), "romm.local"))
        assertEquals("romm:romm.local:9", MusicGameContexts.gameKey(game(rommId = 9), "romm.local"))
        assertEquals("local:42", MusicGameContexts.gameKey(game(), "romm.local"))
    }

    @Test
    fun `tags split the comma lists and fold their keys`() {
        val tags = MusicGameContexts.tags(
            game(genres = "Adventure, Role-playing (RPG)", franchises = "The Legend of Zelda", collections = "Zelda ,Open World")
        )

        assertEquals(
            listOf(
                MusicGameTag(MusicContextTagKind.GENRE, "adventure", "Adventure"),
                MusicGameTag(MusicContextTagKind.GENRE, "role-playing (rpg)", "Role-playing (RPG)"),
                MusicGameTag(MusicContextTagKind.FRANCHISE, "the legend of zelda", "The Legend of Zelda"),
                MusicGameTag(MusicContextTagKind.COLLECTION, "zelda", "Zelda"),
                MusicGameTag(MusicContextTagKind.COLLECTION, "open world", "Open World")
            ),
            tags
        )
    }

    @Test
    fun `franchise falls back to the collections and genre falls back to the single genre`() {
        val tags = MusicGameContexts.tags(game(genre = "Puzzle", collections = "Tetris"))

        assertEquals(
            listOf(
                MusicGameTag(MusicContextTagKind.GENRE, "puzzle", "Puzzle"),
                MusicGameTag(MusicContextTagKind.FRANCHISE, "tetris", "Tetris"),
                MusicGameTag(MusicContextTagKind.COLLECTION, "tetris", "Tetris")
            ),
            tags
        )
    }

    @Test
    fun `a game with no metadata has no tags`() {
        assertEquals(emptyList<MusicGameTag>(), MusicGameContexts.tags(game(genres = " , ")))
    }
}
