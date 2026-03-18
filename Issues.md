*"Bitte arbeite die folgenden Issues der Reihe nach ab. Jedes Issue enthält den Kontext, die betroffenen Dateien und die Akzeptanzkriterien (Definition of Done). Führe für jedes Issue die Code-Änderungen durch und prüfe, ob die App kompiliert, bevor du zum nächsten übergehst."*

---

## 🔴 KATEGORIE A: Kritische Blocker & Stabilität

### Issue A1: Package Visibility (Android 11+) für Intents fehlt
**Kontext:** Die App hat `targetSdk = 35`. Der `PlaylistReceiver` versucht per explizitem Intent andere Apps (`app.rvx.android.apps.youtube.music`, etc.) zu starten. Ab Android 11 schlagen diese Intents fehl (Package visibility filtering), da das System der App verbietet zu sehen, ob diese Packages installiert sind.
**Betroffene Dateien:**
- `app/src/main/AndroidManifest.xml`
**Akzeptanzkriterien (DoD):**
- [ ] Ein `<queries>` Block ist im Manifest (außerhalb des `<application>` Tags) hinzugefügt.
- [ ] Alle im `PlaylistReceiver` aufgerufenen YouTube Music Package-Namen sind im `<queries>` Block als `<package android:name="..." />` deklariert.

### Issue A2: Schweres Memory Leak im Car App Service
**Kontext:** In `PlaylistGridScreen` wird ein eigener CoroutineScope (`CoroutineScope(SupervisorJob() + Dispatchers.Main)`) erstellt. Dieser Scope wird nirgendwo gecancelt. Da Screens in Android Auto neu gerendert werden können, entstehen hier Memory Leaks.
**Betroffene Dateien:**
- `app/src/main/java/app/olus/ytmusic/autolauncher/ui/auto/YTMusicCarAppService.kt`
**Akzeptanzkriterien (DoD):**
- [ ] Der manuelle `CoroutineScope` wurde entfernt.
- [ ] Stattdessen wird die Coroutine über einen passenden, an den Lifecycle gebundenen Scope gestartet (z.B. indem `Screen` das `lifecycleScope` nutzt oder die Subscription sauber in `onStart`/`onStop` verwaltet wird).

### Issue A3: Out of Memory (OOM) Gefahr durch unbereinigten Bitmap-Cache
**Kontext:** In `PlaylistGridScreen` werden Bitmaps via Coil geladen und in einer unbegrenzten `MutableMap<Int, CarIcon>` (`playlistIcons`) zwischengespeichert. Dies hält Bitmaps dauerhaft im RAM und crasht die App bei vielen Playlisten.
**Betroffene Dateien:**
- `app/src/main/java/app/olus/ytmusic/autolauncher/ui/auto/YTMusicCarAppService.kt`
**Akzeptanzkriterien (DoD):**
- [ ] Die `MutableMap` für `CarIcon`s wird auf eine feste Größe limitiert (z.B. LruCache) ODER Coil's natives Caching wird genutzt, ohne die Bitmaps manuell hart im Screen zu referenzieren.

### Issue A4: Aggressiver Datenverlust bei Service-Start
**Kontext:** In `YTMusicCarAppService.onCreate()` wird bei jeglicher Exception beim Initialisieren der Datenbank aggressiv `applicationContext.deleteDatabase(...)` aufgerufen. Das löscht alle User-Daten bei temporären Fehlern.
**Betroffene Dateien:**
- `app/src/main/java/app/olus/ytmusic/autolauncher/ui/auto/YTMusicCarAppService.kt`
- `app/src/main/java/app/olus/ytmusic/autolauncher/data/local/PlaylistDatabase.kt`
**Akzeptanzkriterien (DoD):**
- [ ] Das harte Löschen der Datenbank im `try-catch`-Block in `YTMusicCarAppService` ist entfernt.
- [ ] Wenn die DB nicht geladen werden kann, wird ein Graceful-Degradation-Status (Fehlermeldung) gesetzt.

