---

# ✨ [REFACTOR]: Issue 1: Settings aufräumen & Auto-Lyrics als Opt-In

## Zusammenfassung
Der Einstellungs-Dialog ist derzeit überladen, weil er ein großes Textfeld für Logs anzeigt. Dieses wird entfernt. Gleichzeitig wird das automatische Öffnen der App bei neuen Songtexten (Foregrounding) konfigurierbar gemacht (Opt-In/Opt-Out), da das erzwungene Aufpoppen störend sein kann.

## Kontext & Hintergrund
Aktuell feuert der `MediaSyncManager` bei jedem Track-Wechsel einen Intent (`ACTION_SHOW_LYRICS`), der die `MainActivity` in den Vordergrund holt. Diese Logik ist hardcodiert. Die Settings-UI in `SettingsScreen.kt` kombiniert Jellyfin-Login mit Diagnostik-Tools (inklusive eines 200dp großen Scroll-Containers für Logs).

## Ist-Zustand
- **Settings-UI:** Zeigt rohen Log-Text direkt im Dialog an.
- **Auto-Lyrics:** In `MediaSyncManager.processMetadataUpdate` wird kompromisslos `context.startActivity(intent)` aufgerufen, sobald sich der Titel ändert.
- **Log-Viewer:** Nimmt viel Platz weg und macht den Dialog unübersichtlich.

**Betroffene Dateien (aktuell):**
- `app/src/main/java/app/olus/ytmusic/autolauncher/ui/compose/screens/SettingsScreen.kt` – Enthält die UI für den Log-Viewer.
- `app/src/main/java/app/olus/ytmusic/autolauncher/service/MediaSyncManager.kt` – Startet die Activity hart aus dem Hintergrund.

**Relevante Code-Stellen:**
```kotlin
// Datei: app/src/main/java/app/olus/ytmusic/autolauncher/service/MediaSyncManager.kt, Zeile ~82
if (newTitle != null && newTitle != oldTitle) {
    // Track hat sich geändert -> App in den Vordergrund + Lyrics
    try {
        val intent = Intent(context, MainActivity::class.java).apply { ... }
        context.startActivity(intent)
    }
}
```

## Soll-Zustand
- Der Log-Viewer (das Textfeld) im Settings-Dialog ist komplett entfernt. Die Buttons zum Teilen/Löschen der Logs bleiben erhalten.
- Es gibt einen neuen Switch in den Settings: "Songtexte automatisch anzeigen" (Standardmäßig z.B. deaktiviert oder aktiviert, je nach Präferenz).
- Der `MediaSyncManager` liest diese Präferenz aus den `SharedPreferences` aus und blockiert den Intent, wenn die Option deaktiviert ist.

## Technischer Lösungsansatz

### Schritt-für-Schritt-Plan:
1. **`SettingsScreen.kt`:** - Entferne die `Card`, die das Log-Textfeld (`logText`) anzeigt.
   - Füge einen neuen `Switch` für "Songtexte automatisch anzeigen" hinzu, analog zum Debug-Switch.
   - Speichere den Wert des neuen Switches in den SharedPreferences (z.B. in der Datei `"aa_debug_prefs"` oder den Default-Prefs).
2. **`MediaSyncManager.kt`:** - Lese vor dem Ausführen des Intents die Präferenz für Auto-Lyrics aus den SharedPreferences des übergebenen `context`.
   - Führe `context.startActivity(intent)` nur aus, wenn die Einstellung auf `true` steht.

### Bestehende Dateien ändern:
| Datei | Art der Änderung | Details |
|-------|-----------------|---------|
| `SettingsScreen.kt` | UI anpassen | Log-`Card` (Zeile ~282) komplett löschen. Neuen Switch für "Auto-Lyrics" hinzufügen. State via `SharedPreferences` verwalten. |
| `MediaSyncManager.kt` | Logik anpassen | In `processMetadataUpdate` prüfen: `val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)`. Activity nur starten wenn `prefs.getBoolean("auto_lyrics", false)` true ist. |

### Wichtige Implementierungshinweise:
- Die Variable `logText` und die Logik zum regelmäßigen Aktualisieren im UI-State des Settings-Dialogs können komplett gelöscht werden.
- Nutze für den neuen Switch dieselben Design-Farben (`YTRed`) wie für den Debug-Switch.

