# Changelog

All notable changes to this project will be documented in this file.

## [2.8.1] - 2026-06-18

### Fixed
- **Voice Search Query Cleaning**: Added `SearchQueryCleaner` to filter out assistant voice conversational prefix/suffix phrases (such as "spiele", "play", "auf playlist launcher", "on youtube music"). This ensures that clean, precise track names are queried in `onSearch` and `onPlayFromSearch`, preventing search results polluted by trigger words.

## [2.8.0] - 2026-06-18

### Added
- **Robust HTML Scraping Fallback**: Added a native `HttpURLConnection`-based HTML scraper to `MetadataFetcher.kt`. This retrieves the playlist page by setting a minimal User-Agent, completely bypassing the European GDPR consent wall (`https://consent.youtube.com/...`). Tracks are parsed from the embedded `ytInitialData` JSON, supporting both the traditional `playlistVideoRenderer` and new `lockupViewModel` desktop layouts.
- **App Renaming to "Playlist Launcher"**: Renamed the application name in `strings.xml` to "Playlist Launcher". This resolves Google Assistant and Gemini voice routing conflicts with the official YouTube Music app.

### Changed
- **Invidious Timeout Optimization**: Limited Invidious API instance attempts to `take(2)` to prevent long delays and timeouts when public instances are rate-limited or offline.

## [2.7.0] - 2026-06-17

### Added
- **Native YouTube RSS Fallback**: Added a robust native scraper fallback to YouTube's XML feed in `MetadataFetcher` to bypass Invidious API blocks and empty tracklist responses (`"videos": []`) for album playlists.
- **Universal Cached Track Lookup**: Added query support to retrieve all cached tracks across all playlists from the Room database, used as a fallback to resolve song details and video IDs when the active playlist ID is unknown.

### Changed
- **Persistent Media Context**: Persisted the active playlist ID in `SharedPreferences` inside `YTMediaBrowserService` to survive service reconstructions and background process deaths.
- **Artwork URI Propagation**: Ensured the resolved `artUri` is written back to the `MediaMetadataCompat` builder keys (`METADATA_KEY_ART_URI`, `METADATA_KEY_ALBUM_ART_URI`, and `METADATA_KEY_DISPLAY_ICON_URI`) in both initial and lazy image loaders, allowing Android Auto to reliably cache and display cover art.

### Fixed
- **Room Schema Migration Crash**: Fixed startup crash by incrementing Room DB version to `6` and enabling destructive fallback migration to correctly align schemas after making `PlaylistEntity.imageUrl` nullable.
- **Artwork Race Condition**: Fixed a race condition where lazy Coil artwork callbacks could overwrite metadata with stale/stuck values by matching URI tags (`pendingBitmapUri == artUri`) and rebuilding the builder from the original correct metadata snapshot.
- **Android Auto Binder Transaction Overload**: Implemented safe, aspect-ratio-preserving downscaling to `400px` for all covers to prevent `TransactionTooLargeException` in Android Auto IPC.
- **Missing Cover Art on Cold Starts**: Mapped cover art to all three metadata fields (`METADATA_KEY_ART`, `METADATA_KEY_ALBUM_ART`, `METADATA_KEY_DISPLAY_ICON`) and consolidated fallbacks using whichever bitmap is non-null.

## [2.6.1] - 2026-05-09

### Fixed
- **Race Condition in AudioCacheManager**: Fixed a data race where two threads could simultaneously enter the synchronized block when `cache != null` and `currentMaxBytes != maxBytes`, potentially causing crashes or resource leaks.
- **Callback Leak in MediaSyncManager**: Fixed incorrect `unregisterCallback` target. The callback was being unregistered from the old controller after it was already replaced, causing the callback to leak on the old controller.
- **StateFlow Access Timing in PlaylistViewModel**: Changed `playlists.value` to `playlists.first()` in init block to properly wait for the first emission instead of reading the empty initial value.
- **ImageLoader Memory Leak in YTMediaBrowserService**: Moved `ImageLoader` creation to a lazy singleton to prevent creating a new instance on every metadata emission.
- **Deprecated Uri.fromFile() in SettingsScreen**: Replaced `Uri.fromFile()` with `FileProvider.getUriForFile()` to fix `FileUriExposedException` on Android 10+ (scoped storage). Added `file_paths.xml` and provider configuration to AndroidManifest.
- **Hardcoded Genius API Token**: Moved `GENIUS_ACCESS_TOKEN` from source code to `local.properties` / `BuildConfig` to prevent accidental exposure in version control.
- **Connection Leak in LyricsFetcher**: Refactored `fetchFromLrclibGet` and `fetchFromLrclibSearch` to use `use {}` blocks and `finally` for proper connection cleanup, preventing leaks on non-200 responses.
- **Silent No-Ops in JellyfinExoPlayerManager**: Added error logging via `AALogger.logError` when playback callbacks are invoked with a null `exoPlayer`.

