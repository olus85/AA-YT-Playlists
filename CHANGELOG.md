# Changelog

All notable changes to this project will be documented in this file.

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
