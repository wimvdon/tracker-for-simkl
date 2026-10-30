# Testing

## Running the tests

```bash
./gradlew testDebugUnitTest                      # JVM unit tests + Robolectric launch test
./gradlew lintDebug                              # Android lint (errors fail the build)
python3 -m unittest discover -s scripts/tests    # release scripts
```

Everything CI runs, in one go:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
python3 -m unittest discover -s scripts/tests
```

Reports: `app/build/reports/tests/testDebugUnitTest/index.html` and `app/build/reports/lint-results-debug.html`. The first Robolectric run downloads an Android runtime jar from Maven Central.

## What CI runs

`.github/workflows/ci.yml`, on every pull request and push to `main`:

| Check | Catches |
|---|---|
| Release script tests | Version maths and keystore-secret decoding (`scripts/`) |
| Unit and launch tests | Parsing and shelf logic bugs; crashes while starting the app or composing the first screens |
| Lint | API misuse, missing translations, accessibility, configuration-unaware resource lookups in Compose |
| Debug and release builds (with the `SIMKL_CLIENT_ID` variable) | Compile errors, R8 shrinking problems |
| R8 keep check | That R8 kept WorkManager's database and worker constructors (`build/outputs/r8-seeds.txt`); without them turning on notifications crashes |

The debug APK (`watchlist-<version>-debug.apk`) and the test and lint reports are attached to every run. The release workflow runs the unit tests and lint again before building a signed APK and app bundle.

## What is covered

| Test | Subject |
|---|---|
| `data/ParsersTest` | `Parsers` against hand-written and documented SIMKL responses: library items, next episode, watched state and ratings; details and summaries; episode refs; airing states; anime details, `/ratings/all` (keyed per type), `/sync/activities` timestamps, playback and small responses from SIMKL's docs; `Shelves` assignment; marking episodes moving "next"; continue-from-latest (only aired episodes count; shows watched elsewhere are looked up before a sync is shown); overviews lose HTML tags and entities; the episode count of a search result stays a number; a profile without a name |
| `data/ErrorsTest` | `UserError`: a failed request is told apart by what the user can do about it (offline, signed out, not found, busy, server down, other HTTP error, unexpected), never by its raw message |
| `data/SubscriptionTest` | `SubscriptionRules`: only this product unlocks the app, pending and inactive lock it, a remembered subscription opens the app offline for 7 days (not with a clock set back, not after Play says it ended), debug builds are never locked, the free-trial offer is shown when Google Play returns it and the plain yearly plan otherwise, trial periods in days |
| `data/SyncRulesTest` | `SyncRules`, what SIMKL's API team asked for: automatic syncs at most every 15 minutes, community ratings at most once a day, kept episode lists (downloaded when missing, daily for airing shows, never for ended ones unless opened, not again within the hour of opening), episodes that aired since a list was kept count as aired |
| `data/TmdbTest` | TMDB cast and crew against responses shaped like TMDB's API reference: movie credits (billing order, entries without an ID skipped, jobs merged per person, only key jobs), show aggregate credits (roles joined, episode counts, busiest director first), empty and odd responses, the cast limit; movie or TV source per type (anime movies use the movie); pasted keys cleaned up, v3 keys told apart from v4 tokens; a person's page (appearances as themselves and talk shows left out, roles and jobs merged per title, unreleased first then newest, known for by votes, age, English biography fallback); credits matched to library titles of the same kind only; SIMKL's `/search/id` result; only Read Access Tokens taken from what is pasted (the API Key recognised); the language sent to TMDB; person search results; when the card that offers a TMDB token shows (`TmdbBanner`: no token, a title TMDB knows, not hidden for now or for good) |
| `data/DetailLogicTest` | Details screen logic: broadcast time converted to the phone's time zone (summer time, day changes, unreadable day or zone), show status names, the season the episode list opens on |
| `data/NewReleasesTest` | `NewReleases`: only released movies on the Plan to watch list, each announced once; upcoming releases remembered so a late check still announces them; at most five per check; episodes and movies sharing the stored sets without mixing up; one channel per type, TV keeping the old channel id |
| `data/NewEpisodesTest` | `NewEpisodes`: only aired, unwatched episodes of shows being watched, each announced once; upcoming episodes remembered so a late check still announces them; one notification per show counting its new episodes; at most five shows per check, the rest wait; anime ignores seasons |
| `ui/LibraryFilterTest` | The library filters (anime type, case-insensitive; my rating; airing / ended; kept when the screen is recreated), the sort orders, what each tab offers (`TabSpec`: no my rating on Up next and Want to watch, no "recently watched" on Want to watch, defaults) and the "My ratings" list (`Ratings`) |
| `ui/LaunchTest` | Starts `MainActivity` under Robolectric (API 35) signed out (login screen) and with a token (main screen); any exception during start-up or composition fails |

## Strategy

- Logic that decides what the user sees (parsing, shelves, "what's next") is plain Kotlin and unit tested with JSON taken from SIMKL's documentation, so API changes show up as test failures rather than as wrong shelves.
- The launch test replaces an emulator: it catches start-up crashes, which a release build once had, without instrumented tests.
- Not covered automatically: `SubscriptionRepository` (it needs Google Play; test on the internal test track with a license-test account, see publishing.md), `SimklApi` requests and token refresh, `LibraryRepository` sync and optimistic changes, `NewEpisodesWorker` itself (downloading and posting; the decisions are in `NewEpisodes`), and most screens. These need a fake HTTP server or device tests; neither is set up. There is no coverage tool.

### Writing tests

- Every logic change gets a test in `app/src/test/kotlin/`, mirroring the package.
- Keep logic free of Android types so it runs on the JVM; use Robolectric only for start-up and composition.

## Manual checks on the phone

For each pull request (before or after it is merged: merging only waits for green CI), install the debug APK from the CI run ("Watchlist Dev", next to the released app) and follow the steps in the pull request. Before a release, also check:

- [ ] Sign in, pull to refresh, mark an episode watched in Up next, rate a title.
- [ ] Dark mode on and off: text stays readable.
- [ ] Language: Settings › System › Languages › App languages › Watchlist › Nederlands. All text is translated.
- [ ] Largest font size: nothing is cut off.
- [ ] After a release: install the release APK over the previous one. It updates without signing out.

## Reading a crash from a release build

Release builds are minified. Download `watchlist-<version>-mapping.txt` from the matching GitHub Release and decode the trace with R8's `retrace` (Android SDK `cmdline-tools/latest/bin/retrace`):

```bash
retrace watchlist-<version>-mapping.txt stacktrace.txt
```

The in-app crash report (Copy crash report) gives you the stack trace and the version.
