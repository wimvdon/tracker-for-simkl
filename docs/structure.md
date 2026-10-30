# Project structure

```
tracker-for-simkl/
├── .editorconfig                     Editor/ktlint rules (4 spaces, 140 columns, ktlint_official)
├── .gitattributes                    LF everywhere; *.bat → CRLF; keystores/images binary
├── .github/
│   ├── dependabot.yml                Weekly Gradle and GitHub Actions update PRs
│   ├── pull_request_template.md      Plain-language PR description + checklist
│   └── workflows/
│       ├── ci.yml                    Script tests, unit + launch tests, lint, builds, R8 keep check, debug APK
│       └── release.yml               Release rules → verify signing key → build APK → publish GitHub Release
├── CLAUDE.md                         Rules for Claude Code sessions
├── DESIGN.md                         Design language shared with the sibling apps
├── LICENSE                           Copyright notice: all rights reserved, no open-source licence
├── PRIVACY.md                        Privacy policy
├── README.md
├── docs/                             This documentation
├── scripts/                          Release helpers, shared with the android-app template
│   ├── release_version.py            Tag parsing, next version, versionCode, "is newer"
│   ├── decode_keystore.py            Decodes the keystore secret tolerantly, explains failures
│   ├── VerifySigningKey.java         Checks the key password before the build
│   └── tests/                        unittest suites for both Python scripts
├── build.gradle.kts                  Root build: plugin declarations
├── settings.gradle.kts               Repositories, single :app module, APK name prefix
├── gradle.properties                 JVM args, build and configuration cache
├── gradle/
│   ├── libs.versions.toml            Version catalog (every dependency version)
│   └── wrapper/                      Gradle wrapper
└── app/
    ├── build.gradle.kts              App ID, versioning, signing, SIMKL client ID, lint, dependencies
    ├── proguard-rules.pro            WorkManager/Room keep rules, R8 seeds output for CI
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml   Permissions, simkl.com links, OAuth redirect, WorkManager init
        │   ├── kotlin/to/wim/watchlist/
        │   │   ├── WatchlistApp.kt            Application: object graph, image loader, WorkManager config
        │   │   ├── CrashReporter.kt           Local crash report shown on next start
        │   │   ├── auth/AuthRedirectActivity.kt   Receives trackerforsimkl://oauth
        │   │   ├── data/
        │   │   │   ├── LibraryRepository.kt   Library state, sync, cache files, changes
        │   │   │   ├── CatalogRepository.kt   Details, search, Discover; AccountRepository (profile kept on the device, stats)
        │   │   │   ├── EpisodeStore.kt        Episode lists kept on the device
        │   │   │   ├── Files.kt               Atomic file writes
        │   │   │   ├── SettingsStore.kt       DataStore: tokens, settings, notified and remembered episodes (two files)
        │   │   │   ├── api/
        │   │   │   │   ├── SimklApi.kt        HTTP client for api.simkl.com and data.simkl.in
        │   │   │   │   ├── Parsers.kt         JSON → models
        │   │   │   │   ├── TmdbApi.kt         Read-only HTTP client for api.themoviedb.org (user's own key)
        │   │   │   │   ├── TmdbParsers.kt     TMDB credits JSON → models
        │   │   │   │   ├── JsonExt.kt         Null-safe JSON accessors
        │   │   │   │   └── Pkce.kt            PKCE verifier/challenge
        │   │   │   └── model/
        │   │   │       ├── Models.kt          Items, details, episodes, images (wsrv.nl URLs)
        │   │   │       ├── Credits.kt         Cast, crew, people and their credits (TMDB)
        │   │   │       ├── Shelves.kt         Shelves, episode order, continue-from-latest
        │   │   │       └── NewEpisodes.kt     Which aired episodes a notification announces
        │   │   ├── work/NewEpisodesWorker.kt  Periodic new-episode notifications
        │   │   └── ui/
        │   │       ├── MainActivity.kt, AppRoot.kt, Navigation.kt   Entry, root, back stack
        │   │       ├── main/        Navigation bar and top bar
        │   │       ├── library/     TV Shows / Anime and Movies shelves
        │   │       ├── detail/      Title details, episodes, rating, cast and crew, links
        │   │       ├── person/      A person's page (TMDB): bio, known for, filmography
        │   │       ├── discover/    Trending, airing, premieres, best, "Surprise me"
        │   │       ├── calendar/    Upcoming episodes and releases
        │   │       ├── search/  profile/  settings/  login/
        │   │       ├── components/  Posters, labels, skeletons, shared widgets
        │   │       └── theme/       Material 3 theme, shapes, motion, system bars, settings kit, empty states
        │   └── res/
        │       ├── values/          English strings (split by area), colours, themes
        │       ├── values-{nl,fr,de,es,it}/   Translations
        │       └── xml/                        Backup rules: only the settings file
        └── test/kotlin/to/wim/watchlist/
            ├── data/ParsersTest.kt   Parsers, shelves, continue-from-latest
            └── ui/LaunchTest.kt      Starts MainActivity under Robolectric
```

## Layers

| Layer | Where | Rules |
|---|---|---|
| **Network** | `data/api/SimklApi.kt`, `data/api/TmdbApi.kt` | The only places that build HTTP requests or know URLs, headers, tokens and keys. |
| **Mapping** | `data/api/Parsers.kt`, `data/api/TmdbParsers.kt`, `data/model/` | Pure Kotlin, no Android types, unit tested. |
| **State** | `data/*Repository.kt`, `SettingsStore.kt` | Own the in-memory state, cache files and DataStore. Expose `StateFlow`s and suspend functions. |
| **UI** | `ui/` | Composables render state and forward clicks to repositories. No HTTP, no JSON. |
| **Background** | `work/` | Reads cached state and public data files only; never calls the API. |

## What does NOT go where

- No API calls from `ui/` or `work/`: go through a repository (`work/` reads `data.simkl.in` via `SimklApi.calendar`).
- No Android types in `Parsers`, `Models` or `Shelves`: they must stay JVM-testable.
- No user-visible text in Kotlin: strings live in `res/values*/strings*.xml`, in every language.
- No secrets in the repository: the SIMKL client ID is passed at build time (`-PsimklClientId`), signing keys only exist in CI.
- `scripts/` is release tooling only, never app code.
