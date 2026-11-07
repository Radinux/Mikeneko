<div align="center">

<img src="docs/logo.svg" width="140" alt="Mikeneko logo: a calico cat head">

# Mikeneko 三毛猫

ฅ^•ﻌ•^ฅ **A free YouTube client for Android, made of liquid glass** ✧･ﾟ<br>
No ads · no Google account · with cat ears (=^･ω･^=)

[![Latest release](https://img.shields.io/github/v/release/Radinux/Mikeneko?style=for-the-badge&color=E07A2E&label=version)](https://github.com/Radinux/Mikeneko/releases/latest)
![Android 8+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
[![License GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-55504D?style=for-the-badge)](LICENSE.txt)

### [⛩️ Download Mikeneko ⛩️](https://github.com/Radinux/Mikeneko/releases/latest)

</div>

---

## 🌸 What it can do (ﾉ◕ヮ◕)ﾉ✧

- 🗡️ **No ads**, with **SponsorBlock** skipping sponsors and intros on its own (the "paw swipe")
  and the **dislikes** from Return YouTube Dislike.
- 🏯 **"For you" home feed** computed on the phone (history, suggestions, subscriptions) and a
  **shorts blocker**.
- 📜 **Subscriptions without an account**: import from NewPipe, FreeTube or Google Takeout, filter by
  channel, "Unwatched".
- 🎧 **A real music player**: YouTube Music (songs, albums, artists, playlists), auto Mix,
  "For you" and "Your mixes", synced lyrics, a dancing spectrum wave, your own playlists.
- 🍙 **yt-dlp downloads**: video, MP3, FLAC, Opus, M4A, sponsors cut out, offline listening.
- 🐾 **PiP mini player** with cat ears inside the app, and real system PiP when you leave it.
- 🔮 **Liquid glass**: dock, buttons and music window in glass that refracts the screen (Android 13+).
- 💮 Comments, chapters, channel and playlist pages, a **Library** (history, watch later, likes).
- 🌙 **"Did you see the time?"** after an hour of watching (－_－) zzZ
- 🍥 **Automatic updates** from GitHub, in 🇬🇧 English and 🇫🇷 French.

## 🎴 Install (｀・ω・´)ゞ

1. Download the latest APK from the
   [releases page](https://github.com/Radinux/Mikeneko/releases/latest)
   (`arm64-v8a`, for every recent phone).
2. Open it on the phone and allow the install.
3. That's it: the app then updates itself ٩(◕‿◕)۶

## 🥷 Hacking on it

**Version: 2.6.7** (published as a GitHub release; the last APK shared by hand was `Mikeneko-2.1.2.apk`).
Versioning is automatic: `app/version.properties` holds the version of the next release build; every
`assembleRelease` / `installRelease` uses it, then prepares the next one (last digit +1, `versionCode`
+1). New feature: `minor` +1 and `patch=0` by hand. There is no x.20: where x.20.0 would come, it's a
major version ((x+1).0.0) instead.

📜 The project scrolls: `TECHNICAL-NOTES.md` (how and why, pitfalls, procedures) and `FIXES.md`
(to do / to check).

Source code and APKs live together in this repository: every release is attached to the tag of the
exact source it was built from (GPL-3.0).

## 🎌 What's new ⸜(｡˃ ᵕ ˂ )⸝♡

### 2.6.0 → 2.6.7

- **Music "For you".** On the Music home, based on what you listen to:
  - **For you**: a shelf of songs to discover, taken from the YouTube Music mixes of your 3 most
    played artists lately, without what you already played or saved in a playlist;
  - **Your mixes**: themed playlists made for you, with a mosaic cover: one "*Artist* Mix" per
    favourite artist (them and similar artists), and **Rediscover** (songs from your playlists you
    haven't played in a while). A mix opens as a page (play, shuffle); **Keep** turns it into a real
    playlist.
  - At most 3 requests to YouTube, kept for 6 h; recomputed when your artists change.
- **Downloads fixed**: "yt-dlp could not be installed" (with an empty message) in the release build.
  R8 shrinking broke the library that unpacks Python and ffmpeg; it is now kept. The error also shows
  its real cause, and you can retry without restarting the app.
- 2.6.1: a download failing with a Python trace ("Traceback…"): the app now cleanly reinstalls Python
  and yt-dlp, then retries once. The error shown is the real reason (the end of the trace), and the
  full trace goes to the log.
- 2.6.2: the music window's **spectrum** becomes a **continuous wave** rising from the bottom of the
  window, behind the buttons. The bars above the progress bar pushed the buttons too low (cut off in
  landscape). And in landscape with the status bar shown, the **dock** moves below its black band
  (its top was clipped).
- 2.6.3: the wave is **more colourful** and **fades out towards the bottom**; it no longer shows the
  progress (that's the progress bar's job). Only its crest has an outline.
- 2.6.4: the wave takes the **cover colour** (like the window, but brighter) and becomes **liquid
  glass**: under its crest, the background is refracted like under the glass buttons, with a bright
  highlight on the crest (Android 13+; older versions get the drawn wave).
- 2.6.5: in landscape, the **dock** is centred on the screen when the status bar is hidden (it sat too
  high), and centred between the status bar and the bottom of the screen when it is shown.
- 2.6.6: the glass wave now refracts **the page behind the window**. Before, it redrew the window's
  own veil on top of itself: it was opaque and the glass didn't show. Its tint is lighter too.
- 2.6.7: **the source code is public** and releases now live in this repository
  ([Radinux/Mikeneko](https://github.com/Radinux/Mikeneko)); the app checks for updates here. The old
  [Mikeneko-releases](https://github.com/Radinux/Mikeneko-releases) repository is archived (its APKs
  stay downloadable). Settings › About links to the source code.

### 2.5.0 → 2.5.3

- **Music spectrum.** Above the progress bar of the music window, 40 rounded bars dance with the
  song's frequencies (bass on the left, treble on the right): orange on the part already played,
  pale on the rest; they fall back on pause. The sound is analysed inside the player itself (no
  microphone needed), and only while the window is open.
- 2.5.1: the status bar's black band has rounder inverted corners (34 dp).
- 2.5.2: the spectrum is **smooth** (it stuttered: sound arrives in chunks, about 4 per second; it is
  now analysed on every screen frame).
- 2.5.3: in the **music pill**, the cover stays in place (it crossfades) and only the title follows
  the finger: as soon as you start dragging, the **next (or previous) song's title** slides in next to
  it, then settles. The music window opens and closes with **a single small bounce** (it bounced too
  much).

### 2.4.0

From the "Mikeneko fixes list":
- **Shorts blocker fixed.** Some shorts still got through: "For you" feeds on the history, which
  forgot that a video was a short, and the subscriptions feed didn't say so. Now the RSS `/shorts/`
  link, the flag kept in history and lists, and any video of one minute or less all count as shorts.
- **Music playlists**: the number of songs and the **total duration** under the title ("24 songs ·
  1 h 32 min"), also for YouTube Music albums and playlists; no more numbers in front of songs (they
  were cut after 99).
- **Playlist covers**: "Cover" picks a photo from the phone; without a photo, a **mosaic** of the
  first 4 songs' covers (everywhere: Music home, Your music, the playlist page, video playlists in the
  Library). "Mosaic" goes back to the mosaic.
- **Lyrics**: close the panel by **pulling it down** (from the top bar, or by keeping pulling when the
  lyrics are at the top); it follows the finger and springs back if released early. And while the
  lyrics are open, **the screen stays on**.
- **Status bar shown** (the "hidden" setting turned off): a black band behind it, with inverted
  rounded corners, so nothing slides under the clock and icons.
- **More transparent music player**: the window's veil and the dimming behind it are lighter.

<details>
<summary><b>🏮 The clan archives: versions 2.3.0 → 1.1.0</b></summary>

### 2.3.0

- **Your shorts.** A bookmark in the Shorts player (above "share") keeps the short in
  **Library › Your shorts**, a list separate from videos: a grid of tall thumbnails; a tap opens the
  Shorts player and you swipe through your saved shorts; ✕ removes one. The list is also in the
  Mikeneko backup (.xml, `<shorts>` section).
- **"No shorts" setting** (Settings › Playback): no shorts anywhere: home and "For you", search,
  subscriptions, player suggestions, channels' Shorts tab and the "Your shorts" tile.

### 2.2.0 → 2.2.1

- **Shorts player.** A short (a channel's Shorts tab, search, home, subscriptions, a `/shorts/` link)
  opens **full screen, upright**, and loops. Swipe up / down: next / previous short, first those of the
  list it was opened from, then shorts suggested by YouTube. A tap pauses. On top, in glass: the channel
  (to its page) and the title at the bottom, and on the right Like (filled thumb once liked), comments
  and share; a thin orange line shows the progress. Back (or ⌄): playback stops and you return where
  you came from. No full screen when rotating the phone, and a short takes no slot in the video resume
  list.
- 2.2.1: a short opened from subscriptions or "For you" still opened in the normal player (those lists
  don't know it's a short): it is now recognised once loaded (YouTube says so, or an upright video of
  3 min or less) and moves to the Shorts player.

### 2.1.8 → 2.1.12

- The player's "Like" thumb turns **filled** when the video is liked (on top of the orange capsule):
  you see right away that the tap worked.
- 2.1.10 (2.1.9 was built but never published): the update pill is no longer cut off ("Mikeneko 2.…"):
  two lines, "Update ready" above "Mikeneko X", and a short "Install" button.
- 2.1.10: in video search, the sort capsules (Filters, Best, Recent…) **move up and fade** with the
  top of the results, like the home categories (they stay while the filters are open); dragging
  vertically on them scrolls the results.
- 2.1.11: a test version with no changes, published once 2.1.10 was installed to see the new update
  pill (a pill always comes from the installed version).
- 2.1.12: the update pill **goes away on its own after 6 seconds** (it hid the top of the screen); the
  update stays in Settings › About and installs when you leave the app.

### 2.1.3 → 2.1.7

- **Big code cleanup, with nothing changed on screen.**
  - Google sign-in, switched off for a long time, is removed along with **Play Services**: the APK no
    longer depends on any Google service. Comments stay readable (read-only). The old code is still
    in the git history (`data/GoogleAccount.kt` in commit `11811f8`).
  - Big files are split: the player (`PlayerScreen`, `FullscreenPlayer`, `PlayerControls`), the music
    window (`MusicWindow`, `MusicDock`, `MusicPlayerContent`, `MusicQueue`, `MusicPlayerState`), the
    music pages (`MusicPages`, `MusicHome`, `MusicSearch`, `MusicPageHeader`); `VideoFrames` and
    `PlayerTypes` move out of `PlayerHolder`.
  - The Music tab's old "Radio" names become "Music" (`MusicScreen.kt`, `MusicState`, `MusicPage`,
    `Route.Musique`). A video's "Radio" button keeps its name.
  - Unused code, strings and icons removed; duplicates merged (time format `formatClock`, sharing
    `shareLink`, `lerp`); imports sorted; indentation fixed.
- 2.1.4: in portrait, the buttons in the middle of the video (previous / play / next) centre above the
  bottom bar: "next" was touching the settings button. Far too high; 2.1.6: they stay in the middle and
  only move up as much as needed (2.1.5 never installed).
- 2.1.7: same as 2.1.6, published as a GitHub release to arrive through the automatic update.

### 2.1.0 → 2.1.2

- **Automatic updates from GitHub.** APKs were published as releases on the public repository
  [Radinux/Mikeneko-releases](https://github.com/Radinux/Mikeneko-releases) (the code was private
  until 2.6.7). On launch (at most once a day), Mikeneko checks the latest release; if it is newer, the
  APK downloads in the background and **installs itself, without a tap** (Android 12+), when you leave
  the app and no music is playing. You only need to allow Mikeneko to install apps once (Settings ›
  About › Updates › Allow). Settings › About shows the status, "Check", "Install now" and "What's new".
- 2.1.1: a test version with no changes, published to try the automatic update.
- 2.1.2: a downloaded update shows a **glass pill at the top of the screen**, "Mikeneko X is ready"
  with **Update** (right now) and ✕ (later: it installs when leaving the app). Settings › About: the
  buttons no longer get squashed. Published only as a GitHub release (it reached the phone through
  the automatic update).

### 2.0.0 → 2.0.4

- **A real landscape player page**: the video and its controls on the left, everything else on the
  right in a scrolling column (time bar, SponsorBlock, title, channel, like / later / playlist /
  download…, description, comments, chapters, suggestions).
- **Leaving full screen while holding the phone in landscape no longer forces portrait**: you land on
  that page, still in landscape. Portrait is only forced if you entered full screen with the button
  while holding the phone upright.
- Opening a video in landscape (from the multi-column home, the mini player…) opens that page;
  rotating the phone from portrait still goes full screen, and the landscape page goes back to
  portrait with Android's usual rotation.
- 2.0.1: **the progress bar is inside the video** (portrait and landscape, like YouTube): with the
  controls, the time at the bottom left, the buttons on the right and the full bar below; without
  them, a thin orange line along the bottom edge of the video. The controls don't hide while dragging
  the bar or while marking a SponsorBlock segment.
- 2.0.2: in landscape, no more "Now playing" row above the video (too much space): the arrow to
  minimise is a glass button at the top left of the video, with the controls; the video takes 60 % of
  the width.
- 2.0.3: in landscape, the video is centred on the full screen height (it was shifted down by the
  status bar's height), and dragging the video down turns it into the mini player.
- 2.0.4: in landscape, entering or leaving full screen (button or back) **zooms**: the video grows from
  its place in the page until it fills the screen, and shrinks back (the page fades in behind). Before,
  it switched at once.

### 1.19.0 → 1.19.8

- **Full screen by rotating the phone: only the video rotates**, not the whole interface. On the
  player screen, Android no longer animates the rotation; the video starts from its place at the top
  of the portrait page, lying sideways, and rotates while growing until it fills the screen. Back in
  portrait, it rotates from full screen to its place, and the page fades in behind. The full screen
  button also waits for the screen to rotate before switching.
- 1.19.1 → 1.19.2: **the mini player as a calico cat head** (from a sketch, in the logo's colours): a
  white frame around the video, whose sides rise into big rounded ears; dark grey ear and patch on the
  left (darker inside), orange ear and patch on the right (light inside), dark outline. The ears pop
  up with a "boing" when it lands.
- 1.19.3 → 1.19.4: **no more purple**: missing music covers ("Your library", "Downloaded", "New
  playlist" tiles, the player pill…), the glow of pages without a cover, comment avatars without a
  photo and the ⋮ menu's pending thumbnails turn dark orange (`RouxNuit`, icons and initials
  `RouxClair`), in the calico theme.
- 1.19.5: **a video no longer shows up in Music.** Start a video then go to Music: the pill showed the
  video as a song (and its ▶ / ⏸ controlled the video). It now shows the song the video set aside (▶
  resumes it, the video stops), or "Pick some music"; opening the window brings that song back,
  paused.
- 1.19.6: **the video mini player is no longer in Music.** Going to Music pauses the video and hides its
  mini player; back on Home, Subscriptions or Library, it reappears, paused at the same spot. If a song
  replaced it meanwhile, it comes back (reloaded, paused) as soon as the music stops.
- 1.19.7 → 1.19.8: mini player: the buttons hide by themselves after 3 s (every tap restarts the timer),
  and the time bar only shows with them; everything fades in and out.

### 1.18.0 → 1.18.1

- **Subscription import moved to Settings › Backup** ("Import subscriptions": NewPipe, FreeTube,
  Google Takeout). The big import card no longer sits at the top of the Subscriptions tab; when there
  are no channels yet, a short note and an "Import" button lead to the settings.
- 1.18.1: **followed channels' pictures** are back. The Takeout import has none: they arrive while
  watching a video or opening a channel's page (with no extra request), and slowly in the background
  while the Subscriptions tab is open (one every 4 s, at most 30 per launch).

### 1.17.0 → 1.17.2

- **Progress bar on thumbnails**: an orange bar under the thumbnail of started videos (the last 10,
  see 1.16.0), everywhere there are thumbnails (home, search, channels, subscriptions, history,
  suggestions).
- **Suggestions in the player page**: under the comments, the "Up next" list, on top of the bottom
  glass drawer. Tapping a suggestion starts it and the page goes back to the top.
- 1.17.1: the bottom glass drawer only shows for a video from a playlist (Playlist / Up next tabs); a
  single video has its suggestions in the page.
- 1.17.2: full screen: the "Up next" button is centred on the screen, under the controls (it was
  centred in the space between the time and the exit button, and shifted by the notch).

### 1.16.0

- **Video resume**: the app remembers where you were in your last 10 videos, even after closing it.
  Reopening one of them resumes playback there. A finished video (less than 20 s from the end) or one
  barely started (less than 10 s) starts over. Not for music or live streams. "Clear history" also
  clears these positions, and nothing is kept while history is paused.

### 1.15.4 → 1.15.5

- **Offline = only what is downloaded.** Without a network, only downloaded playlists (and yt-dlp
  files) play. The hidden cache of songs played in full is still used when online (faster start, fewer
  requests to YouTube), but no longer offline. Removing a playlist's download therefore makes it
  unplayable offline right away. (1.15.4 deleted the songs from the cache on removal; 1.15.5 replaces
  that.) The current song and its neighbours stay in the player's memory and can finish playing.

### 1.15.3

- Album, playlist, artist and downloaded pages: **a single play button**. The header's button moves
  up with the page, then slides and shrinks into its place in the top pill, where it stays. Before, a
  second small button faded in inside the pill.
- These pages' **Shuffle** button is a toggle: orange while the list plays shuffled; another tap turns
  shuffle off (the order comes back, the song keeps playing). If another list is playing, the tap
  starts this one shuffled, as before.

### 1.15.2

- Music: when pulling the player window down, the glass could freeze halfway (around a quarter open).
  It now always settles, even if the gesture is cancelled.

### 1.15.1

- Home: swiping up or down starting on a category capsule scrolls the list (and, at the very top,
  pulling down refreshes). Before, the glass capsule grabbed the gesture.

### 1.14.7 → 1.15.0

- 1.14.7: **the Google Takeout import works in French** (`SubscriptionImport`): the header of
  `abonnements.csv` is translated to the account's language ("ID des chaînes,URL des chaînes,Titres des
  chaînes") and starts with a BOM; it is no longer read. Any line with a `UC…` id is a channel, and
  quoted titles (with commas) are read correctly.
- 1.15.0: **Mikeneko backup file in .xml** (`data/Backup.kt`, `MikenekoBackup`): Settings → Backup →
  "Export to a file" / "Import a file". It holds subscriptions, music and video playlists, saved albums
  and artists, favourite radios, "Watch later" and liked videos. Import adds what is missing without
  erasing anything (a playlist with the same name gets the songs it lacks) and says what it added. The
  Subscriptions "Import" button accepts this file too (it only takes the subscriptions from it).
  Checked on the Pixel 10 emulator: French Takeout (BOM, commas), export, import (1 channel, 2
  playlists, 2 albums/artists, 1 video added), reimport ("nothing new").

### 1.14.0 → 1.14.6

- **More liquid glass**:
  - Music pages (playlist, album, artist, downloaded): the bar that appears when scrolling down is a
    **glass pill** (title + orange play button), between back and ⋮; the list warps beneath it. It
    replaces the dark veil.
  - **Glass capsules** (`GlassChip`, `ui/components/Components.kt`), laid over the lists: home
    categories (drawn above the list in `HomeOverlay`, they move up and fade with the top of the list,
    `HomeState.categoriesShift`), search sort and filters, music library filters and music search
    kinds (fixed under the bar). Once selected, a capsule turns orange.
  - Music library: "Your library" sits in the bar's pill (`FixedPageBar`).
- 1.14.1: the glass pill stops taps once visible (they went through to the song below).
- 1.14.2: search history / suggestions, comments, full screen "Up next" and the playlist drawer: the
  frosted glass really shows (dark tint lightened from ~75 % to ~35-50 %, rounder edges, slightly
  stronger blur to keep text readable).
- 1.14.3: suggestions ("Up next") leave the player page for a **frosted liquid glass drawer** at the
  bottom (the playlist drawer, `PlaylistDrawer`, became a tabbed drawer): closed, an "Up next" bar +
  the next video; pulled up, the list. When the video comes from a playlist, two tabs, "Playlist" and
  "Up next". Tapping a suggestion starts it and closes the drawer.
- 1.14.4: this drawer is **clear liquid glass, not frosted** (the page shows through, warped at the
  edges); slightly darker tint and text shadows to keep it readable.
- 1.14.5: the **full screen** "Up next" panel (`FullscreenSuggestions.kt`) in clear liquid glass, not
  frosted (1.14.2 had blurred it by mistake); shadows under the titles.
- 1.14.6: this panel is centred (the camera notch's margin, on one side only, applies to both).

### 1.13.0 → 1.13.6

- **Download your own playlist, Spotify style** (`player/PlaylistDownloads.kt`): a round ⤓ in music
  playlists' header (progress ring, orange once everything is downloaded), a "Download" capsule on
  video playlists. Songs / videos go to two separate stores of the offline backup
  (`OfflineCache.Store.SavedMusic` / `SavedVideos`, in the app's data, with no limit or automatic
  deletion): nothing visible on the phone, everything plays in the app without a network. One at a
  time, with a 2.5 s pause between two (YouTube dislikes bursts); what was already played in full is
  copied from the playback cache, without the network. Videos in 720p at most. An item added to a
  downloaded playlist gets downloaded, an item removed (or the playlist no longer downloaded, after
  confirmation) is deleted. A failure (no network) is retried 10 min later. The small orange ⤓ to the
  right of a song = offline.
- A video from a downloaded playlist starts from the store (no YouTube stream), and its details
  (description, suggestions) arrive afterwards if the network answers.
- The app doesn't clear itself in the background during a playlist download.
- Settings → Offline: the space used by downloaded playlists as well ("Clear" doesn't touch them).
- 1.13.1: "Shuffle" on video playlists, "Watch later" and "Liked" (the shuffled list becomes the
  current playlist).
- 1.13.2: music pages (playlist, album, artist, downloaded): once the header is scrolled past, the
  title comes into the top bar next to back, and the play button stays pinned at the top right, over a
  dark veil (`PageBar` / `PageBarEffect`, like Spotify).
- 1.13.3 → 1.13.5: the bar really follows the scroll (1.13.2 never showed it); the veil, the title
  (moving up) and the play button (growing) come in progressively, pixel-exact, over the 110 dp before
  the header's play button goes under the bar (`FADE`), with no duplicate. 1.13.6: later, over 90 dp
  starting when the bottom of the header gets within 20 dp of the bar (`FADE_LEAD`). The home's
  "Downloaded" tile no longer splits in two (a single word = one line).

### 1.12.0 → 1.12.1

- **Hidden offline cache** (`player/OfflineCache.kt`): everything the player reads from YouTube goes
  through a Media3 cache (`SimpleCache`) in Android's cache folder, `hors_ligne/musique` and
  `hors_ligne/videos` (1 GB each, least recently played first out). Nothing is exposed (no files, no
  gallery). Keys are stable, "id:itag" (`setCustomCacheKey`), since stream URLs change every time; a
  small index (prefs `hors_ligne_musique` / `hors_ligne_videos`) keeps, per video, the keys and the
  title, channel and cover.
- **Music**: a song is loaded entirely as soon as it plays (`player/MusicLoadControl.kt`, up to 20 min
  and 40 MB), so it is whole in the cache even if you skip it. Played again later (tap, queue, song
  prepared in advance), it starts **from the cache without asking YouTube for a stream**: instant, and
  no "Source error". Details (artist, album, SponsorBlock) load afterwards, if the network answers.
  Without a network, it plays anyway.
- **Videos**: a video watched in full (video and audio) replays offline when YouTube doesn't answer (no
  network or refusal).
- Settings → "Offline": the space used (music, videos) and "Clear the offline backup".
- 1.12.1: 1.12.0 crashed at startup (`MusicLoadControl` delegated the `LoadControl` interface with `by`:
  Kotlin doesn't forward its Java default methods). It now extends `DefaultLoadControl`.

### 1.11.0

- **Your music library** (`MusicPage.Library`, in `ui/screens/MusicPages.kt`): a page with capsule
  filters (All / Playlists / Albums / Artists / Downloaded). All: new playlist, Downloaded, your
  playlists, then saved albums and artists; Downloaded: the songs directly (with Shuffle). The chosen
  filter stays when you come back from an album.
- **Save an album or an artist**: a "Save / Saved" capsule in their page's header. The notebook is
  `data/MusicLibrary.kt` (newest first, kept on the phone).
- Music home: a "Your library" tile first in quick access, and the "Your playlists" row becomes "Your
  library ›" (its title opens the page) with the saved albums and artists too (artists in a circle).

### 1.10.0 → 1.10.3

- **Music home in rows**: a greeting depending on the time; a quick access grid (Downloaded, your
  playlists, your latest songs); then rows scrolling sideways: "Recently played" (new history
  `data/MusicHistory.kt`, filled by every song or live stream played as audio), "Your playlists", "Your
  favourites", "Moods" as coloured cards, "Live · mood". A recent song restarts its mix, a live stream
  restarts as is.
- 1.10.1: quick access titles shrink to fit (no more words cut in two).
- 1.10.2: tapping the current song again restarts it without reloading it (no YouTube request on every
  tap); if it is in error, it really reloads.
- 1.10.3: NewPipeExtractor moves to the `dev` code of Nov 2 (commit `6cb36e9823` via JitPack): it has
  the new decryption of the streams' "n" parameter (#1545, #1556) and the client version read from
  `sw.js_data` (#1520), not yet in an official release. With the old decryption, YouTube refused or
  throttled streams ("Source error"). Go back to an official version (`gradle/libs.versions.toml`) as
  soon as 0.26.6 / 0.27 is out.

### 1.9.0 → 1.9.1

- **Music, first step of the redesign** (inspired by the big music apps, in our own style):
  - the player takes the cover's colour: a gradient from its dominant hue to night, gliding from one
    song to the next; the music dock's pill is tinted too (`ui/components/CoverColor.kt`, home-made,
    no library);
  - cleaner song rows: title on one line, a small animated equaliser on the current song
    (`PlayingBars`);
  - album / artist / playlist pages: a big cover on a halo of its colour, title on the left, a big
    round play button on the right, "Shuffle" (or "Mix") next to it.
  - 1.9.1: the pages' colour halo goes up to the top of the screen (no more cut under the back button).

### 1.8.0 → 1.8.1

- **Landscape redesigned**:
  - the dock becomes a glass column on the left (same sliding drop, top to bottom), the content moves
    to its right (`FloatingDock(vertical = true)`, `DOCK_RAIL_SPACE`);
  - video lists use several columns depending on the width (home, search, channels, shorts,
    playlists, subscriptions, Library); in portrait, a single one as before
    (`ui/components/AdaptiveList.kt`);
  - the music dock goes to the bottom right, narrower; its window opens to the right of the column
    dock, in two parts: the cover on the left, title / controls on the right (scrolling to the artist
    and the album).

### 1.7.0

- **Preview while dragging the seek bar** (video, portrait and full screen): a thumbnail of the video
  at the target spot and the time, above the cursor. The images come from YouTube's "storyboards"
  (sheets of small images, through NewPipe `previewFrames`), each sheet downloaded only once
  (`SeekPreview` in `ui/components/Video.kt`).

### 1.6.6

- Fix: with a language chosen in the settings (not "System"), the app froze the phone's whole
  configuration, orientation included: rotating the phone no longer put the video full screen. Only
  the language is enforced now (`AppLocale.overrideConfiguration`).

### 1.6.5

- Progress bar (video, full screen, music window): the part already loaded is drawn in transparent
  white ahead of the playback position (`PlayerHolder.bufferedPosition`).

### 1.6.4

- Fix: starting a video during music could, once the video loaded, restart the next song of the queue
  in video mode instead. The player was emptied then refilled (going through "ended"): it is now
  replaced at once, and the end of a song no longer restarts the queue if something else was started
  meanwhile.

### 1.6.3

- Dock: tapping Music goes back to the Music home (artist / album / playlist pages and search closed),
  like Home, Subscriptions and Library.

### 1.6.2

- **Previous song kept** (music): the last song played stays loaded right before the current one.
  "Previous" (within the first 3 seconds) or a tap on it in the queue goes back to it instantly. The
  player keeps at most [previous] [current] [prepared next] (`PlayerHolder.kept`).

### 1.6.1

- Fix: opening an artist page whose songs and albums weren't cached (from the player, for example)
  crashed the app (two "loading" items with the same key).

### 1.6.0

- **Scrolling music window**: under the controls, the **artist**'s card (to their page) and the song's
  **album** card (to the album). The album is found thanks to the description YouTube writes for
  YouTube Music songs (`Music.albumOf`), only when you scroll. At the very top, pulling down still
  folds the window (`MusicPlayerState.scrollConnection`).

### 1.5.0

- **Next song loaded in advance** (music): as soon as a song plays, the next one in the queue is
  prepared and placed behind it in ExoPlayer. The transition is instant (no more ~2 s of silence),
  "next" too. Rechecked automatically when the queue, repeat mode or sleep timer change
  (`PlayerHolder.syncPreload`).

### 1.4.0

- The music dock only shows in the Music tab (music keeps playing elsewhere, with the notification to
  control it).
- Starting a video during music sets it aside (song, position, queue): back in Music, it comes back
  paused where it was (`PlayerHolder.parkedMusic`).
- Idle for 15 min in the background (nothing playing, no download), the app clears itself as if it
  had been removed from recent apps; if the phone froze it meanwhile, it starts from scratch when
  reopened (`MainActivity.watchIdle`).

### 1.3.3

- "Autoplay" switch right on the video, at the top right (and at the top of full screen, next to the
  subtitles): orange with ▶ when on.

### 1.3.2

- **Autoplay**: at the end of a video (outside a playlist), the first suggestion starts on its own.
  "Autoplay" switch in the player settings and in Settings › Playback (on by default).

### 1.3.1

- **Release key**: the release build is signed with Mikeneko's key (see "Release key" below) instead
  of the debug key. Shared APKs can receive updates on top. A phone that had a version signed with the
  debug key must uninstall once (back up its data first: subscriptions, history, library).

### 1.2.0 → 1.3.0

- **Library** (replaces the Downloads tab): history, "Watch later", liked videos (the player's thumb),
  your own video playlists and downloads, all kept on the phone. Add from a video's ⋮ menu or the
  player's "Later" / "Playlist" buttons (`ui/screens/LibraryScreen.kt`, `data/VideoLibrary.kt`,
  `Playlists.videos`).
- The videos' ⋮ menu works everywhere (home, channels, Library, player), not only on the home.
- A video started from "Later", "Liked" or your own playlist chains with the following ones, with the
  playlist drawer in the player.

### 1.1.0

- **Radio → Music**: a real music player.
  - YouTube Music search (songs, albums, artists, playlists) with suggestions and history.
  - Album and artist pages, auto Mix (the queue extends on its own), 12 live moods.
  - Your own playlists (drag to reorder), downloaded music playable offline.
  - Shuffle, repeat (all / one), progress bar, synced lyrics (LRCLIB).
  - Next / previous in the notification and on the lock screen.
- **Liquid glass music dock**: pull the pill up (or tap it) and it stretches into a window, with a
  jelly bounce; fold it down to put it away.
- **Channel page**: banner, verified badge, expandable description, Videos / Shorts (grid) / Live /
  Playlists tabs. Playlist page with "Play all": videos chain on their own, and in the player a glass
  drawer pulled up lists the playlist (suggestions stay on the page).
- Video player: the channel's avatar and name under the title (to its page) and "Subscribe" on the
  right; buttons fade in and out, and so does loading.
- Tapping a video: its thumbnail flies to the player.
- **Stable volume** reserved for videos: off during music, it comes back on its own on the next video
  if it was on.
- Subscriptions: if YouTube's RSS feed stops answering (404 for every channel on 2026-11-03), videos
  come from the channels' "Videos" tab (NewPipe), with their duration. Long press on an avatar, or "See
  channel" when a channel is selected, to open its page.
- Dock: tapping Home goes back to the "For you" feed (search closed, or to the top if already there);
  tapping Subscriptions goes back to the list of all channels (channel page and filters closed).
- Home: the categories sit at the top of the page and scroll away.
- Search (home and music): the keyboard opens right away; ✕ clears the field without closing (on
  results: back to the bar, keyboard open; back returns to the results) and only closes search if the
  field is already empty. Scrolling the page again puts away suggestions and keyboard, like back. The
  history and suggestions appear and leave smoothly.
- Lighter app: APK from 174 to 59 MB (one APK per CPU, release build shrunk by R8), yt-dlp installed
  only when downloading, capped thumbnail cache.
- Removing the app from recent apps stops the music and really quits it (memory freed, everything
  starts from scratch), except during a download.
- **Animations at the same speed on every phone**: the app ignores the developer options' "animation
  scale" (only exception: animations turned off in accessibility). Global setting: `ANIMATION_SPEED` in
  `ui/AnimationSpeed.kt`, at 2 (everything runs twice as fast as the durations written in the code).

</details>

## ⚔️ Open the project

1. A recent Android Studio (late 2026), JDK 17 or newer.
2. **File › Open** → the `Mikeneko` folder.
3. Let Android Studio sync Gradle (it downloads Gradle 9.6.0 and the dependencies).
4. Run on a phone or an emulator (Android 8.0 minimum, **Android 13+ for the real liquid glass**).

From the command line: `./gradlew assembleRelease`, then
`adb install -r app/build/outputs/apk/release/app-arm64-v8a-release.apk`. One APK per CPU: `arm64-v8a`
for phones, `x86_64` for the emulator; no 32-bit.

### 🗝️ Release key ψ(｀∇´)ψ

Official releases are signed with Mikeneko's release key, kept **outside the project**
(`~/.config/mikeneko/mikeneko-release.jks` and its password in `~/.config/mikeneko/signing.properties`;
the path can be changed with the `MIKENEKO_SIGNING` variable). Without that file, the release build is
signed with the debug key, which is fine for your own builds. Back the key up: without it, no update
can be installed on top of an existing install.
SHA-256 fingerprint of the official APKs:
`0F:0B:6E:19:0A:E7:2C:34:63:A0:20:03:61:12:9D:54:F9:F4:B8:6B:CF:DC:70:8A:09:99:3B:03:A7:96:83:DC`.

### 🍱 App size

- Release APK arm64: ~59 MB (of which ~50 MB are Python + ffmpeg for yt-dlp, ~8 MB of code after R8).
  The debug APK is neither shrunk nor compressed (~120 MB): it is only for development.
- yt-dlp installs itself (~125 MB unpacked in `no_backup/`) only the first time the Downloaded tab is
  opened, and checks for its update at most once a day.
- Thumbnails: disk cache capped at 64 MB (`MikenekoApp.newImageLoader`).
- Downloaded files (`Android/data/app.mikeneko/files/Movies` and `Music`) also count in the app's
  "data" for Android.

All versions are in `gradle/libs.versions.toml`. Android Studio will offer to update them: accept,
especially for **NewPipeExtractor** (it is the one following YouTube's changes).

## 🍣 What's inside

| Feature | Where |
| --- | --- |
| Home (NewPipe kiosk, search with suggestions and filters, categories) + floating glass header | `ui/screens/HomeScreen.kt`, `ui/screens/SearchResults.kt` |
| Subscriptions separate from the home, in chronological order, filter by channel, "Unwatched"; fallback if YouTube's RSS goes down | `ui/screens/SubscriptionsScreen.kt`, `data/Subscriptions.kt` |
| Channel pages: banner, Videos / Shorts / Live / Playlists tabs, playlist pages | `ui/screens/ChannelScreen.kt` |
| Import without an account: NewPipe (.json), FreeTube (.db), Google Takeout (.csv) | `data/Subscriptions.kt` |
| Video player: glass buttons on the video, automatic SponsorBlock ("paw swipe"), dislikes, chapters, channel + "Subscribe", autoplay | `ui/screens/PlayerScreen.kt`, `FullscreenPlayer.kt`, `PlayerControls.kt`, `player/` |
| Playlist drawer in the player (pull up) | `ui/components/PlaylistDrawer.kt` |
| "Mark a segment": send sponsors, intros, credits… to SponsorBlock (anonymous) | `ui/components/SegmentMarker.kt`, `data/CommunityApis.kt` |
| Comments (read-only, no account: replies, links and timestamps) | `data/Comments.kt`, `ui/components/CommentsUi.kt` |
| In-app PiP mini player (video + cat ears) and real system PiP when leaving the app | `ui/components/Video.kt`, `MainActivity.kt` |
| Music (formerly Radio): the music dock (Music tab only) stretches into a liquid glass window (pull up / tap), screen-off playback; a video sets the music aside | `ui/screens/MusicWindow.kt` (+ `MusicDock`, `MusicPlayerContent`, `MusicQueue`, `MusicPlayerState`), `ui/screens/MusicScreen.kt`, `player/PlaybackService.kt` |
| Music: YouTube Music search (songs, albums, artists, playlists), auto Mix, moods | `data/Music.kt`, `ui/screens/MusicPages.kt` (+ `MusicHome`, `MusicSearch`, `MusicPageHeader`) |
| Spectrum wave in liquid glass behind the music controls | `ui/glass/LiquidWave.kt`, `player/AudioSpectrum.kt` |
| Queue: chaining, shuffle, repeat, drag to reorder; next/previous in the notification | `player/PlayerHolder.kt`, `player/PlaybackService.kt` |
| Your own playlists, downloaded music offline, songs' ⋮ menu | `data/Playlists.kt`, `data/LocalMusic.kt`, `ui/screens/MusicUi.kt` |
| Synced lyrics (LRCLIB) | `data/Lyrics.kt`, `ui/screens/LyricsSheet.kt` |
| Library: history, watch later, liked, your shorts, video playlists | `ui/screens/LibraryScreen.kt`, `data/VideoLibrary.kt` |
| yt-dlp downloads (in the Library): video, MP3, FLAC, Opus, M4A, sponsors cut out | `data/Downloads.kt`, `ui/screens/DownloadsScreen.kt` |
| "Did you see the time?" after 1 h | `ui/screens/SleepReminder.kt` |
| Settings: language, content region, playback (including autoplay), SponsorBlock, history, bedtime reminder, backup, updates | `ui/screens/SettingsScreen.kt`, `settings/` |
| Automatic updates from this repository's releases | `update/AppUpdates.kt` |
| Lifecycle: removing the app from recents or 15 min idle in the background clears it | `player/PlaybackService.kt`, `MainActivity.kt` |
| "For you": local algorithm (history + YouTube suggestions + subscriptions) | `data/ForYou.kt`, `data/WatchHistory.kt` |
| Share a YouTube link to Mikeneko / open YouTube links | `AndroidManifest.xml` |

### ⚡ Animation speed (chidori mode)

`MainActivity` hands `setContent` its own recomposer (`fixedSpeedRecomposer()` in
`ui/AnimationSpeed.kt`) with a fixed `MotionDurationScale`. Every Compose animation (menus and popups
included) runs at the same speed whatever the phone's animation scale setting. `ANIMATION_SPEED = 2`:
an animation written `tween(400)` really lasts 200 ms (springs too); keep it in mind when tuning a
duration. Careful: `delay()` never followed this scale, so never sync an animation step with a
`delay()`.

### 🔮 The liquid glass ✧･ﾟ

`ui/glass/LiquidGlass.kt`:

- The screen content is recorded into a `GraphicsLayer` (the "backdrop").
- Each piece of glass redraws it through an **AGSL shader** (Android 13+): squircle-like bulging edge,
  inward refraction, a small rainbow fringe, an edge highlight.
- Android 12: frosted glass (blur). Android 8 to 11: simple tinted glass.
- The video uses a `TextureView` (`res/layout/player_texture.xml`) so the glass can refract it.
- Golden rule: a `LiquidGlass` must never be **inside** the source it refracts. That's why every
  screen is split into `…Content` (what scrolls) and `…Overlay` (what is made of glass).

The music dock and its window are a single pane (`MusicDockWindow`) whose size follows
`MusicPlayerState.expand` (0 = pill, 1 = window): the finger pulls it, a spring settles it. The
window's content is built at its final size and only clipped by the pane, so the whole player isn't
laid out again on every frame.

`ui/glass/LiquidWave.kt` is the same idea for a wave-shaped pane: the shader gets the 40 heights of
the crest and refracts the page behind the music window under it.

## 🗾 Languages

The app is in English and French (English is the default for every other country). Pick it in
**Settings › Language**; "Phone language" follows the system.

To add a language (Spanish, for example):

1. Copy `app/src/main/res/values/strings.xml` (English) to `app/src/main/res/values-es/strings.xml`
   and translate every string (keep the `%1$s`, `%1$d`: they are the inserted values).
2. Add `"es" to "Español"` to `AppLocale.LANGUAGES` (`settings/AppLocale.kt`).

The home categories' searches (`query_*`) are translated strings too: they are what brings videos in
the right language.

## 🌑 No Google account (￢_￢)

Google sign-in (OAuth + YouTube Data API) was removed in the big cleanup (2.1.3), along with Play
Services: it could never be turned on without registering the app in the Google Cloud Console.
Subscriptions are imported from NewPipe, FreeTube or Google Takeout (Settings › Backup); comments are
read without an account. To bring it back: `data/GoogleAccount.kt` and the console procedure are in
the git history (commit `11811f8`).

## 🎋 Structure

```
app/src/main/java/app/mikeneko/
├── MikenekoApp.kt          NewPipe init, thumbnail cache (yt-dlp installs on demand)
├── MainActivity.kt         edge-to-edge, shared links, system PiP, full screen, cleanup after 15 min
├── data/                   NewPipe, SponsorBlock, RYD, subscriptions, Library, music, downloads
├── player/                 shared ExoPlayer, Media3 sources, playback service, audio spectrum
├── update/                 automatic updates from GitHub releases
└── ui/
    ├── MikenekoRoot.kt     navigation, dock, mini PiP
    ├── AnimationSpeed.kt   same animation speed on every phone
    ├── glass/              liquid glass (AGSL): panes and the spectrum wave
    ├── components/         dock, capsules, video cards, ⋮ menu, playlist drawer, mini PiP
    ├── icons/              the mockup's icons
    ├── screens/            Home, Subscriptions, Channels, Music (MusicScreen, MusicWindow…), Library, Player, Bedtime
    └── theme/              "fur" palette + M PLUS Rounded 1c font
```

Code comments are in French.

## 🌠 Possible next steps

- Ideas and bug reports are welcome in the issues.

## 🙇 Credits: arigatō!

- [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) (GPL-3.0): that's why
  Mikeneko is GPL-3.0 too.
- [SponsorBlock](https://sponsor.ajay.app), [Return YouTube Dislike](https://returnyoutubedislike.com).
- [youtubedl-android](https://github.com/yausername/youtubedl-android) / yt-dlp.
- **M PLUS Rounded 1c** font (The M+ FONTS Project Authors), SIL Open Font License 1.1, trimmed to
  Latin, kana and "三毛猫".

---

<div align="center">
<i>"I don't watch ads. I slice them."</i> 🗡️🌑<br><br>
<sub>ฅ^•ﻌ•^ฅ 三毛猫 <i>mikeneko</i>: "calico cat" in Japanese · free software under GPL-3.0</sub>
</div>