## [2.6.0] - 2026-04-15

### Added
- **Google Assistant Voice Search** (`onPlayFromSearch`): Say "Hey Google, play [Song] on YT Playlists" to start playback hands-free. The app searches Jellyfin first (if configured), then falls back to Invidious/YouTube. A "Suche: …" status is shown on the car screen while searching.
- **Offline Audio Caching for Jellyfin**: ExoPlayer now caches Jellyfin audio streams locally using `CacheDataSource` + `SimpleCache` (LRU eviction). Previously played tracks continue without interruption when driving through dead zones.
- **Configurable Cache Size**: New "Audio-Cache" section in Settings lets you choose between 100 MB, 250 MB, 500 MB (default), or 1 GB for the local audio cache.
- **Auto-Lyrics Opt-In**: The app no longer forcibly comes to the foreground when a track changes. A new "Songtexte → Automatisch anzeigen" toggle in Settings (default: off) re-enables the previous behavior for users who want it.

### Changed
- **Compact TopAppBar**: Replaced the large two-row `LargeTopAppBar` with a standard single-line `TopAppBar`. Title and action icons sit on one line; the playlist count appears as a small subtitle.
- **Instant List Rendering**: Removed the staggered entrance animation (per-item delay + `AnimatedVisibility`) from the playlist list. Items now render immediately with no "swim-in" effect. Drag & Drop visual feedback (scale / elevation) is unchanged.
- **Settings Cleanup**: Removed the raw log text viewer (200 dp scroll card) from the Settings dialog. Share and Clear buttons are still present. The dialog is noticeably less cluttered.
- **OOM-safe Cover Loading**: Replaced `URL.readBytes()` + `BitmapFactory.decodeStream()` with Coil's `ImageLoader`, downsampling covers to 400×400 px before loading into RAM. Eliminates out-of-memory crashes when rapidly skipping tracks in Android Auto.
- **Jellyfin Voice Search**: `JellyfinRepository` now exposes a `searchTrack(query)` method (Items API with `SearchTerm`) used by the voice search flow.

### Fixed
- **OOM / Service Crash**: Rapid track skipping in Android Auto no longer causes the background service to run out of memory due to uncompressed 1000×1000 album art bitmaps.

## [2.4.0] - 2026-04-03


### Added
- **Native Jellyfin Playback**: Complete rewrite of the Jellyfin integration. Replaced external app launching with a native, high-performance `ExoPlayer` (Media3) integration. Jellyfin tracks now play directly within the app, supporting background playback and system media controls.
- **Lyrics Cache (Room DB v5)**: Synchronized lyrics are now cached in a local database after the first successful retrieval. Loading lyrics for known tracks is now near-instant (< 50ms).
- **Auto-Lyrics Foregrounding**: The app now automatically brings itself to the foreground and displays the synchronized lyrics dialog whenever a track changes, ensuring a hands-free experience.
- **Screen Keep-Awake**: The lyrics dialog now prevents mobile screen timeout while open.
- **NetEase Lyrics Priority**: Re-ordered lyrics provider fallback chain to prioritize NetEase Cloud Music for higher accuracy and reliability.

### Fixed
- **Jellyfin Thumbnails**: Resolved missing cover art for Jellyfin playlists and tracks by correctly passing authentication tokens in image fetch URLs.
- **Plain-Text Auto-Scroll**: Removed unreliable auto-scrolling for lyrics without timestamps (e.g., Genius) to keep them static and readable.

