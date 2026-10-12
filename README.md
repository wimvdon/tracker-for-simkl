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

SIMKL's API needs a client ID. Create a free app at <https://simkl.com/settings/developer/new/>:
choose **AUTH V2** and the **TV, devices & command line** type (any name). Paste its **Client ID**
into SIMKLr on first launch (the Client Secret is not needed), tap **Sign in with SIMKL** and
approve the shown code on simkl.com.

SIMKLr uses SIMKL's OAuth 2.0 device flow (`POST /oauth2/device`, then `POST /oauth2/token`)
with the `media:read media:write` scope, and renews the week-long access token with the refresh
token. Older AUTH V1 apps still work through the PIN flow (`/oauth/pin`), which SIMKL plans to
retire around April 2027.

To bake a client ID into a build, pass `-PsimklClientId=...` to Gradle.

The API usage follows SIMKL's official documentation ([api.simkl.org](https://api.simkl.org),
formerly [github.com/SIMKL/api](https://github.com/SIMKL/api)): the activities-first sync with
`date_from`, `simkl_ids_only` for removals, `/sync/ratings` for rating changes,
`/ratings/{type}?user_watchlist=all` for ended/airing status, the data.simkl.in trending and
calendar files, and images through wsrv.nl.

## Building and releases

`./gradlew assembleRelease` (JDK 21, Android SDK). The **Release** workflow (the same as
[snoozer](https://github.com/impca201/snoozer)'s) publishes a GitHub release with the APK when a
`v1.2.3` tag is pushed or when it is run from the Actions tab with *Release* = patch/minor/major.
Signing uses the `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and
`ANDROID_KEY_PASSWORD` secrets; without them a build-only run makes a debug APK.
