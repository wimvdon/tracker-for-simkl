package io.github.impca201.simklr.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.impca201.simklr.data.AppSettings
import io.github.impca201.simklr.ui.components.rememberApp
import io.github.impca201.simklr.ui.detail.DetailScreen
import io.github.impca201.simklr.ui.login.LoginScreen
import io.github.impca201.simklr.ui.main.MainScreen
import io.github.impca201.simklr.ui.profile.ProfileScreen
import io.github.impca201.simklr.ui.search.SearchScreen
import io.github.impca201.simklr.ui.settings.SettingsScreen
import io.github.impca201.simklr.ui.theme.SimklrTheme

@Composable
fun AppRoot(
    requested: Route.Detail?,
    onRequestHandled: () -> Unit,
) {
    val app = rememberApp()
    val settings by app.settings.settings.collectAsState(initial = null)
    val current = settings ?: return Box(Modifier.fillMaxSize()) // DataStore loads in a few ms.

    SimklrTheme(mode = current.theme, dynamicColor = current.dynamicColor) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (current.clientId.isBlank() || current.accessToken == null) {
                LoginScreen(current)
            } else {
                LoggedIn(current, requested, onRequestHandled)
            }
        }
    }
}

@Composable
private fun LoggedIn(
    settings: AppSettings,
    requested: Route.Detail?,
    onRequestHandled: () -> Unit,
) {
    val app = rememberApp()
    val navigator = rememberSaveable(saver = Navigator.Saver) { Navigator() }
    val stateHolder = rememberSaveableStateHolder()

    // Sync whenever the app comes to the foreground, so changes made elsewhere (paused episodes,
    // other apps and devices) show up without pulling to refresh.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { app.library.sync() }
    }
    LaunchedEffect(requested) {
        if (requested != null) {
            navigator.push(requested)
            onRequestHandled()
        }
    }
    BackHandler(enabled = navigator.stack.size > 1) { navigator.pop() }

    CompositionLocalProvider(LocalNavigator provides navigator) {
        AnimatedContent(
            targetState = navigator.current,
            transitionSpec = {
                val forward = navigator.stack.contains(initialState)
                val enter = slideInHorizontally(tween(300)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(300))
                val exit = slideOutHorizontally(tween(300)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(200))
                enter togetherWith exit
            },
            contentKey = { it.key },
            label = "navigation",
        ) { route ->
            stateHolder.SaveableStateProvider(route.key) {
                when (route) {
                    Route.Main -> MainScreen(settings)
                    is Route.Detail -> DetailScreen(route, settings)
                    is Route.Search -> SearchScreen(route.type)
                    Route.Profile -> ProfileScreen()
                    Route.Settings -> SettingsScreen(settings)
                }
            }
        }
    }
    // Forget the saved state of screens that were popped.
    val keys = navigator.stack.map { it.key }
    val knownKeys = remember { mutableSetOf<String>() }
    LaunchedEffect(keys) {
        knownKeys.filterNot { it in keys }.forEach { stateHolder.removeState(it) }
        knownKeys.clear()
        knownKeys.addAll(keys)
    }
}
