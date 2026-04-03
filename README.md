# AA YT Playlists

AA YT Playlists is an Android Auto application that allows users to manage and play YouTube Music playlists directly from their car's headunit. It acts as a transparent **Media Session Proxy** between Android Auto and YouTube Music, providing native playback controls, real-time metadata display, and seamless track/playlist launching.

## Features

- **Native Media Session Proxy**: Mirrors YouTube Music's playback state (title, artist, album art, progress) directly onto Android Auto's media interface in real time.
- **Native Media Browser Hierarchy**: Playlists and tracks appear as native browsable/playable media items on the headunit — no templates, no legacy hacks.
- **Full-Screen Lyrics UI**: A stunning, full-screen transparent lyrics dialog with blurred album art and **automatic scrolling** for non-synced tracks.
- **Triple-Tier Lyrics Support**: Synchronized lyrics via `lrclib`, `Musixmatch`, and plain-text fallback via the **Genius API**.
- **Shuffle Play**: One-tap shuffle-play for entire playlists, directly from the car's media UI.
- **Transport Control Forwarding**: Play, Pause, Skip, Seek from the steering wheel or headunit are forwarded to YouTube Music.
- **Background Playback Launch**: Starts YouTube Music playback even when the app isn't in the foreground, using Foreground Service promotion and `SYSTEM_ALERT_WINDOW` permission.
- **Metadata Fetching with Retry Logic**: Pulls playlist details (thumbnails, track counts, duration) dynamically with exponential backoff for extreme reliability.
- **Offline Caching**: Caches images and data using Coil and Room for reliable performance even in poor network conditions.
- **ReVanced Support**: Compatible with `app.rvx.android.apps.youtube.music`, `app.revanced.android.apps.youtube.music`, and the official `com.google.android.apps.youtube.music`.

## Architecture

```
Android Auto Headunit
    │
    ├── MediaBrowser ──► YTMediaBrowserService
    │                        │  (hierarchical: root → playlists → tracks)
    │                        │
    │   ◄── MediaSession ────┘  (mirrors YT Music state via proxy sync)
    │
    └── Transport Controls ──► MediaSyncManager ──► YT Music MediaController
                                    ▲
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