## Akzeptanzkriterien
- [ ] Der Settings-Dialog zeigt keinen Log-Text mehr an.
- [ ] Ein neuer Schalter für "Songtexte automatisch anzeigen" ist vorhanden.
- [ ] Die App öffnet sich bei Track-Wechseln nicht mehr automatisch aus dem Hintergrund, wenn der Schalter deaktiviert ist.
- [ ] Keine bestehenden Tests brechen.

---

# 🔧 [REFACTOR]: Issue 2: Main-Screen UI simplifizieren & Animationen entfernen

## Zusammenfassung
Die Hauptansicht (Playlisten-Liste) ist visuell überladen. Die übergroße Top-Bar, verzögerte Einblend-Animationen (Staggered Entrance) beim Scrollen und unnötige UI-Spielereien werden entfernt, um eine cleane, performante und native Android-Experience zu schaffen.

## Kontext & Hintergrund
In `PlaylistScreen.kt` wird eine `LargeTopAppBar` genutzt, die den Titel und die Buttons auf zwei Zeilen aufteilt und beim Scrollen kollabiert. In der `DraggablePlaylistList` wird jedes Element mit einem `LaunchedEffect` künstlich verzögert eingeblendet (`delay(index * 50L)` + `AnimatedVisibility`), was dazu führt, dass die Listen-Items beim schnellen Scrollen "einschwimmen" oder fehlen.

## Ist-Zustand
- **TopAppBar:** Ist zweizeilig (Titel/Zähler links unten, Icons oben rechts).
- **Listen-Animation:** Die Items haben einen `delay` basierend auf ihrem Index. Wenn man in einer langen Liste nach unten scrollt, poppen die Items erst verzögert auf.

**Betroffene Dateien (aktuell):**
- `app/src/main/java/app/olus/ytmusic/autolauncher/ui/compose/screens/PlaylistScreen.kt`

**Relevante Code-Stellen:**
```kotlin
// Datei: PlaylistScreen.kt, Zeile ~564
var visible by remember { mutableStateOf(false) }
LaunchedEffect(Unit) {
    kotlinx.coroutines.delay(index * 50L)
    visible = true
}
AnimatedVisibility(visible = visible, enter = fadeIn() + slideInVertically { it / 3 }) { ... }
```

## Soll-Zustand
- Die `LargeTopAppBar` wird durch eine kompakte `TopAppBar` ersetzt. Titel ("YT Playlists") und Icons (Songtexte, Einstellungen) sind auf einer horizontalen Linie. Der Playlisten-Zähler kann dezent als Subtitle oder direkt neben dem Titel stehen.
- Die verzögerte Einblend-Animation (`AnimatedVisibility`, `fadeIn`, `delay`) in der Liste wird komplett entfernt. Playlisten werden sofort und statisch gerendert. Nur die Drag & Drop-Animation (`scale` / `elevation`) bleibt erhalten.

## Technischer Lösungsansatz

### Schritt-für-Schritt-Plan:
1. **`PlaylistScreen.kt` (Top Bar):**
   - Ändere `LargeTopAppBar` zu `TopAppBar` (oder `CenterAlignedTopAppBar`).
   - Lege Titel und Icons auf eine Zeile. Den Subtitle ("x Playlisten") kannst du in einem kleinen Font direkt unter den Haupttitel in die `title`-Slot-Column packen.
2. **`PlaylistScreen.kt` (Liste):**
   - Gehe zu `@Composable fun DraggablePlaylistList`.
   - Entferne `var visible by remember...`, den `LaunchedEffect(Unit)` mit dem `delay`, und den `AnimatedVisibility`-Wrapper.
   - Render das `PlaylistItem` direkt im `ReorderableItem`-Block.
   - Behalte `elevation` und `scale` bei, da diese für das visuelle Feedback während des Drag & Drops wichtig sind.

### Bestehende Dateien ändern:
| Datei | Art der Änderung | Details |
|-------|-----------------|---------|
| `PlaylistScreen.kt` | TopAppBar anpassen | Ersetze `LargeTopAppBar` durch `TopAppBar`. |
| `PlaylistScreen.kt` | `DraggablePlaylistList` anpassen | Entferne das künstliche Staggered-Entrance-Muster. Code deutlich vereinfachen. |

