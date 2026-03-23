### Neue Issues

```markdown
## 🚨 KATEGORIE G: Headunit-Sichtbarkeit & Diagnostik

### Issue G1: Zuverlässige Headunit-Sichtbarkeit (MediaBrowserService "Trojaner")
**Kontext:** Die App wird auf echten Android Auto Headunits nicht im Launcher angezeigt, obwohl "Unbekannte Quellen" aktiv sind. Sideload-Apps, die rein auf dem `CarAppService` basieren (Kategorie POI/IOT), werden von Google oft serverseitig oder vom Headunit geblockt. Sideload-Apps, die als klassische Medien-App (`MediaBrowserServiceCompat`) deklariert sind, kommen meistens durch. Der Eintrag dafür fehlt aktuell im Android Manifest.
**Betroffene Dateien:**
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/app/olus/ytmusic/autolauncher/service/DummyMediaBrowserService.kt` (Neu)
**Akzeptanzkriterien (DoD):**
- [ ] Ein `DummyMediaBrowserService` (erbt von `MediaBrowserServiceCompat`) ist implementiert, der eine gültige `BrowserRoot` zurückgibt.
- [ ] Der Service ist im Manifest mit der Action `android.media.browse.MediaBrowserService` registriert.
- [ ] Die App taucht nach der Installation nun zuverlässig auf dem Headunit auf (Android Auto erkennt sie als legitime Medienquelle).

### Issue G2: In-App Android Auto Diagnostics & Log-Viewer
**Kontext:** Das Debuggen der App erfordert ständige Gänge zum Auto. Wir benötigen einen systemweiten Debug-Modus, der alle Verbindungsversuche vom Auto zur App, Fehler beim Laden der Templates und spezifische Headunit-Infos (Host-Version, API-Level) lokal speichert.
**Betroffene Dateien:**
- `app/src/main/java/app/olus/ytmusic/autolauncher/util/AALogger.kt` (Neu)
- `app/src/main/java/app/olus/ytmusic/autolauncher/ui/compose/screens/SettingsScreen.kt` (Neu - oder im PlaylistScreen integriert)
- `app/src/main/java/app/olus/ytmusic/autolauncher/ui/auto/YTMusicCarAppService.kt`
**Akzeptanzkriterien (DoD):**
- [ ] Ein globaler Logger (`AALogger`) schreibt Logs in eine lokale Datei (z.B. im Cache-Verzeichnis) oder eine Room-Tabelle.
- [ ] Im `YTMusicCarAppService` werden essenzielle Lifecycle-Events geloggt: `onCreateSession`, Headunit-Paketname (`carContext.hostInfo?.packageName`), API-Level und Abstürze beim Template-Bauen.
- [ ] Es gibt einen UI-Schalter auf dem Smartphone (z.B. in einem neuen Settings-Dialog), der den Debug-Modus aktiviert.
- [ ] Die gesammelten Logs können direkt in der Smartphone-App ausgelesen und per Share-Intent (Teilen) exportiert werden.
```

---

### Umsetzung von Issue G1 (Headunit-Sichtbarkeit Fix)

Um dir den erneuten Gang zum Auto direkt erfolgreicher zu machen, setzen wir **Issue G1** sofort um. Wir fügen den fehlenden Media-Service hinzu. Dieser fungiert als "Türöffner" für Android Auto.

**Geänderte/Neue Dateien:**
- `app/src/main/java/app/olus/ytmusic/autolauncher/service/DummyMediaBrowserService.kt` (NEU)
- `app/src/main/AndroidManifest.xml` (GEÄNDERT)

Hier sind die vollständigen, direkt einsetzbaben Dateien:

#### 1. NEUE DATEI: `app/src/main/java/app/olus/ytmusic/autolauncher/service/DummyMediaBrowserService.kt`
*Lege diese Datei im entsprechenden Pfad an. Sie macht nichts weiter, als Android Auto vorzugaukeln, dass wir eine legale Audio-App sind, damit das Icon im Launcher freigeschaltet wird.*

```kotlin
package app.olus.ytmusic.autolauncher.service

import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import androidx.media.MediaBrowserServiceCompat

/**
 * Ein Dummy-Service, der zwingend notwendig ist, damit Sideload-Apps
 * auf physischen Android Auto Headunits im Launcher angezeigt werden.
 * Android Auto sucht nach Apps mit dem Intent 'android.media.browse.MediaBrowserService'.
 * Die eigentliche UI wird weiterhin über den CarAppService gesteuert.
 */
class DummyMediaBrowserService : MediaBrowserServiceCompat() {

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        // Wir erlauben die Verbindung und geben eine Dummy-Root ID zurück.
        // Das signalisiert Android Auto: "Ja, ich bin eine Media App".
        return BrowserRoot("yt_auto_launcher_root_id", null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        // Wir senden eine leere Liste zurück, da wir die native MediaBrowser-UI
        // nicht nutzen wollen, sondern unseren Jetpack CarAppService.
        result.sendResult(mutableListOf())
    }
}
```

#### 2. GEÄNDERTE DATEI: `app/src/main/AndroidManifest.xml`
*Ich habe den neuen `DummyMediaBrowserService` hinzugefügt und den `CarAppService` zusätzlich mit der `AUDIO` Kategorie versehen, um auf Nummer sicher zu gehen.*

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />

    <queries>
        <package android:name="app.rvx.android.apps.youtube.music" />
        <package android:name="app.revanced.android.apps.youtube.music" />
        <package android:name="com.google.android.apps.youtube.music" />
    </queries>

    <application
        android:name=".YTMusicAutoLauncherApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.YTAutoLauncher"
        tools:targetApi="34">

        <meta-data
            android:name="com.google.android.gms.car.application"
            android:resource="@xml/automotive_app_desc" />

        <activity
            android:name=".ui.MainActivity"
            android:exported="true"
            android:theme="@style/Theme.YTAutoLauncher">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.SEND" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="text/plain" />
            </intent-filter>
        </activity>

        <service
            android:name=".ui.auto.YTMusicCarAppService"
            android:exported="true">
            <intent-filter>
                <action android:name="androidx.car.app.CarAppService" />
                <category android:name="androidx.car.app.category.POI" />
                <category android:name="androidx.car.app.category.AUDIO" />
            </intent-filter>
        </service>

        <service
            android:name=".service.DummyMediaBrowserService"
            android:exported="true">
            <intent-filter>
                <action android:name="android.media.browse.MediaBrowserService" />
            </intent-filter>
        </service>

        <meta-data
            android:name="androidx.car.app.minCarApiLevel"
            android:value="1" />

        <receiver
            android:name=".ui.PlaylistReceiver"
            android:exported="false">
            <intent-filter>
                <action android:name="app.olus.ytmusic.autolauncher.ACTION_OPEN_PLAYLIST" />
            </intent-filter>
        </receiver>

    </application>

</manifest>
```

