# AA YT Playlists

AA YT Playlists is an Android Auto application that allows users to manage and play YouTube Music playlists directly from their car's headunit. By leveraging Compose and the Android Auto Car App Library, this application bypasses common restrictions and provides an intuitive, native-feeling interface to interact with YouTube Music seamlessly.

## Features

- **Direct Car App Rendering**: Built using the Car App Library, providing native layouts and smooth performance directly on the vehicle's infotainment screen.
- **YouTube App Integration**: Launches specific YouTube playlists locally via Intents or integrates with `app.rvx.android.apps.youtube.music` directly from Android Auto.
- **Background Metadata Fetching**: Pulls playlist details (thumbnails, track counts, duration) dynamically.
- **Offline Caching**: Caches images and data using Coil and Room for a reliable experience even when driving through poor network zones.

## Installation

To ensure the application shows up correctly in Android Auto headunits (which often block side-loaded apps), it must be installed via ADB using the Play Store installer package.

```bash
adb install -r -i "com.android.vending" app-debug.apk
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
