package com.nendo.argosy.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class BoxArtRouteTest {

    private val spine = "/cache/psp/covers/box_spine_1_a.jpg"
    private val box3d = "/cache/psp/covers/box_3d_1_b.png"
    private val cover = "/cache/psp/covers/cover_1_c.jpg"

    @Test
    fun `with every asset the spine render leads, then the 3d image, the cover and the title`() {
        assertEquals(
            listOf(BoxArtRoute.SPINE_RENDER, BoxArtRoute.BOX_3D_IMAGE, BoxArtRoute.FLAT_COVER, BoxArtRoute.TEXT),
            boxArtRoutes(useBoxArt = true, spine, box3d, cover)
        )
    }

    @Test
    fun `a failed spine render falls to the 3d image, then the flat cover`() {
        val routes = boxArtRoutes(useBoxArt = true, spine, box3d, cover)

        assertEquals(BoxArtRoute.BOX_3D_IMAGE, routes.firstWorking(setOf(BoxArtRoute.SPINE_RENDER)))
        assertEquals(
            BoxArtRoute.FLAT_COVER,
            routes.firstWorking(setOf(BoxArtRoute.SPINE_RENDER, BoxArtRoute.BOX_3D_IMAGE))
        )
    }

    @Test
    fun `the title shows only once every image route has failed`() {
        val routes = boxArtRoutes(useBoxArt = true, spine, box3d, cover)
        val imageRoutes = setOf(BoxArtRoute.SPINE_RENDER, BoxArtRoute.BOX_3D_IMAGE, BoxArtRoute.FLAT_COVER)

        imageRoutes.forEach { spared ->
            assertEquals(spared, routes.firstWorking(imageRoutes - spared))
        }
        assertEquals(BoxArtRoute.TEXT, routes.firstWorking(imageRoutes))
    }

    @Test
    fun `the spine render needs both the spine and the cover on disk`() {
        val remoteCover = "https://romm/cover.png"

        assertEquals(BoxArtRoute.BOX_3D_IMAGE, boxArtRoutes(true, spine, box3d, remoteCover).first())
        assertEquals(BoxArtRoute.BOX_3D_IMAGE, boxArtRoutes(true, "https://romm/side.png", box3d, cover).first())
        assertEquals(BoxArtRoute.BOX_3D_IMAGE, boxArtRoutes(true, null, box3d, cover).first())
    }

    @Test
    fun `without box art the flat cover leads and the 3d image stands in for a missing cover`() {
        assertEquals(
            listOf(BoxArtRoute.FLAT_COVER, BoxArtRoute.BOX_3D_IMAGE, BoxArtRoute.TEXT),
            boxArtRoutes(useBoxArt = false, spine, box3d, cover)
        )
        assertEquals(BoxArtRoute.BOX_3D_IMAGE, boxArtRoutes(false, spine, box3d, null).first())
    }

    @Test
    fun `a failed flat cover never falls to the spine render`() {
        listOf(true, false).forEach { useBoxArt ->
            val routes = boxArtRoutes(useBoxArt, spine, box3d, cover)
            val afterCover = routes.drop(routes.indexOf(BoxArtRoute.FLAT_COVER) + 1)
            assertEquals(false, BoxArtRoute.SPINE_RENDER in afterCover)
        }
    }

    @Test
    fun `where the spine render is not allowed the 3d image leads`() {
        assertEquals(
            listOf(BoxArtRoute.BOX_3D_IMAGE, BoxArtRoute.FLAT_COVER, BoxArtRoute.TEXT),
            boxArtRoutes(useBoxArt = true, spine, box3d, cover, allowSpineRender = false)
        )
    }

    @Test
    fun `a game with no art at all draws the title`() {
        assertEquals(listOf(BoxArtRoute.TEXT), boxArtRoutes(true, null, null, null))
        assertEquals(listOf(BoxArtRoute.TEXT), boxArtRoutes(true, null, "", ""))
    }
}