### Wichtige Implementierungshinweise:
- Da die `LargeTopAppBar` entfernt wird, wird möglicherweise auch das Scroll-Verhalten (`TopAppBarDefaults.pinnedScrollBehavior()`) flüssiger und besser berechenbar.
- Achte darauf, dass beim Entfernen der `AnimatedVisibility` keine Modifier (wie das Skalieren und der Shadow für Drag & Drop) verloren gehen. Diese müssen direkt auf das `PlaylistItem` angewendet werden.

## Akzeptanzkriterien
- [ ] Top-App-Bar ist kompakt und einzeilig (Titel und Action-Icons auf gleicher Höhe).
- [ ] Playlisten werden sofort beim Öffnen der App / beim Scrollen angezeigt (kein "Einschwimmen").
- [ ] Drag & Drop Funktionalität (und dessen visuelles Feedback) funktioniert weiterhin einwandfrei.
- [ ] Code folgt den Konventionen des Projekts.

---

# 🐛 [BUG]: Issue 3: OutOfMemoryError (OOM) Gefahr beim Laden von hochauflösenden Covern im Proxy-Service

## Zusammenfassung
Der `YTMediaBrowserService` lädt Album-Cover für Android Auto synchron als komplettes Byte-Array in den Arbeitsspeicher (`readBytes()`), bevor er sie in eine Bitmap umwandelt. Da die App in den Settings explizit nach hochauflösenden 1000x1000px Covern sucht, führt schnelles Skippen von Songs unweigerlich zu Memory Leaks und einem Absturz des Hintergrund-Services (OOM).

## Kontext & Hintergrund
Android Auto benötigt für das Dashboard und den Hintergrund Album-Cover. Wenn YT Music ein Cover nicht rechtzeitig liefert, springt unser "Lazy Thumbnail Fallback" in `YTMediaBrowserService` ein und lädt das Bild über die URL nach. Der Service hat ein restriktiveres RAM-Limit als eine Foreground-Activity. Ein 1000x1000px Bild verbraucht unkomprimiert ca. 4MB RAM.

## Ist-Zustand
Das Bild wird als vollständiges Array in den RAM geladen und ohne Downsampling decodiert.

**Betroffene Dateien (aktuell):**
- `app/src/main/java/app/olus/ytmusic/autolauncher/service/YTMediaBrowserService.kt` – Enthält den blockierenden und speicherintensiven Bild-Download.

**Relevante Code-Stellen:**
```kotlin
// Datei: app/src/main/java/app/olus/ytmusic/autolauncher/service/YTMediaBrowserService.kt, Zeile ~465
val inputStream = if (artUri.startsWith("http")) {
    java.net.URL(artUri).readBytes().inputStream() // ⚠️ KRITISCH: Lädt gesamtes File in den RAM
} else {
    contentResolver.openInputStream(uri)
}
val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream) // ⚠️ Kein inSampleSize Downsampling
```

## Soll-Zustand
Das Herunterladen und Decodieren der Bitmap wird an Coil (den bestehenden Image-Loader der App) oder an einen sauberen Stream-Reader mit `BitmapFactory.Options` delegiert. Die Bilder müssen auf eine Android Auto taugliche Größe (z.B. max. 400x400px) herunterskaliert werden, *bevor* sie als unkomprimierte Bitmap im RAM landen.

## Technischer Lösungsansatz

### Schritt-für-Schritt-Plan:
1. **`YTMediaBrowserService.kt`:**
   - Entferne den gefährlichen `URL(artUri).readBytes()` Aufruf.
   - Nutze `Coil` (ist in `build.gradle.kts` als `libs.coil.compose` vorhanden, bring also die Core-Library mit), um das Bild asynchron und speicherschonend zu laden. Coil kümmert sich automatisch um Caching und Downsampling.
   - Falls Coil im Service Kontext nicht nutzbar ist, implementiere einen sauberen `HttpURLConnection` Stream mit `BitmapFactory.Options.inJustDecodeBounds`, berechne die `inSampleSize` für 400x400, und lade erst dann die tatsächliche Bitmap herunter.

