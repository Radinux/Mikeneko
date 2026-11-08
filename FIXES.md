# Mikeneko: fix list

Items marked "to check on the phone" are built and published but not confirmed on a real device yet.

## Done

- [x] Rotating the video → automatic full screen
- [x] The screen turned off during video playback
- [x] Search bar: the text scrolls
- [x] Subscriptions menu: capsules too close together
- [x] Back from anywhere → first the home, then back again to quit
- [x] Double tap to skip forward/back 10 s, without showing the buttons
      (`ui/components/TapToSeek.kt`, normal player + full screen, chained taps)
- [x] "Subscriptions" and "Download" labels overflowing their bubble
- [x] Previous / next video buttons on the video, in portrait and full screen (instead of ±10 s)
      (next = first suggestion, previous = the session's history)
- [x] Visual feedback when pressing glass buttons (squash + bounce, `LiquidGlass`)
- [x] The play/pause button stays visible while loading, with a spinning ring
- [x] "Bubble" capsule removed; dragging down from "Now playing" turns the video into a bubble
- [x] Full screen: slide up → "Up next" panel (carousel of suggestions, "Up next ⌃" capsule)
      (`ui/screens/FullscreenSuggestions.kt`)
- [x] Bug: while paused, going through the bubble (or switching video screen) advanced the video by
      one frame (`PlayerHolder.pausedFrame` + a snapshot kept by `VideoSurface` until playback resumes)
- [x] Pull to refresh on the home (spinning cat, new videos at the top, no cache)
- [x] For you: ~5 % surprises (random topic 🎲 or "one thing leads to another"), `ForYou.surprises`
- [x] French video titles translated to English: NewPipe 0.26.5 forced en-GB for YouTube
      → language set per request (`YouTubeRepository.localized()`), French abbreviated views/dates
      rewritten for NewPipe (`YouTubeFrench.kt`)
- [x] Search bar: a letter typed quickly got erased ("tarte" → "trte")
- [x] Search: YouTube suggestions while typing + history (swipe left to delete)
      (`ui/screens/SearchSuggestions.kt`, `data/SearchHistory.kt`)
- [x] Sorted search: "Best" (recent and popular rise), "Recent", "Most viewed";
      2 pages up front then more while scrolling (`ui/screens/SearchResults.kt`)
- [x] Search: "Channels" row + channel page with "Subscribe" and its videos (`ui/screens/ChannelScreen.kt`)
- [x] Search: "Filters" button (date, duration, shorts), glass panel
- [x] Poor results: original French titles, more results, suggestions, sort, filters
- [x] Startup: the ~900 KB YouTube page is no longer downloaded 7 times (`YouTubeRepository.ensureClient`)
- [x] "One thing leads to another" surprise without a request: it no longer quietly loads the page of an
      unwatched video
- [x] "Cat clay" style (`ui/theme/Clay.kt`): capsules, tags, player buttons and thumbnails in
      claymorphism; "water drop" press (squashed then bounce) everywhere; "plop" on selection;
      a drop that slides and stretches in the dock; a bouncing cat for loading
- [x] Animation to / from the mini player redone (`VideoMorph.kt`): arc flight, spring (~1 s),
      stretch with speed, tilt, shadow, squashed wobbly landing, ears going "boing"
- [x] Closing the mini player: it inflates then pops like a bubble into droplets (`MiniPip`, `Video.kt`)
- [x] Tab transitions: the new screen slides in the dock's direction with a bounce (`MikenekoRoot`)
- [x] Buttons with a dark clay outline + drop press: subscription import, bedtime reminder, downloads,
      mark a segment, "Undo" bar, comments
- [x] The phone's status bar hidden in the app (setting, on by default), layout unchanged
- [x] The dock no longer hides while scrolling
- [x] Subscriptions: unsubscribe (tap the avatar → "Unsubscribe" → "Sure? Tap again")
- [x] Subscriptions: avatar ears spread apart and rounded (`drawEar(round = …)`)
- [x] Mini player: ears spread towards the corners, tilted outwards and rounded
- [x] Wrong subscriber counts in millions ("3 subscribers"): "3,9 M d'abonnés" misread, fixed in
      `YouTubeFrench.kt`

### Done on Nov 3 and 4 (versions 1.1.0 → 1.15.0)

- [x] Radio renamed Music; music dock window: no more empty glass when pulling too far (the pane
      stretches with its content); spring tuned
- [x] Stable volume off during music, back on for videos if it was on
- [x] Video player buttons and loading: fade in / fade out
- [x] Same animation speed on every phone (`ANIMATION_SPEED`, ui/AnimationSpeed.kt)
- [x] Visual feedback when tapping a video: the thumbnail flies to the player
- [x] Home: the categories scroll away
- [x] Search: keyboard right away, ✕ clears without closing, scrolling puts the suggestions away,
      history / suggestions appear and leave smoothly
- [x] Empty subscriptions: YouTube's RSS feed returns 404 → fallback through the channels' Videos tab
- [x] Subscriptions: open a channel's page (long press on the avatar or "See channel")
- [x] Channel page: banner, badge, expandable description, Videos / Shorts / Live / Playlists tabs
- [x] Channel playlists: page, "Play all", auto chaining, glass drawer in the player
- [x] Player: tappable channel avatar + "Subscribe" on the right
- [x] Dock: Home goes back to the "For you" feed, Subscriptions to the list of all channels
- [x] Music: "audio output" button removed (it was useless)
- [x] Lighter app: APK 174 → 59 MB (one APK per CPU, R8 release), yt-dlp installed on demand,
      capped thumbnail cache
- [x] Removing the app from recent apps stops the music and frees the memory (everything starts over)
- [x] Library (instead of Downloads): history, watch later, liked, video playlists,
      downloads; videos' ⋮ menu available everywhere
- [x] Autoplay of the next video + switch (settings, and on the video at the top right)
- [x] Automatic versions (`app/version.properties`) and release key (outside the project, ~/.config/mikeneko)

- [x] 1.4.0: music dock only in Music; a video sets the music aside (resumed paused when back in
      Music); the app clears itself after 15 min idle in the background (verified on the phone)
- [x] 1.5.0: next song loaded in advance (instant transition and "next", verified on the phone)
- [x] 1.6.0: scrolling music window: artist (to their page) and the song's album (to the album)
- [x] 1.6.1: crash when opening the artist from the player (duplicate "loading" key in the artist page)
- [x] 1.6.2: the last song played stays loaded (instant "previous", verified on the phone)
- [x] 1.6.3: tapping Music in the dock goes back to the Music home
- [x] 1.6.4: a video started during music got replaced by the next song (verified on the phone)
- [x] 1.6.5: the progress bar shows what is already loaded (transparent white)
- [x] 1.6.6: automatic full screen in landscape broken when a language is chosen in the app (verified on the phone)
- [x] 1.7.0: video preview while dragging the seek bar (verified on the phone)
- [x] 1.8.x: landscape interface (dock as a left column, lists in columns, music dock and window adapted)
- [x] 1.9.0: music redesign step 1 (player in the cover's colours, song rows, album headers)
- [x] 1.10.2: tapping the current song again restarts it without reloading it
- [ ] 1.10.3: "Source error" on launch: NewPipeExtractor on the dev code (n-sig fix) — to confirm
      (go back to an official NewPipeExtractor release as soon as one ships after 0.26.5)
- [x] APK 1.15.0 signed with the release key: `Mikeneko-1.15.0.apk` (older APKs removed)
- [x] APK 1.17.2 (Nov 5): `Mikeneko-1.17.2.apk`, replacing 1.15.0
- [x] APK 1.18.1 (Nov 5): `Mikeneko-1.18.1.apk`, replacing 1.17.2
- [x] APK 2.0.4 (Nov 5): `Mikeneko-2.0.4.apk`, replacing 1.18.1
- [x] APK 2.1.0 (Nov 5): `Mikeneko-2.1.0.apk`, replacing 2.0.4, and release v2.1.0 on Radinux/Mikeneko-releases
- [x] APK 2.1.2 (Nov 5): `Mikeneko-2.1.2.apk`, replacing 2.1.0, and release v2.1.2


## To do

### Music: rest of the redesign (chosen on Nov 3, finished on Nov 4)
- [x] 1.10: Music home in rows (greeting, quick access, recently played, playlists, moods, live)
- [x] 1.11.0: music library (All / Playlists / Albums / Artists / Downloaded filters, save albums and artists) (verified on the phone)
- [x] 1.12.1: hidden offline cache (music and videos played offline, no exposed files)
- [x] 1.13.0: download button on your own playlists (music then videos), offline listening in the app
- [x] 1.13.1: Shuffle on video playlists (and Watch later, Liked)
- [x] 1.13.6: scrolling down a music playlist, the title and the play button come progressively into the top bar (verified on the phone)

### More liquid glass (Nov 4)
- [ ] 1.14.0: glass pill on music pages, glass capsules (home categories, search sort, music library
      filters, music search kinds) — to check on the phone
- [ ] 1.14.1: taps went through a music playlist's glass pill — to check on the phone
- [ ] 1.14.2: search history / suggestions and comments: lighter frosted glass — to check on the phone
- [ ] 1.14.6: full screen "Up next" in clear liquid glass, not frosted, and centred — to check on the phone
- [x] 1.14.4: the portrait player's suggestions in a clear glass drawer (Playlist / Up next tabs) — kept
- [ ] 1.15.1: home categories: a vertical drag starting on a capsule now scrolls the list (and pull to
      refresh at the top) — to check on the phone
- [x] 1.15.2: the music pane sometimes got stuck between pill and window (when folding it down) —
      verified on the phone
- [x] 1.15.3: music pages: a single play button, sliding from the header into the top pill (instead
      of a second one appearing) — verified on the phone
- [x] 1.15.3: playlists' Shuffle turns on and off (before, it could only turn on) — verified on the phone
- [ ] 1.15.5: removing a playlist's download: its songs still played offline (hidden cache of played
      songs). Decision: without a network, only downloaded items play — to check on the phone

### Playback (Nov 5)
- [ ] 1.16.0: keep the position of the last 10 videos to resume after closing — to check on the phone
- [ ] 1.17.0: progress bar on started videos' thumbnails — to check on the phone
- [ ] 1.17.0: suggestions in the player page too (under the comments), not only in the drawer — to check on the phone
- [ ] 1.17.1: the bottom drawer only for videos from a playlist (a single video: suggestions in the page) — to check on the phone
- [x] 1.17.2: full screen: the "Up next" button wasn't centred — verified on the phone
- [ ] 1.18.0: subscription import moved to Settings › Backup (the card looked out of place in Subscriptions) — to check on the phone
- [x] 1.18.1: channel pictures didn't show in Subscriptions (import without avatars) — seen on the phone: the first ones arrive
- [ ] 1.19.0: automatic full screen: rotate only the video, not the whole interface — to check on the phone
- [ ] 1.19.1 → 1.19.2: new mini player (PiP) design as a calico cat head (logo colours), bigger rounder ears — to check on the phone
- [ ] 1.19.3 → 1.19.4: no more purple (music, comments, ⋮ menu): dark orange instead — to check on the phone
- [ ] 1.19.5: start a video then go to Music: the pill showed (and controlled) the video — to check on the phone
- [ ] 1.19.6: the video mini player hides (paused) in Music and comes back on the other tabs — to check on the phone
- [ ] 1.19.7 → 1.19.8: the mini player's buttons didn't hide on their own, and the time bar stayed visible; now with a fade — to check on the phone
- [x] 2.0.0: landscape player page; leaving a video in landscape no longer forces portrait — seen on the phone
- [ ] 2.0.1: the progress bar inside the video (portrait and landscape) — to check on the phone
- [ ] 2.0.2: in landscape, the "Now playing" row took too much space (arrow moved onto the video) — to check on the phone
- [ ] 2.0.3: in landscape, video recentred and drag down for the mini player (the gesture had disappeared with the "Now playing" row) — to check on the phone
- [ ] 2.0.4: in landscape, zoom between the page and full screen (no more abrupt switch) — to check on the phone
- [x] 2.1.0: automatic updates from GitHub (Radinux/Mikeneko-releases) — 2.1.1 downloaded and installed itself; Play Protect triggers on every version (expected, see the notes)
- [x] 2.1.1: automatic download from GitHub — seen on the phone ("Mikeneko 2.1.1 is ready")
- [ ] 2.1.2 (arrived on its own on the phone): "Update" pill as soon as the APK is downloaded; Settings › About buttons no longer squashed ("What's new" letter by letter)
- [ ] 2.11.5: music window background = cover color only (no pixelated image); no stutter of the play button when the page bar appears — to check on the phone
- [ ] 2.11.7: full scroll speed as soon as the finger reaches the top bar / the docks (the top was too slow; the bottom was fine) — to check on the phone
- [x] 2.11.6: moving a song redone — bottom verified on the phone, top too slow (→ 2.11.7)
- [ ] 2.11.6 (details): moving a song redone (the song stays under the finger, scroll speed from the finger's position, no double swap) — verified on the emulator (row centred ~25 px from the finger while the list scrolled); to check on the phone
- [x] 2.11.16: landscape, first exit from fullscreen zooms smoothly (was instant) — confirmed on the phone, but the video buttons jumped at the start (→ 2.11.17)
- [x] 2.11.17: no jump of the video at the start of the landscape fullscreen exit zoom — the centre buttons still jumped (→ 2.11.18)
- [ ] 2.11.18: centre buttons no longer jump on fullscreen exit (first-frame lift fixed, controls hidden during the morph) — to check on the phone
- [x] 2.11.13: separate Edit songs page; dragged song stays under the finger, no jump at the top, saved on drop — verified on the emulator and on the phone
- [x] 2.11.12: playlist download button stuck "in progress" when everything was downloaded — downloads verified, back-off retries, unavailable songs, retry on return to the app — confirmed on the phone
- [x] 2.11.11: travelling play button back; Material 3 duration / LIVE labels and Moods row; first music window opening after launch warmed up — confirmed on the phone
- [x] 2.11.10: scroll stutter on music pages = never-ending animations (stuck download spinner after quitting mid-download, "playing" bars while paused); download watchdog + determinate ring + still bars when paused — confirmed on the phone
- [ ] 2.11.9: play button with two states (header / top pill, appearing with a fade), no travelling animation — to check on the phone
- [ ] 2.11.8: play button stutter during fast scroll on music pages (no stutter in Edit mode, where the header has no play button) — fixed by not rewriting its anchor values every frame; to check on the phone
- [ ] 2.11.4: Add | Edit (songs: remove + move) | Details (name, cover, delete) under the play button, search in the playlist (top right), dragged song kept below the top bar — verified on the emulator (drag held at the very top: list scrolled up, song became first; search "david"); to check on the phone
- [ ] 2.11.2: Reorder as its own mode, added songs at the top, auto-scroll while dragging near the edges — verified on the emulator (drag held at the bottom: the list scrolled to the end, the song moved there); to check on the phone
- [ ] 2.11.0: playlist Add | Edit (suggestions + search page, edit mode with name, cover, delete, one-tap remove, reorder only there), Video button in the music player — verified on the emulator (Add/Edit pill, edit mode, suggestions, + → ✓, 19 → 20 tracks); Video button to check on the phone
- [ ] 2.10.4: spectrum wave at 30 fps (system animations lagged a little with the music window open) — to check on the phone
- [x] 2.10.3: smoother music dock animation (pill ↔ window) — verified on the phone, much smoother
- [x] 2.10.2: faster music import (adaptive, 3 searches at once) — verified on the emulator: 72 searches in 26 s (was ~3.5 min), 19/19 songs, 52/53 artists
- [x] 2.10.1: Subscribed | bell as one split pill — verified on the phone
- [ ] 2.10.0: bell next to Subscribe (new video notifications, checked every ~2 h in the background) and update notifications — to check on the phone (turn a bell on, wait for a new video)
- [x] 2.10.0: connected Material 3 capsule rows, wider, lighter tonal colors — verified on the emulator
- [ ] 2.9.0: Share button on the video page; swipe down on the portrait video → mini player; leaving full screen in landscape stays in landscape; live video during the mini player morph (no freeze) — to check on the phone
- [ ] 2.9.0: dark scenes no longer blocky (Auto = step above the screen on Wi-Fi, highest bitrate at equal resolution) — to check on the phone with the same video as on YouTube
- [x] 2.9.0: accent colors + pure black background (Settings › Appearance), Material 3 buttons and capsules — verified on the emulator (Ocean + black)
- [ ] 2.8.0: import a TuneMyMusic export (CSV / TXT): pick playlists, songs found on YouTube Music, artists and albums to the library, duplicates merged — verified on the emulator with a real export (ASMR + Favorite Albums: 8/9 songs, 11/12 albums); to check on the phone with a bigger import
- [ ] 2.7.0: new "For you" (interest profile from watch time, likes, saved videos, topics; tastes page on a new phone; no more shared trending feed; fatigue) — to check on the phone
- [ ] 2.7.0: "What's new" page once after an update — to check on the phone (it should show right after updating to 2.7.0)
- [ ] 2.6.7: releases and update checks moved to Radinux/Mikeneko (public source) — to check on the phone: the next update arrives from the new repository
- [x] 2.6.6: glass wave refracting the page behind the window, more transparent (2.6.4's glass didn't show and the wave was quite opaque) — verified on the phone
- [x] 2.6.5: landscape dock centred on the screen (status bar hidden) or between the status bar and the bottom (shown) — verified on the phone
- [x] 2.6.4: wave in the cover's colour — verified on the phone; the glass didn't show (fixed in 2.6.6)
- [x] 2.6.3: more colourful wave, fading towards the bottom, without the progress inside — verified on the phone (replaced by the glass wave)
- [x] 2.6.2: spectrum as a continuous wave behind the music window (buttons no longer pushed down) — verified on the phone
- [x] 2.6.2: landscape dock below the status bar (no longer clipped) — verified on the phone
- [x] 2.6.1: download with a Python trace on the phone: Python / yt-dlp reinstalled and automatic retry — works on the phone
- [x] 2.6.0: downloads: "yt-dlp could not be installed" fixed (R8 broke Commons Compress) — verified on the emulator then on the phone
- [ ] 2.6.0: Music "For you" (suggested songs) and "Your mixes" (Artist mix, Rediscover, Keep as playlist) — to check on the phone
- [x] 2.5.3: music pill: fixed cover, next title visible from the start of the drag; music window with a single small bounce — verified on the phone
- [x] 2.5.0 → 2.5.2: music frequency spectrum above the progress bar — smooth, verified on the phone
- [ ] 2.4.0 (list from the phone): shorts blocker fixed ("For you", subscriptions, ≤ 1 min) — to check on the phone
- [ ] 2.4.0: number of songs + total duration of music playlists; no more numbers (cut after 99) — to check on the phone
- [ ] 2.4.0: playlist covers: imported photo, otherwise a mosaic of 4 covers — to check on the phone
- [ ] 2.4.0: lyrics panel closed by pulling it down, and the screen stays on while they're open — to check on the phone
- [ ] 2.4.0 → 2.5.1: black band with inverted rounded corners behind the shown status bar (rounder in 2.5.1) — to check on the phone
- [ ] 2.4.0: more transparent music player — to check on the phone
- [ ] 2.3.0: "No shorts" setting (Settings › Playback) hiding every short — to check on the phone
- [ ] 2.3.0: "Your shorts": bookmark in the Shorts player, separate list in the Library (grid, ✕ to remove) — to check on the phone
- [ ] 2.2.1: a short from subscriptions / "For you" opened in the normal player: recognised once loaded — to check on the phone
- [ ] 2.2.0: Shorts player (upright full screen, loop, swipe for the next one, Like / comments / share) — to check on the phone
- [ ] 2.1.12: the update pill leaves on its own after 6 s (it hid the top of the screen) — to check on the phone
- [ ] 2.1.10: video search sort capsules moving up and fading with the results (like the home categories) — to check on the phone
- [x] 2.1.10: update pill cut off ("Mikeneko 2.…") with a larger font: two lines + "Install" button — seen on the phone with 2.1.11
- [ ] 2.1.8: the player's "Like" thumb turns filled when the video is liked (on top of the orange capsule) — to check on the phone
- [ ] 2.1.4 → 2.1.6: in portrait, "next" touched the settings button; 2.1.4 moved them far too high, 2.1.6 moves them up just enough (6 dp gap) — to check on the phone
- [ ] 2.1.3: big code cleanup, nothing should change on screen. Go around: home, subscriptions (channel row, filters, unsubscribe), portrait / landscape / full screen player, mini player, music (pill, window, queue, lyrics, album / artist / search pages), comments (read-only), settings — to check on the phone

### Music: next ideas (Nov 7)
- [x] Import playlists exported with TuneMyMusic as a CSV or TXT file, duplicates
      merged (2.8.0)

### Subscriptions and backup (Nov 4)
- [x] 1.14.7: the Google Takeout import found no channel (CSV header in French) — verified on the emulator
- [x] 1.15.0: Mikeneko .xml import/export file (Settings → Backup) — verified on the emulator
- [ ] Re-import a real Takeout `abonnements.csv` on a phone — to check
- [ ] Idea, not chosen yet: import the `youtube.com/feed/channels` page saved from Chrome (.mhtml),
      without Takeout or a PC (inspired by youtube-subscription-extractor)

### Video player
- [x] Show the description, the release date and the time since release (seen on the phone on Nov 4)
- [x] Suggest other videos you might like: "Up next" (glass drawer in portrait since 1.14.3, a panel
      to slide up in full screen)

### Radio
- [x] Full dock on the Radio + a "music dock" (current station) above it, also on the other tabs while
      the radio plays (`MusicDock`)
- [x] Turn it into a real music player (chosen on Nov 1, verified on the emulator):
  - [x] Queue chaining on its own at the end of a song; shuffle; repeat (off / all / one);
        "previous" goes back to the start after 3 s; next/previous in the notification and on earphones
  - [x] Progress bar + time in the big player (not for live streams)
  - [x] Music search (YouTube Music via NewPipe): songs, albums, artists, playlists (`data/Music.kt`)
  - [x] Auto Mix: from a song, an endless queue of similar tracks (YouTube's "RD" playlist)
        — verified on the phone on Nov 3 (chains with the app on screen and in the background)
  - [x] More moods: 12 capsules (lofi, jazz, classical, chill…), one search per mood
  - [x] Downloads (MP3/FLAC/Opus/M4A) played in the Radio, offline (`data/LocalMusic.kt`)
  - [x] Your own playlists + reorder the queue / playlist by dragging the ≡ handle (`data/Playlists.kt`)
  - [x] Lyrics (LRCLIB, synced when possible, tap a line to jump there) (`LyricsSheet.kt`)
  - [x] Music search: suggestions while typing + a history separate from the videos'
  - [x] Big player replaced (Nov 3): the music dock stretches into a liquid glass window when pulled
        up (or tapped), folds down; main dock visible, works on every tab

## Set aside

- [ ] "Sign in to confirm you're not a bot" (Nov 1): YouTube temporarily blocked playback from the
      network's IP address (not an app bug). Wait a few hours; mobile data works. This block is not
      worked around: the app only reduces its own traffic (see above) and long playback test sessions
      are avoided.
      Nov 4: the "web" PoToken (WebView BotGuard) wouldn't help as is:
      NewPipeExtractor only asks for Android/iOS tokens. What already helps: the n-sig fix (1.10.3) and
      the offline cache (1.12: a cached song no longer asks for a stream).

- [x] "Could not connect to Google" error: no Google account at all any more (removed in the big
      cleanup, 2.1.3, along with Play Services)
