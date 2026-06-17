# AA-YT-Playlists - Bug List (Prioritized)

## CRITICAL (Sofort beheben - Crash/Data Loss Risiko)

---

### Bug 1: NPE durch hard Non-Null Assertion
**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/service/MediaSyncManager.kt:146`
```kotlin
controllerCallback = object : MediaController.Callback() { ... }
controller.registerCallback(controllerCallback!!)  // NPE wenn concurrent setActiveController(null) aufruft
```
**Fix:** nullable handling mit ?. oder temp variable

---

### Bug 2: Cross-Thread Callback Invocation NPE
**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/service/YTMediaProxyService.kt:89`
```kotlin
override fun onDestroy() {
    mediaSyncManager?.setActiveController(null)  // onDestroy nicht garantiert auf main thread
}
```
**Fix:** `post()` verwenden um auf Main-Thread zu wechseln

---

### Bug 3: Memory Leak - Callback nicht abgemeldet bei Session Destroy
**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/service/MediaSyncManager.kt:138-143`
```kotlin
override fun onSessionDestroyed() {
    _activeController.value = null  // controllerCallback bleibt registriert!
}
```
**Fix:** `controllerCallback?.let { cb -> _activeController.value?.unregisterCallback(cb) }` hinzufügen

---

## HIGH (Hohe Priorität - Funktionale Bugs)

---

### Bug 4: Callback Leak bei setActiveController (Race Condition)
**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/service/MediaSyncManager.kt:113-114`
```kotlin
oldController?.let { old ->
    controllerCallback?.let { cb -> old.unregisterCallback(cb) }  // controllerCallback könnte concurrent geändert werden
}
```
**Fix:** Lokale Kopie von controllerCallback vor dem let-Block

---

