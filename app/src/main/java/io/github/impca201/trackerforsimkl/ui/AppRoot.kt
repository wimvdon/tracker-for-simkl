package io.github.impca201.trackerforsimkl.ui

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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.impca201.trackerforsimkl.data.AppSettings
import io.github.impca201.trackerforsimkl.ui.components.rememberApp
import io.github.impca201.trackerforsimkl.ui.detail.DetailScreen
import io.github.impca201.trackerforsimkl.ui.login.LoginScreen
import io.github.impca201.trackerforsimkl.ui.main.MainScreen
import io.github.impca201.trackerforsimkl.ui.profile.ProfileScreen
import io.github.impca201.trackerforsimkl.ui.search.SearchScreen
import io.github.impca201.trackerforsimkl.ui.settings.SettingsScreen
import io.github.impca201.trackerforsimkl.ui.theme.Motion
import io.github.impca201.trackerforsimkl.ui.theme.TrackerTheme

@Composable
fun AppRoot(
    requested: Route.Detail?,
    onRequestHandled: () -> Unit,
) {
    val app = rememberApp()
    val settings by app.settings.settings.collectAsState(initial = null)
    val current = settings ?: return Box(Modifier.fillMaxSize()) // DataStore loads in a few ms.

    TrackerTheme(mode = current.theme, dynamicColor = current.dynamicColor) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (current.accessToken == null) {
                LoginScreen()
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
    // other apps and devices) show up without pulling to refresh; at most every 15 minutes.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { app.library.syncIfStale() }
    }
    LaunchedEffect(requested) {
        if (requested != null) {
            navigator.push(requested)
            onRequestHandled()
        }
    }
    BackHandler(enabled = navigator.stack.size > 1) { navigator.pop() }

    // Material 3 shared-axis X: both screens slide 30dp along the axis; the old one fades out
    // quickly, then the new one fades in, so they never overlap at full opacity.
    val axisOffset = with(LocalDensity.current) { 30.dp.roundToPx() }
    CompositionLocalProvider(LocalNavigator provides navigator) {
        AnimatedContent(
            targetState = navigator.current,
            transitionSpec = {
                val forward = navigator.stack.contains(initialState)
                val slide = tween<IntOffset>(Motion.MEDIUM_2, easing = Motion.Emphasized)
                val enter =
                    slideInHorizontally(slide) { if (forward) axisOffset else -axisOffset } +
                        fadeIn(tween(210, delayMillis = 90, easing = Motion.EmphasizedDecelerate))
                val exit =
                    slideOutHorizontally(slide) { if (forward) -axisOffset else axisOffset } +
                        fadeOut(tween(90, easing = Motion.EmphasizedAccelerate))
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