## [2.3.0] - 2026-04-03

### Added
- **Jellyfin Server Integration**: Full integration with self-hosted Jellyfin music servers. Authenticate via the new Settings dialog, browse albums and playlists from your library, and import them directly into the app. Playback is launched via the Jellyfin Android app (intent-based).
- **Jellyfin Browse Dialog**: A dedicated album/playlist browser with cover art thumbnails, artist names, and type badges (Album/Playlist) for easy importing.
- **Track Caching (Room DB)**: Playlist tracks are now persistently cached in the local database. Android Auto serves cached tracks instantly on startup, while refreshing them in the background — dramatically improving load times and enabling offline access to track lists.
- **LRCLIB Search (Fuzzy Lyrics)**: Added the LRCLIB Search API (`/api/search`) as a second-tier lyrics provider. When the exact match fails, the fuzzy search often finds synced lyrics under alternate titles or transliterations.
- **Megalobiz Lyrics Scraper**: Added Megalobiz as a third-tier synced lyrics source, scraping LRC files from their web search results.
- **NetEase Cloud Music Lyrics**: Added NetEase's internal API as a fourth-tier synced lyrics provider, searching and retrieving LRC data for Chinese and international music catalogs.
- **Settings Dialog**: Replaced the standalone Diagnostics dialog with a combined Settings screen featuring Jellyfin server configuration (login/disconnect/connection test) and the existing debug log viewer.
- **Source Badges**: Playlist items now display a subtle "JF" badge for Jellyfin-sourced playlists, making it easy to distinguish them from YouTube playlists.

### Changed
- **Android Auto Root Hierarchy**: Restructured the MediaBrowser tree to always return a single virtual folder ("Playlisten") as the root item. This forces Android Auto to render playlists as a proper list with thumbnails, eliminating the unwanted tab-bar layout that appeared with ≤4 playlists.
- **Lyrics Fallback Chain**: Expanded from 3 providers to 5: `lrclib/get → lrclib/search → Megalobiz → NetEase → Genius`. Musixmatch has been completely removed due to persistent API failures.
- **Database Schema**: Upgraded from v3 to v4 with a combined migration adding the `tracks` table and `source`/`externalId` columns to `playlists`.

### Fixed
- **TopAppBar Scroll Behavior**: Fixed the annoying bounce/collapse effect on the playlist screen when fewer than ~6 playlists were present. The app bar now stays pinned and stable regardless of list length.

### Removed
- **Musixmatch Provider**: Completely removed the broken Musixmatch API integration (token acquisition, encrypted lyrics fetching) in favor of more reliable open alternatives.

## [2.2.0] - 2026-04-03

### Added
- **Full-Screen Lyrics Dialog**: Replaced the small bottom sheet with a stunning, full-screen transparent dialog featuring blurred album art backgrounds. Added **automatic scrolling** for plain-text lyrics (10-second intervals per line).
- **Genius API Fallback**: Integrated the Genius API as a third fallback for tracks where sync-lyrics aren't available, providing extensive lyrics coverage.
- **Improved Metadata Resilience**: Implemented a robust exponential backoff retry mechanism (1s, 2s, 4s...) for the Invidious API, making track fetching much more reliable during network fluctuations.

### Changed
- **Native Media Architecture**: Completely removed the redundant `androidx.car.app` (Car App Library) dependencies. The app now relies exclusively on the native `MediaBrowserService` for a leaner, more standard Android Auto integration.
- **Enhanced Logging**: Updated the global logger to enforce a **5 MB file size limit** with a stable clear-and-start-over strategy to keep the diagnostics log manageable and efficient.

### Fixed
- **Build Stability**: Resolved Persistent KSP/Room compilation errors through improved build environmental handling.

## [2.1.0] - 2026-03-31

### Added
- **Manual Playlist Refresh**: Added a dedicated refresh button to each playlist card. This forces a metadata re-fetch and clears the in-memory track cache, ensuring Android Auto immediately reflects new songs added via YouTube Music without overwriting your custom playlist titles or covers.
- **Lazy Thumbnail Fallback**: Implemented an async image loader in the proxy service that steps in when YouTube Music transmits a song's metadata without its actual cover bitmap (e.g. when launched from the background with the smartphone screen locked). This reliably prevents "black squares" and ensures the album art appears immediately on the initial track play.

