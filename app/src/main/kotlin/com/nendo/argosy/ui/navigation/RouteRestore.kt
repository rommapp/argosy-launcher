package com.nendo.argosy.ui.navigation

import android.net.Uri
import androidx.navigation.NavBackStackEntry

object RouteRestore {
    private val unrestorable = setOf(
        Screen.FirstRun.route,
        Screen.Doodle.route,
        Screen.AvatarDoodle.route,
        Screen.PostEditor.route
    )

    fun concreteRoute(pattern: String, args: Map<String, String>, encode: (String) -> String): String? {
        if (pattern in unrestorable) return null
        val path = pattern.substringBefore('?').split('/').map { segment ->
            val name = placeholderName(segment) ?: return@map segment
            encode(args[name] ?: return null)
        }.joinToString("/")
        val params = pattern.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.mapNotNull { param ->
            val name = placeholderName(param.substringAfter('=')) ?: return@mapNotNull param
            args[name]?.let { "${param.substringBefore('=')}=${encode(it)}" }
        }
        return if (params.isEmpty()) path else "$path?${params.joinToString("&")}"
    }

    /**
     * The routes to navigate, in order, to rebuild [route] on top of home: its implicit parents
     * followed by the route itself. Empty when [route] is home.
     */
    fun restoreStack(route: String): List<String> {
        val segments = route.substringBefore('?').split('/')
        val parents = when (segments.first()) {
            Screen.ROUTE_HOME -> return emptyList()
            Screen.ROUTE_COLLECTION_DETAIL -> listOf(Screen.Collections.route)
            Screen.ROUTE_VIRTUAL_BROWSER -> if (segments.size > 2) {
                listOf(Screen.Collections.route, "${Screen.ROUTE_VIRTUAL_BROWSER}/${segments[1]}")
            } else {
                listOf(Screen.Collections.route)
            }
            Screen.ROUTE_SAVE_TIMELINE -> listOf("${Screen.ROUTE_GAME_DETAIL}/${segments[1]}")
            Screen.ROUTE_MEDIA_DETAIL -> listOf(Screen.MediaLibrary.route)
            Screen.ROUTE_NOW_PLAYING, Screen.ROUTE_MUSIC_STATS -> listOf(Screen.Music.route)
            Screen.ROUTE_SOCIAL -> if (segments.size > 1) listOf(Screen.Social.route) else emptyList()
            else -> emptyList()
        }
        return parents + route
    }

    private fun placeholderName(text: String): String? =
        text.takeIf { it.length > 2 && it.startsWith("{") && it.endsWith("}") }?.substring(1, text.length - 1)
}

fun NavBackStackEntry.concreteRoute(): String? {
    val pattern = destination.route ?: return null
    val bundle = arguments
    val args = destination.arguments.mapNotNull { (name, argument) ->
        val value = bundle?.let { argument.type[it, name] } ?: return@mapNotNull null
        name to value.toString()
    }.toMap()
    return RouteRestore.concreteRoute(pattern, args, Uri::encode)
}
