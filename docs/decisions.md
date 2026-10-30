# Technical decisions

Newest first. Add an entry for every notable choice: what was decided, why, and what was rejected.

## 2026-10-30: Store texts in fastlane's folder layout

The Google Play texts for all six languages live in `fastlane/metadata/android/<locale>/`, the
layout that fastlane `supply` and the Play upload GitHub Action read, so they can be uploaded
automatically later without moving files. Fastlane itself is not added; for now the texts are
copied into the Play Console by hand ([publishing.md](publishing.md)). Rejected: keeping the
texts only in the Play Console (no history, and not reviewed with the app's own wording).

## 2026-10-30: A card offers the TMDB token on details pages

Cast and crew need the user's own TMDB token, but the only way to find that out was Settings.
Details pages of titles TMDB knows now show a card where the cast would be, which opens the
token dialog. "Not now" hides it until the app restarts (kept in memory); "Don't show again" is
stored in the backed-up settings, like the user's other choices. It is a banner, not an empty
state, because the page works without it (DESIGN.md, "Empty and setup states"). Rejected: a card
on the main screens (it would nag on every tab, not where a token helps) and showing it on
titles TMDB doesn't know (a token wouldn't add anything there).

## 2026-10-30: Pre-launch audit: errors in the user's language, Play bundle, polish

A read-through of the whole app before the Google Play launch (people will pay for it). Fixed in
one pull request:

- **Failed requests are worded in the user's language.** Screens showed raw exception text
  ("Unable to resolve host …", "HTTP 503"). `UserError` (plain Kotlin, tested) sorts a failure by
  what the user can do about it (offline, signed out, not found, busy, server down, other HTTP
  error, unexpected) and `userMessage` turns it into a string in six languages. Used by the sync,
  the details and person pages, sign-in and the Up next checkmark. Rejected: translating every
  server message (they change, and most are not for end users).
- **The release builds an app bundle too** (`watchlist-<version>.aab`, same key), because Google
  Play only takes bundles. The APK stays for GitHub Releases and direct installs.
- **Signing in again restores the notification schedule** that signing out cancelled, and
  signing out clears which episodes were announced (they belong to that account). Before, the
  setting looked on but nothing ran until the next app start.
- **Settings reads survive an unreadable file**: the refresh token, PKCE state and notification
  bookkeeping now read the device file the same forgiving way as the settings flow.
- **No English in the data layer**: "12 eps" in search results, "SIMKL user" and "$1.2B" were
  built in code; the screens now word them with plurals and the locale's compact number format.
- **Overviews decode HTML entities** ("&amp;amp;", "&amp;#39;", "&amp;hellip;") as well as tags.
- **TMDB and Trakt links of anime movies** point at their movie pages (both sites file them
  under movies; SIMKL under anime), the way the cast lookup already did.
- Lint warnings cleared: Spanish and Italian plurals get the `many` quantity, two unused strings
  removed, `SectionHeader` takes its modifier first, the `Format` context (the Application) is
  marked as not a leak, `Uri.parse` → KTX.

Known and left for later (not launch blockers): an optimistic change and a sync finishing at the
same moment can briefly show the older list until the sync two seconds later; `CatalogRepository`
caches details for the whole session; the crash screen shows the raw stack trace (by design, see
"Local crash report instead of a crash-reporting service").

## 2026-10-30: Renamed to Watchlist, application ID `to.wim.watchlist`

Before the Google Play launch the app gets its final name, **Watchlist** (store listing:
"Watchlist for Simkl"), and moves from its old `io.github` application ID to the owner's own domain, `wim.to`: application ID and Kotlin package `to.wim.watchlist`. A Play package name can
never change once published, so this is the last moment to pick one under a domain the owner
controls. The `Application` class is now `WatchlistApp` and APKs are named `watchlist-<version>.apk`.

