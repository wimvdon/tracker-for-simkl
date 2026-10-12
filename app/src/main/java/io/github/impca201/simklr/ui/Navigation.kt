package io.github.impca201.simklr.ui

import androidx.compose.runtime.mutableStateListOf
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
        override val key = "search"
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
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No navigator") }
