# AA-YT-Playlists Issues

---

## 🐛 Bug 1: Race Condition in AudioCacheManager.kt (Zeile 31-38)

**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/util/AudioCacheManager.kt`

**Beschreibung:** Die äußere Bedingung `cache != null && currentMaxBytes != maxBytes` prüft außerhalb des `synchronized`-Blocks. Zwei Threads können gleichzeitig die Prüfung passieren, wenn `cache != null` und `currentMaxBytes != maxBytes`, was zu Datenrennen und potenziellen Abstürzen führen kann.

**Relevanter Code:**
```kotlin
if (cache != null && currentMaxBytes != maxBytes) {
    synchronized(this) {
```

**Fix:** Die gesamte Prüfung muss innerhalb des `synchronized`-Blocks erfolgen.

---

## 🐛 Bug 2: Falsches unregisterCallback-Ziel in MediaSyncManager.kt (Zeile 110)

**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/service/MediaSyncManager.kt`

**Beschreibung:** Der Callback wird vom *alten* Controller abgemeldet (`_activeController.value`), aber sofort danach wird `_activeController.value` überschrieben. Wenn der alte Controller bereits `null` war, wird der Callback nie abgemeldet und leakt.

**Relevanter Code:**
```kotlin
controllerCallback?.let { cb -> _activeController.value?.unregisterCallback(cb) }
_activeController.value = controller
```

**Fix:** Erst den neuen Controller setzen, dann vom alten Callback abmelden, oder mit einer temporären Variable arbeiten.

---

## 🐛 Bug 3: StateFlow-Zugriff vor Collections-Start in PlaylistViewModel.kt (Zeile 59-66)

**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/ui/compose/screens/PlaylistViewModel.kt`

**Beschreibung:** `playlists.value` wird im `init`-Block synchron abgefragt, aber `stateIn` startet die Subscription asynchron. Beim ersten Start ist `playlists.value` immer `emptyList()`, daher wird die Refresh-Logik stillschweigend übersprungen.

**Relevanter Code:**
```kotlin
init {
    viewModelScope.launch {
        val playlistList = playlists.value  // <-- IMMER LEER BEI ERSTEM AUFRUF
        if (playlistList.isNotEmpty()) {
```

**Fix:** `playlist.first()` statt `.value` verwenden, um auf die erste Emission zu warten.

---

## 🐛 Bug 4: Metadata-Collector feuert nicht bei bereits vorhandenem Wert

**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/ui/compose/screens/PlaylistViewModel.kt`

**Beschreibung:** `currentMetadata.collect` feuert nur bei *neuen* Emittierungen. Bei Konfigurationsänderungen (z.B. Rotation) geht der bereits vorhandene Wert verloren, da der Collector nie für den existierenden Wert ausgelöst wird.

**Relevanter Code:**
```kotlin
viewModelScope.launch {
    currentMetadata.collect { metadata ->  // Feuert NICHT für existierenden Wert
        if (metadata != null) { ... }
    }
}
```

**Fix:** `currentMetadata.firstOrNull()` oder `currentMetadata.filterNotNull().first()` verwenden.

---

## 🐛 Bug 5: ImageLoader wird bei jeder Flow-Emission neu erstellt in YTMediaBrowserService.kt (Zeile 796)

**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/service/YTMediaBrowserService.kt`

**Beschreibung:** Bei jeder `mediaSyncManager.currentMetadata.collect`-Emission wird ein neuer `coil.ImageLoader` erstellt. Der alte wird geleakt. Sollte einmalig als Singleton erstellt werden.

**Relevanter Code:**
```kotlin
scope.launch(Dispatchers.IO) {
    try {
        val imageLoader = coil.ImageLoader.Builder(this@YTMediaBrowserService).build()
```

**Fix:** `ImageLoader` als Klassenvariable oder Singleton erstellen.

---

## 🐛 Bug 6: Uri.fromFile() ist deprecated seit API 24 (SettingsScreen.kt, Zeile 490)

**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/ui/compose/screens/SettingsScreen.kt`

**Beschreibung:** `Uri.fromFile(file)` ist seit Android 7.0 (API 24) deprecated und wirft `FileUriExposedException` auf Android 10+ (Scoped Storage). Die App crasht beim Teilen des Backup-Files.

**Relevanter Code:**
```kotlin
val shareIntent = Intent(Intent.ACTION_SEND).apply {
    putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file))  // <-- DEPRECATED
```

**Fix:** `FileProvider.getUriForFile()` verwenden mit einer entsprechenden `provider_paths.xml` Konfiguration.

---

## 🐛 Bug 7: SharedPreferences im Composable erstellt (SettingsScreen.kt, Zeile 103-106)

**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/ui/compose/screens/SettingsScreen.kt`

**Beschreibung:** `getSharedPreferences()` wird bei jeder Recomposition neu aufgerufen. Mit `remember` cached, aber bei Context-Wechsel (z.B. neue Activity) bleibt die alte Referenz erhalten. Sollte mit `rememberSaveable` oder auf ViewModel-Ebene gelesen werden.

**Relevanter Code:**
```kotlin
val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
var autoLyricsEnabled by remember {
    mutableStateOf(prefs.getBoolean("auto_lyrics", false))
```

**Fix:** SharedPreferences in einem ViewModel oder außerhalb des Composables laden.

---

## 🐛 Bug 8: Hardcoded API-Token in LyricsFetcher.kt (Zeile 41)

**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/data/repository/LyricsFetcher.kt`

**Beschreibung:** Der Genius API-Token ist als öffentliche Konstante hardcoded im Source. Security-Risiko – der Token sollte in `local.properties` oder `BuildConfig` ausgelagert und niemals in Version Control eingecheckt werden.

**Relevanter Code:**
```kotlin
const val GENIUS_ACCESS_TOKEN = "e2IjjnIVsdoFj5k3wb5A8US_r6sPdgM9QVbW9P5Rz2I85Rp7ic_FE4yCiERkcCgm"
```

**Fix:** Token aus `BuildConfig` oder `local.properties` lesen. In `.gitignore` sicherstellen, dass `local.properties` nicht eingecheckt wird.

---

## 🐛 Bug 9: Connection Leak bei Non-200 Responses in LyricsFetcher.kt (Zeile 162-171, 208-211)

**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/data/repository/LyricsFetcher.kt`

**Beschreibung:** Bei Error-Response-Codes wird `connection.disconnect()` aufgerufen, aber wenn eine Exception zwischen `connection.responseCode` und `connection.disconnect()` geworfen wird (z.B. in follow-up Operationen), leakt die Verbindung.

**Relevanter Code:**
```kotlin
val responseCode = connection.responseCode
if (responseCode == HttpURLConnection.HTTP_NOT_FOUND || responseCode != HttpURLConnection.HTTP_OK) {
    connection.disconnect()
    return null
```

**Fix:** `disconnect()` in einem `finally`-Block oder `use`-Block aufrufen, um sicherzustellen, dass die Verbindung immer geschlossen wird.

---

## 🐛 Bug 10: Nullable Lambdas werden ohne Fehlerbehandlung gesetzt (JellyfinExoPlayerManager.kt, Zeile 64-69)

**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/service/JellyfinExoPlayerManager.kt`

**Beschreibung:** Callbacks werden zugewiesen, aber wenn `exoPlayer` zwischenzeitlich auf `null` gesetzt wird (z.B. Zeile 139: `exoPlayer = null`), werden `play()`, `pause()` etc. stillschweigend zu No-Ops ohne Fehlerlogging.

**Relevanter Code:**
```kotlin
mediaSyncManager.onJellyfinPlay = { exoPlayer?.play() }
mediaSyncManager.onJellyfinPause = { exoPlayer?.pause() }
mediaSyncManager.onJellyfinSkipToNext = { exoPlayer?.seekToNextMediaItem() }
```

**Fix:** Fehlerlogging hinzufügen, wenn `exoPlayer` null ist, oder den State der Callbacks entsprechend aktualisieren.

---