Trade-off: Android treats the new ID as a different app. Copies installed from GitHub Releases
(1.8.0 and older) don't update to it; they keep working, and users install Watchlist next to them,
sign in again (backed-up settings don't carry over) and uninstall the old app. The signing key
is unchanged. Every trace of the old name is gone, including the `app-name` and User-Agent sent to
SIMKL (now `watchlist`), the theme names and the GitHub repository (now `wimvdon/watchlist`).

The same day the owner changed the redirect URI registered with SIMKL to `watchlist://oauth`, so
the app now uses that scheme. With the old URI no longer registered, version 1.8.0
and older can no longer finish a new sign-in (a signed-in copy keeps working).

## 2026-10-26: Fewer SIMKL calls, as SIMKL's API team asked

SIMKL reviewed the app's traffic: every launch downloaded the community ratings for the whole
library (three `/ratings/{type}` calls), the profile, several episode lists (one show three times),
and launch, foreground and screen triggers each started their own sync within seconds. Now:

- One sync at a time; automatic triggers join a running sync and keep the 15-minute limit
  (rejected: a WorkManager unique job, which would add background scheduling for a foreground sync).
- `/sync/playback` and `/users/settings` only when their `/sync/activities` timestamp moved. Before,
  playback was fetched on every sync because some scrobblers didn't move the timestamp; SIMKL asked
  to rely on it, so a paused session from such a scrobbler shows at the next change instead.
- One `/ratings/all` call, at most once a day, its time kept in `library.json`. Titles added in
  between show their community score and ended/airing state after opening them or the next day.
- Episode lists and the profile are kept on the device in JSON files (`EpisodeStore`,
  `profile.json`), like the library; not backed up. Rejected: relying on the HTTP cache alone (it
  still sends a request each time).
- Identical GETs that overlap share one request (`SimklApi.shared`).

## 2026-10-22: A notification channel per type, movie releases, Android's settings shown in the app

TV shows, anime and movies each get an Android notification channel, so people can silence or
change one type. TV keeps the channel id `new_episodes`, so settings made for it stay; anime and
movies are new. Movies on the Plan to watch list now get an "Out now" notification from SIMKL's
public movie release calendar (data.simkl.in, like the episode calendars; no API call in the
background), with the same seven-day window and memory as episodes, sharing their stored sets.

*Settings › Notifications* mirrors Android: the switch shows off while Android blocks the app's
notifications, each type shows its channel's state, and a tap opens Android's page for that
channel (or for the app), read again when Settings comes back. Rejected: in-app switches per type
that would disagree with Android's (an app can't turn a blocked channel back on), and keeping one
shared channel.

## 2026-10-21: Cast and crew from TMDB, with the user's own key

SIMKL gives no cast, only a director. TMDB has full credits and SIMKL's details already carry each
title's TMDB ID, so details pages now show a Cast and a Crew row from TMDB. SIMKL stays the source
of everything else (lists, sync, sign-in); TMDB is read only, called only while a details page is
open, and never in the background.

- **The user enters their own TMDB key** in Settings (v3 API key or v4 Read Access Token; checked
  with TMDB before saving). Without one, the app never contacts TMDB. Reason: TMDB's free API is for
  non-commercial use and the app may become paid; with each user's own personal key the app ships
  no key and needs no commercial agreement. Rejected: a build-time key like the SIMKL client ID
  (anyone could extract it from the APK, and a paid app would need a commercial TMDB licence).
- **The key is stored in the device DataStore file, not backed up**, like the SIMKL tokens.
- **Photos go through wsrv.nl**, like SIMKL's images, so the phone only contacts one new host
  (api.themoviedb.org). New network destination approved by the owner.
- **A second HTTP class**, `TmdbApi`, next to `SimklApi` (CLAUDE.md updated); OkHttp and
  kotlinx-serialization were already there, so no new dependency.
- **Attribution:** *Settings › Credits* has a row for SIMKL and one for TMDB, each with its own
  logo (unchanged, never tinted), what it provides and a link; TMDB's row carries its notice.
- **Read Access Token only** (changed after 1.6.0, which also took the v3 API Key): one way to
  sign requests, and the owner found the API Key failing. A saved API Key is ignored and Settings
  asks to replace it.
