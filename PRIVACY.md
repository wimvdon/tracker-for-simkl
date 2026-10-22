# Privacy policy

_Last updated: 2026-10-22_

Tracker for Simkl is an unofficial client for [SIMKL](https://simkl.com). It has no analytics, advertising or third-party crash reporting.

## What the app sends, and to whom

- **SIMKL (api.simkl.com, simkl.com).** When you sign in, simkl.com opens in your browser and sends the app an access token. The app then uses SIMKL's API to read and change your lists, ratings, watch history, playback progress, profile and statistics, and to search and browse titles. Every request carries the app's public client ID and name and version; requests for your data carry your access token.
- **SIMKL's data files (data.simkl.in).** Trending lists and release calendars are downloaded as public files. Nothing about you is sent with them.
- **TMDB (api.themoviedb.org), only if you add your own TMDB token.** In *Settings › Cast and crew* you can enter a free API Read Access Token from [TMDB](https://www.themoviedb.org/). The app then asks TMDB for the cast and crew of a title when you open its details page, for a person's page when you open it, and for people matching what you type in Search, sending your token, the title's or person's TMDB ID or your search words, and your phone's language. Nothing about your SIMKL account or library is sent. Without a token the app never contacts TMDB. TMDB's [privacy policy](https://www.themoviedb.org/privacy-policy) applies.
- **Images (wsrv.nl).** Posters, backgrounds and cast photos are loaded through the wsrv.nl image proxy that SIMKL asks apps to use (cast photos come from TMDB's image server, image.tmdb.org, which only the proxy contacts). The proxy sees which image is requested and your IP address.
- **Links you open** (trailers on YouTube, IMDb, TMDB, MyAnimeList and others) open in your browser or the matching app, under their own privacy policies.

## What the app stores on your device

- Your SIMKL access and refresh tokens, your TMDB token if you entered one, and your settings (theme, bottom bar, content types, library options, notifications).
- A copy of your library and of show details, so the app opens fast and works offline.
- Which episodes and movies it already notified you about, and the upcoming episodes of shows you're watching and releases of movies you plan to watch (so it can still notify you if a check runs late).
- An HTTP and image cache.
- If the app crashes: the error's stack trace with the app version and phone model, shown to you the next time the app opens so you can copy it. It is deleted after it is shown and never sent anywhere.

Only your settings (theme, bottom bar, content types and library options) are included in Android's own backup to your Google account, if you turned that on, and in a transfer to a new phone, so they come back after a reset or on a new phone. Nothing else above is: not your tokens (you sign in again on a new phone), not your TMDB token (you enter it again), not the library copy (downloaded again after signing in), not the notification setting (it needs the permission again on a new phone). Signing out deletes the tokens and the library copy; uninstalling the app deletes everything.

## Background activity

Only when you turn on notifications for new episodes and releases: about every 4 hours, with a network connection, the app downloads SIMKL's public calendar files (episodes, and movie releases when movies are on) and compares them with your library as last synced. For each new episode or movie it announces, it downloads that title's poster (and a show's backdrop) picture (through wsrv.nl, as everywhere in the app) to show in the notification. It does not call SIMKL's API or TMDB in the background.

## Permissions

- **Internet** and **network state**: to talk to SIMKL (and TMDB, if you added a token).
- **Notifications** (Android 13+, optional): for notifications about new episodes and movie releases.

If this changes, this file will be updated **before** the change is released.

Questions: open an issue at https://github.com/wimvdon/tracker-for-simkl/issues.
