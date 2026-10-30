package to.wim.watchlist.ui.person

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import to.wim.watchlist.R
import to.wim.watchlist.data.AppSettings
import to.wim.watchlist.data.api.TmdbApi
import to.wim.watchlist.data.api.TmdbKey
import to.wim.watchlist.data.userMessage
import to.wim.watchlist.data.model.Person
import to.wim.watchlist.data.model.PersonCredit
import to.wim.watchlist.ui.LocalNavigator
import to.wim.watchlist.ui.components.Poster
import to.wim.watchlist.ui.components.PosterTile
import to.wim.watchlist.ui.components.openUrl
import to.wim.watchlist.ui.components.rememberApp
import to.wim.watchlist.ui.detail.label
import to.wim.watchlist.ui.library.SectionHeader
import to.wim.watchlist.ui.Route
import to.wim.watchlist.ui.theme.EmptyState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * A person from a title's cast or crew: photo, life dates, biography, best-known titles and
 * filmography, from TMDB. A title opens its details page here, found in the library or on SIMKL.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonScreen(
    route: Route.Person,
    settings: AppSettings,
) {
    val app = rememberApp()
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val library by app.library.state.collectAsState()
    val key = TmdbKey.parse(settings.tmdbKey)

    var person by remember { mutableStateOf<Person?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    // The credit whose SIMKL title is being looked up.
    var opening by remember { mutableStateOf<PersonCredit?>(null) }

    LaunchedEffect(route.personId, key, reload) {
        error = null
        if (key == null) return@LaunchedEffect
        try {
            person = app.catalog.person(route.personId, key)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.userMessage(resources)
        }
    }

    fun open(credit: PersonCredit) {
        credit.match(library.items)?.let { return navigator.openDetail(it.type, it.ids.simkl, it.title, it.poster) }
        if (opening != null) return
        opening = credit
        scope.launch {
            val found = runCatching { app.catalog.simklTitle(credit) }
            opening = null
            val title = found.getOrNull()
            if (title != null) {
                navigator.openDetail(title.type, title.simklId, title.title, title.poster)
            } else {
                val message = resources.getString(if (found.isFailure) R.string.person_lookup_failed else R.string.person_not_on_simkl)
                val result = snackbar.showSnackbar(message, actionLabel = resources.getString(R.string.person_open_tmdb))
                if (result == SnackbarResult.ActionPerformed) context.openUrl(credit.tmdbUrl)
            }
        }
    }

    val name = person?.name ?: route.name.orEmpty()
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }, colors = IconButtonDefaults.filledTonalIconButtonColors()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.detail_back))
                    }
                },
                actions = {
                    IconButton(onClick = { context.openUrl(TmdbApi.personUrl(route.personId)) }) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = stringResource(R.string.person_open_tmdb))
                    }
                },
            )
        },
    ) { innerPadding ->
        val p = person
        when {
            key == null ->
                EmptyState(Icons.Rounded.Person, stringResource(R.string.person_no_key), Modifier.padding(innerPadding))
            p == null && error != null ->
                EmptyState(Icons.Rounded.ErrorOutline, stringResource(R.string.person_load_failed), Modifier.padding(innerPadding), body = error) {
                    Button(onClick = { reload++ }) { Text(stringResource(R.string.detail_try_again)) }
                }
            else ->
                LazyColumn(
                    contentPadding =
                        PaddingValues(
                            top = innerPadding.calculateTopPadding(),
                            bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                        ),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "header") { Header(name, p?.photo ?: route.photo, p) }
                    if (p == null) {
                        item(key = "loading") {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        }
                        return@LazyColumn
                    }
                    p.biography?.let { bio -> item(key = "bio") { Biography(bio) } }
                    val known = p.knownFor()
                    if (known.isNotEmpty()) {
                        item(key = "known") {
                            Column {
                                SectionHeader(stringResource(R.string.person_known_for), modifier = Modifier.padding(horizontal = 8.dp))
                                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    items(known, key = { "${it.movie}:${it.tmdbId}" }) { credit ->
                                        PosterTile(
                                            title = credit.title,
                                            poster = credit.poster,
                                            subtitle = credit.year?.toString(),
                                            inLibrary = credit.match(library.items) != null,
                                            onClick = { open(credit) },
                                            modifier = Modifier.width(120.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (p.credits.isNotEmpty()) {
                        item(key = "films-header") {
                            SectionHeader(stringResource(R.string.person_filmography), count = p.credits.size, modifier = Modifier.padding(horizontal = 8.dp))
                        }
                        items(p.credits, key = { "credit:${it.movie}:${it.tmdbId}" }) { credit ->
                            CreditRow(credit, credit.match(library.items) != null, busy = opening == credit) { open(credit) }
                        }
                    }
                }
        }
    }
}

@Composable
private fun Header(
    name: String,
    photo: String?,
    person: Person?,
) {
    val locale = LocalLocale.current.platformLocale
    val dates = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale) }
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(112.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Person, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            if (photo != null) AsyncImage(photo, null, contentScale = ContentScale.Crop, modifier = Modifier.size(112.dp).clip(CircleShape))
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, style = MaterialTheme.typography.headlineSmall)
            val age = person?.age(LocalDate.now())
            person?.birthday?.let { born ->
                val text =
                    if (person.deathday == null && age != null) {
                        stringResource(R.string.person_born_age, dates.format(born), age)
                    } else {
                        stringResource(R.string.person_born, dates.format(born))
                    }
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
            person?.deathday?.let { died ->
                val text = if (age != null) stringResource(R.string.person_died_age, dates.format(died), age) else stringResource(R.string.person_died, dates.format(died))
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
            person?.placeOfBirth?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Biography(text: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else 6,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = { expanded = !expanded }) {
            Text(stringResource(if (expanded) R.string.detail_show_less else R.string.detail_show_more))
        }
    }
}

@Composable
private fun CreditRow(
    credit: PersonCredit,
    inLibrary: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
) {
    val roles = credit.characters + credit.jobs.map { stringResource(it.label) }
    val episodes = credit.episodes?.takeIf { !credit.movie && it > 0 }?.let { pluralStringResource(R.plurals.person_episodes, it, it) }
    val details = listOfNotNull(credit.year?.toString(), roles.joinToString(" / ").ifEmpty { null }, episodes).joinToString(" · ")
    ListItem(
        headlineContent = { Text(credit.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = details.takeIf { it.isNotEmpty() }?.let { { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
        leadingContent = { Poster(credit.poster, null, Modifier.width(48.dp)) },
        trailingContent =
            when {
                busy -> { { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) } }
                inLibrary -> { { Icon(Icons.Rounded.CheckCircle, stringResource(R.string.person_in_library), tint = MaterialTheme.colorScheme.primary) } }
                else -> null
            },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
