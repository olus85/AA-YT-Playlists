---

# 🐛 [BUG]: Issue 1: Fix des Scroll-Verhaltens auf dem Smartphone-Screen

## Zusammenfassung
Die Smartphone-UI lässt sich scrollen und verkleinert die Top-App-Bar, auch wenn nur sehr wenige Playlisten vorhanden sind und der Bildschirm eigentlich keinen Scroll-Bedarf hat. Das weicht von gängigen Best-Practices ab.

## Kontext & Hintergrund
In Jetpack Compose triggert das `TopAppBarDefaults.exitUntilCollapsedScrollBehavior()` den Scroll-Effekt der App-Bar unabhängig von der Länge des Inhalts in der `LazyColumn`. Es reagiert rein auf die Wisch-Geste (Nested Scrolling).

## Ist-Zustand
Die UI wippt/scrollt auch bei 4 Playlisten, die problemlos auf einen Screen passen.

## Soll-Zustand
Der Screen soll absolut fixiert sein, solange der Inhalt der Playlisten nicht den sichtbaren Bereich der `LazyColumn` überschreitet. Nur bei echten Überlängen soll gescrollt werden.

## Technischer Lösungsansatz

### Schritt-für-Schritt-Plan:
1. **`app/src/main/java/app/olus/ytmusic/autolauncher/ui/compose/screens/PlaylistScreen.kt`**:
   - Entferne das `TopAppBarDefaults.exitUntilCollapsedScrollBehavior()`.
   - Ersetze es entweder durch ein `pinnedScrollBehavior()` oder belasse die `LargeTopAppBar` statisch, sodass nur die `LazyColumn` ihren eigenen inneren Scroll-State verwaltet.
   - Alternativ: Berechne dynamisch, ob die Liste scrollbar ist (`listState.layoutInfo.visibleItemsInfo.size < playlists.size`), und aktiviere das NestedScrolling nur, wenn Overflow existiert. (Empfohlen: Einfach das Nested Scroll Behavior der App-Bar entfernen, um standardmäßiges Listen-Scrollen zu erzielen).

---

# ✨ [FEATURE]: Issue 2: Neuer Synced-Lyrics Provider (Musixmatch-Ersatz)

## Zusammenfassung
Der Musixmatch-Fallback liefert keine Ergebnisse mehr. Da Genius keine Timestamps für Auto-Scrolling bietet, muss ein neuer API-Anbieter für synchronisierte Songtexte (LRC-Format) integriert werden.

## Kontext & Hintergrund
Der `LyricsFetcher` sucht zuerst bei *lrclib*. Schlägt dies fehl, geht er zu *Musixmatch* und dann zu *Genius*. Musixmatch soll durch einen zuverlässigeren Synced-Lyrics-Provider ersetzt werden (z.B. NetEase API oder Rentanadviser/Megalobiz-Scraper).

## Ist-Zustand
Wenn lrclib keine Lyrics hat, landet man fast immer bei Genius (plain text, kein vernünftiges Auto-Scrolling).

## Soll-Zustand
Ein alternativer Provider mit Timestamps wird als zweite Ebene nach lrclib abgefragt. 

## Technischer Lösungsansatz

### Schritt-für-Schritt-Plan:
1. **`app/src/main/java/app/olus/ytmusic/autolauncher/data/repository/LyricsFetcher.kt`**:
   - Entferne die Methode `fetchFromMusixmatch` und die zugehörigen Token-Logiken.
   - Implementiere eine neue Methode `fetchFromNetEase` (oder einen anderen offenen LRC-API-Anbieter wie `Megalobiz` oder eine offene Rentry-LRC-Search).
   - *Tipp für den Agenten:* Die NetEase Cloud Music API kann über öffentliche Endpunkte ohne Auth nach Songs durchsucht und die Lyrics mit Timestamps (`lrc`) im JSON-Format abgerufen werden (z.B. `https://music.163.com/api/search/get/` und `https://music.163.com/api/song/lyric?id=...&lv=1`).
   - Schließe den neuen Provider zwischen `lrclib` und `Genius` in der Fallback-Kette an.

---

# 🔧 [REFACTOR]: Issue 3: Android Auto Root-Hierarchie anpassen (Tab-Problem)

## Zusammenfassung
Android Auto konvertiert Root-Elemente standardmäßig in eine obere Tab-Leiste, wenn es nur wenige sind (<= 4). Dadurch verschwinden unsere Thumbnails und Namen werden stark gekürzt.

## Kontext & Hintergrund
In `YTMediaBrowserService.kt` gibt `onLoadChildren` für die `parentId == ROOT_ID` direkt die Liste der Playlisten zurück. 

## Ist-Zustand
Bei 4 Playlisten zeigt Android Auto 4 Tabs oben an.

## Soll-Zustand
Playlisten sollen *immer* als schöne, vertikale Liste mit Cover-Art angezeigt werden, unabhängig von ihrer Anzahl.

## Technischer Lösungsansatz

### Schritt-für-Schritt-Plan:
1. **`app/src/main/java/app/olus/ytmusic/autolauncher/service/YTMediaBrowserService.kt`**:
   - Modifiziere `loadPlaylists()` bzw. die Logik für `ROOT_ID`.
   - Statt alle Playlisten im Root zurückzugeben, gib im Root genau **ein einziges Item** zurück: Einen virtuellen Ordner namens "Meine Musik" oder "Playlisten" (`FLAG_BROWSABLE`).
   - Wenn der Nutzer auf diesen einzigen Root-Ordner (z.B. `mediaId = "folder_playlists"`) klickt, lade im nächsten `onLoadChildren`-Aufruf die eigentlichen Playlisten aus der Datenbank.
   - Android Auto macht aus dem einen Root-Element zwangsweise einen Tab (z.B. "Start") und rendert den Inhalt beim Klick (die Playlisten) dann als Listenansicht inklusive Thumbnails.

