# AA YT Playlists

AA YT Playlists is an Android Auto application that allows users to manage and play YouTube Music playlists — and **Jellyfin music libraries** — directly from their car's headunit. It acts as a transparent **Media Session Proxy** between Android Auto and YouTube Music, providing native playback controls, real-time metadata display, and seamless track/playlist launching.

## Features

- **Native Media Session Proxy**: Mirrors YouTube Music's playback state (title, artist, album art, progress) directly onto Android Auto's media interface in real time.
- **Native Media Browser Hierarchy**: Playlists and tracks appear as native browsable/playable media items on the headunit — no templates, no legacy hacks.
- **Native Jellyfin Playback**: Direct, high-performance integration with self-hosted Jellyfin music servers using `ExoPlayer` (Media3). Stream your private library directly within the app with full background support.
- **Lyrics Cache (Room DB v5)**: Synchronized lyrics are persistently cached after the first retrieval. Subsequent loads are near-instant (< 50ms) and work entirely offline.
- **Auto-Lyrics Foregrounding**: Automatically brings the app to the foreground and displays the synchronized lyrics dialog on every track change for a hands-free experience.
- **Screen Keep-Awake**: Prevents the mobile display from timing out while the lyrics dialog is active.
- **5-Tier Lyrics Support**: Synchronized lyrics via `NetEase Cloud Music` (Primary), `lrclib` (exact + fuzzy), `Megalobiz` scraping, and `Genius API` plain-text fallback.
- **Shuffle Play**: One-tap shuffle-play for entire playlists (YouTube & Jellyfin), directly from the car's media UI.
- **Background Playback Launch**: Starts playback even when the app isn't in the foreground, using Foreground Service promotion and `SYSTEM_ALERT_WINDOW` exemptions.
- **Offline Track Caching**: Track lists (YouTube & Jellyfin) are cached in the Room database and served instantly on Android Auto.
- **ReVanced Support**: Compatible with `app.rvx.android.apps.youtube.music`, `app.revanced.android.apps.youtube.music`, and the official `com.google.android.apps.youtube.music`.

## Architecture

```
Android Auto Headunit
    │
    ├── MediaBrowser ──► YTMediaBrowserService
    │                        │  (root → "Playlisten" folder → playlists → tracks)
    │                        │
    │                        ├── YouTube playlists  → MetadataFetcher (Invidious)
    │                        │                        + Room DB cache (offline-first)
    │                        │
    │                        └── Jellyfin playlists → JellyfinRepository
    │                                                  (REST API → Native ExoPlayer)
    │
    │   ◄── MediaSession ────┘  (mirrors YT Music OR Native Jellyfin state)
    │
    └── Transport Controls ──► MediaSyncManager ──► YT Music MediaController
                                    ▲               OR Native Player Controls
                                    │
                               YTMediaProxyService
                           (NotificationListenerService)
                        discovers YT Music sessions via
                           MediaSessionManager
```

## Required Permissions

After installing, two permissions must be granted manually:

1. **Notification Access** — Required for the proxy to discover YouTube Music's media session.
2. **Display Over Other Apps** — Required to launch YouTube Music from the background when the app isn't in the foreground.

Both permissions are requested via in-app banners that link directly to the relevant system settings.

## Installation

To ensure the application shows up correctly on Android Auto headunits (which often block side-loaded apps), install via ADB using the Play Store installer package:

```bash
adb install -r -i "com.android.vending" app-release.apk
```

## Building from source

1. Clone the repository.
2. Open the project in Android Studio.
3. Build the project:
   ```bash
   ./gradlew assembleDebug
   ```

## License

MIT
