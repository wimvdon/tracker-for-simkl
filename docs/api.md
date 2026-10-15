# API and entry points

The app has no API of its own. This page lists what it calls (SIMKL) and what other apps can call (deep links).

## SIMKL API

Base URL `https://api.simkl.com`, implemented in `data/api/SimklApi.kt`. Every request sends:

| Where | Name | Value |
|---|---|---|
| Header | `simkl-api-key` | The app's client ID (`BuildConfig.SIMKL_CLIENT_ID`) |
| Header | `Authorization` | `Bearer <access token>` once signed in |
| Header | `User-Agent` | `tracker-for-simkl/<version>` |
| Query | `client_id`, `app-name`, `app-version` | Client ID, `tracker-for-simkl`, version name |

Files on `data.simkl.in` get no extra query parameters, as SIMKL asks.

### Endpoints used

| Method | Path | Used for |
|---|---|---|
| GET (browser) | `https://simkl.com/oauth2/authorize` | Sign-in page (PKCE S256, scope `media:read media:write`) |
| POST (form) | `/oauth2/token` | Exchange the code, refresh the token |
| POST | `/sync/activities` | What changed since the last sync |
| GET | `/sync/all-items/` (`date_from`, `extended=simkl_ids_only`) | Full or incremental library, removal check |
| POST | `/sync/ratings/` (`date_from`) | Changed ratings |
| GET / DELETE | `/sync/playback`, `/sync/playback/{id}` | Paused episodes and movies |
| GET | `/ratings/{tv,anime,movies}?user_watchlist=all` | Airing/ended status and ratings for library titles |
| POST | `/sync/add-to-list` | Set list status |
| POST | `/sync/history`, `/sync/history/remove` | Mark watched / unwatched, remove from lists |
| POST | `/sync/ratings`, `/sync/ratings/remove` | Rate 1–10, clear a rating |
| POST | `/scrobble/checkin` | Check in |
| GET | `/{tv,anime,movies}/{id}?extended=full` | Details |
| GET | `/{tv,anime}/episodes/{id}?extended=full` | Episode list |
| GET | `/search/{tv,anime,movie}?q=` | Search (50 results) |
| POST | `/search/random/` | "Surprise me" |
| GET | `/{type}/best/{filter}`, `/{type}/airing`, `/{type}/premieres/{new,soon}` | Discover sections |
| GET | `https://data.simkl.in/discover/…`, `https://data.simkl.in/calendar/{tv,anime,movie_release}.json` | Trending lists, calendars |
| POST | `/users/settings`, `/users/{id}/stats` | Profile and statistics |

Images are loaded from `https://wsrv.nl/?url=https://simkl.in/...` (the image proxy SIMKL asks apps to use).

### Errors

- A non-2xx response throws `ApiException(code, message)`; `401`/`403` count as "signed out", and the library shows "sign-in expired".
- A build without a client ID throws before any request ("This build has no SIMKL client ID").
- Sync errors are shown in the UI and keep the cached library. Changes are applied to the screen first (optimistically); if the API call fails, the error is shown and a forced full sync puts back SIMKL's state.

### Authentication

OAuth 2.0 authorization code with PKCE (AUTH V2): no client secret. The access token lasts about a week and is refreshed with the refresh token 12 hours before it expires. Tokens are stored in DataStore, in a file that is excluded from backups (see `docs/architecture.md`, "Backup").

## Entry points for other apps

| Entry point | Handled by | Effect |
|---|---|---|
| `trackerforsimkl://oauth?code=…&state=…` | `auth/AuthRedirectActivity` | Finishes sign-in (state must match the one the app started). |
| `https://simkl.com/tv/…`, `/anime/…`, `/movies/…` | `ui/MainActivity` | Opens the title in the app (when the user picks this app for the link). |
| Launcher | `ui/MainActivity` | Opens the app, or the sign-in screen when signed out. |
| New-episode notification | `ui/MainActivity` (extras) | Opens that show's details. |

The debug build (`io.github.impca201.trackerforsimkl.debug`) registers the same links; Android asks which app should open them.
