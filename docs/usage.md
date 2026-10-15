# Usage guide

For people using the app. It assumes the app is installed (see the [README](../README.md#quick-start)).

## Signing in

1. Open the app and tap **Sign in with SIMKL**.
2. simkl.com opens in the browser. Sign in there if needed and approve the app.
3. SIMKL sends you back to the app, which loads your lists.

Nobody needs a SIMKL developer account. If the browser shows the app chooser (when a test build is also installed), pick the app you started the sign-in from.

**Log out:** Profile (top bar) › **Log out**. This deletes the token and the cached library from the phone.

## Navigation

- The floating **bottom bar** holds the destinations: **TV Shows**, **Movies**, **Anime**, **Discover** and **Calendar**. Change their order and turn them on or off in *Settings › Bottom bar*.
- **Tabs** switch between parallel sections of a destination (library shelves, Discover's media types).
- The **sort** button in the top bar of TV Shows, Movies and Anime sorts the open tab; **chips** filter what you are looking at.
- **Search** and your **profile** are in every destination's top bar.

## TV Shows and Anime

Shelves: **Up next** (shows with an aired, unwatched episode), **Watched** (caught up or completed, with the next air date when known), **Plan to watch**, **On hold**, **Dropped**. Every poster shows an *Airing* or *Ended* badge.

- **Mark the next episode watched:** tap ✓ on a show in Up next. Episodes in progress (paused on another device) come first and show their progress; marking one watched asks first, because it removes the saved progress.
- **Sort** with the sort button in the top bar (kept per tab): recently watched, recently added, title, my rating or release date. On Anime, the **Type** chip above each tab filters by type (TV, movie, OVA, ONA, special).
- **Continue from the latest episode** (*Settings › Library*): Up next starts after the most recently watched episode, so skipped earlier episodes or seasons don't keep a show in Up next. SIMKL's API has no "skip", so this is decided in the app.
- **Hide specials** (*Settings › Library*): leaves season 0 episodes out of episode lists.

## Movies

Shelves: **Plan to watch**, **Watched**, **Dropped**, with the same sort button.

## Details

Tap any poster. You get fanart, ratings (SIMKL, IMDb, MAL…), genres, overview, airing info, trailers, recommendations, links (SIMKL, IMDb, TMDB, MAL, AniList…), your list status, your 1–10 rating, **Mark as watched** / **Check in**, and the full episode list per season.

The show's status is shown in the app's language, and the weekly broadcast day and time are converted from the broadcaster's time zone to your phone's time zone. The episode list opens on the season you are watching: the season of a paused episode, else the season of your latest watched episode (not season 1 when you skipped earlier seasons).

In the episode list:

- tap an episode to read its description;
- tick its checkbox to mark it watched;
- long-press an episode to mark everything up to it;
- mark a whole season at once.

Links to `simkl.com/tv/…`, `/anime/…` and `/movies/…` from other apps can open straight in these details.

## Discover

TV Shows / Anime / Movies tabs with trending today, this week and this month, airing today, premieres, best of all time and most voted. **Surprise me** opens a random title.

## Calendar

Upcoming episodes and releases of titles in your lists, filtered by type with the chips.

## Profile

Watch-time statistics per type and status, and **Log out**.

## Syncing

The app syncs when it opens or comes back to the foreground (at most every 15 minutes) and when you pull down to refresh. Everything is cached, so the app opens instantly and works offline; changes you make need a connection.

## New-episode notifications

Turn on *Settings › Library › New episode notifications* (Android 13+ asks for permission). A few times a day the app checks SIMKL's public episode calendar against your library as last synced and notifies you about newly aired episodes of shows you're watching: one notification per show, also when several episodes came out at once. If your phone was off or saving battery, episodes from the past week are still announced at the next check. Each notification shows the show's poster; expand it to see the show's backdrop picture. Tap a notification to open the show.

## Appearance and language

- *Settings › Appearance*: System / Light / Dark theme and, on Android 12+, **Dynamic color** (wallpaper colours instead of the app's violet; off by default).
- Language follows the phone, or Android 13+'s per-app setting: Settings › System › Languages › App languages › Tracker for Simkl. English, Dutch, French, German, Spanish and Italian.

## Common problems

| You see | What to do |
|---|---|
| "Your SIMKL login expired. Log out and in again." | Profile › Log out, then sign in again. |
| "Sign-in failed: …" right after approving | Start the sign-in again from the app (an old browser tab can't finish a new sign-in). |
| "This build has no SIMKL client ID" | The APK was built without `-PsimklClientId`. Install a release APK. |
| A crash report on start | The app crashed last time. Tap **Copy crash report**, paste it in a GitHub issue, then tap **Continue**. |
| No notifications | Check that notifications are on in Settings and allowed for the app in Android's settings. They only cover shows you're watching, as of the last sync. Some phones stop background work for apps that are "optimized" or closed from the recent apps list: open the app once (that restarts the checks) and set its battery use to *Unrestricted* in Android's settings. |
