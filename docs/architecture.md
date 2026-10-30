# Architecture

## Overview

Watchlist is a single-module, single-activity Android app. Compose renders state; repositories own the state, the disk cache and every SIMKL call. There is no server of its own: the app talks directly to SIMKL's API and static data files.

```
 MainActivity ─ AppRoot ─ Navigator (stack of Route)
                   │            ├─ MainScreen (bottom bar: TV Shows, Movies, Anime, Discover, Calendar)
                   │            ├─ DetailScreen, SearchScreen, ProfileScreen (› RatingsScreen › RatedGridScreen), SettingsScreen
                   ▼
 WatchlistApp (Application, one instance)
   ├─ OkHttpClient (50 MB HTTP cache) ─── also used by Coil for images (wsrv.nl)
   ├─ SettingsStore   DataStore: tokens, sign-in state, settings, notified and remembered episodes
   ├─ SimklApi        api.simkl.com + data.simkl.in, headers, token refresh
   ├─ LibraryRepository   library StateFlow, incremental sync, JSON snapshot files
   ├─ CatalogRepository   details, episodes, search, Discover sections, random title
   └─ AccountRepository   profile and statistics
 NewEpisodesWorker (WorkManager, optional) ── reads calendar files + cached library
 AuthRedirectActivity ── receives trackerforsimkl://oauth and hands the URI to the login screen
```

## Components