---

# ✨ [FEATURE]: Issue 4: Vollständiges Caching (Tracks & Metadaten) in Room

## Zusammenfassung
Um die App sofort reaktionsfähig zu machen und Ladezeiten zu minimieren, sollen nicht nur Playlisten, sondern auch deren Songlisten (Tracks) persistent in der Datenbank gespeichert werden.

## Kontext & Hintergrund
Aktuell ruft der `MetadataFetcher` die Tracks jedes Mal live über Invidious ab. Im Auto bei schlechtem Netz führt das zu Timeouts.

## Ist-Zustand
Tracks existieren nur im `LruCache`.

## Soll-Zustand
Alle geladenen Tracks werden in einer neuen Room-Tabelle gespeichert. Die Invidious-API wird nur noch zur Hintergrund-Aktualisierung genutzt.

## Technischer Lösungsansatz

### Schritt-für-Schritt-Plan:
1. **Neue Entity & Dao erstellen**:
   - `TrackEntity.kt` (id, playlistId, title, author, videoId, position) erstellen.
   - `TrackDao.kt` erstellen mit `getTracksForPlaylist(playlistId)` und `insertTracks(tracks)`.
2. **`PlaylistDatabase.kt`**:
   - `TrackEntity` hinzufügen, Versionsnummer der DB erhöhen, Migration anlegen.
3. **`PlaylistRepository.kt` & `MetadataFetcher.kt`**:
   - Wenn Tracks via `MetadataFetcher` erfolgreich aus dem Netz geladen werden, sollen sie via Dao in die Datenbank geschrieben werden.
   - Wenn `loadTracks` im Auto aufgerufen wird, gib *zuerst* die lokalen Tracks aus der DB zurück (falls vorhanden), um Wartezeiten zu vermeiden. Triggere danach asynchron einen Refresh.

---

# ✨ [FEATURE]: Issue 5: Jellyfin Server Integration (Settings & Playback)

## Zusammenfassung
Die App soll erweitert werden, um als Proxy/Launcher für Jellyfin-Playlisten und Alben zu fungieren. Hierfür wird ein Einstellungsbereich für Server-Zugangsdaten benötigt und die Modelle müssen den Typ der Playlist unterscheiden können.

## Kontext & Hintergrund
Dies ist das komplexeste Feature. Da die App aktuell als Proxy für den Android MediaController von YouTube Music agiert, muss entschieden werden, wie Jellyfin-Wiedergabe stattfindet. Da im Issue gefordert ist, dass sie "nahtlos mit den bisherigen playlisten angezeigt und abgespielt werden können", gehen wir davon aus, dass wir Intents an die offizielle Jellyfin-Android-App senden (analog zu YT Music).

## Ist-Zustand
Es gibt nur den "DiagnosticsDialog". Playlisten haben keinen `type`.

## Soll-Zustand
- Es gibt einen echten Settings-Screen für Jellyfin (Server-URL, User, Passwort/Token).
- Beim Hinzufügen einer Playlist via FAB fragt die App: "YouTube URL" oder "Aus Jellyfin importieren".
- Ein neuer Dialog verbindet sich mit dem Jellyfin-Server (via REST API) und listet Musik-Alben/Playlisten auf.
- Das Model `PlaylistEntity` erhält das Feld `sourceType` ("YOUTUBE" oder "JELLYFIN") und `jellyfinItemId`.

## Technischer Lösungsansatz

### Schritt-für-Schritt-Plan:
1. **Modelle & DB anpassen**:
   - `PlaylistEntity` um `source` (String/Enum) und `externalId` (für Jellyfin Item IDs) erweitern. DB-Migration erstellen!
2. **Settings-Screen bauen**:
   - Den Settings-Icon-Button in der Top-Bar so umbauen, dass er zu einem neuen `SettingsScreen` navigiert (oder einem Full-Screen Dialog).
   - Felder für Jellyfin Host, Username, Password hinterlegen (z.B. verschlüsselt in SharedPreferences oder DataStore speichern).
3. **Jellyfin API Client**:
   - Ein neues `JellyfinRepository` erstellen, das sich beim Server authentifiziert (`/Users/AuthenticateByName`) und Alben/Playlisten abruft (`/Users/{UserId}/Items?IncludeItemTypes=MusicAlbum,Playlist`).
4. **UI-Flow anpassen**:
   - Im `AddPlaylistDialog`: Biete Buttons an: "YouTube URL eingeben" oder "Jellyfin durchsuchen".
   - Beim Auswählen von Jellyfin: Zeige Liste der Server-Items, speichere Auswahl als `PlaylistEntity` mit Source JELLYFIN.
5. **Playback-Logik (`YTMediaBrowserService.kt`)**:
   - Beim Starten eines Tracks prüfen: Ist die Quelle `YOUTUBE` -> Sende URL an YT Music App.
   - Ist die Quelle `JELLYFIN` -> Sende Intent an die Jellyfin App (z.B. `org.jellyfin.mobile`).

---
