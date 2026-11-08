<div align="center">

<img src="docs/logo.svg" width="140" alt="Mikeneko logo: a calico cat head">

# Mikeneko 三毛猫

ฅ^•ﻌ•^ฅ **A free YouTube client for Android, made of liquid glass** ✧･ﾟ<br>
No ads · no Google account · with cat ears (=^･ω･^=)

[![Latest release](https://img.shields.io/github/v/release/Radinux/Mikeneko?style=for-the-badge&color=E07A2E&label=version)](https://github.com/Radinux/Mikeneko/releases/latest)
![Android 8+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
[![License GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-55504D?style=for-the-badge)](LICENSE)

### [⛩️ Download Mikeneko ⛩️](https://github.com/Radinux/Mikeneko/releases/latest)

</div>

---

## 🌸 What it can do (ﾉ◕ヮ◕)ﾉ✧

- 🗡️ **No ads**, with **SponsorBlock** skipping sponsors and intros on its own (the "paw swipe")
  and the **dislikes** from Return YouTube Dislike.
- 🏯 **"For you" home feed** computed on the phone: it learns your topics from what you really
  watch, like and save, starts from the tastes you pick, and never shows everyone the same feed.
  Plus a **shorts blocker**.
- 📜 **Subscriptions without an account**: import from NewPipe, FreeTube or Google Takeout, filter by
  channel, "Unwatched".
- 🎧 **A real music player**: YouTube Music (songs, albums, artists, playlists), auto Mix,
  "For you" and "Your mixes", synced lyrics, a dancing spectrum wave, your own playlists.
- 🎶 **Bring your music**: import your playlists, liked artists and saved albums
  from other music apps through a TuneMyMusic export (CSV or TXT), duplicates merged.
- 💾 **Mikeneko backup** (`.xml`): your whole Mikeneko in one file, to move to a new phone or keep
  safe (details below).
- 🍙 **yt-dlp downloads**: video, MP3, FLAC, Opus, M4A, sponsors cut out, offline listening.
- 🐾 **PiP mini player** with cat ears inside the app, and real system PiP when you leave it.
- 🔮 **Liquid glass**: dock, buttons and music window in glass that refracts the screen (Android 13+).
- 💮 Comments, chapters, channel and playlist pages, a **Library** (history, watch later, likes).
- 🌙 **"Did you see the time?"** after an hour of watching (－_－) zzZ
- 🎨 **Your colors**: 7 accent colors (Calico, Sakura, Ocean, Matcha, Cherry, Mint, Amber), a pure
  black background for OLED screens, and Material 3 buttons.
- 🔔 **Notifications**: a bell next to Subscribe for a channel's new videos, and a heads-up when an
  update is available.
- 🍥 **Automatic updates** from GitHub, in 🇬🇧 English and 🇫🇷 French.

## 🎴 Install (｀・ω・´)ゞ

1. Download the latest APK from the
   [releases page](https://github.com/Radinux/Mikeneko/releases/latest)
   (`arm64-v8a`, for every recent phone).
2. Open it on the phone and allow the install.
3. That's it: the app then updates itself ٩(◕‿◕)۶

Official APKs are signed with the key whose SHA-256 fingerprint is
`0F:0B:6E:19:0A:E7:2C:34:63:A0:20:03:61:12:9D:54:F9:F4:B8:6B:CF:DC:70:8A:09:99:3B:03:A7:96:83:DC`.

## 🎌 What's new ⸜(｡˃ ᵕ ˂ )⸝♡

### 2.11

- **Easier playlist management**: under your own playlist, an **Add | Edit** split pill.
  - **Add** opens a page with songs suggested from the playlist's own songs (YouTube Music mixes of
    3 of them, without what's already in it) and a search, a + on every song (✓ once added, tap again
    to remove);
  - **Edit**: the name (edited live), the cover (photo or mosaic), Delete playlist, a ⊖ on the left of
    every song to remove it in one tap, and the handle to reorder. Reordering is no longer active the
    rest of the time (songs moved by accident).
- 2.11.1: texts cleaned up (no other app names in the app).
- 2.11.19: the controls stay visible during that animation again (the jump itself is fixed).
- 2.11.18: leaving fullscreen, the play / skip buttons no longer jump up: the controls hide during the animation and fade back in.
- 2.11.17: no more jump of the video and its buttons at the start of that zoom.
- 2.11.16: in landscape, leaving fullscreen for the first time zooms smoothly too (it was instant).
- 2.11.15: shorter dock labels: Follow (English), Abos and Biblio (French).
- 2.11.14: the Video button of the music player is gone (little used, and it didn't work well).
- 2.11.13: editing a playlist's songs has its own page (like Add): the title and Done stay at the
  top, a moved song no longer slides over the cover and title, stays under the finger, and the list
  scrolls up properly when you drag toward the top.
- 2.11.12: playlist downloads redone. A song that can't be downloaded (removed, private, blocked)
  no longer keeps the button "in progress" forever: after a few tries it gets a small grey cross and
  the playlist counts as downloaded ("Downloaded · 1 unavailable"). Failed songs are retried later
  and later, and right away when you come back to the app; downloaded songs are checked again.
- 2.11.11: the play button of music pages travels again from the header to the top pill (it could
  vanish from the pill). Duration and LIVE labels on thumbnails and the music Moods row now use the
  Material 3 style. The first opening of the music window after launch no longer stutters.
- 2.11.10: scrolling music pages no longer stutters. The real cause was a download left stuck after
  quitting the app mid-download: its spinning mark never stopped. Stuck downloads now restart on
  their own, the mark fills up with the progress, and the "playing" bars stop while paused.
- 2.11.9: the play button of music pages has two states only, both tappable: in the header, or in
  the top pill once the header has scrolled under the bar (it appears there with a short fade). No
  more travelling animation between the two.
- 2.11.8: no more stutter of the play button on music pages during a fast scroll.
- 2.11.7: moving a song scrolls at full speed as soon as the finger reaches the top bar (too slow at
  the top).
- 2.11.6: **moving a song** redone: it stays exactly under your finger, and the scroll speed near the
  top / bottom depends on where your finger is (faster closer to the edge).
- 2.11.5: the music window's background is only the cover's color (the stretched cover image looked
  pixelated); the play button no longer stutters when the page bar appears during a fast scroll.
- 2.11.4: the playlist pill is **Add | Edit | Details**, under the play button: **Edit** is for the
  songs (⊖ to remove, ≡ to move), **Details** for the playlist itself (name, cover, delete). A
  **search** in the playlist replaces the ⋮ at the top right. While moving a song, it stays between
  the top bar and the docks: no need to reach the status bar to scroll (the move used to stop there).
- 2.11.2: **Reorder** is its own mode (Add | Edit | Reorder), apart from Edit; added songs go to the
  **top** of the playlist; while dragging a song near the top or bottom, the list **scrolls by
  itself**.

### 2.10

- **Notifications** (there were none before):
  - a **bell** next to Subscribe (video and channel pages): turned on, a notification for each new
    video of the channel, checked in the background every ~2 hours through its RSS feed, even with the
    app closed (off by default);
  - **updates**: a notification when a new version is available (checked once a day in the
    background) or downloaded and ready to install.
- Every row of capsules (home categories, search sort and filters, music filters, channel tabs,
  subscriptions filters) is a **connected Material 3 group**: capsules almost touching, rounded ends,
  the selected one a full pill in the accent color; a bit wider.
- **Lighter colors**: tonal buttons and unselected capsules are a light cream tinted with the accent.
- 2.10.4: the spectrum wave is drawn at 30 frames per second (it used every screen frame): less
  graphics work while the music window is open, so system animations stay smooth.
- 2.10.3: the **music dock animation** is smoother: no more whole-dock recomposition on every frame,
  the pane follows the finger without delay, and the window's blurred cover no longer costs a blur
  on every frame.
- 2.10.2: **music imports about 8× faster**: up to 3 searches at once, slowing down by itself as
  soon as YouTube resists (72 searches in 26 s instead of ~3.5 min).
- 2.10.1: **Subscribed and the bell form one split pill** (video and channel pages); the "What's new"
  page no longer shows again for a bug-fix version.

### 2.9

- **Your colors**: Settings › Appearance, an accent color to pick (Calico, Sakura, Ocean, Matcha,
  Cherry, Mint, Amber) and a pure black background for OLED screens. Buttons and capsules are now
  **Material 3**; the liquid glass stays.
- **Cleaner videos**: on Wi-Fi, Auto quality picks the YouTube step above your screen (1440p on a
  1264-line screen), like the YouTube app; at equal resolution, the stream with the highest bitrate
  the phone decodes in hardware. Dark scenes were blocky in 1080p.
- The video **keeps playing while it flies** between the mini player and the player (it froze for
  about a second).
- **Swipe the video down** to the mini player in portrait too (only downward: upward still scrolls).
- A **Share** button under the video.
- Leaving full screen while holding the phone sideways **stays in landscape**, even with Android's
  auto-rotate off (it often went back to portrait).
- The import summary and the "What's new" page keep their line breaks.

### 2.8

- **Import your music from other services**: Settings › Backup › Import playlists. Export your
  library from another music app with [TuneMyMusic](https://www.tunemymusic.com) to a CSV or
  TXT file, then tick the playlists to import (with the number of songs and the estimated time).
  - every song is found on YouTube Music (or among videos if it isn't a YouTube Music song), at an
    adaptive speed so YouTube doesn't block you (since 2.10.2); the import runs in the background and
    can be stopped, what was found is kept;
  - liked artists and saved albums go to Your library;
  - **duplicates are merged**: a song repeated in a playlist, a song already in the Mikeneko playlist
    of the same name, two lines landing on the same video; a song in several playlists is searched
    only once;
  - at the end, a summary lists what couldn't be found.

### 2.7

- **A brand new "For you"**, still computed on the phone:
  - it learns from what you **really watch** (a video closed after a few seconds barely counts),
    your likes, "Watch later" and playlists, and understands **topics**, not only channels;
  - on a new phone, you **pick your tastes** first (editable in Settings › For you and history);
  - no more trending videos that everyone gets: discovery (about 1 video in 5) comes from your own
    topics, and every phone gets its own feed;
  - a video shown again and again without being opened slowly goes away.
- A **"What's new" page** shows up once after each update.

### 2.6

- **Music "For you" and "Your mixes"** on the Music home: songs to discover from your favourite
  artists' mixes, an "*Artist* Mix" per favourite artist and **Rediscover** (songs from your
  playlists you haven't played in a while). **Keep** turns a mix into a real playlist.
- **Downloads fixed**: yt-dlp installs again in release builds, and a broken Python install is
  repaired and retried automatically. Errors show their real cause.
- The music spectrum becomes a **liquid glass wave** rising behind the music controls, in the
  cover's colour (Android 13+; a drawn wave on older versions).
- In landscape, the **dock** is centred properly, with or without the status bar.
- 2.6.7: **the source code is public** and releases live in this repository; Settings › About links
  to it. The old [Mikeneko-releases](https://github.com/Radinux/Mikeneko-releases) repository is
  archived (its APKs stay downloadable).

### 2.5

- A **music spectrum** dancing with the song's frequencies, analysed inside the player (no
  microphone needed), smooth on every frame.
- In the **music pill**, swiping shows the next (or previous) song's title right away; the music
  window opens and closes with a single small bounce.

### 2.4

- **Shorts blocker fixed** (shorts slipped through "For you" and subscriptions).
- Music playlists show their **song count and total duration**; playlist covers from a photo, or a
  **mosaic** of the first 4 covers.
- **Lyrics** close by pulling them down, and keep the screen on while open.
- A black band with rounded corners behind the status bar when it's shown; a more transparent music
  player.

<details>
<summary><b>🏮 The clan archives: versions 2.3 → 1.1</b></summary>

- **2.3**: "Your shorts" (bookmarks in the Shorts player, a grid in the Library) and a "No shorts"
  setting.
- **2.2**: a **Shorts player**: upright full screen, loop, swipe for the next one.
- **2.1.8 → 2.1.12**: filled Like thumb, clearer update pill that leaves on its own, search capsules
  that scroll away with the results.
- **2.1.3 → 2.1.7**: big code cleanup; Google sign-in and Play Services removed (the APK depends on no
  Google service).
- **2.1.0 → 2.1.2**: **automatic updates** from GitHub, installed without a tap (Android 12+) when you
  leave the app.
- **2.0**: a real **landscape player page**, the progress bar inside the video, a zoom into full
  screen.
- **1.19**: only the video rotates into full screen; the mini player becomes a **calico cat head**;
  videos and music no longer get mixed up; mini player controls hide on their own.
- **1.18**: subscription import moved to Settings › Backup; channel pictures fill in by themselves.
- **1.17**: progress bar on started videos' thumbnails; suggestions in the player page.
- **1.16**: resume where you left off in your last 10 videos.
- **1.15**: Mikeneko **.xml backup** (export / import); French Takeout import fixed; offline = only
  what is downloaded.
- **1.14**: **more liquid glass**: glass pills and capsules, clear glass drawers and "Up next" panel.
- **1.13**: **download your own playlists** for offline listening.
- **1.12**: hidden **offline cache**: what was played in full replays without a network.
- **1.11**: a **music library** with filters, saved albums and artists.
- **1.10**: the Music home in rows; a NewPipeExtractor dev build fixing "Source error".
- **1.9**: the music player takes the **cover's colour**.
- **1.8**: landscape redesign: side dock, multi-column lists.
- **1.7**: thumbnail previews while dragging the seek bar.
- **1.6**: scrolling music window (artist and album), buffered part on the progress bar, the previous
  song kept loaded.
- **1.5**: the next song is preloaded (no gap between songs).
- **1.4**: a video sets the music aside and gives it back paused; the app clears itself after 15 min
  idle in the background.
- **1.3**: **autoplay** of the next video; release signing key.
- **1.2**: the **Library** tab: history, watch later, likes, video playlists, downloads.
- **1.1**: Radio becomes a **real music player** with a liquid glass dock; channel pages; a much
  lighter APK; the same animation speed on every phone.

</details>

## ⚔️ Build it

1. A recent Android Studio, JDK 17 or newer.
2. **File › Open** → the `Mikeneko` folder, and let Gradle sync.
3. Run on a phone or an emulator (Android 8.0 minimum, **Android 13+ for the real liquid glass**).

From the command line: `./gradlew assembleRelease`, then
`adb install -r app/build/outputs/apk/release/app-arm64-v8a-release.apk` (`x86_64` for the emulator).
Without the release key, your build is signed with the debug key.

📜 More in the project scrolls: `TECHNICAL-NOTES.md` (how and why, pitfalls, release procedure) and
`FIXES.md` (to do / to check).

## 🍣 What's inside

| Feature | Where |
| --- | --- |
| Home (NewPipe kiosk, search with suggestions and filters, categories) + floating glass header | `ui/screens/HomeScreen.kt`, `ui/screens/SearchResults.kt` |
| Subscriptions separate from the home, in chronological order, filter by channel, "Unwatched"; fallback if YouTube's RSS goes down | `ui/screens/SubscriptionsScreen.kt`, `data/Subscriptions.kt` |
| Channel pages: banner, Videos / Shorts / Live / Playlists tabs, playlist pages | `ui/screens/ChannelScreen.kt` |
| Import without an account: NewPipe (.json), FreeTube (.db), Google Takeout (.csv) | `data/Subscriptions.kt` |
| Mikeneko backup: export / import of everything in one `.xml` file | `data/Backup.kt` |
| Import from other music services (TuneMyMusic CSV / TXT) | `data/PlaylistImport.kt`, `ui/screens/PlaylistImportUi.kt` |
| Video player: glass buttons on the video, automatic SponsorBlock ("paw swipe"), dislikes, chapters, channel + "Subscribe", autoplay | `ui/screens/PlayerScreen.kt`, `FullscreenPlayer.kt`, `PlayerControls.kt`, `player/` |
| Shorts player | `ui/screens/ShortsPlayer.kt` |
| "Mark a segment": send sponsors, intros, credits… to SponsorBlock (anonymous) | `ui/components/SegmentMarker.kt`, `data/CommunityApis.kt` |
| Comments (read-only, no account: replies, links and timestamps) | `data/Comments.kt`, `ui/components/CommentsUi.kt` |
| In-app PiP mini player (video + cat ears) and real system PiP when leaving the app | `ui/components/Video.kt`, `MainActivity.kt` |
| Music: the music dock stretches into a liquid glass window, screen-off playback; a video sets the music aside | `ui/screens/MusicWindow.kt` (+ `MusicDock`, `MusicPlayerContent`, `MusicQueue`, `MusicPlayerState`), `player/PlaybackService.kt` |
| Music: YouTube Music search, auto Mix, moods, "For you" and "Your mixes" | `data/Music.kt`, `data/MusicForYou.kt`, `ui/screens/MusicPages.kt` (+ `MusicHome`, `MusicSearch`) |
| Spectrum wave in liquid glass behind the music controls | `ui/glass/LiquidWave.kt`, `player/AudioSpectrum.kt` |
| Your own playlists, offline downloads, synced lyrics (LRCLIB) | `data/Playlists.kt`, `player/PlaylistDownloads.kt`, `data/Lyrics.kt` |
| Library: history, watch later, liked, your shorts, video playlists | `ui/screens/LibraryScreen.kt`, `data/VideoLibrary.kt` |
| yt-dlp downloads (in the Library): video, MP3, FLAC, Opus, M4A, sponsors cut out | `data/Downloads.kt`, `ui/screens/DownloadsScreen.kt` |
| Settings: language, region, playback, SponsorBlock, history, bedtime reminder, backup, updates | `ui/screens/SettingsScreen.kt`, `settings/` |
| Automatic updates from this repository's releases | `update/AppUpdates.kt` |
| Notifications: new videos (channel bells), updates, background check | `update/Notifier.kt`, `ui/components/BellButton.kt` |
| "For you": local algorithm (interest profile, tastes, YouTube suggestions, subscriptions, fatigue) | `data/ForYou.kt`, `data/Interests.kt`, `data/Tastes.kt`, `data/Impressions.kt` |
| "What's new" page after an update | `update/WhatsNew.kt` |

### 🔮 The liquid glass ✧･ﾟ

The screen content is recorded into a "backdrop" layer, and each piece of glass redraws it through an
**AGSL shader** (`ui/glass/LiquidGlass.kt`, Android 13+): a bulging squircle edge, inward refraction,
a small rainbow fringe. Android 12 gets frosted glass, older versions simple tinted glass. Golden
rule: a piece of glass must never be **inside** the source it refracts, which is why screens are split
into `…Content` (what scrolls) and `…Overlay` (what is made of glass).

## 🗾 Languages

The app is in English and French (English is the default everywhere else). To add a language
(Spanish, for example):

1. Copy `app/src/main/res/values/strings.xml` to `app/src/main/res/values-es/strings.xml` and
   translate every string (keep the `%1$s`, `%1$d`).
2. Add `"es" to "Español"` to `AppLocale.LANGUAGES` (`settings/AppLocale.kt`).

The home categories' searches (`query_*`) are translated strings too: they bring videos in the right
language.

## 💾 The Mikeneko backup

**Settings › Backup › Export to a file** writes a readable `.xml` file with everything you built up
in the app:

- subscriptions;
- music and video playlists (with their songs and videos);
- saved albums and artists, favourite radios;
- "Watch later", liked videos and "Your shorts".

**Import a file** reads it back on any phone. Import **merges**: it adds what is missing and never
erases anything (a playlist with the same name gets the songs it lacks), then says what it added;
importing the same file twice adds nothing. The Subscriptions "Import" button accepts this file too
(it only takes the subscriptions from it). The format is plain XML, so other apps can read it:

```xml
<mikeneko version="1" app="2.6.7" date="…">
  <subscriptions><channel id="…" name="…" url="…" avatar="…"/></subscriptions>
  <playlists kind="music|videos">
    <playlist name="…"><video url="…" title="…" channel="…" thumbnail="…" duration="…"/></playlist>
  </playlists>
  <library><album …/><artist …/></library>
  <favorites>…</favorites> <later>…</later> <liked>…</liked> <shorts>…</shorts>
</mikeneko>
```

Code: `data/Backup.kt` (`MikenekoBackup`).

## 🌑 No Google account (￢_￢)

There is no Google sign-in: subscriptions are imported from NewPipe (.json), FreeTube (.db), Google
Takeout (.csv) or a Mikeneko backup (Settings › Backup), and comments are read without an account.

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
    ├── glass/              liquid glass (AGSL): panes and the spectrum wave
    ├── components/         dock, capsules, video cards, ⋮ menu, playlist drawer, mini PiP
    ├── screens/            Home, Subscriptions, Channels, Music, Library, Player, Shorts, Settings
    └── theme/              "fur" palette + M PLUS Rounded 1c font
```

Code comments are in French. Ideas and bug reports are welcome in the issues.

## 🙇 Credits: arigatō!

- [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) (GPL-3.0): that's why
  Mikeneko is GPL-3.0 too.
- [SponsorBlock](https://sponsor.ajay.app), [Return YouTube Dislike](https://returnyoutubedislike.com).
- [youtubedl-android](https://github.com/yausername/youtubedl-android) / yt-dlp.
- **M PLUS Rounded 1c** font (The M+ FONTS Project Authors), SIL Open Font License 1.1, trimmed to
  Latin, kana and "三毛猫".

## 📄 License

Mikeneko — a free YouTube client for Android
Copyright (C) 2026 Kirby

This program is free software: you can redistribute it and/or modify it under the terms of the GNU
General Public License as published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version. It uses NewPipeExtractor, itself under GPL-3.0.
Full text: [LICENSE](LICENSE).

---

<div align="center">
<i>"I don't watch ads. I slice them."</i> 🗡️🌑<br><br>
<sub>ฅ^•ﻌ•^ฅ 三毛猫 <i>mikeneko</i>: "calico cat" in Japanese · free software under GPL-3.0</sub>
</div>