### Bestehende Dateien ändern:
| Datei | Art der Änderung | Details |
|-------|-----------------|---------|
| `YTMediaBrowserService.kt` | Coroutine Logik ersetzen | Nutze `coil.ImageLoader(this).execute(ImageRequest.Builder(this).data(artUri).size(400).build())` um die `bitmap` sicher als `Drawable` abzurufen und umzuwandeln. |

### Wichtige Implementierungshinweise:
- Achte darauf, dass Coil im Hintergrund-Thread (`Dispatchers.IO`) ausgeführt wird.
- Behandle Timeout-Exceptions (Netzwerkabbruch beim Fahren).

---

# ✨ [FEATURE]: Issue 4: Google Assistant Voice Search Integration (onPlayFromSearch)

## Zusammenfassung
Die App soll Sprachbefehle über den Google Assistant im Auto ("Hey Google, spiele [Artist] auf YT Playlists") unterstützen. Dies ermöglicht eine komplett haptikfreie Bedienung während der Fahrt.

## Kontext & Hintergrund
Aktuell können Nutzer nur vordefinierte Playlisten antippen. Android Auto (und Google Assistant) sendet beim Kommando "Spiele X auf [App-Name]" einen `onPlayFromSearch` Callback an die `MediaSession`. Wird dieser implementiert, kann die App den Suchbegriff nutzen, um on-the-fly ein Lied zu suchen und abzuspielen.

## Ist-Zustand
Der `MediaSessionCompat.Callback` in unserem Proxy-Service fängt Such-Intents nicht ab.

**Betroffene Dateien (aktuell):**
- `app/src/main/java/app/olus/ytmusic/autolauncher/service/YTMediaBrowserService.kt` – Hier fehlt der Callback.
- `app/src/main/java/app/olus/ytmusic/autolauncher/data/repository/MetadataFetcher.kt` – Kann aktuell nur Playlisten-Metadaten holen, keine allgemeine Suche.

**Relevante Code-Stellen:**
```kotlin
// Datei: app/src/main/java/app/olus/ytmusic/autolauncher/service/YTMediaBrowserService.kt, Zeile ~237
private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
    override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) { ... }
    override fun onPlay() { ... }
    // ❌ onPlayFromSearch fehlt komplett
}
```

## Soll-Zustand
Wenn ein Sprachbefehl eingeht, fragt die App asynchron die Invidious-API oder Jellyfin nach dem ersten Treffer für den Suchbegriff ab und startet sofort das Playback. 

## Technischer Lösungsansatz

### Schritt-für-Schritt-Plan:
1. **`YTMediaBrowserService.kt`**:
   - Ergänze den `PlaybackStateCompat.Builder` um die Action `PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH`.
   - Überschreibe `onPlayFromSearch(query: String?, extras: Bundle?)` im `mediaSessionCallback`.
2. **`MetadataFetcher.kt`**:
   - Füge eine Methode `searchTrack(query: String): Track?` hinzu.
   - Nutze einen Invidious-Endpunkt (z.B. `/api/v1/search?q={query}&type=video`) um die `videoId` des ersten passenden Ergebnisses zu extrahieren.
3. **Wiedergabe**:
   - Rufe `launchYouTubeMusic("https://music.youtube.com/watch?v=$videoId")` mit dem gefundenen Track auf.

### Bestehende Dateien ändern:
| Datei | Art der Änderung | Details |
|-------|-----------------|---------|
| `YTMediaBrowserService.kt` | Callback hinzufügen | `onPlayFromSearch` überschreiben, Such-State setzen, `MetadataFetcher.searchTrack` aufrufen, dann `launchYouTubeMusic`. |
| `MetadataFetcher.kt` | Methode hinzufügen | `suspend fun searchTrack(query: String): Track?` – fragt API ab und gibt erstes Video zurück. |

### Wichtige Implementierungshinweise:
- Während der Suche (die 1-3 Sekunden dauern kann), muss der Service auf `STATE_CONNECTING` mit dummy-Metadaten ("Suche nach $query...") geschaltet werden, damit Android Auto keinen Fehler ("App reagiert nicht") wirft.

---

# ✨ [FEATURE]: Issue 5: Offline Audio Caching für Jellyfin (Seamless Playback)