### Issue A5: Sicherheitslücke im PlaylistReceiver
**Kontext:** Der `PlaylistReceiver` ist mit `android:exported="true"` deklariert. Da er Intents mit einer bestimmten Action verarbeitet und sofort URLs öffnet, kann jede andere (bösartige) App auf dem Gerät diesen Receiver triggern.
**Betroffene Dateien:**
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/app/olus/ytmusic/autolauncher/ui/auto/YTMusicCarAppService.kt`
**Akzeptanzkriterien (DoD):**
- [ ] Im Manifest wird der `PlaylistReceiver` auf `android:exported="false"` gesetzt (da der Intent nur von der eigenen Car App Service / App kommt).
- [ ] Im `CarAppService` wird beim Senden des Broadcasts explizit das eigene Package per `setPackage()` im Intent spezifiziert.

---

## 🟡 KATEGORIE B: UI/UX & Design

### Issue B1: UI-Blockierung bei Metadaten-Download beheben
**Kontext:** Im `AddPlaylistDialog` blockiert der Scraping-Vorgang die UI. Wenn es lange dauert, muss der User auf den Spinner warten.
**Betroffene Dateien:**
- `app/src/main/java/app/olus/ytmusic/autolauncher/ui/compose/screens/PlaylistScreen.kt`
- `app/src/main/java/app/olus/ytmusic/autolauncher/ui/compose/screens/PlaylistViewModel.kt`
**Akzeptanzkriterien (DoD):**
- [ ] Die URL wird beim Hinzufügen sofort als Playlist (mit Platzhalter/Skeleton-UI) in die Datenbank/Liste aufgenommen.
- [ ] Das Fetching der Metadaten läuft asynchron als Hintergrund-Task.
- [ ] Sobald die Metadaten geladen sind, aktualisiert sich das Listen-Item (Recomposition).

### Issue B2: Manuelles Neuladen via Pull-to-Refresh hinzufügen
**Kontext:** Es gibt keine Möglichkeit für den User, die Dauer oder Songanzahl einer existierenden Playlist manuell zu aktualisieren.
**Betroffene Dateien:**
- `app/src/main/java/app/olus/ytmusic/autolauncher/ui/compose/screens/PlaylistScreen.kt`
- `app/src/main/java/app/olus/ytmusic/autolauncher/ui/compose/screens/PlaylistViewModel.kt`
**Akzeptanzkriterien (DoD):**
- [ ] Ein `pullRefresh` Modifier (Material 3) ist der `LazyColumn` im `PlaylistScreen` hinzugefügt.
- [ ] Eine `refreshAll()` Funktion im ViewModel triggert den Metadaten-Download für alle Playlisten und zeigt den Ladeindikator.

### Issue B3: Native Drag & Drop Animation implementieren
**Kontext:** Das Sortieren der Playlisten nutzt manuelles `pointerInput` und statisches `offset`. Das führt zu Rucklern und ist nicht state-of-the-art in Compose.
**Betroffene Dateien:**
- `app/src/main/java/app/olus/ytmusic/autolauncher/ui/compose/screens/PlaylistScreen.kt`
**Akzeptanzkriterien (DoD):**
- [ ] Im `itemsIndexed` Block der `LazyColumn` wird der `Modifier.animateItem()` genutzt.
- [ ] Drag-Gesten verwenden nach Möglichkeit eine etablierte Library (z.B. `shreyaspatil/compose-reorderable`) oder einen sauberen State-Driven Ansatz ohne manuelles Offset-Hacking.

### Issue B4: Hardcodierte UI-Strings auslagern
**Kontext:** In der Compose-UI sind Strings wie "YT Playlists", "Lade Metadaten...", "Speichern" hardcodiert, obwohl eine `strings.xml` existiert.
**Betroffene Dateien:**
- `app/src/main/java/app/olus/ytmusic/autolauncher/ui/compose/screens/PlaylistScreen.kt`
- `app/src/main/res/values/strings.xml`
**Akzeptanzkriterien (DoD):**
- [ ] Alle sichtbaren UI-Texte in `PlaylistScreen.kt` sind durch `stringResource(id = R.string.xxx)` ersetzt.
- [ ] Alle fehlenden Strings sind in der `strings.xml` deklariert.

### Issue B5: System-Theme (Dark/Light Mode) respektieren
**Kontext:** Die Funktion `YTMusicAutoLauncherTheme` forciert `darkTheme = true` als Default-Parameter. Das führt zu Kontrastproblemen in der Statusleiste, wenn das Android-System auf Light-Mode steht.
**Betroffene Dateien:**
- `app/src/main/java/app/olus/ytmusic/autolauncher/ui/compose/theme/Theme.kt`
- `app/src/main/java/app/olus/ytmusic/autolauncher/ui/MainActivity.kt`
**Akzeptanzkriterien (DoD):**
- [ ] `YTMusicAutoLauncherTheme` evaluiert `isSystemInDarkTheme()` statt pauschal `true` als Default zu nehmen.
- [ ] Edge-to-Edge System-Bars passen ihre Icons (hell/dunkel) dynamisch an den System-State an.

---

## 🟢 KATEGORIE C: Architektur & Allgemeine Verbesserungen

### Issue C1: Hilt für Dependency Injection einführen
**Kontext:** Abhängigkeiten wie Database, Repository und Fetcher werden im `YTAutoLauncherApp.kt` manuell initialisiert (`lazy`) und ViewModel Factories von Hand gebaut.
**Betroffene Dateien:**
- `app/build.gradle.kts`
- `app/src/main/java/app/olus/ytmusic/autolauncher/YTAutoLauncherApp.kt`
- Alle ViewModels und Activities
**Akzeptanzkriterien (DoD):**
- [ ] Dagger Hilt Gradle Plugins & Dependencies (aktuellste verifizierte Version) sind konfiguriert.
- [ ] `YTAutoLauncherApp` nutzt `@HiltAndroidApp`.
- [ ] `PlaylistViewModel` wird via `@HiltViewModel` und `@Inject` bereitgestellt.

### Issue C2: Room FallbackToDestructiveMigration entfernen
**Kontext:** Die App nutzt `.fallbackToDestructiveMigration()` in Room. Bei einer Änderung des Datenbankschemas durch ein App-Update verlieren die Nutzer alle Playlisten.
**Betroffene Dateien:**
- `app/src/main/java/app/olus/ytmusic/autolauncher/data/local/PlaylistDatabase.kt`
**Akzeptanzkriterien (DoD):**
- [ ] `.fallbackToDestructiveMigration()` wird entfernt.
- [ ] (Optional/Vorbereitend) Eine leere Migration (`MIGRATION_3_4`) wird angelegt, um das korrekte Pattern für die Zukunft zu definieren.

### Issue C3: Dynamische Remote Config für Scraping-URLs
**Kontext:** Im `MetadataFetcher` sind Invidious-URLs hardcodiert. Wenn diese Server offline gehen, geht das Scraping kaputt.
**Betroffene Dateien:**
- `app/src/main/java/app/olus/ytmusic/autolauncher/data/repository/MetadataFetcher.kt`
**Akzeptanzkriterien (DoD):**
- [ ] Die Liste `invidiousInstances` ist nicht mehr hartkodiert.
- [ ] Die App fetcht beim Start (oder per Background Worker) eine JSON-Liste mit aktuellen Invidious-URLs (z.B. von einem GitHub Gist oder einer Invidious API) und speichert diese als Fallback.

### Issue C4: Robustes URL-Parsing
**Kontext:** Die Methode `extractUrl` in der MainActivity nutzt einen simplen Regex. Die URL `https://youtu.be/...` oder Share-Texte aus der App werden teilweise nicht korrekt zu Playlisten aufgelöst.
**Betroffene Dateien:**
- `app/src/main/java/app/olus/ytmusic/autolauncher/ui/MainActivity.kt`
- `app/src/main/java/app/olus/ytmusic/autolauncher/data/repository/MetadataFetcher.kt`
**Akzeptanzkriterien (DoD):**
- [ ] Die URL-Extraktion nutzt robuste `java.net.URI` und `android.net.Uri` Mechanismen.
- [ ] Die Parameter `v=` (Video) und `list=` (Playlist) werden sauber getrennt behandelt.
- [ ] Unit-Tests für `extractUrl` decken die 5 häufigsten YouTube-Share-Formate ab.

### Issue C5: Car App Offline Image Caching aktivieren
**Kontext:** Die `ImageRequest` Aufrufe in der Car App haben keine explizite Cache-Policy. Fährt das Auto in eine Offline-Zone (Garage), fehlen die Icons im Autoradio.
**Betroffene Dateien:**
- `app/src/main/java/app/olus/ytmusic/autolauncher/ui/auto/YTMusicCarAppService.kt`
**Akzeptanzkriterien (DoD):**
- [ ] Das Coil `ImageRequest` Builder-Pattern in `loadIcons()` wird erweitert, um `.memoryCachePolicy` und `.diskCachePolicy` auf `CachePolicy.ENABLED` zu setzen.
- [ ] Das Timeout für das Netzwerk-Laden in der CarApp wird verkürzt, um im Offline-Fall schneller das Cache-Bild (oder das Fallback-Icon) anzuzeigen.
