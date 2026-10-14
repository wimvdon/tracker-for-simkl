# Privacy policy

_Last updated: 2026-10-14_

Tracker for Simkl is an unofficial client for [SIMKL](https://simkl.com). It has no analytics, advertising or third-party crash reporting.

## What the app sends, and to whom

- **SIMKL (api.simkl.com, simkl.com).** When you sign in, simkl.com opens in your browser and sends the app an access token. The app then uses SIMKL's API to read and change your lists, ratings, watch history, playback progress, profile and statistics, and to search and browse titles. Every request carries the app's public client ID and name and version; requests for your data carry your access token.
- **SIMKL's data files (data.simkl.in).** Trending lists and release calendars are downloaded as public files. Nothing about you is sent with them.
- **Images (wsrv.nl).** Posters and backgrounds are loaded through the wsrv.nl image proxy that SIMKL asks apps to use. The proxy sees which image is requested and your IP address.
- **Links you open** (trailers on YouTube, IMDb, TMDB, MyAnimeList and others) open in your browser or the matching app, under their own privacy policies.

## What the app stores on your device

- Your SIMKL access and refresh tokens, and your settings (theme, bottom bar, library options, notifications).
- A copy of your library and of show details, so the app opens fast and works offline.
- Which episodes it already notified you about.
- An HTTP and image cache.
- If the app crashes: the error's stack trace with the app version and phone model, shown to you the next time the app opens so you can copy it. It is deleted after it is shown and never sent anywhere.

None of this is included in cloud backups or device-to-device transfers. Signing out deletes the tokens and the library copy; uninstalling the app deletes everything.

## Background activity

Only when you turn on new-episode notifications: about every 4 hours, with a network connection, the app downloads SIMKL's public calendar files and compares them with your library as last synced. It does not call SIMKL's API in the background.

## Permissions

- **Internet** and **network state**: to talk to SIMKL.
- **Notifications** (Android 13+, optional): for new-episode notifications.

If this changes, this file will be updated **before** the change is released.

Questions: open an issue at https://github.com/impca201/tracker-for-simkl/issues.
