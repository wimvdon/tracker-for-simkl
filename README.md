# Tracker for Simkl

## 📚 Documentation
| | |
|---|---|
| [⚙️ Architecture](docs/architecture.md) | System design, components and flow |
| [📁 Structure](docs/structure.md) | Project organization and responsibilities |
| [🚀 Installation](docs/installation.md) | Requirements and steps to run the project |
| [🧠 Technical decisions](docs/decisions.md) | Trade-offs and design justifications |
| [📖 Usage guide](docs/usage.md) | Signing in, shelves, Discover, Calendar, settings |
| [🔌 API](docs/api.md) | SIMKL endpoints used, sign-in flow, deep links |
| [🧪 Testing](docs/testing.md) | How to run tests and what they cover |

---

## Description

An unofficial Android app for tracking TV shows, anime and movies with [Simkl](https://simkl.com). Not affiliated with or endorsed by Simkl; "Simkl" is Simkl's trademark and only describes the service the app works with.

- **What it does:** shows your SIMKL lists as shelves (Up next, Watched, Plan to watch, On hold, Dropped), lets you mark episodes and movies watched, rate titles, check in, browse Discover and a Calendar of upcoming episodes, and optionally notifies you when a new episode of a show you watch airs.
- **What problem it solves:** a fast, offline-first Material 3 client for SIMKL's tracking that follows SIMKL's API rules (sync on open and pull-to-refresh, incremental `/sync/activities` first, no background API traffic).
- **Real use case:** after watching an episode, open the app, tap ✓ on the show in **Up next**, and the next unwatched episode moves up; the Calendar tab shows what airs this week.

English, Dutch, French, German, Spanish and Italian. Android 8.0+. See the [privacy policy](PRIVACY.md).

## Quick start

Install the latest `tracker-for-simkl-x.y.z.apk` from [Releases](https://github.com/impca201/tracker-for-simkl/releases), open it and tap **Sign in with SIMKL**.

To build and install a debug build from source on a connected phone:

```bash
git clone https://github.com/impca201/tracker-for-simkl.git
cd tracker-for-simkl
./gradlew installDebug -PsimklClientId=<your SIMKL client ID>
```

## Technologies used

| Area | Technology |
|---|---|
| Language | Kotlin 2.4, coroutines, `StateFlow` |
| UI | Jetpack Compose, Material 3 (dynamic colour optional), Material icons |
| Networking | OkHttp 5 (50 MB HTTP cache), kotlinx.serialization JSON, Coil 3 for images |
| Storage | Jetpack DataStore (settings, tokens), JSON snapshot files (library cache) |
| Background | WorkManager (optional new-episode notifications, every 4 hours) |
| Build | Gradle 9.8 (Kotlin DSL, version catalog), AGP 9.4, R8 |
| CI/CD | GitHub Actions: tests, lint, R8 keep check and a debug APK on every pull request; signed releases; Dependabot |
| Tests | JUnit 4, Robolectric (launch test) |

## Quick installation

1. Install [Android Studio](https://developer.android.com/studio) (brings the JDK and Android SDK).
2. Clone the repo and run `./gradlew assembleDebug -PsimklClientId=<id>`.
3. Install `app/build/outputs/apk/debug/app-debug.apk` and sign in.

Full steps: [docs/installation.md](docs/installation.md).

## Architecture (summary)

A single-activity Compose app. `TrackerApp` owns one OkHttp client, the `SimklApi` client and three repositories. `LibraryRepository` keeps the whole library in memory as a `StateFlow`, persisted as JSON files, and syncs incrementally from `/sync/activities`; screens render that state and send changes back through it. A small stack-based `Navigator` replaces a navigation library. Details: [docs/architecture.md](docs/architecture.md).

## Project structure

```
tracker-for-simkl/
├── .github/            CI and release workflows, PR template, Dependabot
├── CLAUDE.md           Rules for Claude Code sessions
├── scripts/            Release helpers (versioning, keystore secret) with tests
├── app/src/main/kotlin/io/github/impca201/trackerforsimkl/
│   ├── TrackerApp.kt   Application: HTTP client, API, repositories, image loader
│   ├── auth/           Sign-in redirect activity
│   ├── data/           Repositories, settings, API client, parsers, models
│   ├── work/           New-episode notification worker
│   └── ui/             Compose screens, navigation, theme
└── app/src/test/       JVM unit tests and the Robolectric launch test
```

Full tree: [docs/structure.md](docs/structure.md).