- **Person search** under Search (a People chip and a row under All), only with a token.
- **A person screen** (photo, life dates, biography, known for, filmography). A title opens its
  SIMKL details: matched in the library by TMDB ID and kind first, else looked up with SIMKL's
  `/search/id` (a foreground call on a tap, as SIMKL's rules allow). Rejected: TMDB title pages
  inside the app, which would duplicate the details screen without the user's lists.

## 2026-10-17: Drag handles for the bottom bar order

A design review of the sibling apps found four ways to put a list in order (drag handles, a long
press, chevrons and arrows). The shared rule in `DESIGN.md` ("Reordering") is now a drag handle at
the end of each row, from the shared `Reorder.kt`. Settings › Bottom bar drops its up and down
arrows: drag a destination by its handle (screen readers keep Move up and Move down). The
navigation bar's selected icons come from Material's Rounded set like every other icon; lists keep
a readable width on wide windows (`Layout.kt`); and English says "Sign out", matching "Sign in".
Rejected: keeping the arrows (one more way to do the same thing across the apps).

## 2026-10-17: Ratings, rank and genres in the detail page's info box

The rating, rank and genre chips sat above the overview, and nothing happened when tapped. A chip
is for input or filtering, so M3 does not allow read-only chips. Ratings, rank and genres are now
rows in the info box, with the status and air facts. The overview is the first text under the
actions. Rejected: keeping them as read-only tags (still look like buttons), and a separate
"Ratings" section (one more box for a few lines). The gold star no longer appears on the detail
page's ratings; it stays on poster badges as the user's own rating.

## 2026-10-17: Material 3 check of the profile and detail page

"My ratings" is content you browse, so it no longer sits among the profile's account actions; it
has its own row right below the watch-time statistics, with how many titles you rated (rejected:
an entry in the library tabs, which already sort by "My rating"). Log out has its own group at
the bottom, apart from the routine actions (DESIGN.md: destructive actions are separated). The
Settings row is gone: the gear in the top bar already opens it. The profile keeps a readable
width (640dp) on wide windows, like Settings. The detail page's overview tells screen readers it
can be tapped and whether it is expanded. Rating stars use one colour (`RatingStar`) everywhere.

## 2026-10-17: Caption directly under a one-line poster title

Poster tiles still reserve two title lines and one caption line (rows keep one height), but the
caption now follows the title: with a one-line title the spare line sits at the bottom of the tile
instead of as a gap between title and caption. Done with an invisible placeholder of the same
lines behind the text, so it keeps working with any font size. Rejected: a fixed height in dp
(breaks with larger system fonts).

## 2026-10-17: Per-tab sort and filter options, "My ratings" page

With a filter and sort button on every library screen, each tab now offers only what makes sense
there (`TabSpec`): no my-rating filter or sort on Up next and Want to watch (titles you haven't
seen aren't rated), no "recently watched" on Want to watch, an airing / ended filter on the show
tabs, and "fewest episodes left" on Up next. Defaults stay: recently watched where something was
watched, recently added on Want to watch. A filter or sort a tab doesn't offer is ignored, so
nothing hidden can filter a list. The profile links to "My ratings": a poster row per type,
highest rating first, whose header opens a grid of that type. The ratings come from the synced
library, so the page needs no extra SIMKL calls.

## 2026-10-17: Less help text

The owner found the app over-explanatory; the shared rule is in `DESIGN.md` ("Wording"): help text only says what the screen can't. Settings repeated their labels and described the obvious. Removed: the line above the bottom-bar list (a list with checkboxes and arrows under the header "Bottom bar") and "Leave out special episodes" under "Hide specials". Shortened: the Content explanation (no list of every screen), "Continue from the latest episode", the notifications subtitle, the Up next empty text and the Dynamic color line. Kept: consequences, privacy notes and setup steps. Rejected: removing every hint (the remaining ones say something the screen doesn't).

## 2026-10-16: Shared empty state, steady poster rows, short buttons

A Material 3 audit of the sibling apps added shared rules to `DESIGN.md` ("Empty and setup states"). This app's empty, no-result and failed screens now use the shared `ui/theme/EmptyState.kt` instead of its own copy in `Common.kt` (same look, plus a heading for screen readers and a readable width). Poster tiles always take two title lines and one caption line, and trailers always one name line, because a row of tiles only measures the tiles on screen: a longer title scrolling into view made the whole row taller and pushed everything below it down. "Mark as watched" buttons now say "Watched" ("Gezien"); the screen already says what is being marked. Rejected: measuring every tile in a row to find the tallest (costs a full layout pass of items that aren't visible) and keeping long button labels (they wrap or get cut off in Dutch and German).

## 2026-10-16: My-rating filter back, in the title bar

The rating filter went away with the chip row (15 October). The owner wants to see what they have
and haven't rated, so it is back as part of the title bar's filter button, on TV Shows, Anime and
Movies, kept per tab. On Anime the same menu also holds the type filter, so there is still one
filter button per screen.

## 2026-10-16: Extra check at air time instead of exact alarms or a foreground service

