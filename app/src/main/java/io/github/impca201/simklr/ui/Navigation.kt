package io.github.impca201.simklr.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.impca201.simklr.data.model.MediaType

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

    data class Search(
        val type: MediaType? = null,
    ) : Route {
        override val key = "search/${type?.name}"
    }

    data object Profile : Route {
        override val key = "profile"
    }

    data object Settings : Route {
        override val key = "settings"
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
        is Route.Search -> listOf("search", type?.name.orEmpty(), "", "", "")
        Route.Profile -> listOf("profile", "", "", "", "")
        Route.Settings -> listOf("settings", "", "", "", "")
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
        "search" -> Route.Search(type)
        "profile" -> Route.Profile
        "settings" -> Route.Settings
        else -> null
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No navigator") }