| Component | Responsibility |
|---|---|
| `WatchlistApp` | Creates the shared HTTP client, API client and repositories lazily; provides Coil's image loader and WorkManager's configuration; installs `CrashReporter` before library start-up providers run. |
| `SimklApi` | The only class that builds HTTP requests to SIMKL. Adds `simkl-api-key`, `client_id`, `app-name`, `app-version` and the bearer token; leaves data.simkl.in URLs untouched (SIMKL asks for no query strings there). Refreshes AUTH V2 tokens 12 hours before expiry under a mutex. |
| `TmdbApi`, `TmdbParsers` | Read-only TMDB client with the user's own key (v3 key as `api_key`, v4 token as bearer), and the mapping of credits to `Credits` (cast in billing order, crew limited to key jobs, one entry per person). See `docs/api.md`. |
| `Parsers` | Maps `JsonElement` responses to the app's models by hand. Pure Kotlin, unit tested against SIMKL's documented examples. |
| `LibraryRepository` | Holds `LibraryState` (items, show info, calendar, playback, "continue from latest" positions). Loads the cache from `library.json` / `show_info.json`, syncs, applies changes optimistically and writes files atomically (temp file + rename). |
| `CatalogRepository` | Stateless reads for screens: details, episodes, search, Discover sections (API lists and data.simkl.in trending files), random title, TMDB cast and crew and people (cached in memory), and the SIMKL title of a TMDB credit. |
| `EpisodeStore` | Episode lists kept on the device (`episodes.json`) for the details screen and "continue from latest"; downloaded again only as `SyncRules.episodesDue` allows or after the show was in a sync delta. |
| `AccountRepository` | The user's profile, kept in `profile.json` until the sync sees `settings.all` move; statistics. |
| `SettingsStore` | DataStore Preferences wrapper over two files (see "Backup"). A corrupt file is replaced with empty preferences instead of crashing. |
| `Navigator` | A `mutableStateListOf<Route>` back stack. Pushing a route already on the stack pops back to it. |
| `NewEpisodesWorker` | Every 4 hours (network required), only while signed in with notifications on: downloads the calendar files (movie releases too when movies are on, matched by `NewReleases` against the Plan to watch list, "Out now"; a failed movie file doesn't hold up the episodes) and lets `NewEpisodes` match them against the cached library. It posts one notification per show with newly aired, unwatched episodes ("S01E05 is out", or "3 new episodes, up to S01E07"), with the show's poster (thumbnail) and backdrop (expanded) from wsrv.nl. Upcoming episodes of watched shows are remembered, so a check that runs days late (phone off, battery saver) still announces episodes from the last 7 days; at most five shows per check, the others follow at the next. After each check, one extra check is scheduled 15 minutes after the next remembered episode airs (`NewEpisodes.nextAirDate`), so a notification doesn't wait for the 4-hourly check; Android can still delay it a little (Doze). Air dates are parsed with SIMKL's offset (`Dates.parse`), so they are moments in time and the phone's time zone only changes how they are shown. Every app start re-registers the check (`restore`), because Android drops scheduled work when an app is force-stopped. |
| Notification channels | One per type (`NotifyChannel`): TV keeps the id `new_episodes` of earlier versions so the user's settings for it stay, anime and movies get their own. Created at every start, which also renames them into the phone's language. *Settings › Notifications* reads Android's state (`AndroidNotifications`: the app switch and each channel) whenever it comes into view, shows it, and opens Android's channel or app page to change it, since apps can't turn a blocked channel back on. |
| `CrashReporter` | Saves an uncaught exception's stack trace to `files/last_crash.txt` and shows it once on the next start, with a Copy button. Nothing is sent. |

## Data flow

**Sync.** On start and when the app returns to the foreground, `syncIfStale()` syncs if the last sync is older than 15 minutes; pull-to-refresh calls `sync()` directly. Only one sync runs at a time: a trigger while one is running waits for it and gets its result (a change queues one more after it). See `docs/api.md`, "Keeping the number of calls down".

1. `POST /sync/activities` returns timestamps per type.
2. No previous sync (or a forced one): `GET /sync/all-items/` loads everything.
3. Unchanged `all` timestamp: nothing more (playback only if its timestamp moved).
4. Otherwise: `GET /sync/all-items/?date_from=<last all>` for changed items; `?extended=simkl_ids_only` only when `removed_from_list` changed; `/sync/ratings/?date_from=` only when `rated_at` changed.
5. Shows in the delta lose their kept episode list (`EpisodeStore.forget`). With "Continue from the latest episode" on, shows whose watched count changed (watched in another app) get their episode list first (`Shelves.changedLatestLookups`), so they don't jump into Up next for a moment.
6. The snapshot is saved, together with a fresh `GET /sync/playback` when a `playback` timestamp moved. A moved `settings.all` makes `AccountRepository` fetch the profile again the next time it is shown. Then calendars, community ratings (once a day) and "next after latest" (from the kept episode lists) refresh in the background.

While `syncIfStale()` refreshes an outdated cache (`LibraryState.refreshingStale`), the Up next tab shows its loading placeholder instead of the cached list, so episodes watched elsewhere don't show briefly and then disappear. Other tabs show the cache at once. If the sync fails (offline), the cached list comes back.

**Changes** (mark watched, rate, list status, check-in) patch the in-memory state first, so the screen updates at once, then call the API inside `LibraryRepository.change`. Success schedules a quick incremental sync; a failure rethrows (the screen shows it) and starts a forced full sync, which replaces the optimistic change with SIMKL's real state.

**Images** are `https://wsrv.nl/?url=https://simkl.in/...` URLs loaded by Coil with the shared OkHttp client and cached on disk.

## Patterns

- **Repository + unidirectional state:** screens collect `StateFlow`s and call suspend functions; no ViewModels. State that must survive is in the repositories (process-wide), screen-local state uses `remember`/`rememberSaveable`.
- **Offline first:** the snapshot is the source of truth for the UI; the network only updates it.
- **Manual JSON mapping** instead of `@Serializable` API models: SIMKL's responses vary by endpoint and `extended` level, and hand mapping tolerates missing fields without crashing. The on-disk snapshot does use `@Serializable` classes, because the app controls that format.

## Why this design

- SIMKL's API rules ask clients to check `/sync/activities` first and to sync on open/refresh rather than in the background; the sync and worker designs follow those rules directly.
- One shared OkHttp client gives a single connection pool and HTTP cache for API calls and images.
- No dependency-injection framework or navigation library: the object graph is five objects and the navigation is a stack of five route types, so the frameworks would add more code than they remove (see [decisions.md](decisions.md)).

## Backup

Android's own backup (to the Google account) and phone-to-phone transfer copy only the
`backed_up_settings` DataStore file: theme, wallpaper colours, bottom bar order and tabs, the
media types shown (*Settings › Content*), hide specials and "continue from latest". The rules are an allow list, so nothing else is ever copied
(`res/xml/data_extraction_rules.xml` for Android 12+, `res/xml/backup_rules.xml` for Android 8-11,
guarded by `BackupRulesTest`).

| Stays on this phone | Why |
|---|---|
| `settings` DataStore: tokens, pending sign-in, TMDB key, notifications on/off, notified and remembered episodes | The sign-in and the TMDB key are credentials and must not leave the phone. Notifications need the permission and a scheduled check on the new phone, which a restore doesn't bring. |
| `library.json`, `show_info.json`, `profile.json`, `episodes.json` | Downloaded again after signing in. |
| `last_crash.txt`, HTTP and image caches | Only about this phone. |

The file names are in `DataFiles` (`SettingsStore.kt`). Up to version 1.3.1 all settings lived
in the `settings` file; `MoveKeysMigration` moves the backed-up ones over the first time the
settings are read after the update.