## [2.0.0] - 2026-03-30

### Added
- **Native Media Session Proxy Architecture**: Complete rewrite of the Android Auto service stack. The app now acts as a transparent proxy between Android Auto and YouTube Music.
- **YTMediaProxyService (NotificationListenerService)**: Listens for active YouTube Music media sessions (including ReVanced variants), capturing real-time metadata (title, artist, album art) and playback state.
- **MediaSyncManager**: Singleton bridge that pipes YT Music session state as `StateFlow`s between the proxy service and the Auto browser service. Provides transport-control forwarding (`play()`, `pause()`, `skipToNext()`, `skipToPrevious()`, `seekTo()`).
- **YTMediaBrowserService**: Hierarchical `MediaBrowserServiceCompat` with flat root → playlist → track browsing. Includes 10-second timeout protection for track fetching.
- **"▶ Shuffle abspielen" Action**: First item in every track list allows shuffle-playing the entire playlist directly from Android Auto.
- **Foreground Service Promotion**: Service temporarily promotes to foreground before launching YouTube Music, ensuring reliable activity starts.
- **SYSTEM_ALERT_WINDOW Permission**: Added "Display over other apps" permission to guarantee background activity launches work on Android 10–16, even when the app is not in the foreground.
- **Notification Access Permission Banner**: Prominent UI banner guiding the user to grant notification listener access.
- **Overlay Permission Banner**: Second UI banner guiding the user to grant "Display over other apps" permission. Both banners auto-refresh on lifecycle resume.
- **Transport Control Forwarding**: Play, Pause, Skip, Seek commands from the car's steering wheel are forwarded directly to YouTube Music's active MediaController.

### Changed
- **Playback State**: Replaced all `STATE_ERROR` hacks with `STATE_CONNECTING` + dummy metadata ("Lade...", "YouTube Music") when launching playback.
- **Launch Suppression**: 6-second state-sync suppression window prevents stale YT Music state from overwriting `STATE_CONNECTING`. Only `STATE_PLAYING` lifts suppression early. Metadata is never suppressed.
- **Playlist Launch URL**: Auto playlist clicks now use `&shuffle=1` to start in shuffle mode.
- **Direct Service Launch**: Playback intents fired directly via `startActivity()` from the service instead of via BroadcastReceiver.
- **Browser Hierarchy**: Removed the intermediate "tab_playlists" layer. Root now directly returns all playlists as browsable items.

### Removed
- **DummyMediaBrowserService**: The old hack-based fallback service using `STATE_ERROR` has been completely replaced by `YTMediaBrowserService`.

## [1.4.1] - 2026-03-23

### Fixed
- **MediaBrowser Hierarchy**: Reworked the root tree to prevent Android Auto from converting playlist folders into unwanted top-level tabs. Playlists now properly display as an expanding list.
- **Track Playback State**: Temporarily sets `STATE_ERROR` upon track selection to force Android Auto to drop the "fetching selection" spinner, allowing YouTube Music to grab audio focus seamlessly.
- **Video ID Parsing**: Fixed truncation bug for video IDs containing underscores (e.g., Loredana tracks) which previously broke playback.
- **PlaylistReceiver Shuffle override**: Removed automatic `&shuffle=0` appending for specific `videoId` intents, fixing the bug where YouTube Music always played the first song.

## [1.4.0] - 2026-03-23
### Added
- **Fallback Media Browser Service**: Implemented a functional `MediaBrowserServiceCompat` as a fallback for headunits that ignore the CarAppService templates. It serves real playlist data from the database.
- **In-App Diagnostics & Log-Viewer**: Added a dedicated diagnostics dialog (accessible via settings gear) to monitor AA activity in real-time.
- **Global Logger (AALogger)**: A robust, file-based logging system with rotation, status toggle, and critical event tracking.

### Fixed
- **Infinite Loading on Headunit**: Added a 20-second timeout to track fetching and optimized the automotive descriptor to prevent media-browse-overrides.
- **Diagnostics Sharing**: Fixed the share intent functionality to support all Android versions and corrected FLAG_ACTIVITY_NEW_TASK crashes.
- **Logger Initialization**: Now defaults to 'Enabled' on first run and captures early boot/service events via `forceLog`.

