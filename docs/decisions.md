# Technical decisions

Newest first. Add an entry for every notable choice: what was decided, why, and what was rejected.

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
