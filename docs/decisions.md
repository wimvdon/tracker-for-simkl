# Technical decisions

Newest first. Add an entry for every notable choice: what was decided, why, and what was rejected.

## 2026-10-15: Sort in the title bar, fewer filter chips

The chip row above each library tab (sort, status, my rating, anime type and a title search) took a row of space for filters the owner didn't use. Sorting is back in the title bar as a sort button with a menu, per tab as before. The status and rating filters and the per-tab title search are gone: sorting by title finds a title quickly, and the search in the title bar still searches everything. Only Anime keeps a chip row, for its Type filter. Rejected: keeping the row with only the sort chip (a whole row for one control).

Update the same day: the anime Type filter also moved to the title bar, as a filter button with a menu and a dot when a type is chosen, so no library screen has a chip row. The Calendar's type chips became the same kind of filter button (with several types to switch, its menu stays open). Other filters were considered (available now, airing/ended, my rating, in progress, year, has a note) and left out: the tabs, badges and sort orders already cover them, or they are rarely needed.

## 2026-10-15: More reliable new-episode notifications

Notifications could silently stop or miss episodes: Android drops scheduled work when an app is force-stopped (some phones do this to save battery), and WorkManager only puts it back once it starts, which the app did only when the switch was turned on. An episode was also lost when the check ran more than two days late, when more than five arrived at once, or when a second episode of the same show replaced the first one's notification. Now every app start re-registers the check (WorkManager `KEEP`, so a running schedule is untouched); upcoming episodes of watched shows are remembered between checks and the window is 7 days; there is one notification per show counting its new episodes, and shows over the limit wait for the next check. The decisions moved to `NewEpisodes`, a plain Kotlin object with unit tests. Rejected: asking to be excluded from battery optimisation (a new permission; the usage guide explains how to set it by hand) and exact alarms (also a permission, and the calendar is only updated a few times a day).

## 2026-10-15: Settings screens make room for the keyboard

The app targets Android 15+ and draws edge to edge, so the system no longer shrinks the screen when the keyboard opens. `SettingsScaffold` (shared design kit, same change in every app) now adds the keyboard to its content insets, so a focused text field scrolls into view instead of hiding behind the keyboard. Rejected: opting out of edge-to-edge (not possible for apps targeting Android 15+).

## 2026-10-15: Settings are backed up, the sign-in is not

