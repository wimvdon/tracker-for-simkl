# Usage guide

For people using the app. It assumes the app is installed (see the [README](../README.md#quick-start)).

## Signing in

1. Open the app and tap **Sign in with SIMKL**.
2. simkl.com opens in the browser. Sign in there if needed and approve the app.
3. SIMKL sends you back to the app, which loads your lists.

Nobody needs a SIMKL developer account. If the browser shows the app chooser (when a test build is also installed), pick the app you started the sign-in from.

**Sign out:** Profile (top bar) › **Sign out**. This deletes the token and the cached library from the phone.

## Navigation

- The **navigation bar** at the bottom holds the destinations: **TV Shows**, **Movies**, **Anime**, **Discover** and **Calendar**. Change their order (drag a row by its handle) and turn them on or off in *Settings › Bottom bar*.
- **Content** (*Settings › Content*): turn **TV Shows**, **Anime** and **Movies** on or off for the whole app. A type that is off disappears from the bottom bar (and its row in *Settings › Bottom bar*), Discover's tabs, the Calendar, Search, the profile's stats and new-episode notifications. At least one stays on. Your SIMKL lists don't change, and turning a type back on brings everything back (episodes that aired while it was off are not notified afterwards).
- **Tabs** switch between parallel sections of a destination (library shelves, Discover's media types).
- The **sort** button in the top bar of TV Shows, Movies and Anime sorts the open tab; the **filter** button next to it filters it. Each tab offers only the options that make sense there (see below). The Calendar has a filter button too; **chips** filter search results.
- **Search** (the whole SIMKL catalogue, not just your lists) and your **profile** are in every destination's top bar. There is no separate search inside a tab: sort by title to find something in a list. Search always opens on **All**, with a row of posters per type (swipe sideways for more); tap a type chip for a full list of that type with descriptions.

## TV Shows and Anime

Shelves: **Up next** (shows with an aired, unwatched episode), **Watched** (caught up or completed, with the next air date when known), **Plan to watch**, **On hold**, **Dropped**. Every poster shows an *Airing* or *Ended* badge.

- **Mark the next episode watched:** tap ✓ on a show in Up next. Episodes in progress (paused on another device) show their progress and keep their place in the chosen sort order; marking one watched asks first, because it removes the saved progress.
- **Sort and filter** with the buttons in the top bar, kept per tab. A dot on the filter button shows a filter is on.

  | Tab | Sort (default first) | Filter |
  |---|---|---|
  | Up next | Recently watched, Fewest episodes left, Recently added, Title, Release date | Status (airing / ended), Type (Anime) |
  | Want to watch | Recently added, Title, Release date | Status, Type (Anime) |
  | Watched, On hold, Dropped | Recently watched, Recently added, Title, My rating, Release date | My rating (rated by me / not rated), Status, Type (Anime) |

  There is no "my rating" on Up next and Want to watch: you rate what you have seen. On Movies, Want to watch sorts like the shows' (no filter button); Watched and Dropped have the same sorts as the shows' Watched tab and the my-rating filter.
- **Continue from the latest episode** (*Settings › Library*): Up next starts after the most recently watched episode, so skipped earlier episodes or seasons don't keep a show in Up next. SIMKL's API has no "skip", so this is decided in the app.
- **Hide specials** (*Settings › Library*): leaves season 0 episodes out of episode lists.

## Movies

Shelves: **Plan to watch**, **Watched**, **Dropped**, with the same sort button.

## Details

Tap any poster. You get fanart, your list status, your 1–10 rating, **Watched** / **Check in**, the overview, an info card (ratings from SIMKL, IMDb, MAL… as tiles with their vote counts, then rank, genres and status; **Show more** adds airing info, release date, episodes, network, country, director, budget and more, each with an icon), trailers, recommendations, links (SIMKL, IMDb, TMDB, MAL, AniList…), and the full episode list per season.

The show's status is shown in the app's language, and the weekly broadcast day and time are converted from the broadcaster's time zone to your phone's time zone. The episode list opens on the season you are watching: the season of a paused episode, else the season of your latest watched episode (not season 1 when you skipped earlier seasons).

In the episode list:

- tap an episode to read its description;
- tick its checkbox to mark it watched;
- long-press an episode to mark everything up to it;
- mark a whole season at once.

A title you paused part-way (on another device) shows a **progress banner** with its own **Watched** button. While the banner is there, the plain Watched button for that same movie or episode is hidden, so there is only one; **Check in** stays.

**Add to list** sits in the title bar, next to Share (list icon; once the title is in a list it shows the list's name) and opens the list statuses. **Your rating** is the tile on the right of the info card's ratings row, as tall as SIMKL's and the other rating tiles; it stays in place while the other ratings scroll, and you tap it to open the 1–10 rating. Below the poster, **Watched** (check mark) marks it watched, and **Check in** (TV icon) tells SIMKL you are watching it right now. Check in asks first and explains itself; SIMKL then marks the title watched when it ends.

Links to `simkl.com/tv/…`, `/anime/…` and `/movies/…` from other apps can open straight in these details.

## Discover

TV Shows / Anime / Movies tabs with trending today, this week and this month, airing today, premieres, best of all time and most voted. **Surprise me** opens a random title.

## Calendar

Upcoming episodes and releases of titles in your lists. The **filter** button in the top bar shows or hides TV shows, anime and movies (ticked = shown; a dot on it means something is hidden).

## Profile

Watch-time statistics per type and status, then **My ratings** (with how many titles you rated), then sync and your SIMKL page, and **Sign out** on its own at the bottom. Settings is the gear at the top.

**My ratings** shows everything you rated, a row per type (TV Shows, Anime, Movies; only the types turned on in *Settings › Content*), highest rating first. Tap a row's header (with the **>**) to see all of that type in a grid.

## Syncing

The app syncs when it opens or comes back to the foreground (at most every 15 minutes) and when you pull down to refresh. Everything is cached, so the app opens instantly and works offline; changes you make need a connection.

## New-episode notifications

Turn on *Settings › Library › New episode notifications* (Android 13+ asks for permission). A few times a day, and shortly after an episode of a show you're watching airs, the app checks SIMKL's public episode calendar against your library as last synced and notifies you about newly aired episodes of shows you're watching: one notification per show, also when several episodes came out at once. If your phone was off or saving battery, episodes from the past week are still announced at the next check. Each notification shows the show's poster; expand it to see the show's backdrop picture. Tap a notification to open the show.

## Appearance and language

- *Settings › Appearance*: System / Light / Dark theme and, on Android 12+, **Dynamic color** (wallpaper colours instead of the app's violet; off by default).
- Language follows the phone, or Android 13+'s per-app setting: Settings › System › Languages › App languages › Tracker for Simkl. English, Dutch, French, German, Spanish and Italian.

## Common problems

| You see | What to do |
|---|---|
| "Your SIMKL sign-in expired. Sign out and sign in again." | Profile › Sign out, then sign in again. |
| "Sign-in failed: …" right after approving | Start the sign-in again from the app (an old browser tab can't finish a new sign-in). |
| "This build has no SIMKL client ID" | The APK was built without `-PsimklClientId`. Install a release APK. |
| A crash report on start | The app crashed last time. Tap **Copy crash report**, paste it in a GitHub issue, then tap **Continue**. |
| No notifications | Check that notifications are on in Settings and allowed for the app in Android's settings. They only cover shows you're watching, as of the last sync. Some phones stop background work for apps that are "optimized" or closed from the recent apps list: open the app once (that restarts the checks) and set its battery use to *Unrestricted* in Android's settings. |
