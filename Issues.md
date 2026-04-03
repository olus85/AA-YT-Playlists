### 🎟️ Issue 1: Entfernung der redundanten Car App Library (Priorität: Hoch)

**Titel:** Refactoring: Entfernung der Car App Library (`YTMusicCarAppService`) zugunsten des nativen `MediaBrowserService`

**Beschreibung:**
Die Anwendung implementiert derzeit zwei parallele Schnittstellen für Android Auto: den klassischen `YTMediaBrowserService` und den auf der Car App Library basierenden `YTMusicCarAppService`. 
Da die Car App Library Version auf physischen Headunits aufgrund der Google Play Store Richtlinien blockiert wird und der `YTMediaBrowserService` als transparenter Media-Proxy vollumfänglich funktioniert, ist die Car App Library Implementierung redundant. Sie soll vollständig entfernt werden, um die Codebasis zu verschlanken und mögliche Konflikte im Manifest zu vermeiden.

**Akzeptanzkriterien (Tasks):**
* [ ] Löschen der Datei `YTMusicCarAppService.kt` (und ggf. zugehöriger reiner UI-Template-Klassen im Ordner `app/olus/ytmusic/autolauncher/ui/auto/`).
* [ ] Bereinigen der `AndroidManifest.xml`:
  * Entfernen des `<service>`-Eintrags für `YTMusicCarAppService`.
  * Überprüfen und ggf. Entfernen von `<meta-data>` Tags, die exklusiv für die Car App Library benötigt werden (z. B. `androidx.car.app.minCarApiLevel`). *Hinweis: `automotive_app_desc.xml` mit `<uses name="media"/>` muss für den MediaBrowserService erhalten bleiben!*
* [ ] Entfernen der Abhängigkeiten (Dependencies) zur `androidx.car.app`-Bibliothek aus der `app/build.gradle.kts`.
* [ ] Sicherstellen, dass das Projekt fehlerfrei kompiliert und der Emulator (DHU) nur noch ein einzelnes App-Icon (die Media-App) für Android Auto anzeigt.

---

### 🎟️ Issue 2: Behebung der KSP/Room Datenbank-Kompilierungsfehler (Priorität: Hoch)

**Titel:** Bugfix: KSP Compilation Errors bei der Room Database Generierung beheben

**Beschreibung:**
Im Projektverzeichnis befinden sich Fehlerprotokolle (z. B. `.kotlin/errors/errors-1773855237364.log`), die auf Abstürze oder Kompilierungsfehler des Kotlin Symbol Processors (KSP) im Zusammenhang mit der Room-Datenbank hinweisen. Dies kann zu instabilen Builds führen oder verhindern, dass neue Datenbank-Migrationen korrekt generiert werden.

**Akzeptanzkriterien (Tasks):**
* [ ] Analysieren der KSP-Fehlerlogs im `.kotlin/errors/`-Verzeichnis.
* [ ] Überprüfen der Kompatibilität der Versionen von Kotlin, KSP und Room in den Gradle-Konfigurationen (`build.gradle.kts` / `libs.versions.toml`).
* [ ] Sicherstellen, dass die Annotationen in `PlaylistDatabase.kt`, `PlaylistDao.kt` und `PlaylistEntity.kt` den aktuellen Room-Spezifikationen entsprechen.
* [ ] Erfolgreicher, fehlerfreier Clean-Build (ohne KSP-Warnings/Errors im Build-Log).

---

### 🎟️ Issue 3: Resilienz des Invidious-Scrapers verbessern (Priorität: Mittel)

**Titel:** Optimierung: Fallback-Logik und Caching im `MetadataFetcher` robuster gestalten

**Beschreibung:**
Der `MetadataFetcher` nutzt Invidious-Instanzen, um Metadaten und Tracklisten abzurufen, ohne Google APIs zu belasten. Da öffentliche Invidious-Instanzen häufig offline gehen, ratelimited werden oder sehr langsam antworten, muss der Fetcher resilienter werden, um die User Experience im Auto (wo Netzwerk oft instabil ist) nicht zu beeinträchtigen.

**Akzeptanzkriterien (Tasks):**
* [ ] Implementierung eines serverseitigen Fallback-Mechanismus (Retry-Logik mit Exponential Backoff), wenn eine Invidious-Instanz einen HTTP 5xx Fehler oder Timeout wirft.
* [ ] Erweitern der Fallback-Kette: Wenn die dynamisch abgerufene Invidious-Instanz fehlschlägt, soll automatisch die nächste aus der Liste probiert werden.
* [ ] Überprüfen, ob persistentes Caching der Playlist-Metadaten in der Room-Datenbank sinnvoll erweitert werden kann, sodass Playlisten auch bei fehlender Internetverbindung im Auto zumindest angezeigt werden können (Offline-First Ansatz).

---

### 🎟️ Issue 4 (Aktualisiert): Limitierung der Log-Dateigröße auf 5 MB

