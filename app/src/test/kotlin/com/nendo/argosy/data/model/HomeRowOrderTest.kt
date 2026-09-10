package com.nendo.argosy.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The home row leads with installed favourites, then installed, then favourites, then the rest,
 * the same four tiers GameDao's platform query orders by. A row carrying every game goes by title
 * inside each tier; a capped row keeps whatever order it arrived in inside each tier.
 */
class HomeRowOrderTest {

    private data class Row(
        val title: String,
        val installed: Boolean = false,
        val favorite: Boolean = false
    )

    private object RowProps : SortableProps<Row> {
        override fun isInstalled(item: Row) = item.installed
        override fun isFavorite(item: Row) = item.favorite
        override fun sortTitle(item: Row) = item.title
        override fun rating(item: Row): Float? = null
        override fun userRating(item: Row) = 0
        override fun userDifficulty(item: Row) = 0
        override fun releaseYear(item: Row): Int? = null
        override fun playCount(item: Row) = 0
        override fun playTimeMinutes(item: Row) = 0
        override fun lastPlayedEpochMilli(item: Row): Long? = null
        override fun addedAtEpochMilli(item: Row) = 0L
    }

    private val installedFavoriteB = Row("Bastion", installed = true, favorite = true)
    private val installedFavoriteA = Row("Axiom Verge", installed = true, favorite = true)
    private val installedD = Row("Dead Cells", installed = true)
    private val installedC = Row("Celeste", installed = true)
    private val favoriteF = Row("Fez", favorite = true)
    private val favoriteE = Row("Enter the Gungeon", favorite = true)
    private val plainH = Row("Hades")
    private val plainG = Row("Gris")

    private val shuffled = listOf(
        plainH, favoriteF, installedD, installedFavoriteB,
        plainG, installedC, favoriteE, installedFavoriteA
    )

    @Test
    fun `every game lands in four tiers, alphabetical inside each`() {
        val ordered = orderedForEveryGame(shuffled, RowProps)

        assertEquals(
            listOf(
                installedFavoriteA, installedFavoriteB,
                installedC, installedD,
                favoriteE, favoriteF,
                plainG, plainH
            ),
            ordered
        )
    }

    @Test
    fun `a capped row keeps its incoming order inside each tier`() {
        val ordered = tieredByOwnership(shuffled, RowProps)

        assertEquals(
            listOf(
                installedFavoriteB, installedFavoriteA,
                installedD, installedC,
                favoriteF, favoriteE,
                plainH, plainG
            ),
            ordered
        )
    }

    @Test
    fun `tiers follow the platform query's case order`() {
        assertEquals(0, ownershipTier(installedFavoriteA, RowProps))
        assertEquals(1, ownershipTier(installedC, RowProps))
        assertEquals(2, ownershipTier(favoriteE, RowProps))
        assertEquals(3, ownershipTier(plainG, RowProps))
    }
}
