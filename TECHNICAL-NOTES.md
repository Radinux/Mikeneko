# Mikeneko: technical notes (to pick the work back up)

This file explains **how** and **why** changes were made, so that anyone picking the project up
doesn't have to rediscover everything.
- What users see, version by version → `README.md` ("What's new").
- What is left to do or to check → `FIXES.md`.
- Here: the architecture, the files, the choices and the pitfalls.

Code comments are in French.

---

## 0. Build, signing, size, animation speed

- **Versioning**: `app/version.properties` holds the version of the next release build; every
  `assembleRelease` / `installRelease` uses it, then prepares the next one (last digit +1,
  `versionCode` +1). New feature: `minor` +1 and `patch=0` by hand. No upper limit on `minor` or
  `patch` (since 2.12: x.20.0 and beyond are fine; until 2.11 the rule was no x.20, so 1.19 → 2.0.0).
  **1.x was the beta; 2.0.0 is the first stable version.** `major` only goes up for a real big
  milestone, decided by the user.
- **ABIs**: one APK per CPU, `arm64-v8a` for phones and `x86_64` for the emulator; no 32-bit.
- **Release key**: official releases are signed with `~/.config/mikeneko/mikeneko-release.jks`, its
  password in `~/.config/mikeneko/signing.properties` (path can be changed with the
  `MIKENEKO_SIGNING` variable). Without that file, the release build is signed with the debug key.
  Back the key up: without it, no update can be installed on top of an existing install. SHA-256:
  `0F:0B:6E:19:0A:E7:2C:34:63:A0:20:03:61:12:9D:54:F9:F4:B8:6B:CF:DC:70:8A:09:99:3B:03:A7:96:83:DC`.
- **Size**: release APK arm64 ~59 MB (~50 MB of Python + ffmpeg for yt-dlp, ~8 MB of code after R8);
  the debug APK is neither shrunk nor compressed (~120 MB). yt-dlp installs itself (~125 MB unpacked
  in `no_backup/`) only the first time the Downloaded tab opens, and checks for its update at most
  once a day. Thumbnails: disk cache capped at 64 MB (`MikenekoApp.newImageLoader`). Downloaded files
  (`Android/data/app.mikeneko/files/Movies` and `Music`) count in the app's "data" for Android.
- **Dependencies**: all versions are in `gradle/libs.versions.toml`; keep **NewPipeExtractor** up to
  date (it follows YouTube's changes).
- **Animation speed**: `MainActivity` hands `setContent` its own recomposer (`fixedSpeedRecomposer()`
  in `ui/AnimationSpeed.kt`) with a fixed `MotionDurationScale`, so every Compose animation (menus and
  popups included) runs at the same speed whatever the phone's animation scale (only exception:
  animations turned off in accessibility). `ANIMATION_SPEED = 2`: `tween(400)` really lasts 200 ms
  (springs too). `delay()` never followed this scale: never sync an animation step with a `delay()`.
- **Liquid glass details** (`ui/glass/LiquidGlass.kt`): the video uses a `TextureView`
  (`res/layout/player_texture.xml`) so the glass can refract it. The music dock and its window are a
  single pane (`MusicDockWindow`) whose size follows `MusicPlayerState.expand` (0 = pill, 1 = window):
  the finger pulls it, a spring settles it; the window's content is built at its final size and only
  clipped by the pane, so the player isn't laid out again on every frame.
- **Google account code**: removed in 2.1.3; `data/GoogleAccount.kt` and the Google Cloud Console
  procedure are still in commit `11811f8`.

---

## 1. Working conventions

- The app's **default language is English**; the French translations live in `values-fr`.
- **Every shipped change = a new version.**
  - `patch` and `code` go up on their own with every `assembleRelease`, `installRelease` or
    `bundleRelease` (`app/build.gradle.kts`).
  - For a **new feature**: set `minor+1` and `patch=0` by hand in `app/version.properties` before
    building.
  - Careful: even `assembleRelease --dry-run` bumps the version. If that happens, put `patch` and
    `code` back by hand.
- **Releasing a version**:
  1. `./gradlew assembleRelease`, signed with the release key (see below).
  2. Check the APK: `aapt2 dump badging` (versionName) and `apksigner verify --print-certs`
     (SHA-256 `0f0b6e19…`).
  3. Commit, push, then publish the release on this repository (section 8 quinquies).
  4. Update `README.md` ("What's new") and `FIXES.md`.
  5. For a version that brings something users should know about, rewrite `whats_new_body` (EN in
     `values/strings.xml`, FR in `values-fr/strings.xml`): the "What's new" page shows it once after
     the update (section 8 duodecies).
- **After every install**, launch the app and check that it runs (`pidof app.mikeneko`,
  `dumpsys dropbox --print data_app_crash` for crashes). 1.12.0 crashed at startup and the error was
  only noticed afterwards.
- When a UI element is named loosely in a bug report, **take a screenshot first**. Example: "Up next"
  meant the full screen panel, not the portrait list, and the misreading cost two versions
  (1.14.3 → 1.14.5).
- Playback tests:
  - few video loads, with calm videos, so as not to get blocked by YouTube;
  - never work around YouTube's anti-bot checks (section 2).
