package com.nendo.argosy.ui.navigation

sealed class Screen(val route: String) {
    data object FirstRun : Screen("first_run")
    data object Showcase : Screen("showcase")
    data object Library : Screen("library?platformId={platformId}&source={source}") {
        fun createRoute(platformId: Long? = null, source: String? = null): String {
            val params = mutableListOf<String>()
            if (platformId != null) params.add("platformId=$platformId")
            if (source != null) params.add("source=$source")
            return if (params.isEmpty()) "library" else "library?${params.joinToString("&")}"
        }
    }
    data object Collections : Screen("collections")
    data object CollectionDetail : Screen("collection/{collectionId}") {
        fun createRoute(collectionId: Long) = "collection/$collectionId"
    }
    data object VirtualBrowser : Screen("virtual/{type}") {
        fun createRoute(type: String) = "virtual/$type"
    }
    data object VirtualCategory : Screen("virtual/{type}/{category}") {
        fun createRoute(type: String, category: String) = "virtual/$type/${java.net.URLEncoder.encode(category, "UTF-8")}"
    }
    data object Downloads : Screen("downloads")
    data object Apps : Screen("apps")
    data object Settings : Screen("settings?section={section}&action={action}") {
        fun createRoute(section: String? = null, action: String? = null): String {
            val params = mutableListOf<String>()
            if (section != null) params.add("section=$section")
            if (action != null) params.add("action=$action")
            return if (params.isEmpty()) "settings" else "settings?${params.joinToString("&")}"
        }
    }
    data object GameDetail : Screen("game/{gameId}") {
        fun createRoute(gameId: Long) = "game/$gameId"
    }
    data object Search : Screen("search")

    companion object {
        const val ROUTE_SHOWCASE = "showcase"
        const val ROUTE_LIBRARY = "library"
        const val ROUTE_COLLECTIONS = "collections"
        const val ROUTE_COLLECTION_DETAIL = "collection"
        const val ROUTE_VIRTUAL_BROWSER = "virtual"
        const val ROUTE_GAME_DETAIL = "game"
        const val ROUTE_SETTINGS = "settings"
        const val ROUTE_DOWNLOADS = "downloads"
        const val ROUTE_APPS = "apps"
        const val ROUTE_SEARCH = "search"
    }
}
