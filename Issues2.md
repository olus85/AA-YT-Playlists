**Ziel:** Die UI komplett überholen, Bugs beim Drag & Drop beheben und die Android Auto Features massiv erweitern (Track-Auswahl & optimierte Thumbnails).

***

## 🛠️ KATEGORIE D: Smartphone UI/UX Redesign & Fixes

### Issue D1: Komplettumbau der App UI (Material 3 Redesign)
**Kontext:** Die aktuelle Benutzeroberfläche ist rudimentär und wirkt unfertig. Der User wünscht sich ein komplettes Redesign. Die App soll moderner, flüssiger und ansprechender werden.
**Betroffene Dateien:**
- `app/src/main/java/app/olus/ytmusic/autolauncher/ui/compose/theme/Theme.kt`
- `app/src/main/java/app/olus/ytmusic/autolauncher/ui/compose/screens/PlaylistScreen.kt`
**Akzeptanzkriterien (DoD):**
- [ ] Implementierung eines neuen, modernen Layouts für Playlisten (z. B. Edge-to-Edge Cards, aufgeräumtere Metadaten-Darstellung).
- [ ] Optional: Unterstützung für echtes Material You (Dynamic Colors via `dynamicDarkColorScheme`), falls der User das wünscht, statt des hartkodierten YT-Rot/Schwarz.
- [ ] Leerraum (Whitespace) und Typografie nach aktuellen Material 3 Guidelines optimieren.
- [ ] Animationen beim Erweitern/Schließen von Menüs hinzufügen.

### Issue D2: Drag & Drop Glitches beheben & Drag-Handle aktivieren
**Kontext:** Das Sortieren der Playlisten führt zu Rucklern und die Liste "glitcht" nach unten. Dies liegt an der manuellen `detectDragGesturesAfterLongPress` Offset-Berechnung. Zudem ist das Drag-Handle (die zwei Linien links) aktuell ohne echte Funktion, da der Long-Press auf der gesamten Karte liegt.
**Betroffene Dateien:**
- `app/src/main/java/app/olus/ytmusic/autolauncher/ui/compose/screens/PlaylistScreen.kt`
- `app/build.gradle.kts`
**Akzeptanzkriterien (DoD):**
- [ ] Die Eigenbau-Logik mit `pointerInput` wird durch eine etablierte Jetpack Compose Reorder-Logik (z.B. `Calvin-LL/Reorderable` Library oder eine saubere Compose 1.7 `Modifier.animateItem()` Lösung) ersetzt.
- [ ] Das `pointerInput`-Event wird explizit an das `Icon` des Drag-Handles gebunden (`Modifier.draggableHandle()`), sodass User dort sofort ziehen können (ohne Long-Press).
- [ ] Die Listen-Items springen beim Scrollen über die Ränder während des Draggings nicht mehr wild umher.

### Issue D3: Thumbnail-URL auf dem Smartphone manuell bearbeiten
**Kontext:** Der User kann momentan nur den Titel einer Playlist über den `EditTitleDialog` anpassen. Da der Web-Scraper manchmal fehlschlägt oder falsche Bilder lädt, soll das Thumbnail manuell per URL ersetzbar sein.
**Betroffene Dateien:**
- `app/src/main/java/app/olus/ytmusic/autolauncher/ui/compose/screens/PlaylistScreen.kt`
- `app/src/main/java/app/olus/ytmusic/autolauncher/ui/compose/screens/PlaylistViewModel.kt`
**Akzeptanzkriterien (DoD):**
- [ ] Der `EditTitleDialog` wird zu einem generischen `EditPlaylistDialog` umgebaut.
- [ ] Ein zweites `OutlinedTextField` für die "Bild-URL" wird hinzugefügt (mit dem aktuellen Thumbnail-Wert vorausgefüllt).
- [ ] Beim Klick auf Speichern wird im ViewModel die `imageUrl` des `Playlist`-Objekts aktualisiert und in der Room-Datenbank gespeichert.

---

## 🚘 KATEGORIE E: Android Auto (Car App Library) "Next Level"

### Issue E1: Dynamische und randlose Thumbnails im Headunit
**Kontext:** Die Playlisten-Bilder im Android Auto Display (Headunit) sind dem User zu klein. Die Car App Library limitiert zwar Bildgrößen stark, aber aktuell nutzt die App `Scale.FIT` im Coil-Loader. Das erzeugt unschöne Ränder bei 16:9 YT-Thumbnails in den quadratischen Car-Icons.
**Betroffene Dateien:**
- `app/src/main/java/app/olus/ytmusic/autolauncher/ui/auto/YTMusicCarAppService.kt`
**Akzeptanzkriterien (DoD):**
- [ ] Im `ImageRequest` von Coil (in `loadIcons()`) wird `.scale(Scale.FIT)` durch eine geeignete Crop-Strategie ersetzt, um das Icon randlos und maximal formatfüllend zu machen.
- [ ] Es wird geprüft, ob die Umstellung auf ein `ListTemplate` mit großen Row-Images (`Row.Builder().setImage(..., Row.IMAGE_TYPE_LARGE)`) bei breiten Infotainment-Screens wuchtigere Thumbnails zulässt als das aktuelle `GridTemplate`.

### Issue E2: Playlist Detail Screen (In-Car Track Selection)
**Kontext:** Klickt man momentan im Auto auf eine Playlist, delegiert die App nur einen "Play"-Befehl an das Handy (`/watch?list=X`). Die Next-Level-Erfahrung erfordert, dass man in Android Auto die Playlist öffnet, durch die echten Songs scrollt und spezifische Tracks starten kann.
**Betroffene Dateien:**
- `app/src/main/java/app/olus/ytmusic/autolauncher/ui/auto/YTMusicCarAppService.kt` (Benötigt neuen Screen: `PlaylistDetailScreen`)
- `app/src/main/java/app/olus/ytmusic/autolauncher/data/repository/MetadataFetcher.kt`
- `app/src/main/java/app/olus/ytmusic/autolauncher/ui/PlaylistReceiver.kt`
**Akzeptanzkriterien (DoD):**
- [ ] Der `MetadataFetcher` erhält eine neue Methode `fetchTracks(playlistUrl)`, die die tatsächliche Songliste (Titel, Autor, Video-ID) einer Playlist scrapt (z.B. über die Invidious API).
- [ ] Klickt man im `PlaylistGridScreen` auf eine Playlist, navigiert die Car App Library via `screenManager.push()` zu einem neuen `PlaylistDetailScreen`.
- [ ] Dieser Screen rendert ein `ListTemplate` mit den Songs der Playlist. Ein Ladeindikator wird gezeigt, während der Fetcher im Hintergrund läuft.
- [ ] Klickt man auf einen konkreten Song, sendet der Broadcast eine erweiterte URL an den Receiver (z.B. `music.youtube.com/watch?v=[SONG_ID]&list=[PLAYLIST_ID]`), um exakt diesen Track im Kontext der Playlist auf dem Smartphone zu starten.
