# Bauen und veröffentlichen

## Die eine harte Voraussetzung: GitHub-Packages-Auth

Das Morphe-Gradle-Plugin (`app.morphe.patches`) liegt auf GitHub Packages unter
`maven.pkg.github.com/MorpheApp/registry`. GitHub Packages verlangt **immer**
Authentifizierung, auch für öffentliche Pakete. Der Build scheitert deshalb mit
`Plugin [id: 'app.morphe.patches'] was not found`, bis ein Token mit dem Scope
`read:packages` hinterlegt ist. Alles andere am Setup ist Standard.

Ein Token reicht — einer dieser beiden Wege:

**Variante A — `gh`-Token erweitern** (interaktiv, öffnet den Browser):

```bash
gh auth refresh -h github.com -s read:packages
```

Danach dem Build über die Umgebung mitgeben, weil `settings.gradle.kts` als Fallback
`GITHUB_ACTOR` / `GITHUB_TOKEN` liest:

```bash
GITHUB_ACTOR=ysamjo GITHUB_TOKEN="$(/opt/homebrew/bin/gh auth token)" ./gradlew :patches:buildAndroid
```

**Variante B — klassischer PAT** (empfohlen, stabil):

1. Anlegen unter <https://github.com/settings/tokens/new?scopes=read:packages&description=Morphe>
   mit dem Scope `read:packages`.
2. In `~/.gradle/gradle.properties` eintragen (Datei anlegen, falls nicht vorhanden):

```properties
gpr.user = ysamjo
gpr.key = ghp_xxxxxxxxxxxxxxxxxxxx
```

Das ist der Weg, den die Morphe-Doku empfiehlt, und er gilt projektübergreifend.

### Wenn beide Quellen leer sind, kommt eine irreführende Meldung

Fehlen sowohl `gpr.user`/`gpr.key` als auch `GITHUB_ACTOR`/`GITHUB_TOKEN`, bricht der Build
nicht mit einem Auth-Fehler ab, sondern mit:

```
Failed to apply plugin 'app.morphe.patches'.
> java.lang.IllegalArgumentException (no error message)
```

Der Stacktrace führt auf `SettingsPlugin.configureDependencies` — das Plugin registriert das
GitHub-Packages-Repo und wirft, sobald beide Credential-Quellen leer sind. Mit
`--stacktrace` sieht man es, ohne bleibt die Meldung nichtssagend. Die Lösung ist dieselbe wie
oben: Token setzen. `~/.gradle/gradle.properties` existiert auf einer frischen Maschine oft
gar nicht.

In jeder neuen Shell müssen `GITHUB_ACTOR` und `GITHUB_TOKEN` erneut exportiert werden — sie
überleben kein Terminal-Fenster. Deshalb ist Variante B (Datei) die bequemere.

## Voraussetzungen

| | |
| --- | --- |
| JDK | 21 oder neuer (verifiziert mit JBR 21.0.11) |
| Android SDK | nötig für das Extension-Modul — `ANDROID_HOME` oder `~/Library/Android/sdk` |
| Gradle | kommt über den Wrapper (9.8.0), keine Installation nötig |
| Netzwerk | der erste Build lädt Gradle sowie Patcher- und Smali-Abhängigkeiten |

### Android-SDK-Pfad eintragen

Das Extension-Modul ist ein Android-Library-Modul und braucht eine SDK-Angabe. Ohne sie
bricht der Build mit `SDK location not found` ab. `local.properties` im Projekt-Root anlegen
(die Datei ist gitignored und maschinenspezifisch):

```properties
sdk.dir=/Users/family/Library/Android/sdk
```

Alternativ `ANDROID_HOME` exportieren. Auf CI-Runnern ist beides bereits gesetzt.

## Bauen

```bash
./gradlew :patches:buildAndroid
```

Ergebnis: `patches/build/libs/patches-<version>.mpp` — **ein** Bundle für alle drei Apps.

**Verifiziert am 2026-10-10:** Build läuft durch (Gradle 9.8.0, JBR 21.0.11) und erzeugt
`patches-1.0.0.mpp` (824 KB). Das Bundle enthält alle vier Patch-Klassen, alle Fingerprints und
`extensions/extension.mpe`; `generatePatchesList` listet vier Patches in drei App-Gruppen mit
den erwarteten Zielversionen. Apply-Läufe gegen echte APKs sind in
[`docs/ZUSAMMENFUEHRUNG.md`](ZUSAMMENFUEHRUNG.md) protokolliert.

Ein `clean` ist Pflicht, sobald sich die Extension geändert hat: `:patches:buildAndroid`
betrachtet die gebaute `.mpe` nicht als Input und hält den Task sonst für „up-to-date".