Backups were switched off completely to keep the SIMKL token on the phone, so a new or reset phone started with default settings. Now Android's own backup (Google account) and phone-to-phone transfer copy the settings: theme, wallpaper colours, bottom bar, hide specials and "continue from latest". They moved to their own DataStore file, `backed_up_settings`, and the backup rules list only that file, so the token and anything added later stay on the phone. Existing settings are moved over once by a DataStore migration; the tokens never move, so there is no moment when they sit in a backed-up file. The notification switch stays on the phone: it needs the permission and a scheduled check that a restore doesn't bring. Rejected: backing up the old `settings` file (it holds the token), excluding single keys (Android's rules work per file) and backing up the library copy (SIMKL is the source and it is downloaded again after signing in).

## 2026-10-15: Phone testing doesn't block merging

The owner can't review code and can't always test a pull request on the device right away, so pull requests waited for a manual test that adds little over CI. Green CI (unit tests, lint, builds) is now enough to merge; the owner tests afterwards with the steps from the pull request and always before a release, and problems are fixed in a new pull request. Merging to `main` does not reach users: releases stay manual. Rejected: keeping the phone test as a merge gate (pull requests pile up and go stale).

## 2026-10-14: Back to GitHub Releases instead of Google Play

The owner chose to publish through GitHub Releases again, as before the Google Play entry below. The Release workflow once more builds a signed APK and attaches it (with the R8 mapping file) to a GitHub Release with the `vX.Y.Z` tag; the Play upload job, the AAB and the `PLAY_SERVICE_ACCOUNT_JSON` secret are no longer used. The signing key stays the one already in the GitHub secrets, so phones that installed a GitHub APK keep updating. Trade-off: the repository is public, so anyone can download the APK. Rejected: keeping Play internal testing (the developer account and its setup are not wanted for now).

## 2026-10-14: Shared design rules have their master copy in the template

`DESIGN.md` was kept identical in the three sibling apps by hand, with no single place to start a change, and the copies had drifted (Snoozer was missing the Loading section; the Tracker for Simkl icon row was out of date in two apps). The android-app template (`wimvdon/android-app`) now holds the master copy; each app keeps a full copy so Claude sessions can read the rules without opening another repository, and only its "This app" section is its own. Rejected: keeping the file only in the template (sessions in an app would have to fetch it every time) and a shared library for the design kit (a new dependency and more setup than three small apps need). The design kit files (`ui/theme/`) stay in the apps.

## 2026-10-14: Launcher icon shows a checkmark instead of a play button

The icon's TV stand (a short bar under the screen) did not read as a stand, and a play button says "video player" more than "tracker". The screen is now a little taller, has no stand, and carries a white checkmark ("watched"). Gradient, white background and the one-shape, one-detail rule from `DESIGN.md` are unchanged. The notification icon (previously a stock Material TV) is now the themed layer scaled to 24dp, so it matches. Rejected: keeping the play button with a small checkmark badge (too small at launcher sizes), stacked screens and a progress bar (busier).

## 2026-10-14: Publish through Google Play instead of GitHub Releases

The repository becomes public (free GitHub Actions minutes), and the app should not be freely downloadable as an APK. The Release workflow now builds a signed AAB and uploads it to Google Play's **internal testing** track; the owner installs from Play with automatic updates and no longer needs Obtainium. No GitHub Release is created any more; the `vX.Y.Z` tag is still created (after a successful upload), and the version rules (`scripts/release_version.py`) are unchanged.

- **Upload with `r0adkll/upload-google-play`** (approved by the owner), a widely used action, pinned to the v1.1.5 commit. It runs in its own job with read-only GitHub access and only the Play service account secret; the tag is created by a separate job afterwards, so a failed upload leaves no tag and can be re-run. Rejected: a hand-written upload script against the Play Developer API (more code to maintain).
- **A new Google-generated app signing key** (Play App Signing), not the key shared with the owner's other apps; the existing key is only the upload key. Cost: the GitHub-installed app must be uninstalled once, losing only sign-in and settings (the library lives on SIMKL). Rejected: giving Google the shared key, which keeps current installs updating but ties the three apps' signing together.
- **The dry run (`none`) still produces an APK and an AAB as artifacts**, for checking the build and for the first upload by hand, with versionCode 1 so they can never update an installed app. The signed APK step and the R8 mapping file stay; the mapping file is also sent to Play.
- Selling the app later would need Google's billing library (a new dependency) and a check of SIMKL's API terms for paid apps; not part of this change.

## 2026-10-14: All rights reserved, no open-source licence

The repository becomes public so GitHub Actions minutes are free, but the code is not meant to be reused: the app may become paid later. `LICENSE` is therefore a plain copyright notice ("all rights reserved"): the source can be read, but not copied, modified, distributed or sold without written permission. Without any licence the default is the same under copyright law; the explicit notice makes it clear to readers. Rejected: MIT, Apache 2.0 and GPL, which would let anyone reuse the code or publish their own build. Note that by GitHub's terms of service, other GitHub users may still view and fork a public repository on GitHub itself; that grants no other rights.

## 2026-10-14: Aligned with the android-app template

The app now follows the rules, layout and workflows of the private `wimvdon/android-app` template, like its sibling apps Snoozer and Time Tracker for Notion.

- **`CLAUDE.md`**, a plain-language pull request template and weekly **Dependabot** updates for Gradle and GitHub Actions.
- **CI** now also runs **lint** (errors fail the build, no baseline) and the release-script tests, cancels outdated runs and uploads `tracker-for-simkl-<version>-debug.apk` unzipped. The existing launch test and R8 keep check stay. Enabling lint surfaced 18 errors, all fixed in code: string lookups in Compose now use `LocalResources` instead of `LocalContext` (configuration-aware), and the detail screen's error state uses the scaffold padding.
- **Release workflow** from the template: version maths in `scripts/release_version.py` (unit tested), the signing key checked before the build, tests and lint before the signed build, the R8 mapping file attached, published with `gh release create` instead of a third-party action. The secret names without `ANDROID_` keep working.
- **Debug builds use `applicationIdSuffix = ".debug"` and the name "Tracker for Simkl Dev"**, so a pull request's APK installs next to the released app. Trade-off: both apps register `trackerforsimkl://oauth` and the simkl.com links, so Android asks which app opens them. Rejected: a separate debug redirect scheme, because the SIMKL API app only accepts its registered redirect URI.
- **Sources moved from `src/*/java/` to `src/*/kotlin/`**, the template's layout.
- **Kept on purpose, unlike the template:** `minSdk` 26 (Android 8.0) instead of 31, because the app is released and raising it would stop updates for users on older phones; six languages; the ktlint settings in `.editorconfig`.

## Talk to SIMKL directly, no backend

The app calls api.simkl.com from the phone with the user's own token. A backend would need hosting and would see every user's token and library. SIMKL's API is designed for direct client use (public client ID, PKCE).

## OAuth 2.0 authorization code with PKCE

SIMKL's recommended flow for mobile apps: no client secret ships in the APK, and the `state` and `code_verifier` are kept in DataStore so the flow survives the browser round trip and process death. Rejected: embedding a client secret, which anyone could extract from the APK.

## The client ID is a build property, not committed

`-PsimklClientId` (CI: the `SIMKL_CLIENT_ID` Actions variable) becomes `BuildConfig.SIMKL_CLIENT_ID`. SIMKL says the client ID is not secret; keeping it out of the source means each build chooses which SIMKL API app it signs in to. Builds without it start and show a clear sign-in error, so CI and tests never need it.

## Activities-first incremental sync, only in the foreground

SIMKL's API rules ask clients to call `/sync/activities` first, fetch only what changed with `date_from`, and not to sync in the background. The sync follows that: a full `/sync/all-items/` only the first time, then only the changed parts, and IDs-only removal checks. Automatic syncs are at most every 15 minutes; pull-to-refresh is always allowed.

## Notifications from data.simkl.in, not the API

New-episode notifications compare SIMKL's public calendar files (a CDN outside the API quota) with the library as last synced. This respects the "no background sync" rule and costs no API calls. Trade-off: an episode watched elsewhere since the last sync can still be notified once.

## Hand-written JSON mapping

API responses are parsed into `JsonElement`s and mapped in `Parsers`. SIMKL's responses differ per endpoint and `extended` level and fields are often missing; hand mapping makes every field optional without a large set of DTOs. `ParsersTest` pins the mapping against SIMKL's documented examples. The on-disk cache, whose format the app controls, uses `@Serializable` classes.

## JSON snapshot files instead of Room

The library is read and written as a whole (one sync replaces a snapshot), and screens filter it in memory. Two JSON files, written atomically, are simpler than a database schema and migrations. WorkManager still brings Room as its own internal dependency.

## Explicit R8 keep rules, checked in CI

R8 full mode once removed WorkManager's generated database constructor (a crash when turning on notifications in 0.0.4). The constructors are now kept in `proguard-rules.pro`, R8 writes its seeds file, and CI fails if they are missing.

## Local crash report instead of a crash-reporting service

Crashes are written to a local file and shown once on the next start with a Copy button. This finds start-up crashes without sending data to a third party, which the privacy policy rules out.

## No ViewModels, DI framework or navigation library

The object graph is five lazily created objects in `TrackerApp`; the navigation is a stack of five route types. Repositories already hold the long-lived state, so ViewModels would only forward it. Revisit if screens get complex enough to need per-screen state holders.

## Robolectric launch test

`LaunchTest` starts `MainActivity` signed out and signed in under Robolectric. Start-up and composition crashes fail CI with their stack trace, without the emulator an instrumented test would need.
