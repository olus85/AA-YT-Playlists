
## 🚀 KATEGORIE F: Feature Requests & Layout-Optimierungen

### Issue F1: Android Auto Dashboard / Split-Screen Support (Media-Kategorie)
**Kontext:** Die App verschwindet aktuell, wenn Nutzer in die Dashboard-Ansicht (Multi-Layout mit Maps und Widgets) wechseln, da sie als `IOT`-App deklariert ist. Nutzer möchten die App aber in der kompakten Split-Screen-Ansicht behalten. Um vom Android Auto System einen Platz im Dashboard zu bekommen, muss sich die App wie eine Medien-App verhalten und dem System eine aktive `MediaSession` bereitstellen, aus der das Headunit das kompakte Widget generiert.
**Betroffene Dateien:**
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/app/olus/ytmusic/autolauncher/ui/auto/YTMusicCarAppService.kt`
- (Neu) `app/src/main/java/app/olus/ytmusic/autolauncher/service/LauncherMediaSession.kt`
**Akzeptanzkriterien (DoD):**
- [ ] Die App-Kategorie im Manifest wird erweitert/angepasst, um Media-Funktionen zu signalisieren (Prüfung, ob Wechsel von `IOT` zu `MEDIA` oder dualer Betrieb sinnvoll ist).
- [ ] Beim Starten der App im Auto wird eine `MediaSessionCompat` initialisiert, die dem System signalisiert, dass diese App Medien kontrollieren kann.

### Issue F2: Inline Media-Controls für Playlisten (Headunit)
**Kontext:** In der Playlist-Übersicht im Auto wird viel horizontaler Platz verschwendet. Nutzer möchten Playlisten direkt aus der Liste starten, pausieren oder shufflen können. Da die Car App Library strikte Limitierungen hat (max. 1 Action-Button pro Listen-Reihe), muss das UI intelligent aufgebaut werden.
**Betroffene Dateien:**
- `app/src/main/java/app/olus/ytmusic/autolauncher/ui/auto/YTMusicCarAppService.kt` (speziell `PlaylistGridScreen` bzw. List-Builder)
**Akzeptanzkriterien (DoD):**
- [ ] Jedes `Row`-Element in der Playlisten-Übersicht erhält einen dedizierten Action-Button (über `rowBuilder.addAction(...)`).
- [ ] Der Action-Button ist visuell ein "Play"-Icon. Wenn die Playlist als "aktuell spielend" markiert ist (State aus dem ViewModel/Service), wechselt das Icon zu "Pause".
- [ ] Da nur *ein* Button pro Reihe erlaubt ist, führt der direkte Klick auf den **Reihen-Hintergrund** (oder einen dedizierten Bereich) in den `PlaylistDetailScreen` (aus Issue E2), wo große "Shuffle"- und "Skip"-Buttons implementiert werden.
- [ ] (Zusatz) Im neuen `PlaylistDetailScreen` wird oberhalb der Songliste eine prominente `Row` (oder `ActionStrip`) mit drei Buttons eingefügt: [Play], [Shuffle], [Skip].

### Issue F3: Google Bildersuche-Integration für Cover-URLs (Smartphone)
**Kontext:** Beim manuellen Bearbeiten einer Playlist über den `EditPlaylistDialog` ist das Suchen, Kopieren und Einfügen einer passenden Bild-URL für das Thumbnail auf dem Smartphone mühsam. Eine direkte Absprungmöglichkeit zur Google Bildersuche mit dem Namen der Playlist würde die UX massiv verbessern.
**Betroffene Dateien:**
- `app/src/main/java/app/olus/ytmusic/autolauncher/ui/compose/screens/PlaylistScreen.kt`
- `app/src/main/res/values/strings.xml`
**Akzeptanzkriterien (DoD):**
- [ ] Im `EditPlaylistDialog` (bzw. `AddPlaylistDialog`) wird neben/unter dem Eingabefeld für die Bild-URL ein Button (z. B. "Cover im Web suchen" mit einer Lupe als Icon) hinzugefügt.
- [ ] Ein Klick auf den Button feuert einen `Intent.ACTION_VIEW`.
- [ ] Der Intent öffnet den Standard-Browser des Smartphones mit der Google Bildersuche. Die Suchanfrage wird automatisch aus dem aktuellen Textfeld "Playlist Name" generiert (URL-encoded: `https://www.google.com/search?tbm=isch&q=PLAYLIST_NAME`).
- [ ] Der Nutzer kann sich dort ein Bild aussuchen, den Link kopieren und nach Rückkehr in die App in das Textfeld einfügen.