```bash
./gradlew clean :patches:buildAndroid
```

Alle Patches des Bundles auflisten, ohne zu patchen — der schnellste Smoke-Test:

```bash
java -jar /tmp/morphe.jar list-patches --patches=patches/build/libs/patches-*.mpp -p -v -o
```

## Lokal patchen mit Morphe Desktop

Das fertige JAR aus den Releases reicht — der Selbstbau aus dem Quelltext ist nicht nötig:

```bash
gh release download v1.18.0 -R MorpheApp/morphe-cli \
  -p "morphe-desktop-1.18.0-all.jar" -O /tmp/morphe.jar
```

`-e` aktiviert einen Patch über seinen **Namen**, `-O key=value` setzt eine Option. Ohne `-e`
wird nichts angewandt — die Patches sind bewusst fast alle `default = false`.

### YouTube for Android TV

Beide Splits in ein ZIP legen; der Patcher merged das Bundle selbst und signiert das Ergebnis:

```bash
zip -q -0 youtube-tv.apkm base.apk split_config.armeabi_v7a.apk

java -Xms1024m -jar /tmp/morphe.jar patch \
  --patches patches/build/libs/patches-*.mpp \
  -e "Cobalt-Start-URL ändern" \
  -e "Werbe-Blocker-Userscript einspritzen" \
  -O "startupUrl=https://www.youtube.com/tv" \
  -o /tmp/youtube-tv-patched.apk \
  youtube-tv.apkm
```

### TizenTube Cobalt

Einzelne APK, nur die Injektion — die Start-URL bleibt unangetastet:

```bash
gh release download v2.0.2 -R reisxd/TizenTubeCobalt -p "cobalt-arm.apk" -O /tmp/tizentube.apk

java -Xms1024m -jar /tmp/morphe.jar patch \
  --patches patches/build/libs/patches-*.mpp \
  -e "Werbe-Blocker-Userscript einspritzen" \
  -e "MOD-Kennzeichnung für Icon und Banner" \
  -o /tmp/tizentube-patched.apk \
  /tmp/tizentube.apk
```

Das Gerät ist `armeabi-v7a` — immer die arm-Variante nehmen, nicht arm64.

### RTL+

Drei Splits ins `.apkm`:

```bash
zip -q -0 rtlplus.apkm base.apk split_config.armeabi_v7a.apk split_config.xhdpi.apk

java -Xms1024m -jar /tmp/morphe.jar patch \
  --patches patches/build/libs/patches-*.mpp \
  -e "RTL+ Werbung deaktivieren (Yospace DAI)" \
  -o /tmp/rtlplus-patched.apk \
  rtlplus.apkm
```

Achtung: Bei `list-patches` ist `-p` mit `--with-packages` belegt, die Patch-Datei geht dort
nur über `--patches=`. Beim Unterbefehl `patch` ist `-p` dagegen die Kurzform für `--patches`.

## Als Morphe-Patch-Quelle veröffentlichen

Der Morphe Manager akzeptiert eine GitHub-Repository-URL direkt:

```
github.com/ysamjo/morphe-patches
```

oder den Deep-Link:

```
https://morphe.software/add-source?github=ysamjo/morphe-patches
```

Der Manager liest `patches-bundle.json` aus dem Repository und die `.mpp` aus dessen Releases.
Alle drei Apps erscheinen als eigene Gruppe.

### Repo anlegen

```bash
cd ~/Developer/morphe-patches
gh repo create ysamjo/morphe-patches --public --source=. --remote=origin
git push -u origin main
git push origin main:dev
```

Das Template bringt beide Branches mit und `.releaserc` erwartet sie: gearbeitet wird auf
`dev`, stabile Releases kommen aus `main`. `dev` mitpushen, sonst hat der Prerelease-Kanal
nichts zu bauen.

### Eine Einstellung im Repo

**Settings → Actions → General → Workflow permissions →**
*Allow GitHub Actions to create and approve pull requests* muss aktiv sein. Das braucht
`open_pull_request.yml`, um bei Commits auf `dev` automatisch einen Pull-Request
`dev → main` zu öffnen (dessen Merge dann das stabile Release auslöst). `release.yml`
schreibt Changelog und Patch-Liste übrigens unabhängig davon über `contents: write` im
eigenen Job zurück — diese Einstellung betrifft ausschließlich das Öffnen von PRs.

Der Workflow selbst braucht **keine Secrets**: Er authentifiziert sich gegenüber GitHub
Packages mit dem eingebauten `GITHUB_TOKEN`. Deshalb läuft der CI-Build auch dann, wenn ein
lokaler Build deinen PAT braucht.

### Release-Ablauf

