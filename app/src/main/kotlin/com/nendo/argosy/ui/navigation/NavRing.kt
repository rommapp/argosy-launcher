package com.nendo.argosy.ui.navigation

import com.nendo.argosy.R
import com.nendo.argosy.ui.DrawerItem

/**
 * The pages reachable from the drawer, in drawer order, and the user-ordered subset that the
 * floating nav bar and shoulder-button paging cycle through.
 *
 * A page token is its route with any query pattern removed. Stored rings hold tokens, never
 * labels, and unknown tokens are dropped on read.
 */
object NavRing {
    val PAGES: List<DrawerItem> = listOf(
        DrawerItem(Screen.Home.route, R.string.ui_drawer_nav_home),
        DrawerItem(Screen.Social.route, R.string.ui_drawer_nav_social),
        DrawerItem(Screen.QuayPass.route, R.string.ui_drawer_nav_quaypass),
        DrawerItem(Screen.Collections.route, R.string.ui_drawer_nav_collections),
        DrawerItem(Screen.Library.route, R.string.ui_drawer_nav_library),
        DrawerItem(Screen.MediaLibrary.route, R.string.ui_drawer_nav_media),
        DrawerItem(Screen.Downloads.route, R.string.ui_drawer_nav_downloads),
        DrawerItem(Screen.SyncMonitor.route, R.string.syncmonitor_drawer_title),
        DrawerItem(Screen.SaveSync.route, R.string.ui_drawer_nav_save_sync),
        DrawerItem(Screen.Apps.route, R.string.ui_drawer_nav_apps),
        DrawerItem(Screen.Running.route, R.string.running_drawer_title),
        DrawerItem(Screen.Settings.route, R.string.ui_drawer_nav_settings)
    )

    val DEFAULT_TOKENS: List<String> = listOf(
        Screen.Home.route,
        Screen.Library.route,
        Screen.Collections.route,
        Screen.Social.route,
        Screen.MediaLibrary.route,
        Screen.Downloads.route,
        Screen.Settings.route
    ).map { token(it) }

    private val homeToken = token(Screen.Home.route)
    private val settingsToken = token(Screen.Settings.route)
    private val knownTokens: Set<String> = PAGES.map { token(it.route) }.toSet()

    fun token(route: String): String = route.substringBefore('?')

    fun routeMatches(destinationRoute: String, currentRoute: String?): Boolean =
        currentRoute != null && token(destinationRoute) == token(currentRoute)

    fun isPinned(token: String): Boolean = token == homeToken || token == settingsToken

    fun page(pageToken: String): DrawerItem? = PAGES.firstOrNull { token(it.route) == pageToken }

    /**
     * Enabled tokens in ring order. A null or unreadable stored value yields [DEFAULT_TOKENS];
     * pinned pages are restored when a stored ring lacks them.
     */
    fun resolve(stored: List<String>?): List<String> {
        val cleaned = (stored ?: DEFAULT_TOKENS).filter { it in knownTokens }.distinct().toMutableList()
        if (homeToken !in cleaned) cleaned.add(0, homeToken)
        if (settingsToken !in cleaned) cleaned.add(settingsToken)
        return cleaned
    }

    fun rows(enabled: List<String>): List<String> =
        enabled + PAGES.map { token(it.route) }.filter { it !in enabled }

    fun toggle(enabled: List<String>, token: String): List<String> = when {
        token !in knownTokens || isPinned(token) -> enabled
        token in enabled -> enabled - token
        else -> enabled + token
    }

    /**
     * Route to switch to for a shoulder press, or null when [currentRoute] is not a drawer page.
     * On a ring page it steps through [ring]. On a page outside the ring it walks [pages] in
     * [delta] direction, wrapping, to the first page that is in the ring.
     */
    fun routeFrom(
        ring: List<DrawerItem>,
        pages: List<DrawerItem>,
        currentRoute: String?,
        delta: Int
    ): String? {
        if (ring.isEmpty()) return null
        val ringIndex = ring.indexOfFirst { routeMatches(it.route, currentRoute) }
        if (ringIndex >= 0) {
            if (ring.size < 2) return null
            return ring[(ringIndex + delta).mod(ring.size)].route
        }
        val start = pages.indexOfFirst { routeMatches(it.route, currentRoute) }
        if (start < 0) return null
        val step = if (delta < 0) -1 else 1
        for (offset in 1..pages.size) {
            val candidate = pages[(start + step * offset).mod(pages.size)]
            if (ring.any { it.route == candidate.route }) return candidate.route
        }
        return null
    }
}
