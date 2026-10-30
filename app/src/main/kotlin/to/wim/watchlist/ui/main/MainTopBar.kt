package to.wim.watchlist.ui.main

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.res.stringResource
import to.wim.watchlist.R
import to.wim.watchlist.ui.LocalNavigator
import to.wim.watchlist.ui.Route
import to.wim.watchlist.ui.components.AvatarButton
import to.wim.watchlist.ui.components.rememberApp

/** Top bar of the bottom bar destinations: title, search and the profile avatar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val app = rememberApp()
    val navigator = LocalNavigator.current
    val avatar by produceState<String?>(null) { value = runCatching { app.account.profile().avatar }.getOrNull() }
    TopAppBar(
        title = { Text(title) },
        actions = {
            actions()
            IconButton(onClick = { navigator.push(Route.Search) }) {
                Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.screens_search_placeholder))
            }
            AvatarButton(avatar) { navigator.push(Route.Profile) }
        },
        scrollBehavior = scrollBehavior,
    )
}
