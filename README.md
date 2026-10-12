# SIMKLr

An Android client for [SIMKL](https://simkl.com), built with Jetpack Compose and Material 3
(dynamic color, light/dark theme).

## Features

- **Floating bottom bar** with **TV Shows**, **Movies**, **Anime**, **Discover** and **Calendar**.
  Re-order and enable/disable destinations in *Settings › Bottom bar*.
- **TV Shows / Anime** shelves:
  - **Up next** – shows you're watching that have an aired, unwatched episode. One tap on ✓ marks
    the next episode watched.
  - **Waiting** – shows you're caught up on, split into *Coming soon* (with the next air date),
    *Still airing · no date yet*, and *Ended · nothing left to watch* (one tap moves them to Completed).
  - **Plan to watch**, **On hold**, **Completed**, **Dropped** – split into *Still airing* and
    *Ended*, with an Airing/Ended badge on every poster.
- **Movies**: Plan to watch, Watched, Dropped.
- **Details**: fanart, ratings (SIMKL, IMDb, MAL…), genres, overview, airing info, trailers,
  recommendations, links (SIMKL, IMDb, TMDB, MAL, AniList…), list status, your 1–10 rating,
  "mark as watched", and the full episode list per season (tap to toggle, long-press to mark
  everything up to that episode, mark a whole season).
- **Discover**: trending today/this week/this month, airing today, premieres, best of all time,
  most voted, and "Surprise me" (random title).
- **Search** across TV shows, anime and movies.
- **Calendar** of upcoming episodes and releases (your lists or everything).
- **Profile** with watch-time statistics per type and status.
- **Notifications** (optional) when a new episode of a show you're watching becomes available.
- Offline cache and efficient sync (`/sync/activities` first, then incremental `/sync/all-items`).

## Signing in

SIMKL's API needs a client ID. Create a free app at
<https://simkl.com/settings/developer/new/> (any name; redirect URI `urn:ietf:wg:oauth:2.0:oob`),
paste its **Client ID** into SIMKLr on first launch, then tap **Sign in with SIMKL** and enter
the shown code on simkl.com (PIN login – no client secret needed).

To bake a client ID into builds, add a `SIMKL_CLIENT_ID` repository secret (CI) or pass
`-PsimklClientId=...` to Gradle.

## Building and releases

`./gradlew assembleRelease` (JDK 21, Android SDK). The **Release** workflow builds and tests every
pull request and push to `main`. Run it from the Actions tab with *Release* = patch/minor/major,
or push a `v1.2.3` tag, to publish a GitHub release with the APK.

There is no release key yet: release APKs are signed with the CI runner's debug key, so uninstall
the previous version before installing a newer one.
