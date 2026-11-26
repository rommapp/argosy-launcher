package com.nendo.argosy.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteRestoreTest {

    private val identity: (String) -> String = { it }

    @Test
    fun `path arguments fill the pattern`() {
        assertEquals("game/42", RouteRestore.concreteRoute(Screen.GameDetail.route, mapOf("gameId" to "42"), identity))
    }

    @Test
    fun `absent query arguments are dropped`() {
        val route = RouteRestore.concreteRoute(Screen.Library.route, mapOf("platformId" to "7"), identity)
        assertEquals("library?platformId=7", route)
    }

    @Test
    fun `a route with no query arguments set has no query`() {
        assertEquals("media_library", RouteRestore.concreteRoute(Screen.MediaLibrary.ROUTE_WITH_ARGS, emptyMap(), identity))
    }

    @Test
    fun `arguments are encoded`() {
        val route = RouteRestore.concreteRoute(
            Screen.VirtualCategory.route,
            mapOf("type" to "genre", "category" to "Beat 'em up"),
            { it.replace(" ", "%20") }
        )
        assertEquals("virtual/genre/Beat%20'em%20up", route)
    }

    @Test
    fun `a missing path argument yields no route`() {
        assertNull(RouteRestore.concreteRoute(Screen.GameDetail.route, emptyMap(), identity))
    }

    @Test
    fun `flows that cannot resume are never carried`() {
        assertNull(RouteRestore.concreteRoute(Screen.FirstRun.route, emptyMap(), identity))
        assertNull(RouteRestore.concreteRoute(Screen.AvatarDoodle.route, emptyMap(), identity))
        assertNull(RouteRestore.concreteRoute(Screen.PostEditor.route, emptyMap(), identity))
    }

    @Test
    fun `home rebuilds nothing`() {
        assertEquals(emptyList<String>(), RouteRestore.restoreStack("home"))
    }

    @Test
    fun `a top-level screen sits directly on home`() {
        assertEquals(listOf("settings?platformId=-1"), RouteRestore.restoreStack("settings?platformId=-1"))
        assertEquals(listOf("game/42"), RouteRestore.restoreStack("game/42"))
    }

    @Test
    fun `nested screens rebuild their parents first`() {
        assertEquals(listOf("collections", "collection/3"), RouteRestore.restoreStack("collection/3"))
        assertEquals(listOf("collections", "virtual/genre"), RouteRestore.restoreStack("virtual/genre"))
        assertEquals(
            listOf("collections", "virtual/genre", "virtual/genre/rpg"),
            RouteRestore.restoreStack("virtual/genre/rpg")
        )
        assertEquals(listOf("game/42", "save_timeline/42"), RouteRestore.restoreStack("save_timeline/42"))
        assertEquals(listOf("media_library", "media_item/abc"), RouteRestore.restoreStack("media_item/abc"))
        assertEquals(listOf("social", "social/profile/u1"), RouteRestore.restoreStack("social/profile/u1"))
        assertEquals(listOf("social"), RouteRestore.restoreStack("social"))
    }

    @Test
    fun `music screens rebuild the music browser beneath them`() {
        assertEquals(listOf("music", "now_playing"), RouteRestore.restoreStack("now_playing"))
        assertEquals(listOf("music", "music_stats"), RouteRestore.restoreStack("music_stats"))
        assertEquals(listOf("music"), RouteRestore.restoreStack("music"))
    }
}
