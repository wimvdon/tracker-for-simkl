# Tracker for Simkl

An unofficial Android app for tracking TV shows, anime and movies with [Simkl](https://simkl.com),
built with Jetpack Compose and Material 3 (dynamic color, light/dark theme). Not affiliated with
or endorsed by Simkl; "Simkl" is Simkl's trademark and only describes the service the app works with.

## Features

- **Floating bottom bar** with **TV Shows**, **Movies**, **Anime**, **Discover** and **Calendar**.
  Re-order and enable/disable destinations in *Settings › Bottom bar*.
- **TV Shows / Anime** shelves: **Up next** (shows with an aired, unwatched episode; one tap on ✓
  marks the next episode watched; episodes in progress come first), **Watched** (caught up or
  completed, with the next air date when known), **Plan to watch**, **On hold** and **Dropped**.
  Airing/Ended badges on every poster.
- **Sort & filter**: one row of dropdown chips above each tab (kept per tab): sort (recently
  watched, recently added, title, my rating, release date), status (still airing / ended, or
  released / upcoming for movies), my rating and, for anime, the type.
- **In progress**: paused episodes and movies (SIMKL playback) show their progress; marking one
  watched asks first, because it removes the saved progress.
- **Continue from the latest episode** (setting): Up next starts after the most recently watched
  episode, so skipped earlier episodes or seasons don't keep a show in Up next. (SIMKL's API has
  no "skip" option.)
- **Movies**: Plan to watch, Watched, Dropped.
- **Details**: fanart, ratings (SIMKL, IMDb, MAL…), genres, overview, airing info, trailers,
  recommendations, links (SIMKL, IMDb, TMDB, MAL, AniList…), list status, your 1–10 rating,
  "mark as watched", and the full episode list per season (tap to toggle, long-press to mark
  everything up to that episode, mark a whole season).
- **Discover** with TV Shows / Anime / Movies tabs: trending today/this week/this month, airing
  today, premieres, best of all time, most voted, and a "Surprise me" (random title) button.
- **Search** across TV shows, anime and movies.
- **Calendar** of upcoming episodes and releases of titles in your lists, with type chips.
- **Profile** with watch-time statistics per type and status.
- **Notifications** (optional) when a new episode of a show you're watching airs. They come from
  SIMKL's calendar data files (data.simkl.in, a CDN outside the API quota) checked against the
  library as last synced; nothing syncs with the API in the background.
- **Syncing** happens when the app is opened or comes back to the foreground (at most every 15
  minutes) and on pull-to-refresh, as SIMKL's API rules ask.
- **Languages**: English, Dutch, French, German, Spanish and Italian (follows the system or
  Android 13+'s per-app language setting).
- Offline cache and efficient sync (`/sync/activities` first, then incremental `/sync/all-items`).

## Navigation

Following Material 3: the floating bottom bar holds the top-level destinations; **tabs** switch
between parallel sections of a destination (library shelves, Discover's media types); **chips**
sort and filter the content you are looking at (library sort/filters, Calendar and Search types).
Search and your profile are in every destination's top bar.

## Signing in

Tap **Sign in with SIMKL**: simkl.com opens in the browser, and after you approve, SIMKL sends you
back to the app. That's all; nobody needs a SIMKL developer account.

The app uses SIMKL's recommended sign-in for mobile apps, the OAuth 2.0 authorization code flow
with PKCE: `/oauth2/authorize` with an S256 `code_challenge` and the redirect URI
`trackerforsimkl://oauth`, then `POST /oauth2/token` with the `code_verifier`, scope
`media:read media:write`, no client secret. The week-long access token is renewed with the
refresh token.

The app's own SIMKL client ID is baked in at build time with `-PsimklClientId=...`; the GitHub
workflows take it from the `SIMKL_CLIENT_ID` Actions variable. (A client ID isn't a secret, per
SIMKL's API rules.) Its SIMKL API app must be AUTH V2 with `trackerforsimkl://oauth` as redirect
URI. Builds without a client ID start, but can't sign in.

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
