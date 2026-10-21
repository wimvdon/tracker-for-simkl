package io.github.impca201.trackerforsimkl.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.impca201.trackerforsimkl.data.model.MediaType

/** Screens above the main (bottom bar) screen. */
sealed interface Route {
    /** Unique per entry, so the same screen can be on the stack twice with its own state. */
    val key: String

    data object Main : Route {
        override val key = "main"
    }

    data class Detail(
        val type: MediaType,
        val simklId: Long,
        val title: String? = null,
        val poster: String? = null,
    ) : Route {
        override val key = "detail/$type/$simklId"
    }

    /** Always opens on "All", whichever tab it was opened from. */
    data object Search : Route {
        override val key = "search"
    }

    data object Profile : Route {
        override val key = "profile"
    }

    data object Settings : Route {
        override val key = "settings"
    }

    /** "My ratings", from the profile. */
    data object Ratings : Route {
        override val key = "ratings"
    }

    /** A person from a title's cast or crew (TMDB), with their filmography. */
    data class Person(
        val personId: Long,
        val name: String? = null,
        val photo: String? = null,
    ) : Route {
        override val key = "person/$personId"
    }

    /** All rated titles of one type, from a row of "My ratings". */
    data class RatedGrid(
        val type: MediaType,
    ) : Route {
        override val key = "rated/$type"
    }
}

class Navigator {
    val stack = mutableStateListOf<Route>(Route.Main)

    val current: Route get() = stack.last()

    fun push(route: Route) {
        // Re-opening a screen that is already on the stack goes back to it.
        val existing = stack.indexOfFirst { it.key == route.key }
        if (existing >= 0) {
            while (stack.size > existing + 1) stack.removeAt(stack.lastIndex)
        } else {
            stack.add(route)
        }
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun openDetail(
        type: MediaType,
        simklId: Long,
        title: String? = null,
        poster: String? = null,
    ) = push(Route.Detail(type, simklId, title, poster))

    companion object {
        /** Keeps the back stack across configuration changes (rotation, theme) and process death. */
        val Saver: Saver<Navigator, Any> =
            listSaver<Navigator, String>(
                save = { navigator -> navigator.stack.flatMap { it.encode() } },
                restore = { saved ->
                    Navigator().apply {
                        val routes = saved.chunked(5).mapNotNull { decodeRoute(it) }
                        if (routes.firstOrNull() == Route.Main) {
                            stack.clear()
                            stack.addAll(routes)
                        }
                    }
                },
            )
    }
}

private fun Route.encode(): List<String> =
    when (this) {
        Route.Main -> listOf("main", "", "", "", "")
        is Route.Detail -> listOf("detail", type.name, simklId.toString(), title.orEmpty(), poster.orEmpty())
        Route.Search -> listOf("search", "", "", "", "")
        Route.Profile -> listOf("profile", "", "", "", "")
        Route.Settings -> listOf("settings", "", "", "", "")
        Route.Ratings -> listOf("ratings", "", "", "", "")
        is Route.RatedGrid -> listOf("rated", type.name, "", "", "")
        is Route.Person -> listOf("person", "", personId.toString(), name.orEmpty(), photo.orEmpty())
    }

private fun decodeRoute(parts: List<String>): Route? {
    if (parts.size < 5) return null
    val type = MediaType.entries.firstOrNull { it.name == parts[1] }
    return when (parts[0]) {
        "main" -> Route.Main
        "detail" ->
            Route.Detail(
                type = type ?: return null,
                simklId = parts[2].toLongOrNull() ?: return null,
                title = parts[3].ifEmpty { null },
                poster = parts[4].ifEmpty { null },
            )
        "search" -> Route.Search
        "profile" -> Route.Profile
        "settings" -> Route.Settings
        "ratings" -> Route.Ratings
        "rated" -> Route.RatedGrid(type ?: return null)
        "person" -> Route.Person(parts[2].toLongOrNull() ?: return null, parts[3].ifEmpty { null }, parts[4].ifEmpty { null })
        else -> null
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No navigator") }
