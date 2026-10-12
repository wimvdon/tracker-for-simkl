package io.github.impca201.simklr.ui.main

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
import androidx.compose.ui.text.font.FontWeight
import io.github.impca201.simklr.data.model.MediaType
import io.github.impca201.simklr.ui.LocalNavigator
import io.github.impca201.simklr.ui.Route
import io.github.impca201.simklr.ui.components.AvatarButton
import io.github.impca201.simklr.ui.components.rememberApp

/** Top bar of the bottom bar destinations: title, search and the profile avatar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    searchType: MediaType? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val app = rememberApp()
    val navigator = LocalNavigator.current
    val avatar by produceState<String?>(null) { value = runCatching { app.account.profile().avatar }.getOrNull() }
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        actions = {
            actions()
            IconButton(onClick = { navigator.push(Route.Search(searchType)) }) {
                Icon(Icons.Rounded.Search, contentDescription = "Search")
            }
            AvatarButton(avatar) { navigator.push(Route.Profile) }
        },
        scrollBehavior = scrollBehavior,
    )
}
