package to.wim.watchlist.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import to.wim.watchlist.data.AppSettings
import to.wim.watchlist.ui.components.rememberApp
import to.wim.watchlist.ui.detail.DetailScreen
import to.wim.watchlist.ui.person.PersonScreen
import to.wim.watchlist.ui.login.LoginScreen
import to.wim.watchlist.ui.main.MainScreen
import to.wim.watchlist.ui.profile.ProfileScreen
import to.wim.watchlist.ui.search.SearchScreen
import to.wim.watchlist.ui.profile.RatedGridScreen
import to.wim.watchlist.ui.profile.RatingsScreen
import to.wim.watchlist.ui.settings.SettingsScreen
import to.wim.watchlist.ui.theme.Motion
import to.wim.watchlist.ui.theme.TrackerTheme

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

    // Material 3 shared-axis X (see Motion.sharedAxisX).
    val axisOffset = with(LocalDensity.current) { 30.dp.roundToPx() }
    CompositionLocalProvider(LocalNavigator provides navigator) {
        AnimatedContent(
            targetState = navigator.current,
            transitionSpec = {
                Motion.sharedAxisX(forward = navigator.stack.contains(initialState), offsetPx = axisOffset)
            },
            contentKey = { it.key },
            label = "navigation",
        ) { route ->
            stateHolder.SaveableStateProvider(route.key) {
                when (route) {
                    Route.Main -> MainScreen(settings)
                    is Route.Detail -> DetailScreen(route, settings)
                    Route.Search -> SearchScreen(settings.typeList, settings.tmdbKey)
                    Route.Profile -> ProfileScreen(settings.types)
                    Route.Settings -> SettingsScreen(settings)
                    Route.Ratings -> RatingsScreen(settings.typeList)
                    is Route.RatedGrid -> RatedGridScreen(route.type)
                    is Route.Person -> PersonScreen(route, settings)
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
