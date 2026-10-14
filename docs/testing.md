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

The debug APK (`tracker-for-simkl-<version>-debug.apk`) and the test and lint reports are attached to every run. The release workflow runs the unit tests and lint again before building the signed bundle for Google Play.

## What is covered

| Test | Subject |
|---|---|
| `data/ParsersTest` | `Parsers` against hand-written and documented SIMKL responses: library items, next episode, watched state and ratings; details and summaries; episode refs; airing states; anime details, watchlist ratings, playback and small responses from SIMKL's docs; `Shelves` assignment; marking episodes moving "next"; continue-from-latest (only aired episodes count) |
| `data/DetailLogicTest` | Details screen logic: broadcast time converted to the phone's time zone (summer time, day changes, unreadable day or zone), show status names, the season the episode list opens on |
| `ui/LaunchTest` | Starts `MainActivity` under Robolectric (API 35) signed out (login screen) and with a token (main screen); any exception during start-up or composition fails |

## Strategy

- Logic that decides what the user sees (parsing, shelves, "what's next") is plain Kotlin and unit tested with JSON taken from SIMKL's documentation, so API changes show up as test failures rather than as wrong shelves.
- The launch test replaces an emulator: it catches start-up crashes, which a release build once had, without instrumented tests.
- Not covered automatically: `SimklApi` requests and token refresh, `LibraryRepository` sync and optimistic changes, `NewEpisodesWorker`, and most screens. These need a fake HTTP server or device tests; neither is set up. There is no coverage tool.

### Writing tests

- Every logic change gets a test in `app/src/test/kotlin/`, mirroring the package.
- Keep logic free of Android types so it runs on the JVM; use Robolectric only for start-up and composition.

## Manual checks on the phone

For each pull request, install the debug APK from the CI run ("Tracker for Simkl Dev", next to the released app) and follow the steps in the pull request. Before a release, also check:

- [ ] Sign in, pull to refresh, mark an episode watched in Up next, rate a title.
- [ ] Dark mode on and off: text stays readable.
- [ ] Language: Settings › System › Languages › App languages › Tracker for Simkl › Nederlands. All text is translated.
- [ ] Largest font size: nothing is cut off.
- [ ] After a release: update the app from the Play Store (internal testing). It updates without signing out.

## Reading a crash from a release build

Release builds are minified. Crashes reported through Google Play are decoded by Play (the workflow uploads the mapping file). For a trace from the in-app crash report, download `tracker-for-simkl-<version>-mapping.txt` from the matching run of the Release workflow (**Actions › Release › Artifacts**, kept 90 days; older versions: the GitHub Release) and decode the trace with R8's `retrace` (Android SDK `cmdline-tools/latest/bin/retrace`):

```bash
retrace tracker-for-simkl-<version>-mapping.txt stacktrace.txt
```

The in-app crash report (Copy crash report) gives you the stack trace and the version.