The owner asked for notifications at the right time, and whether a background service should fetch
the calendar when the app hasn't been opened for a day. The 4-hourly WorkManager check already runs
without the app being opened, so no service was added; a foreground service would show a permanent
notification and Google Play limits them to user-visible work. To announce episodes on time, each
check schedules one extra WorkManager check 15 minutes after the next known air time of a watched
show. Rejected: exact alarms (`SCHEDULE_EXACT_ALARM`, a new permission that Play reserves for
alarm and calendar apps) and syncing the library in the background (SIMKL's API rules forbid it).
Air dates keep SIMKL's offset when parsed, so time zones don't shift them.

## 2026-10-16: Content types switch in Settings

The owner asked for global switches to turn TV shows, movies and anime on or off throughout the
app. They are stored as one backed-up set (`media_types`) in `AppSettings.types`; at least one
stays on. The setting only hides things: the library still syncs every type, so SIMKL's lists and
the sync stay complete and a type comes back at once when it is turned on again. Screens get the
list of types that are on; the bottom bar logic (`NavConfig.visible(types)`, `moved`) and the
notifications filter (`NewEpisodes.plan(types)`) are plain Kotlin and unit tested. Episodes of a
type that is off are marked as notified, so turning it back on doesn't bring a burst of old
notifications. Rejected: skipping disabled types in the sync (fewer downloads, but a partial
library copy would need a full sync on every switch).

## 2026-10-16: Docked navigation bar, settings list, no "recently watched" on Plan to watch

The owner asked whether the floating navigation pill was correct Material 3. It isn't: Material 3
Expressive uses floating toolbars for actions on the current screen and keeps the navigation bar
docked to the bottom edge for switching between an app's sections. The destinations now sit in
Material's `NavigationBar`, which also stays put while scrolling (the pill hid itself). Settings
are one list with section headers (shared `SettingsKit.kt`), and ratings and genres on a detail
page are outlined tags instead of chips that did nothing when tapped. Plan to watch no longer
offers "Recently watched" as a sort: nothing on it has been watched. Rejected: keeping the pill as
the app's signature (it broke the shared navigation rule) and a navigation rail on phones.

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

`DESIGN.md` was kept identical in the three sibling apps by hand, with no single place to start a change, and the copies had drifted (Snoozer was missing the Loading section; this app's icon row was out of date in two apps). The android-app template (`wimvdon/android-app`) now holds the master copy; each app keeps a full copy so Claude sessions can read the rules without opening another repository, and only its "This app" section is its own. Rejected: keeping the file only in the template (sessions in an app would have to fetch it every time) and a shared library for the design kit (a new dependency and more setup than three small apps need). The design kit files (`ui/theme/`) stay in the apps.

## 2026-10-14: Launcher icon shows a checkmark instead of a play button

The icon's TV stand (a short bar under the screen) did not read as a stand, and a play button says "video player" more than "watch list". The screen is now a little taller, has no stand, and carries a white checkmark ("watched"). Gradient, white background and the one-shape, one-detail rule from `DESIGN.md` are unchanged. The notification icon (previously a stock Material TV) is now the themed layer scaled to 24dp, so it matches. Rejected: keeping the play button with a small checkmark badge (too small at launcher sizes), stacked screens and a progress bar (busier).

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
- **CI** now also runs **lint** (errors fail the build, no baseline) and the release-script tests, cancels outdated runs and uploads `watchlist-<version>-debug.apk` unzipped. The existing launch test and R8 keep check stay. Enabling lint surfaced 18 errors, all fixed in code: string lookups in Compose now use `LocalResources` instead of `LocalContext` (configuration-aware), and the detail screen's error state uses the scaffold padding.
- **Release workflow** from the template: version maths in `scripts/release_version.py` (unit tested), the signing key checked before the build, tests and lint before the signed build, the R8 mapping file attached, published with `gh release create` instead of a third-party action. The secret names without `ANDROID_` keep working.
- **Debug builds use `applicationIdSuffix = ".debug"` and the name "Watchlist Dev"**, so a pull request's APK installs next to the released app. Trade-off: both apps register the sign-in redirect and the simkl.com links, so Android asks which app opens them. Rejected: a separate debug redirect scheme, because the SIMKL API app only accepts its registered redirect URI.
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

The object graph is five lazily created objects in `WatchlistApp`; the navigation is a stack of five route types. Repositories already hold the long-lived state, so ViewModels would only forward it. Revisit if screens get complex enough to need per-screen state holders.

## Robolectric launch test

`LaunchTest` starts `MainActivity` signed out and signed in under Robolectric. Start-up and composition crashes fail CI with their stack trace, without the emulator an instrumented test would need.
