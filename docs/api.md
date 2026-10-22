# API and entry points

The app has no API of its own. This page lists what it calls (SIMKL, and TMDB with the user's own token) and what other apps can call (deep links).

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
| GET | `/search/id?tmdb=&type={movie,show}` | The SIMKL title of a TMDB title tapped on a person's page (only when it isn't in the library) |
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

## TMDB API

Base URL `https://api.themoviedb.org/3`, implemented in `data/api/TmdbApi.kt`, mapped in `data/api/TmdbParsers.kt`. Only used when the user entered their own token in *Settings › Cast and crew*; the app ships no TMDB key. Read only, and only while a details, person or search screen is open (never in the background).

Every request sends the user's "API Read Access Token" as `Authorization: Bearer <token>` and `User-Agent: tracker-for-simkl/<version>`. TMDB's shorter v3 "API Key" is not accepted (the dialog says to paste the token instead; a key saved by 1.6.0 is ignored and Settings asks to replace it). The language is the phone's language and country only (`nl-NL`), without Android's locale extensions.

| Method | Path | Used for |
|---|---|---|
| GET | `/authentication` | Checks a token before Settings saves it (401 = rejected) |
| GET | `/movie/{tmdb}/credits` | Cast and crew of a movie or anime movie |
| GET | `/tv/{tmdb}/aggregate_credits` | Cast and crew of a show or anime series, over every season, with episode counts |
| GET | `/person/{id}?append_to_response=combined_credits&language=<phone language>` | A person's page: photo, life dates, biography and every movie and TV credit |
| GET | `/person/{id}` (English) | Only when the biography is missing in the phone's language |
| GET | `/search/person?query=&language=&include_adult=false` | Search › People, and the People row under All (first page) |

The TMDB ID comes from SIMKL's details (`ids.tmdb`); titles without one show no cast and crew. A failed request just leaves the section out. Cast photos are `https://wsrv.nl/?url=https://image.tmdb.org/t/p/w185<profile_path>`. Tapping a person opens their page in the app; its title bar links to `https://www.themoviedb.org/person/{id}`. On that page, appearances as themselves and in talk or news shows are left out. Tapping a title opens its details: straight away when it is in the library (matched by TMDB ID and kind, since TMDB's movie and TV IDs overlap), else after SIMKL's `/search/id`; when SIMKL doesn't have it, a snackbar offers the TMDB page.

TMDB's terms require the notice "This product uses the TMDB API but is not endorsed or certified by TMDB." and one of TMDB's approved logos in an About or Credits section. They are in *Settings › Credits*: TMDB's stacked logo (`res/drawable/tmdb_logo_square.xml`, `blue_square_2.svg` converted unchanged), what TMDB provides and the notice, next to SIMKL's logo (`res/drawable-nodpi/simkl_logo.png`, simkl.com's own app icon, unchanged).

## Entry points for other apps

| Entry point | Handled by | Effect |
|---|---|---|
| `trackerforsimkl://oauth?code=…&state=…` | `auth/AuthRedirectActivity` | Finishes sign-in (state must match the one the app started). |
| `https://simkl.com/tv/…`, `/anime/…`, `/movies/…` | `ui/MainActivity` | Opens the title in the app (when the user picks this app for the link). |
| Launcher | `ui/MainActivity` | Opens the app, or the sign-in screen when signed out. |
| New-episode or movie release notification | `ui/MainActivity` (extras) | Opens that show's or movie's details. |

The debug build (`io.github.impca201.trackerforsimkl.debug`) registers the same links; Android asks which app should open them.
