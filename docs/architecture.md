# Architecture

## Overview

Tracker for Simkl is a single-module, single-activity Android app. Compose renders state; repositories own the state, the disk cache and every SIMKL call. There is no server of its own: the app talks directly to SIMKL's API and static data files.

```
 MainActivity ─ AppRoot ─ Navigator (stack of Route)
                   │            ├─ MainScreen (bottom bar: TV Shows, Movies, Anime, Discover, Calendar)
                   │            ├─ DetailScreen, SearchScreen, ProfileScreen, SettingsScreen
                   ▼
 TrackerApp (Application, one instance)
   ├─ OkHttpClient (50 MB HTTP cache) ─── also used by Coil for images (wsrv.nl)
   ├─ SettingsStore   DataStore: tokens, sign-in state, settings, notified episodes
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
| `TrackerApp` | Creates the shared HTTP client, API client and repositories lazily; provides Coil's image loader and WorkManager's configuration; installs `CrashReporter` before library start-up providers run. |
| `SimklApi` | The only class that builds HTTP requests. Adds `simkl-api-key`, `client_id`, `app-name`, `app-version` and the bearer token; leaves data.simkl.in URLs untouched (SIMKL asks for no query strings there). Refreshes AUTH V2 tokens 12 hours before expiry under a mutex. |
| `Parsers` | Maps `JsonElement` responses to the app's models by hand. Pure Kotlin, unit tested against SIMKL's documented examples. |
| `LibraryRepository` | Holds `LibraryState` (items, show info, calendar, playback, "continue from latest" positions). Loads the cache from `library.json` / `show_info.json`, syncs, applies changes optimistically and writes files atomically (temp file + rename). |
| `CatalogRepository` | Stateless reads for screens: details, episodes, search, Discover sections (API lists and data.simkl.in trending files), random title. |
| `SettingsStore` | DataStore Preferences wrapper. A corrupt file is replaced with empty preferences instead of crashing. |
| `Navigator` | A `mutableStateListOf<Route>` back stack. Pushing a route already on the stack pops back to it. |
| `NewEpisodesWorker` | Every 4 hours (network required), only while signed in with notifications on: downloads the calendar files, matches them against the cached library and posts one notification per newly aired, unwatched episode, with the show's poster (thumbnail) and backdrop (expanded) from wsrv.nl. |
| `CrashReporter` | Saves an uncaught exception's stack trace to `files/last_crash.txt` and shows it once on the next start, with a Copy button. Nothing is sent. |

## Data flow

**Sync.** On start and when the app returns to the foreground, `syncIfStale()` syncs if the last sync is older than 15 minutes; pull-to-refresh calls `sync()` directly.

1. `POST /sync/activities` returns timestamps per type.
2. No previous sync (or a forced one): `GET /sync/all-items/` loads everything.
3. Unchanged `all` timestamp: only playback is refreshed.
4. Otherwise: `GET /sync/all-items/?date_from=<last all>` for changed items; `?extended=simkl_ids_only` only when `removed_from_list` changed; `/sync/ratings/?date_from=` only when `rated_at` changed.
5. With "Continue from the latest episode" on, shows whose watched count changed (watched in another app) get their episode list first (`Shelves.changedLatestLookups`), so they don't jump into Up next for a moment.
6. The snapshot is saved together with a fresh `GET /sync/playback`, then calendars, show info and "next after latest" refresh in the background.

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