- Commit-Messages steuern es: `fix:` → Patch-Release, `feat:` → Minor, `chore:` → kein Release.
- Pushes auf `dev` erzeugen Prereleases (Nutzer aktivieren *Pre-release patches* im Manager).
- Merge von `dev` nach `main` erzeugt ein stabiles Release.

Releases nie von Hand anlegen oder ändern — `release.yml` schreibt außerdem `CHANGELOG.md`,
`gradle.properties`, `patches-bundle.json`, `patches-list.json` und die Patch-Liste im README
neu.

### Drei Stolperfallen, die beim Setup aufgetreten sind

**1. `release.yml` fehlen Scopes.** Der `success`-Hook von `@semantic-release/github`
kommentiert zugehörige Issues und Pull Requests. Das `permissions`-Block des Templates vergibt
nur `contents`, `packages`, `id-token` und `attestations` — der Lauf bricht dann **nach** dem
erfolgreichen Release mit `Resource not accessible by integration`
(`path: repository.issues`) ab. Tag und Release-Asset sind zu dem Zeitpunkt schon geschrieben,
der rote Haken ist also irreführend. In diesem Repo behoben; bei einem neuen Template-Klon
`issues: write` und `pull-requests: write` ergänzen.

**2. `dev` und `main` nicht gleichzeitig pushen.** Beide Läufe erzeugen ihren eigenen
Release-Commit auf den generierten Dateien (`CHANGELOG.md`, `README.md`,
`gradle.properties`, `patches-bundle.json`, `patches-list.json`). Erst auf `dev` arbeiten,
dann nach `main` mergen und pushen — nicht beide in einem Rutsch.

**3. Kein Backmerge-Plugin — `dev` läuft `main` systematisch hinterher.** Das Template bringt
`@cleyrop-org/semantic-release-backmerge` mit, das `main` automatisch nach `dev` zurückführt.
Es rebased dabei und kollidiert zwangsläufig in genau den versionsgestempelten generierten
Dateien aus Punkt 2. In diesem Repo ist es aus `.releaserc` entfernt (das Paket steht noch in
`package.json`, wird aber nicht mehr geladen). Wer es zurückholt, holt sich den Konflikt zurück.

Die Kehrseite: **nach jedem Release** liegt auf `main` ein Release-Commit, den `dev` nicht hat.
Das ist der Normalzustand, kein Fehler — aber vor der nächsten Arbeit gehört `dev` nachgezogen:

```bash
git checkout dev
git merge origin/main -X theirs    # generierte Dateien von main gewinnen lassen
git push origin dev
```

Ohne diesen Schritt baut der nächste Prerelease auf einem veralteten `gradle.properties` auf,
und `dev` und `main` driften über mehrere Releases auseinander.

Falls es doch passiert ist: `main` nach `dev` nachziehen und `dev` auf den stabilen Stand
zurücksetzen, damit beide inhaltsgleich sind.

### Sichtbarkeit des Repos

Eine Patch-Quelle für den Morphe Manager muss **öffentlich** sein — der Manager liest
`patches-bundle.json` und das `.mpp` ohne Authentifizierung. Ein privates Repo funktioniert
nur mit hinterlegtem Token im Manager. Umschalten mit:

```bash
gh repo edit ysamjo/morphe-patches --visibility public --accept-visibility-change-consequences
```

## Fehlerbehebung

| Symptom | Ursache |
| --- | --- |
| `Plugin [id: 'app.morphe.patches'] was not found` | Kein Token mit `read:packages`. Siehe oben. |
| `Failed to apply plugin 'app.morphe.patches'` / `IllegalArgumentException (no error message)` | Weder `gpr.user`/`gpr.key` noch `GITHUB_ACTOR`/`GITHUB_TOKEN` gesetzt. Mit `--stacktrace` steht `SettingsPlugin.configureDependencies` im Trace. |
| `SDK location not found` | Android SDK fehlt oder `ANDROID_HOME` nicht gesetzt — betrifft nur das Extension-Modul. |
| `Could not resolve app.morphe:morphe-patcher` | Token ohne den Scope, oder GitHub Packages drosselt. Erneut versuchen. |
| `Invalid register: vNN. Must be between v0 and v15` | Injizierte Instruktion nutzt `invoke-static` (Format 35c) mit einem Register über v15. `invoke-static/range` verwenden. |
| `PatchException: Package <pkg> not found` beim Bildtausch | `rawResourcePatch` statt `resourcePatch` verwendet — `get("res/…")` löst nur im Full-Resource-Modus auf. |
| Patch angewandt, wirkt aber nicht | Veraltete Extension im Bundle. `./gradlew clean :patches:buildAndroid`. |
| Release-Workflow tut nichts | Commit-Typ war `chore:`, oder der Push ging auf einen anderen Branch als `main` / `dev`. |