## Zusammenfassung
Die native Jellyfin-Wiedergabe soll gecached werden. Wenn man mit dem Auto in ein Funkloch fährt, bricht der Musik-Stream ab. Durch die Implementierung eines lokalen Audio-Caches in Media3 (ExoPlayer) können bereits gehörte oder aktuell laufende Lieder nahtlos weitergespielt werden.

## Kontext & Hintergrund
Die App nutzt den modernen `ExoPlayer` für Jellyfin. Aktuell greift dieser aber direkt auf den Netzwerk-Stream zu (`DefaultDataSource`). ExoPlayer bringt von Haus aus die Funktionalität mit, Streams in einen lokalen Ordner (z.B. 500 MB Limit) herunterzuladen und von dort zu lesen/abzuspielen.

## Ist-Zustand
Audio wird live gestreamt. Wenn die Netzwerkverbindung weg ist, puffert der Player, bis er abstürzt.

**Betroffene Dateien (aktuell):**
- `app/src/main/java/app/olus/ytmusic/autolauncher/service/JellyfinExoPlayerManager.kt`

**Relevante Code-Stellen:**
```kotlin
// Datei: app/src/main/java/app/olus/ytmusic/autolauncher/service/JellyfinExoPlayerManager.kt, Zeile ~23
exoPlayer = ExoPlayer.Builder(context).build().apply {
    val audioAttributes = AudioAttributes.Builder()
// Keine MediaSourceFactory mit Cache konfiguriert.
```

## Soll-Zustand
Der `ExoPlayer` erhält eine `CacheDataSource.Factory`. Wenn ein Jellyfin-Lied abgespielt wird, speichert ExoPlayer die Chunks auf dem Dateisystem. Springt der Nutzer zu einem bereits gehörten Lied zurück (oder spult in der aktuellen Playlist), wird keine Internetverbindung mehr benötigt.

## Technischer Lösungsansatz

### Schritt-für-Schritt-Plan:
1. **Cache Singleton erstellen**:
   - Da mehrere ExoPlayer-Instanzen (nach Re-Starts) nicht denselben Cache-Ordner sperren dürfen, richte einen Singleton-Cache in der `YTMusicAutoLauncherApp` oder einer neuen Object-Klasse ein. Verwende den `SimpleCache` von Media3.
2. **`JellyfinExoPlayerManager.kt`**:
   - Erstelle eine `DefaultHttpDataSource.Factory`.
   - Wickle diese in eine `CacheDataSource.Factory` ein, die auf den `SimpleCache` zeigt.
   - Übergib diese Factory beim Bauen des ExoPlayers an einen `DefaultMediaSourceFactory`, welcher dann im `ExoPlayer.Builder(context).setMediaSourceFactory(...)` gesetzt wird.

### Neue Dateien erstellen:
| Datei | Zweck | Basis-Struktur |
|-------|-------|----------------|
| `app/src/main/java/app/olus/ytmusic/autolauncher/util/AudioCacheManager.kt` | Singleton für ExoPlayer Cache | `object AudioCacheManager { val cache: SimpleCache ... }` begrenzt auf z.B. 500 MB (LeastRecentlyUsedCacheEvictor). |

### Bestehende Dateien ändern:
| Datei | Art der Änderung | Details |
|-------|-----------------|---------|
| `JellyfinExoPlayerManager.kt` | ExoPlayer Setup anpassen | `setMediaSourceFactory(DefaultMediaSourceFactory(CacheDataSource.Factory().setCache(AudioCacheManager.cache)...))` hinzufügen. |
| `build.gradle.kts` | Dependencies prüfen | Stelle sicher, dass `androidx.media3:media3-datasource` für den Cache eingebunden ist (bzw. ist oft in exoplayer-core enthalten). |

### Wichtige Implementierungshinweise:
- Ein `LeastRecentlyUsedCacheEvictor` (z.B. `1024 * 1024 * 500` für 500 MB) stellt sicher, dass der Handy-Speicher des Nutzers nicht vollläuft.
- Der Cache-Ordner sollte im `context.cacheDir` liegen, damit das OS ihn bei akutem Speichermangel selbst wegräumen kann.

---
