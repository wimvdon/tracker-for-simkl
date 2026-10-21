package io.github.impca201.trackerforsimkl.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.impca201.trackerforsimkl.R
import io.github.impca201.trackerforsimkl.data.api.TmdbApi
import io.github.impca201.trackerforsimkl.data.model.CastMember
import io.github.impca201.trackerforsimkl.data.model.CrewJob
import io.github.impca201.trackerforsimkl.data.model.CrewMember
import io.github.impca201.trackerforsimkl.ui.components.openUrl
import io.github.impca201.trackerforsimkl.ui.library.SectionHeader

/** "Cast" on a details page: photos, names and roles from TMDB. A tap opens the person on TMDB. */
@Composable
fun CastRow(cast: List<CastMember>) {
    val context = LocalContext.current
    PeopleRow(stringResource(R.string.detail_cast), cast) { person ->
        Person(person.name, person.character, person.photo) { context.openUrl(TmdbApi.personUrl(person.personId)) }
    }
}

/** "Crew": directors, writers and other key jobs, from TMDB. */
@Composable
fun CrewRow(crew: List<CrewMember>) {
    val context = LocalContext.current
    PeopleRow(stringResource(R.string.detail_crew), crew) { person ->
        val jobs = person.jobs.map { stringResource(it.label) }.joinToString(", ")
        Person(person.name, jobs, person.photo) { context.openUrl(TmdbApi.personUrl(person.personId)) }
    }
}

private val CrewJob.label: Int
    get() =
        when (this) {
            CrewJob.Director -> R.string.detail_fact_director
            CrewJob.Writer -> R.string.detail_job_writer
            CrewJob.Story -> R.string.detail_job_story
            CrewJob.Novel -> R.string.detail_job_novel
            CrewJob.Music -> R.string.detail_job_music
            CrewJob.Camera -> R.string.detail_job_camera
        }

@Composable
private fun <T> PeopleRow(
    title: String,
    people: List<T>,
    content: @Composable (T) -> Unit,
) {
    Column {
        SectionHeader(title, modifier = Modifier.padding(horizontal = 8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(people) { content(it) }
        }
    }
}

@Composable
private fun Person(
    name: String,
    role: String?,
    photo: String?,
    onClick: () -> Unit,
) {
    Column(
        Modifier.width(96.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier.size(80.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Person, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            if (photo != null) {
                AsyncImage(photo, null, contentScale = ContentScale.Crop, modifier = Modifier.size(80.dp).clip(CircleShape))
            }
        }
        // Fixed line counts, so the row keeps one height while it scrolls.
        Text(
            name,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            role.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