- **Release key**: outside the project, in `~/.config/mikeneko/`. Never print its password.
- Emulator: an x86_64 AVD (APK `app-x86_64-release.apk`). If the install answers
  `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, uninstall the old test copy first. Close the emulator after
  testing (`adb emu kill`).

---

## 2. YouTube: blocks and the extractor (1.10.3)

- **Symptom**: "Source error" when starting a video.
- **Probable cause**: NewPipeExtractor 0.26.5's "n-sig" decryption was outdated.
- **Fix**: `gradle/libs.versions.toml` → `newpipeExtractor = "6cb36e9823"`, the **dev** commit of
  Nov 2 taken from JitPack, which contains #1545, #1556 and #1520.
  - API consequence: `Description.HTML` became **`Description.Type.HTML`** (`Description` is now a
    record). Files touched: `data/Comments.kt` and `player/PlayerHolder.kt`.
  - **To do**: go back to an official release as soon as one after 0.26.5 ships.
- **"Sign in to confirm you're not a bot"**: a temporary block of the network's IP address. It is
  **not worked around**.
  - A "web" PoToken through a WebView BotGuard (as described in the yt-dlp guide) is useless
    here, since NewPipeExtractor's `PoTokenProvider` only asks for Android/iOS tokens.
  - What really helps:
    - the n-sig fix;
    - the offline cache (section 4): a cached song no longer asks for a stream;
    - preloading with reuse.

---

## 3. Music: library (1.11)

- `data/MusicLibrary.kt` (singleton `get(context)`, prefs `bibliotheque_musique`):
  - content: YouTube Music albums and playlists (`MusicCollection`) and artists (`ChannelItem`);
  - functions: `toggleAlbum`, `toggleArtist`, `hasAlbum`, `hasArtist`, and `addAll` for import;
  - two artists are the same if they have the same URL, or the same name when the URL is missing
    (an artist coming from a song).
- `ui/screens/MusicUi.kt`:
  - `MusicPage.Library`;
  - `enum LibraryFilter { All, Playlists, Albums, Artists, Downloaded }`, kept in
    `MusicState.libraryFilter`.
- `ui/screens/MusicPages.kt`:
  - `MusicLibraryContent`, plus the `SaveAlbumButton`, `SaveArtistButton` and `SaveCapsule` buttons on
    album and artist pages;
  - on the Music home, the "Your library" quick access tile and `Shelf(onMore)`;
  - `SquareCard(round)` for round artists.

---

## 4. Hidden offline cache (1.12)

**Idea**: everything the player reads from YouTube goes through a Media3 cache private to the app
(nothing in the gallery or in the files). What was played in full plays again **without a network**.

### `player/OfflineCache.kt`

Four stores (`enum Store`):

| Store | Folder | Size | Eviction |
| --- | --- | --- | --- |
| `Music` | `cacheDir/hors_ligne/musique` | 1 GB | LRU (`LeastRecentlyUsedCacheEvictor`) |
| `Videos` | `cacheDir/hors_ligne/videos` | 1 GB | LRU |
| `SavedMusic` | `filesDir/hors_ligne/playlists_musique` | unlimited | never (`NoOpCacheEvictor`) |
| `SavedVideos` | `filesDir/hors_ligne/playlists_videos` | unlimited | never |

- The stores share a single `StandaloneDatabaseProvider`.
- `warmUp()` opens them in the background at startup (`MikenekoApp`): reading a `SimpleCache` index
  on the first song would block the screen.
- **Stable keys**: YouTube URLs change every time, so every stream gets a key
  `setCustomCacheKey("<id>:<a|v|m><itag>")`: `a` for audio, `v` for video only, `m` for the
  "all-in-one" (muxed) video.
- **Index per store** (SharedPreferences): for each videoId, an `Entry` (url, title, channel,
  thumbnail, `audioKey`, `videoKey`, height, `at`).
- Functions:
  - `entry()` only returns the entry if the streams are **complete** in the cache (`complete()` uses
    `ContentMetadata.getContentLength` + `isCached`);
  - `playable(url, audioOnly)` looks in the downloaded playlists' store first, then in the playback
    cache;
  - `source(entry)` builds a `ProgressiveMediaSource` on a `CacheDataSource` **without upstream** (a
    gap = an error), merging video + audio if needed;
  - `prune()` only removes entries **older than an hour** whose cache is empty. A recent entry can be
    empty because the preloaded song hasn't started yet; a first version deleted them by mistake;
  - `clear()` (Settings) only empties `Music` and `Videos`; downloaded playlists stay.

### The rest

- `player/MediaSources.kt`:
  - `build(..., cache)` returns `Built(source, height, audioKey, videoKey)` and reads through
    `OfflineCache.factory(cache, upstream)` (`FLAG_IGNORE_CACHE_ON_ERROR`);
  - `bestVideo(streams, limit)` picks the video stream;
  - `downloadPlan(info, audioOnly, maxHeight)` returns the same keys for playlist downloads.
- `player/MusicLoadControl.kt`: a **subclass** of `DefaultLoadControl` that loads the whole song (audio
  only, not live, up to 20 min and 40 MB). The song thus ends up in the cache even if it isn't played
  to the end.
  - **Pitfall (1.12.0 crashed at startup)**: writing `LoadControl by base` in Kotlin does **not**
    delegate the Java interface's `default` methods, hence the error
    `getBackBufferDurationUs not implemented`. Extend `DefaultLoadControl` instead (1.12.1).
- `player/PlayerHolder.kt`:
  - `.setLoadControl(MusicLoadControl())`;
  - `play()` checks the cache **first** (music: `OfflineCache.playable`; video: the `SavedVideos`
    store);
  - if video extraction fails, it falls back on the `Videos` cache;
  - added functions: `rememberOffline`, `playOffline`, `offlineNowPlaying` and `fillDetails`, which
    completes the info in the background when the network comes back;
  - preloading (`Preloaded(..., offline)`) and `adoptPreloaded` handle an offline entry.
- Settings (`SettingsScreen.kt`, `OfflineSection`): store sizes and a button to clear them. Write the
  full name `OfflineCache.Store` (there is another `cache` in that file).

---

## 5. Downloaded playlists (1.13)

- `player/PlaylistDownloads.kt`: two instances, `PlaylistDownloads.music` and
  `PlaylistDownloads.videos`.
  - Functions: `init` (in `MikenekoApp`), `setDownloaded(id, on)`, and the `playlists`, `saved`,
    `current` and `busy` StateFlows.
  - A **single sequential worker** does the downloads:
    - a 2.5 s pause between two songs (to stay discreet with YouTube);
    - a retry 10 min after an error;
    - if the stream is already complete in the playback cache, it **copies** it; otherwise it
      downloads it with `CacheWriter`;
    - videos are capped at **720p**.
  - `busy` is wired to `MainActivity.busy()` (no cleanup during a download) and to
    `PlaybackService.onTaskRemoved`.
- `ui/screens/PlaylistDownloadButton.kt`:
  - `PlaylistDownloadCircle` on your own music playlists;
  - `PlaylistDownloadCapsule` on the Library's video playlists;
  - `DownloadedMark`, the small mark on each song;
  - a dialog confirming before removing downloads.
- `LibraryScreen.kt`: `PlayButtons` + `ShuffleVideosButton`, shuffle on video playlists,
  "Watch later" and "Liked" (1.13.1).

### Title and play button following the scroll (1.13.2 → 1.13.6)

- `MusicUi.kt`:
  - `class PageBar(title, onPlay, owner, passed: State<Float>)`;
  - `MusicState.bar` and `MusicState.barProgress` (0 outside the pages).
- `MusicPages.kt`, `PageBarEffect(state, list, title, onPlay)`:
  - progress = `(offset − (header size − bar − FADE_LEAD)) / FADE`, with `FADE = 90.dp` and
    `FADE_LEAD = 20.dp`;
  - this tuning was validated on the phone: leave it alone.
- **A single play button (1.15.3)**: the pill's small button was removed (an empty slot remains, a
  `Spacer` of `PLAY_SMALL`). `PlayAnchor` (`MusicUi.kt`) computes where the header's big button is:
  from the list's `layoutInfo` (top of the header), the button's position in the header and
  `MusicState.barPill` (the pill without its animation). Over `PLAY_TRAVEL = 56.dp` of scrolling, it
  slides and goes from 58 to 42 dp into the pill (y = place + travel·(1−t)², slowing down as it
  arrives). At t = 0 the header's button is drawn; as soon as t > 0, it is `TravellingPlayButton`
  (`RadioOverlay`), and the header's one goes to alpha 0. No `onGloballyPositioned` during scrolling
  for the position: it would be one frame late. The pill's fade (FADE / FADE_LEAD) is unchanged.
- **Pages' Shuffle (1.15.3)**: `ShuffleButton` is a toggle, on when `PlayerHolder.shuffle` is true and
  every song of the page is in the queue.
- **Pitfall (1.13.2, the bar never showed)**: a `derivedStateOf` read **only in a `SideEffect`**
  doesn't trigger recomposition. Read it during composition.
- **Offline = only what is downloaded (1.15.5)**: `OfflineCache.playable` only uses the playback cache
  (`Music` / `Videos`, the hidden cache of 1.12) when there is a network (`ConnectivityManager`,
  permission `ACCESS_NETWORK_STATE`). Without a network, only the `SavedMusic` / `SavedVideos` stores
  play. Before, after "Remove", a song already played in full stayed playable offline, which made the
  removal look broken. 1.15.4 deleted those songs from the cache on removal: that's reverted (the
  cache saves requests to YouTube), and it covered neither a playlist removed earlier nor a song played
  again afterwards. The player keeps the current song and its neighbours in memory
  (`MusicLoadControl`): those can finish playing without a network.

---

## 6. More liquid glass (1.14)

Reminder: a `LiquidGlass` only shows what is **behind** it. It must live in the `…Overlay` layer,
above the backdrop source, never inside it.
Parameters: `backdrop`, `cornerRadius`, `bezel`, `refraction`, `tint`, `frost` (the frosted glass
blur) and `onClick`.

### Glass pill and capsules

- **Music pages** (`MusicScreen.kt`, `MusicHeader`): a 50 dp glass pill, with the title and a 42 dp
  orange play button. Its alpha, translation and scale follow `barProgress`, and the reads go through
  `graphicsLayer`.
  - Taps going through the pill (1.14.1): once active, the pill consumes taps with
    `pointerInput { detectTapGestures { } }`.
- **`GlassChip`** (`ui/components/Components.kt`):
  - height 44 dp, `cornerRadius` 22;
  - Roux tint at 0.82 when the capsule is selected, otherwise `0x38100D0B`.
- **`GlassChipsRow`** (`MusicScreen.kt`): the music library filters and the music search kinds (when
  not typing). The "tabs" item of `MusicSearchContent` was removed.
- **Home** (`HomeScreen.kt`):
  - the categories moved into `HomeOverlay`, in `HomeCategories(state, backdrop)`;
  - the content follows them with `HomeState.categoriesShift`, updated by a `snapshotFlow` on the
    list;
  - in the list, a 56 dp `Spacer` (`CATEGORIES_HEIGHT`) keeps their place;
  - the capsules are moved with **`offset { }`** and not `graphicsLayer`, so the glass's
    `onGloballyPositioned` follows;
  - the search sort and filters are `GlassChip`s too;
  - **Vertical drag on a capsule (1.15.1)**: the glass layer is above the list, so Compose doesn't pass
    it the gesture. The `LazyRow` carries `Modifier.scrollable(list, Vertical)` on the list's
    `LazyListState`, and, before it, `Modifier.pullToRefresh(...)` with the same
    `PullToRefreshState`. `HomeState.list`, `pull`, `refreshing` and `onRefresh` are filled by a
    `SideEffect` of the content. The row is **never removed**: it stays empty once gone, because
    removing it would cut the gesture and its momentum. Its shift is bounded (`categoriesShift` is
    `MAX` when the list is scrolled down).
- The dark veil of `RadioOverlay` was removed.

### Lighter frosted glass (1.14.2)

- `SearchSuggestions.kt`: tint `0x70`, bezel 20, frost 26.
- `CommentsUi.kt`: tint `0x80`, frost 32.

### Player suggestions

- **Portrait (1.14.3/1.14.4)**: the "Up next" list under the video was removed from
  `PlayerScreen.kt`. Only a margin remains when there is a playlist or suggestions.
  - `ui/components/PlaylistDrawer.kt` became a tabbed drawer: `PlaylistDrawer(backdrop, onOpen)`, with
    `DrawerTab { Playlist, UpNext }` and `DrawerTabChip`.
  - Tapping a suggestion closes the drawer and starts the video. In `MikenekoRoot`,
    `onOpen = { openVideo(it.url) }`.
  - Clear glass: tint `0x9E120F0D`, **no frost**, and a text shadow through
    `CompositionLocalProvider(LocalTextStyle)`.
  - The drawer was kept, but since 1.17.1 only for videos from a playlist; the "Up next" list came
    back into the page (1.17.0), under the comments.
- **Full screen (1.14.5/1.14.6)**: `FullscreenSuggestions.kt`.
  - **Clear liquid glass, NOT frosted**: no frost, tint `0x4D100D0B`, and a shadow under the title.
  - Centred: `side = max(left inset, right inset of safeDrawing)`, then
    `padding(horizontal = side + 12.dp)`. The notch no longer shifts the panel to one side.

---

## 7. Subscriptions: Takeout import fixed (1.14.7)

- **Bug**: "no channels" with Google Takeout's `abonnements.csv` in French. The file starts with a BOM
  and its header is in French, while the parser looked for English columns.
- `data/Subscriptions.kt` (`SubscriptionImport`):
  - the BOM is removed;
  - dispatch: `MikenekoBackup.looksLike` means an XML backup, `{` NewPipe's JSON, and everything else
    is read as Takeout CSV;
  - Takeout is read **ignoring the header**: on each line, a field that looks like
    `UC[A-Za-z0-9_-]{22}` or a channel URL gives the id, and the title is the last field that is
    neither the id nor a URL;
  - the `csvFields` parser handles quoted fields.
- Verified on the emulator with a French CSV.

---

## 8. Mikeneko backup in .xml (1.15.0)

`data/Backup.kt`, object `MikenekoBackup`:
- `export(context, out, appVersion)`;
- `read(input)` / `read(text)` returns a `Content`;
- `import(context, content)` returns `Added` (the number of items added per category);
- `looksLike(text)`.

Format (`android.util.Xml`, serializer and pull parser):

```xml
<mikeneko version="1" app="1.15.0" date="…">
  <subscriptions><channel id="" name="" url="" avatar=""/></subscriptions>
  <playlists kind="music|videos">
    <playlist name=""><video url="" title="" channel="" thumbnail="" duration="" live=""/></playlist>
  </playlists>
  <library><album …/><artist …/></library>
  <favorites>…</favorites> <later>…</later> <liked>…</liked>
</mikeneko>
```

- Import **merges** and never replaces. It relies on `Playlists.merge(name, items)`,
  `VideoLibrary.addLater` / `addLiked`, `Favorites.addAll` and `MusicLibrary.addAll`.
- Reimporting the same file adds nothing (verified on the emulator).
- Settings → Backup section: Export (`CreateDocument("text/xml")`, named `mikeneko-<date>.xml`) and
  Import (`OpenDocument`), with toasts.
- The Subscriptions screen accepts the .xml too, but only takes the subscriptions from it.

- 1.18.0: importing subscriptions from other apps (`SubscriptionImport`: NewPipe, FreeTube, Takeout,
  and our .xml) left the Subscriptions tab for `BackupSection` ("Import subscriptions", above the
  Mikeneko export / import). In `SubscriptionsContent`, `AccountCard` only shows if
  `GoogleAccount.ENABLED`; with no channel, an `EmptyCat` and an "Import" capsule (`onOpenSettings` →
  `Route.Reglages`).
- 1.18.1: subscription avatars. Takeout (CSV) and the RSS feed don't provide any. They come:
  - in passing, with no request: `PlayerHolder.play()` (the video's channel avatar) and a channel page
    (`ChannelScreen`), through `SubscriptionStore.setAvatar`;
  - in the background: `SubscriptionAvatars.fill` (`Subscriptions.kt`), started by the Subscriptions
    tab, one channel every 4 s, at most 30 per launch, with `YouTubeRepository.channelAvatar` (the
    channel page only, the smallest image ≥ 176 px). The pace is chosen to go easy on YouTube.

---

## 8 bis. Video resume (1.16.0)

- `data/WatchPositions.kt`: SharedPreferences `reprise_videos`, video id → `position;date`, at most 10
  (the oldest go out). Under 10 s or less than 20 s from the end: the entry is deleted (it will start
  over). Respects "history paused"; cleared by "Clear history".
- `PlayerHolder`:
  - saves (`saveVideoPosition`) on pause, at the end, before switching streams (`setCurrent`), on
    `stop()`, and every 10 s during playback (in case the app is killed);
  - `play()`: a video requested without a position (`startAtMs == 0`) resumes at the kept position.
- **Pitfall**: ExoPlayer calls its listeners during `setMediaSource` (`onIsPlayingChanged(false)`),
  while `nowPlaying` is still the old video. The new one's position would have been saved under the
  old id. Hence `positionOwner`: set to null in `setCurrent` (after saving the old position), and set
  again by `ownPosition()` right after `nowPlaying.value = …` (online and offline video).
- 1.17.0: the entry also keeps the duration (`position;date;duration`); `WatchPositions.progress()` is
  a `StateFlow` id → fraction, read by `WatchProgressBar` (`Components.kt`), laid in `VideoThumbnail`
  and in the history's small card (`LibraryScreen`). 1.16.0 entries, without a duration, get no bar.
- 1.17.0: suggestions are also in the player page (`PlayerContent`, under the comments,
  `CompactVideoRow`, without the thumbnail morph); the page scrolls back to the top on every new
  video. 1.17.1: `PlaylistDrawer` only shows for a video from a playlist; a single video only has the
  page's list.

---

## 8 ter. Rotation: only the video rotates (1.19.0)

- `MainActivity.setVideoRotation`: on the player screen (or in full screen), the window switches to
  `ROTATION_ANIMATION_JUMPCUT` (Android no longer animates the rotation); elsewhere
  `ROTATION_ANIMATION_ROTATE`. Called by `MikenekoRoot` (`onVideoScreen`).
- `MikenekoRoot` decides full screen **during composition**, when the orientation changes (no longer
  in a `LaunchedEffect`): otherwise, without Android's animation to hide it, a frame of the portrait
  player stretched to landscape showed. `onFullscreenChanged` runs in a `SideEffect`.
- `ui/components/RotationMorph.kt`: start = the inverse of the screen rotation (`Display.rotation`,
  old → new, brought to ±90°). To full screen: the video box starts sideways, at 9/16, in place of the
  portrait video (`portraitVideo`, kept even when the portrait player is gone), on the left or right
  depending on the direction. To portrait: sideways in the centre at 16/9, then to its place, `zIndex`
  above the page and a black veil fading out. `tween(700)` (~350 ms in reality).
- The full screen button (`toggleFullscreen`) no longer switches before the rotation: it forces the
  orientation, and the orientation change does the switch (safety net: after 1 s).
- 1.19.1 → 1.19.2: the mini player (`MiniPip`, `Video.kt`) is a **calico** cat head, like the logo:
  `CatHeadFrame` draws the white silhouette (`catHeadPath`, a polygon with rounded corners
  `roundedPolygon`), two patches (`clipPath` on the head), dark grey (`GrisChat`) on the left and
  orange (`RouxChat`) on the right, outline `Contour`, shadow; inside of the ears dark on the left,
  light on the right. Ears 30 dp high, 52 dp wide at the base, tip 13 dp from the edge (straight,
  extending the sides). Video 168 dp, 6 dp frame: `MiniPipSize` = 180 × 136.5 dp. The shapes were
  checked with a PIL rendering of the same geometry before installing.
- 1.19.3 → 1.19.4: `Pelage.Violet` is removed from the palette. Backgrounds without an image (`Cover`,
  the music pill and window, `rememberCoverColor`'s default, comment avatars, the ⋮ menu's thumbnail)
  are `RouxNuit`, icons / initials `RouxClair`.
- 1.19.5: music (`MusicWindow.kt`) no longer reads `PlayerHolder.nowPlaying` as is but `musicNow()`
  (nothing if it's a video); during a video, the pill shows `parkedTrack()` (the song set aside, found
  in the queue), its ▶ calls `restoreParkedMusic(autoPlay = true)`, and opening the window brings it
  back paused. The play buttons no longer touch the video.
- 1.19.6: `PlayerHolder.parkVideo()` (entering Music: pause + `parkedVideo` = URL and position) and
  `restoreParkedVideo()` (leaving: nothing if the video is still loaded, otherwise
  `play(…, autoPlay = false)`), called by `MikenekoRoot`. The mini player isn't shown on
  `Route.Musique`. The video doesn't come back while music plays. The set-aside music no longer
  reloads when entering Music while a video is loaded (the pill shows it, 1.19.5).
- 1.19.7: `MiniPip`: `LaunchedEffect(controls, poke)` hides the controls after `MINI_CONTROLS_MS`
  (3 s), `poke` restarted by play / pause. 1.19.8: veil, buttons and time bar in a fading
  `AnimatedVisibility` (340 / 480 ms, like the big player).
- No x.20: 1.19 was followed by 2.0.0.

---

## 8 quater. Landscape player page (2.0.0)

- `PlayerContent` is split into three pieces (`header`, `videoArea(modifier)`, `details`): portrait = a
  scrolling column (as before); landscape = a `Row`: on the left (60 % since 2.0.2) the centred video,
  rounded corners (the header is no longer there); on the right (40 %) the scrolling `details`.
  `RotationMorph.portraitVideo` is only updated in portrait.
- `MainActivity.applyFullscreen` returns whether the screen will rotate. Leaving full screen: portrait
  is forced only if landscape had been forced by the button and never "released" (phone kept upright,
  `releaseWhen == Hold.LANDSCAPE`); otherwise `releaseOrientation()` and it stays in landscape.
  `toggleFullscreen` switches right away when the screen doesn't rotate.
- `RotationMorph` only starts when entering or leaving full screen (`wasFullscreen != fullscreen`);
  `ROTATION_ANIMATION_JUMPCUT` only in full screen or on the player's portrait page: the landscape page
  rotates to portrait with Android's animation.
- Opening a video in landscape doesn't go full screen (automatic full screen only triggers when the
  orientation changes).
- 2.0.1: the progress bar moved from `details` into the video (`videoArea`): in the controls layer, a
  `Column` at the bottom (time + buttons, then `ProgressWithSegments` with sponsors, preview and
  marking); controls hidden, a 3 dp line (`progress.fraction`) fading. No auto-hide during a drag
  (`scrubbing`) or a marking (`marking`). Subtitles raised to 0.34.
- 2.0.2: in landscape, `header()` is no longer shown; the "minimise" arrow is a `GlassCircleButton` at
  the top left of the controls layer. Columns 60 % / 40 %.
- 2.0.3: in landscape, `statusBarSpacePadding()` only on the info column (the video centres on the full
  height), and `pullToMinimize` (drag down → mini player) on the video.
- 2.0.4: `RotationMorph.startZoom(toFullscreen)` (called by `toggleFullscreen` when the screen doesn't
  rotate, in landscape on the player): same `token` / `progress`, but without rotation. Full screen:
  the box starts at the size and place of `landscapeVideo` (the landscape page's video); back: the
  page's video starts from the size of the full screen picture, in the centre (`landscapeVideo(…)`),
  with the black veil. `videoArea` gets its `shape` and clips **after** the transformation; the left
  column goes above the info one (`zIndex`) during the zoom.

---

## 8 quinquies. Updates from GitHub (2.1.0, moved in 2.6.7)

- Since 2.6.7, the source and the releases live together in the **public** repository
  `Radinux/Mikeneko`. From 2.1.0 to 2.6.6, the APKs were published on `Radinux/Mikeneko-releases`
  (now archived: its releases stay downloadable) while the code was private. 2.6.7 was published on
  both repositories, so phones still checking the old one move over. No token in the APK (it would be
  extractable).
- **Publishing**: build, commit, push, then
  `gh release create v<version> app/build/outputs/apk/release/app-arm64-v8a-release.apk --repo Radinux/Mikeneko --target main --title "Mikeneko <version>" --notes "<What's new>"`,
  then rename the attached file:
  `gh api -X PATCH repos/Radinux/Mikeneko/releases/assets/<id> -f name=Mikeneko-<version>.apk`.
  The tag name must be `v` + versionName (the tag also marks the exact source of the APK, for the
  GPL); the app takes the release's first `.apk` file.
- 2.1.2: `UpdatePill` (a glass pill at the top, "Mikeneko X is ready · Update", ✕ later) as soon as
  the APK is downloaded; it replaces the toast. And `UpdateRow`'s buttons are in a `FlowRow` (text
  `maxLines = 1`, `softWrap = false`): in a `Row`, "What's new" got squashed and wrapped letter by
  letter.
- **Play Protect** triggers on every new version installed outside the Play Store (unknown file,
  unverified developer, `REQUEST_INSTALL_PACKAGES` permission). The app cannot and must not avoid it.
  Clean options: scan / send for scanning, or Google's developer verification (linking `app.mikeneko`
  and the signing key to a verified identity).
- `update/AppUpdates.kt`: `checkIfDue` (once a day, `MikenekoRoot`), API `releases/latest` (404 =
  nothing published = up to date), digit-by-digit comparison with the installed versionName, download
  into `cacheDir/mises_a_jour`, then `PackageInstaller` with
  `setRequireUserAction(USER_ACTION_NOT_REQUIRED)` (Android 12+: an app can update itself without a
  tap, same signature, if it was granted `REQUEST_INSTALL_PACKAGES` once). `UpdateReceiver` gets the
  answer (confirmation to show before Android 12, or an error).
- Automatic install in `MainActivity.onStop` (installing closes the app), unless music is playing;
  otherwise "Install now" in Settings › About (`UpdateRow`).

---

## 8 sexies. Big cleanup (2.1.3, `nettoyage` branch)

A deep cleanup of old code and merging of related pieces, done on a branch so that `main` (= 2.1.2)
stayed untouched. Nothing was meant to change on screen.

- **Google account removed**: `data/GoogleAccount.kt`, the comment bar (`CommentBar`, replies,
  `Comments.addMine`), `AccountCard` and the Subscriptions account button, the Settings'
  `AccountSection`, `SubscriptionStore.connected/setConnected`, the `Account` / `Send` icons, and the
  `play-services-auth` + `kotlinx-coroutines-play-services` dependencies. The subscriptions' `google`
  preference stays in old installs' prefs (no effect).
- **Split files** (same package, nothing renamed in the API):

  | Before | After |
  | --- | --- |
  | `PlayerScreen.kt` (1066 l.) | `PlayerScreen.kt` (page), `FullscreenPlayer.kt`, `PlayerControls.kt` (progress, centre buttons, autoplay, CC, fades) |
  | `MusicWindow.kt` (1181 l.) | `MusicWindow.kt` (pane + gesture), `MusicPlayerState.kt`, `MusicDock.kt` (pill), `MusicPlayerContent.kt` (open window, details), `MusicQueue.kt` (queue) |
  | `MusicPages.kt` (970 l.) | `MusicPages.kt` (album, artist, playlist, downloaded, your music), `MusicHome.kt`, `MusicSearch.kt`, `MusicPageHeader.kt` |
  | `PlayerHolder.kt` (1459 l.) | `PlayerHolder.kt`, `PlayerTypes.kt` (`NowPlaying`, `SettingsPage`, `SleepTimer`, `Repeat`), `VideoFrames.kt` (transition frame between video screens) |

  What was `private` and is now used across files became `internal`. `PlayerHolder` is still a big
  object: the rest shares too much private state to be moved out safely.
- **Radio → Music** in the Music tab's code: `RadioScreen.kt` → `MusicScreen.kt`, `RadioState` →
  `MusicState`, `RadioPage` → `MusicPage`, `RadioOverlay/Header/Content/Home` → `Music…`,
  `Route.Radio` → `Route.Musique` (the variable in `MikenekoRoot`: `musicPages`). Still "radio": a
  video's Radio button (`onRadio`, a real YouTube radio), `MkIcons.Radio`, `R.string.radio` /
  `error_play_radio`.
- **Merged duplicates**: `formatClock(ms)` (in `data/Models.kt`) replaces the music's `clock()` and the
  `formatDuration(x / 1000).ifEmpty { "0:00" }`; `shareLink` replaces the music's private `share`;
  `lerp` comes from `androidx.compose.ui.util`; the `musicKindLabel`/`kindLabel` and
  `libraryFilterLabel`/`filterLabel` pairs are now a single function each.
- **Unused code removed**: `CatEars`, `VideoLibrary.isLater/isLiked`, about fifty strings (EN + FR,
  including the `radio_*` ones and `tab_downloads`).
- **Tidying**: unused imports removed (~200), fully qualified names replaced by imports (~140; except
  `androidx.compose.animation.AnimatedVisibility`, written in full on purpose inside a `Column` so as
  not to pick the `ColumnScope` version), imports sorted, indentation of re-wrapped blocks fixed
  (whitespace only).
- **Pitfall met**: a cut by text pattern first took away the Subscriptions' channel row and filters
  (the end pattern was further down); restored from `HEAD`. Always reread the `git diff` of a
  deletion.

---

## 8 septies. Shorts player (2.2.0)

A full screen player like YouTube's; next shorts = the original list, then the suggestions.

- `ui/screens/ShortsPlayer.kt`:
  - `object Shorts`: `offer(list)` keeps the last 8 shown lists containing shorts (home and search
    through a `SideEffect`, a channel's Shorts tab and subscriptions on tap); `start(url)` (called by
    `openVideo`) finds the list containing the video and opens a `Session(items, index)` if it's a
    short (`VideoItem.isShort`, or a `/shorts/` link), otherwise resets the session to null.
  - `ShortsPlayer`: `VerticalPager`; the settled page (`settledPage`) starts
    `PlayerHolder.play(…, short = true)` (except the first, already started by `openVideo`). Near the
    end, the `related` items that are shorts get appended. Each page: thumbnail below,
    `VideoSurface(lastFrame = false)` only on the playing page (otherwise it would show the previous
    short's frame).
- `PlayerHolder.play(short = true)`: `REPEAT_MODE_ONE` + `loop`, no reading of `WatchPositions` nor
  `positionOwner` (a short doesn't replace a video in the resume list). `stop()` resets
  `playingShort` to false.
- `MikenekoRoot`: `Route.Lecteur` shows `ShortsPlayer` when `Shorts.session != null` (with only
  `CommentsPanel` above); `closeShorts()` stops playback and goes back to `beforePlayer`;
  `videoScreen` excludes shorts (no automatic full screen when rotating).
- 2.2.1: lists don't always know a video is a short (subscriptions RSS feed, "For you": `isShort`
  false). `NowPlaying.isShort` comes from `StreamInfo.isShortFormContent`, or failing that from an
  upright video (highest stream: width < height) of 3 min or less. A `LaunchedEffect(now)` in
  `MikenekoRoot` then calls `Shorts.adopt(now)` and `PlayerHolder.becomeShort()` (loop, no more
  resume): the player page gives way to Shorts.

- 2.3.0, "Your shorts": `VideoLibrary.shorts` (prefs `bibliotheque`, key `shorts`, loaded with
  `isShort = true`), `toggleShort` / `removeShort` / `addShorts`; bookmark `MkIcons.Bookmark` /
  `BookmarkFilled` in `ShortActions`; `LibraryPage.Shorts` → `ShortsPage` (a grid of `ShortCard`, now
  `internal` in `ChannelScreen.kt`, calling `Shorts.offer(shorts)` before opening); `MikenekoBackup`
  exports / imports `<shorts>`.
- 2.3.0, "No shorts": `AppSettings.hideShorts` (prefs `reglages`, `shorts_masques`).
  `VideoItem.looksShort` = `isShort` or a `/shorts/` URL (the subscriptions RSS only gives that).
  Filtered in `Feedback.allows` (home, "For you", player suggestions: `related` items are filtered on
  load), `SearchFilters.accepts`, the subscriptions list, a channel's tabs (read without observing:
  inside the `LazyColumn`) and the "Your shorts" tile.

---

## 8 octies. The "Mikeneko fixes list" (2.4.0)

- Shorts blocker: `VideoItem.looksShort` also counts videos of 1 to 60 s; the subscriptions RSS reads
  `<link href=…/shorts/…>`; `isShort` is kept in the lists' JSON (`toTrackJson`, key `short`) and the
  history's (`WatchHistory`, whose `related` items feed "For you": that's how shorts came back).
- `tracksSummary(tracks)` (`MusicSearch.kt`): count + total duration; `TrackRow(number)` is no longer
  used by albums.
- Covers: `Playlist.cover` (a path in `filesDir/pochettes`, a new name for every photo for Coil's
  cache); `Playlist.coverModel()` = the file, otherwise `PlaylistArt(4 URLs)`, otherwise the first
  cover. `PlaylistArtFetcher` (+ `Key`, registered in `MikenekoApp`) builds the 512 px mosaic with the
  `ImageLoader`. `Cover`, `PageHeader`, `SquareCard`, `QuickTile`, `CollectionRow` and
  `rememberCoverColor` now take an `Any?` model. The music pages' header buttons are in a `FlowRow`
  (they no longer get squashed).
- Lyrics: an `Animatable` `drag` as `translationY`, a `NestedScrollConnection` (the rest of the drag
  when the lyrics are at the top) and a drag on the top bar; closes past 120 dp or on a quick fling.
  Lyrics open: `view.keepScreenOn` (DisposableEffect).
- `StatusBarBand` (`MikenekoRoot`): a black `Canvas` the height of `statusBarSpace` + 34 dp (22 dp in
  2.4.0), minus a rectangle with rounded top corners (`PathOperation.Difference`), only if the status
  bar is shown and outside Shorts.
- More transparent music: `PlayerWindowContent`'s veil at alpha 0.55 → 0.8, dimming of the page behind
  0.38 → 0.22.

---

## 8 nonies. Music spectrum (2.5.0)

- `player/AudioSpectrum.kt`: `SpectrumRenderersFactory` (passed to `ExoPlayer.Builder`) builds a
  `DefaultAudioSink` with a `TeeAudioProcessor(AudioSpectrum)`: the PCM goes through unchanged, and a
  copy goes to `AudioSpectrum.handleBuffer` (audio thread). No `Visualizer` (it would need
  `RECORD_AUDIO`). Mono, a ring of 1024 samples, radix-2 FFT with a Hann window 30 times a second, 40
  logarithmic bands from 40 Hz to 16 kHz (each band's peak in dB, −60…0 dB → 0…1, treble raised by up
  to 6 dB), smoothing rise 0.65 / fall 0.2. Computed only while `listening` (set by the spectrum
  composable when the window is open).
- 2.5.2: `DefaultAudioSink` passes sound in chunks (~4 per second, when the AudioTrack has room):
  analysing on arrival stuttered. Now `handleBuffer` only fills a mono ring of 65,536 samples
  (`written` counts the total, `epoch` changes on every `flush`); and the composable moves a real-time
  playhead (`withFrameNanos`, only while playing), 0.5 s behind `written` (≈ the sound still in the
  speaker's buffer), and calls `bandsAt(playhead)` on every frame; the `Canvas` rereads a frame counter
  (redraw only).
- 2.5.3, pill (`MusicDockRow`): the cover no longer moves (`Crossfade`); only the text (`TrackText`)
  follows `swipe`, and the neighbour (`nextItem` / `previousItem`, the queue loops like `playAt`; on
  the right after 3 s, it is the current song, which "previous" restarts) is drawn at
  `swipe ± (width + 40 px)`. Confirmed swipe: the neighbour finishes its travel to 0, it is kept in
  `landed` (shown right away), then `swipe` goes back to 0 and `next` / `previous` is started;
  `landed` fades when `now` or `pending` catches up (2 s at most).
- 2.5.3, music window: `settle` springs 0.66/58 → 0.8/80 (opening) and 0.62/75 → 0.86/110 (closing): a
  single small overshoot.

---

## 8 decies. Music "For you" and "Your mixes" (2.6.0)

- `data/MusicForYou.kt`: seeds = the 3 most frequent artists (`cleanArtist(channel)`) in
  `MusicHistory` (last 30), with their latest song; `Music.mix(id)` for each (in parallel). "For you"
  = the mixes interleaved, without ids already played or in a playlist (30 at most). Mixes kept if ≥ 4
  songs: `GeneratedMix("mix:artist", …)`; plus "Rediscover" (your playlists minus recent plays, a
  shuffle stable over the day). `TimedCache` of 6 h, key = the seed artists.
- UI: `MusicHome` (`produceState` on the last song played: `cached` otherwise `load`), "For you"
  shelves (`SquareCard`, plays the list from the tapped song) and "Your mixes" (`PlaylistArt` mosaic);
  `MusicPage.Generated(mix)` → `GeneratedMixContent` (header, shuffle,
  "Keep" = `Playlists.merge(title, songs)`).
- 2.6.0, yt-dlp: in release, `YoutubeDL.init` → `ZipUtils.unzip` threw `ExceptionInInitializerError`
  (no message) ← "class … is not a concrete class": R8 broke Apache Commons Compress (zip extra fields
  created by reflection). `proguard-rules.pro` keeps `org.apache.commons.compress.**` and
  `org.apache.commons.io.**`. Reproduced then verified on the emulator (`Medium_Phone_API_37.0`, x86_64
  APK, Library › Downloads). `Downloads` now writes the error to the log (`Log.e`), shows kind +
  message + cause (`describe`), and `prepare` retries after a failure (`ready` recreated).
- 2.6.1: on the phone, downloads then failed with a Python trace ("frozen runpy,
  _run_module_as_main") while the same link in FLAC worked on the emulator. Likely cause: a Python /
  yt-dlp install damaged by the earlier failed attempts. `repair()` deletes
  `no_backup/youtubedl-android/packages/python` and `yt-dlp`, the `pythonLibVersion` preference (prefs
  `youtubedl-android`) and `maj_ytdlp`, then calls `initPython` / `init_ytdlp` (public); a single
  retry. `readableError` shows the last `ERROR:` line (or the trace's last line); `Log.e` keeps
  everything. If it fails again: `adb logcat | grep -A40 "téléchargement raté"`. After the 2.6.1
  update, downloads worked on the phone: the damaged-install hypothesis holds (the automatic repair
  was enough).

---

## 8 undecies. Spectrum wave in liquid glass (2.6.2 → 2.6.6)

- `SpectrumWave` (`ui/screens/MusicPlayerContent.kt`): behind the music window's content, aligned to
  the bottom, 28 % of its height in portrait, 46 % in landscape. It replaces the bars above the
  progress bar (2.5.0), which pushed the buttons too low. It doesn't show the progress (that's the
  progress bar's job).
- Colour: `rememberCoverColor` (the same as the window's veil, which is darkened), brightened in HSV
  (value 0.95), crossfading between songs.
- `ui/glass/LiquidWave.kt` (Android 13+): an AGSL shader receives the 40 crest heights as
  `uniform float crest[40]` and redoes the same curve as `quadraticTo` through the midpoints
  (quadratic B-spline, straight segments at both ends). Under the crest: refraction of the backdrop,
  tint and transparency fading towards the bottom, a highlight on the crest. The shader is compiled
  once as a test (`liquidWaveSupported`): if it fails, the wave drawn with a `Canvas` comes back
  instead of crashing.
- AGSL: no variable array index outside a loop, hence `at(k)` walking the 40 points.
- 2.6.6: the wave must refract **the page behind the window** (`MusicDockWindow`'s `Backdrop`, passed
  to `PlayerWindowContent` as `page`), not the window veil's `Backdrop`. With the veil, it redrew it on
  top of itself: opaque, and the refraction of a blurred gradient is invisible.

---

## 8 duodecies. New "For you" and "What's new" page (2.7.0)

Common recommendation principles: two stages (candidates, then ranking), aimed at watch time;
completion as a strong signal, interests picked at the start, diversity and exploration; fatigue for
things shown without engagement. Collaborative
filtering is impossible locally, but YouTube's related videos already are one, computed over all its
users: the point is to take them from the videos really watched.

- `data/Interests.kt`: the profile, computed on demand. `watchWeight(entry)` = engagement × recency
  (half-life 30 days); engagement from `WatchHistory.Entry.watched` (< 10 % → 0.1, ≥ 80 % → 1,
  unknown → 0.6). Likes +2, "Watch later" and video playlists +1, subscriptions +0.8 on the channel,
  "Not interested" −1.5 on the channel and −0.9 on its title's words (fading over 60 days), chosen
  tastes +1.5 on their queries' words (weaker as history grows: ÷ (1 + strength/10)). Topics = title
  words + tags, lowercased, accents removed, without stop words (EN/FR + YouTube noise) nor the
  channel's own name; a word seen on a single channel is damped. `topicMatch` / `channelMatch` give
  −1…1.
- `data/WatchHistory.kt`: entries keep `watched` (max fraction, from `PlayerHolder.saveVideoPosition`
  → `recordProgress`) and up to 10 `tags`; 200 entries (60 before), only the 60 most recent keep their
  related videos (file size); `loaded` tells when the file has been read.
- `data/Tastes.kt`: 20 themes (`taste_*` labels, `taste_q_*` query arrays, EN/FR), the chosen set,
  `skipped`, and a per-install `seed`.
- `data/Impressions.kt`: fatigue. `HomeContent` reports the For you cards that become visible
  (at most once per 30 min per video); score × 0.55^(views − 1), dropped after 5 views, forgotten
  after 10 days; saved when the feed leaves the screen; cleared with the history.
- `data/ForYou.kt`: personal candidates = related videos of the 25 most weighted watched entries +
  subscriptions' new uploads; score × (1 + 0.9·topicMatch + 0.5·channelMatch) × random 0.85–1.15 ×
  `Feedback.penalty` × fatigue; `diversify`. Discovery (1 in 5, `mix`): a random query of 1 chosen
  taste (3 when cold), one strong profile topic plus the word that most often goes with it in watched
  titles, surprise topics to fill a budget of 3 searches (4 when cold), and "one thing leads to
  another"; results filtered (views, ≥ 60 s), sorted by topic match + randomness, 6 per query. The
  random generator is seeded with `Tastes.seed` (stable for 10 min unless refreshing). No more
  `YouTubeRepository.home()` (trending kiosks + fixed searches, the same for everyone).
- `ui/components/TastesPicker.kt`: `TasteChips` (clay capsules, in the list = the glass source, so no
  glass) and `TastesPicker`, shown by `HomeContent` instead of the For you feed when history is
  loaded and empty, no taste is chosen and it wasn't skipped (no request meanwhile). Settings ›
  For you and history › Your tastes uses `TasteChips` too.
- `update/WhatsNew.kt`: `WhatsNew.pending` compares the installed versionName with the last one seen
  (prefs `nouveautes`); a first install (`firstInstallTime == lastUpdateTime`) counts as seen.
  `WhatsNewSheet` (frosted glass panel over a veil, in `MikenekoRoot` above everything) shows
  `whats_new_title` / `whats_new_body`.
- Checked on the emulator (fresh install): tastes page, then a feed from the chosen tastes
  ("Because you like Science"), no crash. French strings: use ’ rather than `&apos;` (aapt fails on
  an unescaped apostrophe).

---

## 8 terdecies. Import from TuneMyMusic (2.8.0)

- `data/PlaylistImport.kt`. TuneMyMusic doesn't document its file formats; a real export (1219
  lines) had `Track name, Artist name, Album, Playlist name, Type, ISRC`, then a service id column, UTF-8
  with a BOM, every field quoted, `Type` = Playlist / Favorite (liked songs, playlist "Favorite
  Songs") / Artist (followed artists: the name is in `Track name`, no artist) / Album (saved albums:
  the album name in `Track name`). Artist names can contain `,` or `&` ("OH, HAYIR!", "W&W"): never
  split them. The parser finds columns by name, picks `,` `;` or tab from the header, handles quotes;
  without a known header it reads "Artist - Title" lines (TXT).
- `preview()` → `Preview.groups` (one per playlist and kind, duplicates already merged in the
  counts) → `PlaylistImportPicker` (checkboxes, searches and minutes estimate; everything ticked if
  ≤ 150 searches, nothing otherwise) → `start()`.
- One search per distinct song (`key` = normalized artist + title without feat./remaster/live…),
  `Music.songs` then, if nothing matches, `YouTubeRepository.search` (ASMR, podcasts…). Best result:
  title word overlap + 0.35 if an artist word is in the channel or title, accepted from 0.6. Artists:
  `Music.artists` (exact normalized name first); albums: `Music.collections(…, Albums)` (≥ 0.8).
  Adaptive speed (2.10.2; before, one search every 2 s): 3 workers take tasks from a queue
  (`MAX_PARALLEL`), 250 ms pause; every error or timeout lowers the allowed parallel searches by one
  and doubles the pause (up to 1 search, 4 s); 20 successes in a row speed up again. 5 failures in a
  row (errors or timeouts, not "no match") stop the import (`Result.refused`). Measured on the
  emulator: 72 searches in 26 s. `estimateSeconds` = 0.6 s per search. Results are merged at the end, even after a stop: `Playlists.merge` per
  playlist (skipping songs already there), `MusicLibrary.addAll` for artists and albums.
- `busy` keeps the app from clearing itself in the background (`MainActivity.busy`,
  `PlaybackService.onTaskRemoved`). UI: `PlaylistImportProgress` under the Settings row,
  `PlaylistImportSummary` dialog at the end.
- Checked on the emulator with the real export (pushed to Download): picker groups, progress,
  summary. A full import of that file would be ~1100 searches (~40 min): import a few playlists at a
  time.

---

## 8 quattuordecies. Colors, Material 3, video quality and orientation (2.9.0)

- **Colors**: `ui/theme/Theme.kt`: `Accent` (7 presets: base / light / dark / night, no purple) and
  `Appearance` (Compose states `accent`, `amoled`, filled by `AppSettings.init`, saved as `accent` /
  `amoled`). `Pelage.Roux/RouxClair/RouxFonce/RouxNuit` and `Nuit/Coussin/Bord` are getters on
  `Appearance`, so everything reading them redraws when the setting changes; the cat's colors
  (`RouxChat`, `GrisChat`) stay calico. `MikenekoTheme` builds the Material 3 color scheme from them
  (`secondaryContainer` = Bord tinted 22 % with the accent, for tonal buttons). Settings ›
  Appearance: color swatches + pure black switch.
- **Material 3**: `Capsule` (Components.kt) = `Button` when selected, `FilledTonalButton` otherwise;
  player actions = `FilledTonalButton` with icon, like/dislike = a two-part pill in the button colors;
  tastes = `FilterChip`; downloads, SponsorBlock marker, bedtime reminder, What's new = Material
  buttons; the undo bar = `inverseSurface`. Still clay: `Tag` (small labels) and the dock's selection
  drop (inside the glass).
- **Video quality**: `MediaSources.autoMaxHeight` is now a getter: on an unmetered network, the first
  YouTube tier ≥ the screen's short side (1264 → 1440), otherwise ≤ the screen (as before), always
  720…1440. `bestVideo` compares tier, then "decoded in hardware" (`codecRank >= 3`), then **bitrate**,
  then codec and fps. Downloads use the same Auto limit.
- **Morph without freeze**: `VideoFrames.live(width)` grabs the current frame of the attached
  TextureView (`getBitmap(reuse)`, 640 px wide, one reused Bitmap). The destination screen is only
  hidden by alpha during the morph, so it already receives frames; `VideoMorphLayer` swaps the photo
  for the live frame on every screen frame once `firstFrames` moved and the video plays.
- **Swipe to the mini player**: `pullToMinimize` (PlayerScreen) uses `awaitVerticalTouchSlopOrCancellation`
  and only takes downward drags (upward ones go to the page scroll); in portrait it's on the video too,
  only when the page is at the top.
- **Orientation**: `MainActivity.physical` remembers how the phone is really held (orientation sensor,
  even with Android's auto-rotate off). Leaving full screen with the phone held sideways forces
  `SENSOR_LANDSCAPE` with `Hold.UNTIL_PORTRAIT` (released when the phone is turned upright). Before,
  the decision depended on `releaseWhen`, which only switched once the sensor had seen landscape
  *with auto-rotate on*, so it often forced portrait.
- Pitfall: in Python `re.sub`, a `\\n` in the replacement becomes a real newline; Android then turns it
  into a space (the 2.8.0 import summary lost its line breaks). Use a function as replacement.

---

## 8 quindecies. Notifications and connected capsules (2.10.0)

- `update/Notifier.kt`: channels `mises_a_jour` and `nouvelles_videos` (created in `MikenekoApp`),
  `WatchWorker` (WorkManager 2.12, `enqueueUniquePeriodicWork("veille", KEEP)`, every 2 h with a
  network). It checks:
  - the channels whose bell is on (`SubscriptionStore.bells`, prefs `abonnements` key `cloches`,
    off by default): `SubscriptionFeed.latest(listOf(sub))` (RSS, or the channel's Videos tab if RSS
    is down), notifies what was published after the last check (`cloches_vues`, per channel; the
    first check only records), 3 per channel at most, no live streams, no shorts with "No shorts",
    nothing already watched; thumbnail through Coil (`BigPictureStyle`); a tap opens the video
    (`ACTION_VIEW` to `MainActivity`).
  - an update once a day (`AppUpdates.latest()`), notified once per version (`maj_annoncee`);
    `AppUpdates.download` also notifies when the APK is ready (`maj_prete`). A tap opens the app with
    `EXTRA_UPDATE` → `AppUpdates.check`.
  - Nothing is posted without the permission (`Notifier.allowed`).
- `ui/components/BellButton.kt`: `FilledIconToggleButton` next to Subscribe (only when subscribed),
  on the player page and the channel page; asks for `POST_NOTIFICATIONS` (Android 13+) when turned on.
  Unsubscribing turns the bell off.
- `ConnectedChip` (Components.kt) replaces `GlassChip` and option rows of `Capsule`: 44 dp high, 22 dp
  side padding, 3 dp gap (`CONNECTED_GAP`), outer ends rounded and inner corners 6 dp, the selected
  one animates to a full pill in `primary`. The home categories, search sort (with the Filters chip
  apart), search filter options, music library filters and kinds, channel tabs, subscriptions filters.
- Lighter colors: `secondaryContainer` = cream mixed 30 % with the accent's light color, text `Encre`.
- 2.10.1: Subscribe and the bell are one split pill: `ConnectedChip(first = true, last = !subscribed,
  pillWhenSelected = false)` + `BellButton` (a `ConnectedChip` with an icon only, `last = true`).
  `WhatsNew.pending` ignores patch updates (same major.minor).

---

## 8 sexdecies. Smoother music dock (2.10.3)

- `MusicDockWindow` read `state.expand.value` in composition (for the dimming veil): the whole dock
  recomposed on every animation frame. Now `derivedStateOf { open || expand > 0.01 }`.
- `MusicPlayerState.dragTo` / `stop` launch with `CoroutineStart.UNDISPATCHED`: the pane moves in the
  same frame as the finger (before, every move waited for the next main-thread turn).
- The window background no longer uses `Modifier.blur(60.dp)` (a RenderEffect recomputed on every
  frame, twice since the background is also recorded as the buttons' backdrop): the cover is loaded
  at 48 px and stretched (`FilterQuality.Low`), which looks blurred for free.
- 2.10.4: `SpectrumWave` still moves its playhead on every frame, but computes the bands and redraws
  (`frame++`, a new RenderEffect for `LiquidWave`) at most every 33 ms (`WAVE_FRAME_NANOS`): the
  open window no longer keeps the GPU busy on every frame (system animations lagged).

---

## 8 septdecies. Playlist management (2.11.0)

- `MyPlaylistContent` (MusicPages.kt): `editing` (rememberSaveable, back leaves it). Normal: download
  circle, Shuffle, and an Add | Edit split pill (`ConnectedChip`); song rows with ⋮ and the downloaded
  mark, no drag handle. Editing: `PlaylistEditPanel` (MusicPageHeader.kt: `OutlinedTextField` renaming
  live, Cover / Mosaic chips, Delete playlist touched twice, Done), rows with a leading ⊖
  (`TrackRow(leading = …)`, `Playlists.remove(id, index)`) and the drag handle. Deleting pops the
  playlist and its Add page from `MusicState.pages`.
- `MusicPage.AddSongs(id)` → `AddSongsContent`: suggestions = `Music.mix(videoId)` of 3 random songs
  of the playlist, interleaved, without the songs it had when the page opened, 40 at most; a search
  field (`Music.songs`, 400 ms debounce). Trailing `FilledTonalIconButton` + / `FilledIconButton` ✓
  (removes again). Tapping a row plays it.
- Music player: a Video `ActionSlot` → `onWatchVideo(url, position)` (MusicDockWindow → MikenekoRoot):
  the window folds and `openVideo(url, startAtMs)` opens the video at the music's position (the music
  is set aside as when any video starts).
- 2.11.2: a separate `reordering` mode (Add | Edit | Reorder pill): handles only, a hint and Done;
  Edit keeps name, cover, delete and the ⊖ buttons. `Playlists.add` inserts at the top.
  `ReorderState` auto-scrolls: given a scope and the hidden top / bottom insets (top bar, docks), when
  the dragged row is within `edge` of the visible area, a frame loop calls `list.scrollBy` (up to
  22 px per frame, faster closer to the edge), adds the scrolled amount to the drag offset (the row
  stays under the finger) and swaps rows as it goes; it stops at the end of the list or on release.
- 2.11.4: modes `details` (`PlaylistEditPanel`: name, cover, delete) and `songsEdit` (⊖ + handles,
  hint, Done); the Add | Edit | Details pill moved to `PageHeader(below = …)` (a new slot under the
  play button row), equal widths (`ConnectedChip(sidePadding = 8.dp)`), icons Pencil / Info.
  `MusicState.playlistFilter`: the top-right ⋮ of a playlist became a search (`PlaylistFilterField`,
  glass, in place of the title pill); the list shows matching titles / artists with their original
  indices; editing is turned off while filtering; back closes the search first.
  (2.11.6: replaced, see below.)
- 2.11.6: `ReorderState` follows the finger: `finger` (viewport coordinates, set to the row's centre
  on start, + every drag delta; unchanged by auto-scroll since the finger doesn't move on screen),
  the row drawn centred on it (`offset = finger − (slot + size/2)`), the swap target = the row under
  the finger, and the auto-scroll speed from the finger's position in zones of `edge` (110 dp) before
  the top bar / docks, quadratic up to 26 px per frame (2.11.7: the maximum, 64 px per frame, is
  reached when the finger gets to the top bar / docks line; at the top the finger stops around the
  bar, where the speed was only a third of the maximum). After a swap, `awaiting` blocks new swaps until
  the list is laid out again (or 100 ms): two drag events in the same frame used to swap twice and
  undo the move.
- 2.11.5: the music window background has no image any more, only the cover-color gradient (the 48 px
  cover stretched since 2.10.3 looked pixelated). The page bar pill's tap blocker is a separate
  `PillTapBlocker` composable shown once the pill is visible: adding / removing a `pointerInput` on
  the `LiquidGlass` itself rebuilt the pane mid-scroll (a short stutter of the travelling play button).
- 2.11.8: the header play button's `onGloballyPositioned` (called on every scroll frame) rewrote
  `PlayAnchor.inHeader / left / listTop` every time; `listTop` is a difference of two scrolling
  positions, so it changed by a rounding each frame and re-laid out the travelling play button. Only
  written now when it moves by more than half a pixel. Clue: no stutter in Edit mode, where the header
  has no play button.
- 2.11.9: no more travelling play button (`TravellingPlayButton` removed, `PlayAnchor` no longer
  used by the header). Two states: the header button (plain, scrolls with the page) and
  `PillPlayButton` in the top pill (`AnimatedVisibility` fade + scale, shown when
  `barProgress > 0.6`, in its own composable so only it recomposes).
- 2.11.10: the real cause of the music pages' scroll stutter (found by the user): any never-ending
  animation inside the page redraws it every frame, and the glass pill above with it. Two of them:
  `DownloadedMark`'s indeterminate `CircularProgressIndicator` (stuck forever when the app was
  removed from recents mid-download: the process stays alive for the download, gets frozen by the
  system, and the connection hangs without finishing or failing) and `PlayingBars`, which animated
  even while paused (hence "only with a song loaded"). Now: `PlaylistDownloads` exposes `progress`
  (2 % steps from `CacheWriter`'s progress listener), the mark is a determinate ring read at draw
  time in its own composable; `PlayingBars` draws still bars when `PlayerHolder.isPlaying` is false.
  Downloads have a watchdog: no byte for 45 s → `CacheWriter.cancel()` + closing the data source
  from a separate thread; still stuck 20 s later → the work is abandoned (`generation` bumped so the
  old one stops if it wakes up, item retried 10 min later) and a new worker starts.
  `MainActivity.onStart` calls `PlaylistDownloads.resume()` so a frozen download is detected within
  ~10 s. HTTP connect/read timeouts set to 15 s.
- 2.11.11: the travelling play button is back (2.11.8 code: `TravellingPlayButton` + the header's
  anchor with 0.5 px thresholds); the 2.11.9 `PillPlayButton` sometimes left the pill empty, and the
  stutter it was meant to fix came from the never-ending animations (2.11.10). `Tag` is a flat
  Material 3 `Surface` (8 dp corners, secondaryContainer colors) instead of `clay`. Music Moods: a
  `ConnectedChip` group (`Shelf(gap = CONNECTED_GAP)`) instead of gradient cards. `MusicDockWindow`
  warms the window up 1.5 s after launch: `liquidWaveSupported` touched (wave shader compiled) and
  `PlayerWindowContent` placed for 4 frames at alpha 0.004 (at 0 the renderer skips it), so its first
  real draw (display lists, image uploads, shader pipelines) no longer happens mid-animation.
- 2.11.12: `PlaylistDownloads` checks and fallbacks. `saved` = index entries whose streams are
  whole in the store (`OfflineCache.entry`), checked at start and on every sync; listed-but-broken
  entries are forgotten and downloaded again. Every download is checked the same way before counting.
  Failures: back-off 30 s / 2 min / 10 min / 30 min (`retryJob` comes back at the earliest retry, the
  worker itself stops so the app can still idle-clear); `ExtractionException` 3 times, or 5 failures
  that aren't network errors (cause chain: UnknownHost, Connect, SocketTimeout, SSL, stall…) →
  `unavailable` (persisted). Network errors never exhaust the tries. `resume()` clears `retryAt`.
  Turning a playlist's download off and on again retries its unavailable songs. The entry is stored
  under the requested id (not `info.id`, which could differ and made the entry look unwanted, so it
  was deleted and downloaded again forever). UI: complete = done + unavailable >= total; the ring
  pales while waiting for a retry (`working` flow); unavailable rows get a grey cross.
- 2.11.13: `MusicPage.EditSongs(id)` / `EditSongsContent`: title row + hint outside the
  `LazyColumn` (the list clips the dragged row, its viewport starts under them, so `topInset` = 0 and
  a finger above the list means full scroll speed). Edit mode removed from `MyPlaylistContent`.
  Moves change a local `mutableStateListOf` and are saved once on drop (`ReorderState.onDrop` →
  `Playlists.setItems`): before, every swap re-serialized all playlists to JSON on the main thread and
  re-ran the download sync, so the row lagged behind the finger. `ReorderState.settle`: swapping with
  the first visible item calls `requestScrollToItem(first, offset)` first, otherwise the LazyList
  keeps that item's key in place and jumps with it (the "top" bug). Checked on the emulator: song
  dragged from the middle and held above the list → scrolled to the top, became first, order saved.
- 2.11.14: music player Video button removed (`onWatchVideo` gone from `PlayerWindowContent`, `MusicDockWindow` and the root; `watch_video` string removed). `openVideo(url, startAtMs)` kept.
- 2.11.16: `RotationMorph.startZoom(toFullscreen = false)` no longer needs `landscapeVideo` up front:
  the first time, the landscape player page had never been laid out, so the zoom was skipped. `animate()`
  now waits (video held at its start) for `landscapeVideo` (400 ms max) and two frames before running,
  so the page's heavy first layout frames don't eat the animation.
- 2.11.17: `RotationMorph.landscapeVideo` / `portraitVideo` are snapshot state: the video's
  `graphicsLayer` re-runs as soon as the page reports its place. As plain vars, on the first exit
  the layer ran once with `null` (video drawn small at its page place during the hold frames), then
  jumped to fullscreen size when the progress started. While the place is still unknown the video is
  hidden (alpha 0) instead.
- 2.11.18: the real cause of the jump: the centre controls' `lift` (keeps them clear of the bottom
  row) came from `videoHeight` / `bottomBar` measured with `onSizeChanged`; on the first frame both
  were 0, giving a 38 dp lift, corrected one frame later (buttons jumped up and back). `lift` is now 0
  until both are measured. The controls overlay is also at alpha 0 while `RotationMorph.running`
  (`derivedStateOf`), fading back in over 200 ms.
- 2.11.19: the 2.11.18 controls fade during `RotationMorph` removed, so the `lift` fix can be seen on its own.
- 2.11.20: resumed videos stayed black (`Source error`, player IDLE) and play did nothing. Cause:
  the playback cache key was `id:kind+itag`, but YouTube serves different files under one itag
  (the "DRC" audio variant of itag 140/251, re-encodes). Resuming mid-video read the cached start
  of one file and fetched the rest of another (different length). Fixes: `OfflineCache.key` now
  appends the content length (`ItagItem.contentLength`, else the `clen=` URL parameter), so old
  partial entries are simply not reused; `bestAudio` drops `isDrc` tracks unless nothing else is
  left; `PlayerHolder.onPlayerError` calls `retry()` once per playback (forgets the video's played
  cache via `OfflineCache.forgetPlayed`, asks YouTube for fresh streams, same position, without
  letting the errored player's position overwrite the resume point); `togglePlay` on an errored
  (IDLE) player calls `retry()` too, since `play()` alone does nothing there. Checked on the
  emulator: resume at 11 min plays; Wi-Fi off + seek far ahead → `Source error`; Wi-Fi on + play →
  plays again at the same position.
- 2.12.0: bell notifications. Before, `Notifier.checkNewVideos` kept only the newest publish date
  per channel (`cloches_vues`) and announced anything newer. When RSS failed (even for one channel,
  `SubscriptionFeed.rssDownUntil` switches every channel to the Videos tab for 30 min), the tab's
  dates are approximations computed from "1 year ago" / "12 days ago" *at fetch time*, so they move
  forward on every check and land after the stored real date: old videos were re-announced, and the
  stored date drifted. Now `cloches_videos` keeps the last 80 video ids per channel (RSS and tab
  ids merged); a video is announced only if its id was never seen AND its date is less than 3 days
  old (unknown date = EPOCH = skipped); an empty/failed fetch changes nothing. First check of a
  channel only records. The worker runs every hour (`ExistingPeriodicWorkPolicy.UPDATE` so phones
  with the old 2 h schedule switch). Testing tip: WorkManager ignores a forced JobScheduler run of
  a periodic job that isn't due; delete `no_backup/androidx.work.workdb*` (debug build, run-as)
  and relaunch to get an immediate first run.
- 2.12.0: updates: `AppUpdates.CHECK_EVERY_MS` = 4 h, checked from `MainActivity.onStart` (launch
  and every return to the app) instead of a `LaunchedEffect` at launch; the background check
  (`Notifier.checkUpdate`) uses the same interval.
- 2.13.0: comments. `CommentsPreview` + the glass `CommentsPanel` over the page became
  `CommentsCard(resetKey = info.url)` in the player page: folded it shows the count and the first
  comment; tapped, it grows (`animateContentSize`) to a fixed height (62% of the screen, 320–620 dp)
  holding the same `CommentList` (a LazyColumn with a bounded height inside the page's
  `verticalScroll`, so nested scrolling works: the list scrolls first, the page at its ends).
  `BringIntoViewRequester` scrolls the page to show the whole card once it has grown; `BackHandler`
  folds it; a new video folds it. `CommentsPanel` is now only used by the Shorts player
  (`Comments.open`). Checked on the emulator.
- 2.14.0: channel search and Popular. NewPipe has neither (no search tab, and its
  `YoutubeChannelTabExtractor.getPage` only reads `appendContinuationItemsAction`, while the sort
  chips answer with `reloadContinuationItemsCommand`). `data/ChannelBrowse.kt` sends the InnerTube
  `browse` requests itself through NewPipe's helpers (`YoutubeParsingHelper.prepareDesktopJsonBuilder`
  + `getJsonPostResponse`, same client and headers): search = `browseId` + params
  `EgZzZWFyY2jyBgQKAloA` + `query` (read only the selected tab, the header holds other lists);
  Popular = the Videos tab (`EgZ2aWRlb3PyBgQKAjoA`), then the token of the 2nd sort chip (always
  Latest, Popular, Oldest; `chipViewModel`, or the old `chipCloudChipRenderer`); more pages =
  `continuation` token from `continuationItemRenderer`. Items are parsed with NewPipe's public
  `YoutubeStreamInfoItemExtractor` (`videoRenderer`) and `YoutubeStreamInfoItemLockupExtractor`
  (`lockupViewModel` of type VIDEO). Pitfall: in a channel's tabs the lockups have no channel row, so
  NewPipe reads the views row as the uploader name: the channel name is forced on every item.
  nanojson is added as a direct dependency at the exact version NewPipeExtractor requires (it was
  only on the runtime classpath). UI: `ChannelSearch` (query / submitted) shared by `ChannelOverlay`
  (magnifier → glass field, ✕) and `ChannelContent` (results replace the channel header and tabs;
  back closes it, from `MikenekoRoot`); `POPULAR` is a pseudo tab kind after Videos. Subscriptions:
  magnifier next to the title, `folded()` (lowercase, accents removed) filters the avatars row and
  the feed. Checked on the emulator.
- 2.15.0: playlist sort and BPM / Camelot. The user chose local analysis (no outside database) and
  albums read in the background. `data/TrackTags.kt`: per track (`trackKey` = video id, or the file
  URL) bpm, camelot, album, `analyzed`, `albumChecked`, in prefs `tags_morceaux`; `TrackTags.sorted`
  (Collator, unknown values last, stable); `fillAlbums` reads `YouTubeRepository.stream` one song every
  4 s (30 s after an error, not marked) and `albumNameFrom` on its description; `PlayerHolder.play`
  also stores the album of a song it plays (the StreamInfo is already there). `Playlist.sort` (JSON
  `sort`) = `TrackTags.Sort.id`; dragging in Edit mode saves that order and switches back to My order;
  removal is by URL (the shown list can be sorted). `player/TrackAnalysis.kt` (`TrackAnalyzer`): one
  low-priority thread; a song is analysed only if it is whole on the phone (local file, or
  `OfflineCache` SavedMusic / Music entry, copied to a temp file), so a song played gets analysed
  ~40 s / 120 s after it starts (`watch`, the whole track is cached by `MusicLoadControl`).
  MediaExtractor + MediaCodec → mono, box-decimated to ~11 kHz, 90 s from the middle (intro skipped).
  `player/TrackDsp.kt` (pure Kotlin, JUnit tests in `app/src/test`, `./gradlew testDebugUnitTest`):
  tempo = log spectral flux (1024 / hop 128), local-mean removal, autocorrelation, every 0.1 BPM from
  60 to 200 scored on its 4 first multiples × a log-Gaussian prior around 120, then the double tempo
  if it explains ≥ 70 % as well (174 not 87); key = chromagram 55 Hz–1.76 kHz (8192 / hop 4096,
  triangular weight around each semitone, each frame normalised) correlated with KeyFinder's major /
  minor profiles, mapped to Camelot. Real check on the emulator: Get Lucky 116 BPM (10A: its Bm–D–F♯m–E
  is listed as F♯ minor 11A or B dorian), Uptown Funk 115 BPM 7B (D minor 7A is the usual answer:
  relative major / minor is the typical miss, same wheel number).
- 2.15.1: the sort capsule row became `SortButton` (Capsule, next to Shuffle and in Edit mode) opening
  `MusicSheet.SortPlaylist` → `SortMenu` in `MusicSheetHost` (check on the current sort, a
  description per option, the "not analysed yet / albums" note at the bottom). New icon `MkIcons.Sort`.
- 2.15.2: `LyricsSheet(anchor)`: `MusicPlayerState.lyricsAnchor` = window bounds of the player's
  button capsule (`onGloballyPositioned` in `MusicPlayerContent`). One `Animatable` (0 = capsule, 1 =
  open; spring) lerps the glass rect (left / top / size / corner radius) from the anchor to the open
  panel (bottom, 78 %, pulled down by `drag`); the sheet stays composed until it is back to 0. The
  content is laid out at the open size (no reflow), top-aligned, clipped by the glass and faded in
  over the second half. `Music.albumOf` now keeps only albums whose artist matches the song's artist
  or one credited in the description (`creditedArtistsFrom`, `folded`): a single shares the song's
  name and the album search could return another artist's album.
- 2.15.3: the lyrics drag no longer translates the panel: `drag` / `travel` (open height minus the
  capsule's) is subtracted from the morph progress (`p = progress × (1 − pulled)`), so the panel shrinks
  in place towards the capsule under the finger. On release past 120 dp (or a fling > 1500 px/s)
  `onClose` → the close effect snaps `progress` to the shrunk value, resets `drag`, and springs to 0
  with the finger's velocity; otherwise `drag` springs back to 0.
- 2.16.0: `Accent` is a data class (was an enum): `Accent.presets` (3 new: Sora, Yuzu, Hojicha) and
  `Accent.custom(color)`, which derives `light` / `dark` / `night` from the color's HSV. Prefs:
  `accent` = preset id or `perso`, `accent_perso` = ARGB; `Appearance.custom` keeps the custom color
  even when a preset is chosen (its bubble stays). `ui/components/ColorWheelDialog`: sweep gradient of
  hues (clockwise, like the finger angle with y down) + radial fade to grey at the centre; tap or drag
  sets hue (angle) and saturation (distance, at least 0.12); the value is fixed at 0.92 so the dark text
  of selected buttons stays readable; the "Use it" button previews the color.
- Emulator tip (2026-11-10): with `-gpu swiftshader_indirect` the emulator itself crashes when the
  music window opens; `-gpu host` works again.
- Emulator tip: if screenshots turn black and the app "isn't responding" while its main thread just
  waits for vsync, the emulator's GPU is stuck: restart it with `-gpu swiftshader_indirect`.

---

## 9. Compose and Android pitfalls to remember

- A **`derivedStateOf` read only in a `SideEffect`** doesn't trigger recomposition: read it during
  composition.
- For an animation following the scroll pixel by pixel, read the state in **`graphicsLayer { }`**:
  that avoids a recomposition per frame.
- `RowScope.AnimatedVisibility`: implicit receiver error in a nested `Row`. Replaced by
  `animateFloatAsState` + `graphicsLayer`.
- For a moving glass element, use `offset { }`, not `graphicsLayer`, otherwise the position seen by
  the glass doesn't follow.
- Kotlin delegation of a Java interface (`by`): `default` methods are **not** delegated; extend the
  concrete class.
- A duplicate string in `strings.xml` fails the build. Example: the old `music_library` ("Your music")
  was removed.
- `ANIMATION_SPEED = 2`: `tween(400)` really lasts 200 ms; `delay()` doesn't follow this scale.
- **Centring on the screen inside a block with `safeDrawing`** (1.17.2, full screen "Up next"): in
  landscape, the notch gives a bigger margin on one side, so the block's middle isn't the screen's.
  Shift by `(right − left) / 2` of the `safeDrawing` margins. And no `Spacer(weight)` on each side:
  that centres in the remaining space, not on the screen.
- **Centring the landscape dock rail** (2.6.2 → 2.6.5): with the status bar hidden, centre it on the
  whole screen (no top or bottom padding); with it shown, pad the top by the status bar height only,
  so it centres between the status bar and the bottom. A bottom padding for the navigation bar moved
  the centre up.
- **A node that is no longer placed (`place` skipped) no longer receives the pointer**, even
  mid-gesture, nor its release (`HitPathTracker` checks `isPlaced` on every event). 1.15.1 bug, fixed
  in 1.15.2: the music window (`MusicWindow.kt`) is only placed above 30 % open; when folding it
  through its scroll (`scrollConnection`), the finger went below 30 %, the scroll never received the
  release, `onPreFling` never came and the pane froze around 0.26–0.29. Now the window stays placed
  while `MusicPlayerState.pulling` is true (an observed state). On top of that, `endPull()` settles
  the pane when the finger lifts without a fling, because a cancelled gesture doesn't trigger
  `onPreFling` (`Draggable` → `processDragCancel`).

---

## 10. Open leads (details in FIXES.md)

- To check on a phone:
  - 1.10.3's Source error;
  - the visuals of 1.14.0, 1.14.1, 1.14.2 and 1.14.6, and dragging on the home capsules (1.15.1);
  - offline, only downloaded items play, even a removed playlist (1.15.5);
  - resuming the last 10 videos after closing (1.16.0);
  - the thumbnails' progress bar and the suggestions in the player page (1.17.0);
  - reimporting a real Takeout `abonnements.csv`.
- Suggested but not chosen yet:
  - importing the `youtube.com/feed/channels` page saved as .mhtml from Chrome (an idea taken from
    youtube-subscription-extractor).
- Go back to an official NewPipeExtractor release when it ships.