**Titel:** Optimierung: Dateigröße für `AALogger` auf maximal 5 MB begrenzen (ohne Historie)

**Beschreibung:**
Die Klasse `AALogger` schreibt kontinuierlich Debug-Informationen in die Datei `aa_debug.log`. Aktuell wächst diese Datei unendlich an. Für Debugging-Zwecke ist lediglich das aktuelle Log relevant. Die Datei soll daher auf eine maximale Größe von 5 MB limitiert werden. Es werden *keine* alten Logs (`_old.log`) oder Rotations-Dateien benötigt. Sobald das Limit erreicht ist, soll die Datei entweder von vorne überschrieben werden (Circular Logging) oder geleert und neu gestartet werden.

**Akzeptanzkriterien (Tasks):**
* [ ] Logik in `AALogger` anpassen: Überprüfen der Dateigröße vor dem Schreiben neuer Einträge.
* [ ] Wenn `aa_debug.log` 5 MB erreicht, die Datei leeren/überschreiben, sodass sie nicht weiter anwächst.
* [ ] Sicherstellen, dass die "Teilen"-Funktion in der UI (z. B. im Diagnose-Screen) reibungslos mit dieser limitierten Datei funktioniert und es nicht zu Zugriffsfehlern während des Überschreibens kommt.

---

### 🎟️ Issue 5: Genius API Fallback für Lyrics

**Titel:** Feature: Genius API als Fallback-Datenquelle für Songtexte (Lyrics) integrieren

**Beschreibung:**
Aktuell werden für nicht alle Songs die zugehörigen Songtexte gefunden. Um die Abdeckung zu verbessern, soll die Genius API ([https://docs.genius.com/](https://docs.genius.com/)) als Fallback integriert werden. Wenn die primäre Quelle keine Lyrics liefert, soll anhand von Künstler und Songtitel eine Suchanfrage an Genius gestellt und der Text von dort bezogen werden.

**Akzeptanzkriterien (Tasks):**
* [ ] API-Client für die Genius-Schnittstelle aufbauen (z. B. via Retrofit oder Ktor).
* [ ] Platzhalter-Konstanten für die API-Authentifizierung im Code anlegen (Kunde trägt diese später selbst ein):
  * `GENIUS_CLIENT_ID = "2o3DcfgPEO3esYG5Zvywv5o7TNh1xhE0nqK0TLOXChHrkC1SEUAeID6VBL0kT3VP"`
  * `GENIUS_CLIENT_SECRET = "eymPtpHXby0yVLTpED9pDmTPmXU8e7tRrSvg6EOupOs1fckPTsc6oV0SBX5qlJlSKpeSIPGiRNBietllC0vOBg"`
  * `GENIUS_ACCESS_TOKEN = "e2IjjnIVsdoFj5k3wb5A8US_r6sPdgM9QVbW9P5Rz2I85Rp7ic_FE4yCiERkcCgm"`
* [ ] Fallback-Logik implementieren: Wird kein Text über den primären Weg gefunden, Suche über die Genius `/search` API (Query: "Artist - Title") anstoßen und den Text extrahieren.
* [ ] Fehlerbehandlung (Timeouts, Rate Limits oder keine Treffer bei Genius) implementieren, sodass die App nicht abstürzt, sondern "Keine Lyrics gefunden" anzeigt.

---

### 🎟️ Issue 6: Überarbeitung der Lyrics-UI

**Titel:** Refactoring & UI: Aktuelles Lyrics-Overlay durch eigenständige Ansicht ersetzen

**Beschreibung:**
Das aktuelle UI-Overlay für Songtexte bietet eine schlechte User Experience: Das Scrollen der Texte wird fehlerhaft unterbrochen, sobald die aktuelle Zeile den unteren Bildschirmrand des Smartphones erreicht. 
Dieses Overlay soll komplett aus dem Code entfernt werden. Stattdessen wird eine saubere, eigenständige Ansicht (z. B. ein Fullscreen-Dialog) für die Lyrics implementiert. Der Aufruf dieser neuen Ansicht erfolgt über einen neuen Button, der sich in der Top-Bar links neben dem bestehenden Settings-Button befindet.

**Akzeptanzkriterien (Tasks):**
* [ ] Vollständiges Entfernen des alten, fehlerhaften Lyrics-Overlays aus dem Compose-Code.
* [ ] Hinzufügen eines neuen Icon-Buttons (z. B. ein Noten- oder Text-Icon) in der Top-AppBar. Platzierung: Direkt links neben dem Settings-Button.
* [ ] Erstellen einer neuen, scrollbaren Compose-Ansicht für die Lyrics (Empfehlung: `ModalBottomSheet` oder dedizierter Fullscreen-Screen).
* [ ] Sicherstellen, dass in der neuen Ansicht lückenlos und ohne Hänger bis zum Ende des Songtextes gescrollt werden kann.
* [ ] Verknüpfen der neuen Ansicht mit dem State, sodass immer der Text des aktuell laufenden Songs angezeigt wird.

---