## [1.3.1] - 2026-03-19

### Fixed
- **Restored CarAppService UI**: Reverted the rigid native media tabs in favor of the flexible, thumbnail-rich list interface for better UX and direct song control.
- **Head Unit Visibility**: Successfully registered the app as a `POI` category, ensuring it appears on physical Android Auto head units.
- **Background Activity Fix**: Moved playback intent logic into the main service to bypass Android 14 background launch restrictions.
- **Metadata Feedback**: Added dummy metadata to the media session during track launch to prevent Android Auto from hanging on "Fetching selection".
- **Loading Performance**: Implemented in-memory caching and pre-fetching of Invidious API instances to drastically reduce playlist and track load times.


## [1.3.0] - 2026-03-19

### Added
- **MediaBrowserServiceCompat Support**: Implemented a legacy Media Browser Service to ensure the app appears on physical Android Auto headunits (bypassing strict Car App Library Play Store verification).
- **Native Media UI**: Playlists and tracks are now browsable and playable directly through Android Auto's native media center interface.

### Changed
- **CarAppService Category**: Changed `CarAppService` category from `AUDIO` to `POI` to prevent conflicts with the new Media Browser Service on real hardware.
- **Automotive Descriptor**: Updated `automotive_app_desc.xml` to include the `media` usage tag.

## [1.2.0] - 2026-03-18

### Added
- **In-App Cover Search**: Search for album covers directly inside the app. Results are fetched concurrently from **iTunes/Apple Music** (albums + singles) and **Deezer** — all in 1000×1000px quality.

### Changed
- **Thumbnail Caching (Android Auto)**: Image cache is now keyed by URL instead of playlist ID, so cover changes are reflected instantly on the car screen.

### Fixed
- **Pause Button**: Pressing Pause in the playlist overview now actually pauses playback by dispatching a `KEYCODE_MEDIA_PAUSE` media key event.
- **Drag & Drop Cover Reset**: Fixed a state capture bug where reordering playlists via drag & drop would reset custom covers to their previous values.
- **Pull-to-Refresh Removed**: Removed the pull-down gesture that was silently overwriting user-set covers with YouTube's default thumbnails.

## [1.1.0] - 2026-03-18

### Added
- **Android Auto Dashboard Support**: Converted category to `AUDIO` and initialized `LauncherMediaSession` so the app appears in the split-screen/dashboard mode.
- **Inline Media Controls**: Added Play/Pause functionality efficiently bridging between Android Auto and the smartphone app.
- **Shuffle Option**: Replaced the Play button in `PlaylistDetailScreen` with a direct Shuffle button.
- **Google Image Search Link**: Quickly search for playlist covers via Google Images directly from the "Edit" dialog.

## [1.0.0] - 2024-xx-xx

### Added
- **Android Auto Playlist Detail Screen**: A new view in the car (`PlaylistDetailScreen`) displaying the tracks of the selected playlist, replacing the direct playback intent.
- **Track Fetching API**: `MetadataFetcher.kt` integrated with Invidious API to fetch and parse exact track lists from playlists.
- **Manual Thumbnail URL**: The `EditPlaylistDialog` now allows manually updating a playlist's image URL.

### Changed
- **UI/UX Redesign**: Revamped the Compose UI with a modern Material 3 design, edge-to-edge cards, improved typography, and smooth entrance/scroll animations.
- **Android Auto Thumbnails**: Switched from `GridTemplate` to `ListTemplate` and updated Coil to use `Scale.FILL`, rendering gorgeous, borderless, large thumbnails on the infotainment screen.
- **Drag & Drop Reorder**: Replaced manual `pointerInput` with an established reorderable library (`org.burnoutcrew.composereorderable`) handling the drag & drop seamlessly via the drag handle.

### Fixed
- Fixed visual glitches and the list jumping erratically when reordering playlists on the smartphone.
- Handled correct parsing for individual videos (`&v=`) versus list launches in the `PlaylistReceiver.kt` for more robust track start logic.