### Bug 5: Artwork Caching Logic Error (&& statt ||)
**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/service/YTMediaBrowserService.kt:782-789`
```kotlin
if (albumArtBmp != null) {  // Immer null hier wegen Bug
    builder.putBitmap(...)
} else if (lastLoadedBitmapUri == artUri && lastLoadedBitmap != null) {  // sollte || sein
```
**Fix:** `||` statt `&&` verwenden für fallback logik

---

### Bug 6: Busy-Wait Loop mit potenziellem ANR
**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/service/YTMediaBrowserService.kt:619-626`
```kotlin
val success = withTimeoutOrNull(2500L) {
    while(true) {  // Busy wait ohne yield im fast path
```
**Fix:** `yield()` oder `delay(1)` am Anfang jeder Iteration

---

### Bug 7: Fire-and-Forget Coroutine Leak
**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/service/YTMediaBrowserService.kt:252`
```kotlin
scope.launch {  // Detached von parent scope
    try { ... }
}
```
**Fix:** `CoroutineScope` als Parameter übergeben oder `async` statt `launch`

---

### Bug 8: Data Race auf Controller Zugriff
**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/service/YTMediaBrowserService.kt:615`
```kotlin
val controller = mediaSyncManager.activeController.value  // Snapshot von main thread
mainHandler.post {
    controller.transportControls.playFromUri(...)  // controller könnte zwischenzeitlich null sein
}
```
**Fix:** Nullable check nach dem post oder Controller Referenz captures

---

### Bug 9: Race Condition in switchSourceMode
**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/service/MediaSyncManager.kt:48-60`
```kotlin
fun switchSourceMode(mode: SourceMode) {
    if (_currentSourceMode.value != mode) {  // Check
        _currentSourceMode.value = mode  // Act - non-atomar
```
**Fix:** `synchronized` Block oder `compareAndSet`

---

### Bug 10: Duration Metadata Corruption (TIME_UNSET)
**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/service/JellyfinExoPlayerManager.kt:131`
```kotlin
metaBuilder.putLong(MediaMetadata.METADATA_KEY_DURATION, player.duration.coerceAtLeast(0))
// player.duration = C.TIME_UNSET = -9223372036854775808 -> coerceAtLeast(0) = 0
```
**Fix:** Prüfen auf `C.TIME_UNSET` und `C.TIME_UNKNOWN` und null setzen wenn undefiniert

---

### Bug 11: Race Condition in AudioCacheManager (Check-Then-Act)
**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/util/AudioCacheManager.kt:31-34`
```kotlin
if (cache == null || currentMaxBytes != maxBytes) {  // Äußere Prüfung nicht in synchronized
    synchronized(this) {
```
**Fix:** Bereits gefixt - gesamte Prüfung in synchronized Block

---

### Bug 12: Cache Released ohne Thread-Safe Cleanup
**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/util/AudioCacheManager.kt:34`
```kotlin
cache?.release()
cache = null
currentMaxBytes = 0  // Inconsistent state zwischen diesen Zeilen
```
**Fix:** Atomic operation oder single assignment pattern

---

### Bug 13: Force Unwrap auf nullable cache
**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/util/AudioCacheManager.kt:47`
```kotlin
return cache!!  // Könnte theoretisch null sein nach komplexem race
```
**Fix:** early return mit proper error handling oder sealed class

---

### Bug 14: Unchecked IOException Propagation
**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/util/AudioCacheManager.kt:54`
```kotlin
return SimpleCache(cacheDir, evictor, databaseProvider)  // IOException nicht gefangen
```
**Fix:** try-catch um cache creation mit graceful degradation

---

### Bug 15: Room @Transaction funktioniert nicht mit suspend
**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/data/local/dao/TrackDao.kt:28`
```kotlin
@Transaction  // Room transactions funktionieren nicht mit suspend
suspend fun replaceTracksForPlaylist(playlistId: Int, tracks: List<TrackEntity>) {
    deleteTracksForPlaylist(playlistId)
    insertTracks(tracks)  // Wenn das fehlschlägt, sind alle tracks verloren
}
```
**Fix:** Non-suspend transaction wrapper oder withTransaction verwenden

---

### Bug 16: Non-Thread-Safe LruCache
**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/data/repository/MetadataFetcher.kt:42`
```kotlin
private val trackCache = android.util.LruCache<String, List<Track>>(20)
// Konkurrierender Zugriff von multiple coroutines ohne Synchronisation
```
**Fix:** `Collections.synchronizedMap` oder ConcurrentHashMap verwenden

---

### Bug 17: Race Condition in getActiveInstances()
**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/data/repository/MetadataFetcher.kt:63-104`
```kotlin
if (dynamicInstances.isNotEmpty() && ...) { return dynamicInstances }
 // Multiple coroutines können gleichzeitig hier eintreten wenn leer
```
**Fix:** mutex oder single-flight pattern

---

### Bug 18: runBlocking in createBackup() - ANR Risiko
**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/data/repository/BackupManager.kt:60-80`
```kotlin
fun createBackup(): BackupData {
    val playlists = kotlinx.coroutines.runBlocking {  // BLOCKING CALL
```
**Fix:** createBackup() zu suspend function machen

---

### Bug 19: Access Token in URL exponiert
**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/data/repository/JellyfinRepository.kt:283-285`
```kotlin
return "$serverUrl/Audio/$itemId/stream?static=true&UserId=$userId&api_key=$accessToken"
 // Token in URL - kann in logs, analytics, crash reports landen
```
**Fix:** Authorization Header statt query parameter

---

### Bug 20: Sensitive Data in Log File
**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/util/AALogger.kt:52`
```kotlin
fun forceLog(tag: String, msg: String) {
    file.writeText(...)  // Tokens, URLs, User-IDs in Klartext
}
```
**Fix:** PII scrubbing oder separate secure log

---

### Bug 21: Full Stack Trace in Log File
**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/util/AALogger.kt:64`
```kotlin
logError(tag: String, msg: String, e: Exception?) {
    file.appendText(e?.stackTraceToString())  // Implementation details geleaked
}
```
**Fix:** Nur message + basic stack ohne internals

---

### Bug 22: Empty Catch Blocks - Silent Failures
**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/ui/compose/screens/PlaylistScreen.kt:1178-1180`
```kotlin
try { allImages.addAll(itunesAlbumDef.await()) } catch (e: Exception) { }
try { allImages.addAll(deezerDef.await()) } catch (e: Exception) { }
```
**Fix:** Error state setzen oder retry logic

---

### Bug 23: State Desync in DraggableList
**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/ui/compose/screens/PlaylistScreen.kt:685-689`
```kotlin
var displayList by remember { mutableStateOf(playlists) }
LaunchedEffect(playlists) { displayList = playlists }  // Race mit onMove
```
**Fix:** `derivedStateOf` oder update in launch statt remember

---

### Bug 24: Blocking Call in Composable
**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/ui/compose/components/JellyfinBrowseDialog.kt:160`
```kotlin
val imageUrl = jellyfinRepository.getImageUrl(item.id)  // I/O im Composable
```
**Fix:** LaunchedEffect oder remember with async loading

---

### Bug 25: Missing remember key
**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/ui/compose/components/JellyfinBrowseDialog.kt:61-63`
```kotlin
var items by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
// Kein key - state wird nicht invalid wenn repository ändert
```
**Fix:** key parameter hinzufügen oder state hoisting

---

## MEDIUM (Mittlere Priorität - Verbesserungen)

---

### Bug 26: Unbounded file read - OOM Risk
**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/util/AALogger.kt:100`
```kotlin
val content = file.readText()  // Lädt gesamtes file in memory
```
**Fix:** Size-limited read oder streaming approach

---

### Bug 27: Lyrics Loading State nie beobachtbar
**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/ui/compose/screens/PlaylistViewModel.kt:76-77`
```kotlin
_lyricsState.value = LyricsState.Loading
_lyricsState.value = lyricsFetcher.fetchLyrics(...)  // Sofort überschrieben
```
**Fix:**两步 update oder sealed class mit sealed Flow

---

### Bug 28: Uncollectable Flow in init block
**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/ui/compose/screens/PlaylistViewModel.kt:59-66`
```kotlin
val playlistList = playlists.first()  // Könnte empty sein wegen timing
```
**Fix:** first() mit timeout oder collectAsState().value prüfen

---

### Bug 29: Null Pointer in Auto-Scroll Coroutine
**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/ui/compose/screens/PlaylistScreen.kt:1516-1537`
```kotlin
if (playbackState?.state == STATE_PLAYING) {
    val pos = playbackState.position  // NPE wenn state wechselt
```
**Fix:** Lokale Kopie oder separate nullable checks

---

### Bug 30: Screen Wake Lock ohne cleanup Garantie
**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/ui/compose/screens/PlaylistScreen.kt:1424-1430`
```kotlin
DisposableEffect(view) {
    view.keepScreenOn = true
    onDispose { view.keepScreenOn = false }  // View reference leak möglich
}
```
**Fix:** WeakReference oder viewmodel scope

---

### Bug 31: SharedPreferences read on every composition
**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/ui/compose/screens/SettingsScreen.kt:103-106`
```kotlin
val prefs = context.getSharedPreferences("app_prefs", ...)
var autoLyricsEnabled by remember { mutableStateOf(prefs.getBoolean(...)) }
// prefs wird bei jeder recomposition neu erstellt
```
**Fix:** remember mit key oder viewmodel level pref reading

---

### Bug 32: State update inside drag callback
**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/ui/compose/screens/PlaylistScreen.kt:692-696`
```kotlin
onMove = { from, to ->
    displayList = ...  // Triggers recomposition bei jedem drag event
}
```
**Fix:** Batch updates oder drag state in viewmodel

---

### Bug 33: SharedPreferences race condition
**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/util/AALogger.kt:31-34`
```kotlin
var isEnabled: Boolean
    get() = prefs.getBoolean("debug_enabled", false)  // Kein synchronized
    set(value) { prefs.edit().apply { ... } }  // apply() ist async
```
**Fix:** Volatile oder synchronized access

---

### Bug 34: Silent log truncation
**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/util/AALogger.kt:88`
```kotlin
if (file.size() > MAX_LOG_SIZE) {
    file.writeText("")  // Ohne warnung oder backup
}
```
**Fix:** Backup before truncate oder rotate

---

## LOW (Nice to fix)

---

### Bug 35: PlaylistEntity imageUrl not nullable aber API responses
**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/data/local/entity/PlaylistEntity.kt:12`
```kotlin
val imageUrl: String,  // NOT NULL aber API gibt null zurück
```
**Fix:** String? machen oder default value

---

### Bug 36: Wrong Track class used in Repository
**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/data/repository/PlaylistRepository.kt:57-65`
```kotlin
Track(  // Verwendet Track von MetadataFetcher statt domain model
```
**Fix:** Eigenes domain Track model erstellen

---

### Bug 37: BackupManager erstellt neue JellyfinRepository Instanz
**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/data/repository/BackupManager.kt:88-96`
```kotlin
val jfRepo = JellyfinRepository(context)  // Neue instanz statt reuse
```
**Fix:** Singleton oder DI

---

### Bug 38: NPE potential in JSON parsing
**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/data/repository/LyricsFetcher.kt:362-363`
```kotlin
val song = songs.optJSONObject(i)
val songName = song.optString("name", "")  // song könnte null sein
```
**Fix:** Null check auf song

---

### Bug 39: Missing ConnectException handling
**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/data/repository/MetadataFetcher.kt:109-116`
```kotlin
private fun isTransientError(e: Exception): Boolean {
    // ConnectException und UnknownHostException fehlen
}
```
**Fix:** ConnectException, UnknownHostException, 429 handling

---

### Bug 40: Playlist data class validation
**Datei:** `app/src/main/java/app/olus/ytmusic/autolauncher/domain/model/Playlist.kt:4-12`
```kotlin
data class Playlist(
    val id: Int = 0,  // Nicht garantiert unique
    val url: String = ""  // Keine validation
)
```
**Fix:** Validation in constructor oder factory method

---

## Zusammenfassung

| Severity | Count | Top Bugs |
|----------|-------|----------|
| Critical | 3 | NPE in MediaSyncManager, Cross-Thread NPE in YTMediaProxyService, Callback Memory Leak |
| High | 22 | Race conditions, ANR potential, Token leakage, Data corruption |
| Medium | 9 | OOM risk, Silent failures, State sync issues |
| Low | 6 | Architecture issues, Missing null checks |

**Total: 40 Bugs identifiziert**